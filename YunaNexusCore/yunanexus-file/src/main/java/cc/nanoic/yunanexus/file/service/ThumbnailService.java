package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.file.entity.FileObject;
import cc.nanoic.yunanexus.file.entity.UserFile;
import cc.nanoic.yunanexus.file.mapper.FileObjectMapper;
import cc.nanoic.yunanexus.file.mapper.UserFileMapper;
import cc.nanoic.yunanexus.file.support.FileStorageProvider;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Set;

@Service
public class ThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailService.class);
    private static final int THUMB_WIDTH = 256;
    private static final int THUMB_HEIGHT = 256;
    private static final Set<String> SUPPORTED = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp");

    private final FileObjectMapper fileObjectMapper;
    private final UserFileMapper userFileMapper;
    private final FileStorageProvider storageProvider;

    public ThumbnailService(FileObjectMapper fileObjectMapper,
            UserFileMapper userFileMapper,
            FileStorageProvider storageProvider) {
        this.fileObjectMapper = fileObjectMapper;
        this.userFileMapper = userFileMapper;
        this.storageProvider = storageProvider;
    }

    public byte[] getThumbnail(String fileUuid) {
        UserFile userFile = userFileMapper.selectOne(
                new LambdaQueryWrapper<UserFile>()
                        .eq(UserFile::getFileUuid, fileUuid)
                        .eq(UserFile::getDeleteStage, 0));
        if (userFile == null)
            throw new RuntimeException("文件不存在");

        FileObject fo = fileObjectMapper.selectById(userFile.getObjectId());
        if (fo == null)
            throw new RuntimeException("物理文件不存在");
        if (!SUPPORTED.contains(fo.getFileExt().toLowerCase())) {
            throw new RuntimeException("该文件类型不支持缩略图");
        }

        if (fo.getPreviewObjectId() != null) {
            FileObject cache = fileObjectMapper.selectById(fo.getPreviewObjectId());
            if (cache != null) {
                try (InputStream is = storageProvider.read(cache.getObjectKey())) {
                    return is.readAllBytes();
                } catch (Exception e) {
                    log.warn("Cached thumbnail read failed, regenerating");
                }
            }
        }
        return generate(fo);
    }

    @Transactional
    public byte[] generate(FileObject fo) {
        try (InputStream is = storageProvider.read(fo.getObjectKey())) {
            BufferedImage original = ImageIO.read(is);
            if (original == null)
                throw new RuntimeException("无法解码图片");

            BufferedImage scaled = new BufferedImage(THUMB_WIDTH, THUMB_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(original, 0, 0, THUMB_WIDTH, THUMB_HEIGHT, null);
            g.dispose();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(scaled, "jpeg", bos);
            byte[] thumbBytes = bos.toByteArray();
            String thumbHash = computeSha256(thumbBytes);

            FileObject existing = fileObjectMapper.selectOne(
                    new LambdaQueryWrapper<FileObject>()
                            .eq(FileObject::getFileHash, thumbHash)
                            .eq(FileObject::getFileSize, (long) thumbBytes.length)
                            .last("LIMIT 1"));
            if (existing != null) {
                fileObjectMapper.incrementRefCount(existing.getId());
                fo.setPreviewObjectId(existing.getId());
                fileObjectMapper.updateById(fo);
                return thumbBytes;
            }

            String key = storageProvider.generateObjectKey("jpg");
            FileObject preview = new FileObject();
            preview.setObjectUuid(IdUtil.fastSimpleUUID());
            preview.setObjectKey(key);
            preview.setStorageType(0);
            preview.setFileHash(thumbHash);
            preview.setHashAlgo("SHA256");
            preview.setFileSize((long) thumbBytes.length);
            preview.setFileExt("jpg");
            preview.setFileMime("image/jpeg");
            preview.setRefCount(1L);
            preview.setStatus(1);
            fileObjectMapper.insert(preview);
            storageProvider.store(key, new ByteArrayInputStream(thumbBytes), thumbBytes.length);

            fo.setPreviewObjectId(preview.getId());
            fileObjectMapper.updateById(fo);
            return thumbBytes;
        } catch (Exception e) {
            throw new RuntimeException("生成缩略图失败", e);
        }
    }

    private String computeSha256(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(data)) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA256 failed", e);
        }
    }
}