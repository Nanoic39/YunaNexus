package cc.nanoic.yunanexus.file.service.impl;

import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.datascope.DataScopeContext;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.client.MetaIdClient;
import cc.nanoic.yunanexus.file.client.PermissionClient;
import cc.nanoic.yunanexus.file.config.FileProperties;
import cc.nanoic.yunanexus.file.config.ShardContextHolder;
import cc.nanoic.yunanexus.file.domain.ChunkInitRequest;
import cc.nanoic.yunanexus.file.domain.ChunkInitResult;
import cc.nanoic.yunanexus.file.domain.ChunkUploadStatus;
import cc.nanoic.yunanexus.file.domain.DataScopeView;
import cc.nanoic.yunanexus.file.domain.FileRange;
import cc.nanoic.yunanexus.file.domain.FileView;
import cc.nanoic.yunanexus.file.domain.UploadSession;
import cc.nanoic.yunanexus.file.entity.FileMeta;
import cc.nanoic.yunanexus.file.service.FileQuotaService;
import cc.nanoic.yunanexus.file.mapper.FileMetaMapper;
import cc.nanoic.yunanexus.file.service.FileService;
import cc.nanoic.yunanexus.file.shard.ShardRoute;
import cc.nanoic.yunanexus.file.shard.ShardRouter;
import cc.nanoic.yunanexus.file.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件中心服务实现（roadmap 7.3 / 7.4，含分片上传 / 秒传 / Range 增强）。<p>
 *
 * 单次上传：校验 → 号段取号 → {@code fileId % logicalCount} 决策分片 → 内容落
 * {@link FileStorage} → 元数据落库。<br>
 * 分片上传：初始化（秒传查重）→ 逐片上传 → 合并（再查重）→ 元数据落库。<br>
 * 下载：按 {@link ShardRoute} 路由，支持 HTTP Range 区间；删除：逻辑删除元数据 +
 * 引用计数归零后物理删除内容。<br>
 * 列表：跨物理库扫描，叠加 {@code @DataScope} 行级过滤（L4 试点）。
 *
 * @author yunanexus
 */
@Service
public class FileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    /** 号段业务标识（元数据中心 {@code id_segment.biz_tag}） */
    private static final String BIZ_TAG = "file";

    /** 默认每页大小 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页大小上限 */
    private static final int MAX_PAGE_SIZE = 100;

    private final FileMetaMapper fileMetaMapper;
    private final FileStorage fileStorage;
    private final FileProperties properties;
    private final ShardRouter shardRouter;
    private final MetaIdClient metaIdClient;
    private final PermissionClient permissionClient;
    private final ObjectMapper objectMapper;
    private final FileQuotaService quotaService;

    public FileServiceImpl(FileMetaMapper fileMetaMapper,
                           FileStorage fileStorage,
                           FileProperties properties,
                           ShardRouter shardRouter,
                           MetaIdClient metaIdClient,
                           PermissionClient permissionClient,
                           ObjectMapper objectMapper,
                           FileQuotaService quotaService) {
        this.fileMetaMapper = fileMetaMapper;
        this.fileStorage = fileStorage;
        this.properties = properties;
        this.shardRouter = shardRouter;
        this.metaIdClient = metaIdClient;
        this.permissionClient = permissionClient;
        this.objectMapper = objectMapper;
        this.quotaService = quotaService;
    }

    @Override
    public FileView upload(MultipartFile file, long userId) {
        validate(file);
        // 配额在进入存储前校验：宁可让用户在上传前被拒，也不要白传几 G 再失败
        quotaService.ensure(userId, file.getSize());
        String originalName = normalize(file.getOriginalFilename());
        if (originalName.isEmpty()) {
            originalName = "unnamed";
        }
        String ext = extractExt(originalName);
        rejectDangerous(ext);
        long fileId = nextFileId();
        int shardIndex = shardRouter.route(fileId);
        Long deptId = resolveDeptId(userId);

        FileStorage.StoredObject stored;
        try (InputStream in = file.getInputStream()) {
            stored = fileStorage.store(in, ext);
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "读取上传流失败: " + e.getMessage());
        }
        quotaService.addUsage(userId, stored.size());

        FileMeta meta = buildMeta(fileId, shardIndex, originalName, ext, file.getContentType(),
                stored.size(), stored.path(), stored.sha256(), userId, deptId);
        try {
            FileMeta persisted = persistMeta(meta);
            log.info("文件上传成功 fileId={} shardIndex={} size={} user={}", fileId, shardIndex, stored.size(), userId);
            return FileView.from(persisted);
        } catch (RuntimeException e) {
            // 落库失败：清理已写入的存储内容，避免产生孤儿文件
            fileStorage.delete(stored.path());
            throw e;
        }
    }

    @Override
    @ShardRoute
    public FileRange download(long fileId, long userId, Long start, Long end) {
        FileMeta meta = requireVisible(fileId, userId);
        return resolveRange(meta, start, end);
    }

    @Override
    @ShardRoute
    public void delete(long fileId, long userId) {
        deleteMeta(requireVisible(fileId,userId),userId);
    }

    @Override
    @ShardRoute
    public void deleteOwned(long fileId,long userId) {
        FileMeta meta=fileMetaMapper.selectByFileId(fileId);
        if(meta==null || !Integer.valueOf(FileMeta.STATUS_NORMAL).equals(meta.getStatus()) || !Long.valueOf(userId).equals(meta.getCreateBy()))
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        deleteMeta(meta,userId);
    }

    private void deleteMeta(FileMeta meta,long userId) {
        long fileId=meta.getFileId();
        meta.setStatus(FileMeta.STATUS_DELETED);
        fileMetaMapper.updateById(meta);
        // 秒传复用的内容被多条元数据共享：仅当无其他有效引用时才物理删除
        if (countStoragePathAcrossShards(meta.getStoragePath()) == 0) {
            if (!fileStorage.delete(meta.getStoragePath())) {
                log.warn("元数据已逻辑删除，但存储内容删除失败 fileId={} path={}", fileId, meta.getStoragePath());
            }
        } else {
            log.info("存储内容仍被其他文件引用，保留物理内容 fileId={} path={}", fileId, meta.getStoragePath());
        }
        // 释放配额：只在逻辑删除时扣一次（重复删除会被上面的状态校验挡掉）
        Long owner = meta.getCreateBy();
        if (owner != null) {
            quotaService.removeUsage(owner, meta.getSize() == null ? 0L : meta.getSize());
        }
        log.info("文件删除成功 fileId={} user={}", fileId, userId);
    }

    @Override
    public PageResult<FileView> listVisible(long userId, int page, int size) {
        int p = normalizePage(page);
        int s = normalizeSize(size);
        DataScopeContext.Scope scope = resolveScope(userId);
        int physicalCount = properties.getShard().getPhysicalCount();
        List<FileView> views = new ArrayList<>();
        for (int index = 0; index < physicalCount; index++) {
            // 逐物理库设置路由上下文后扫描；physicalIndex < physicalCount，取模后即自身
            final int physicalIndex = index;
            views.addAll(DataScopeContext.execute(scope, () -> {
                ShardContextHolder.set(physicalIndex);
                try {
                    return fileMetaMapper.selectVisible().stream().map(FileView::from).toList();
                } finally {
                    ShardContextHolder.clear();
                }
            }));
        }
        long total = views.size();
        if (total == 0) {
            return PageResult.empty(p, s);
        }
        // 跨物理库汇总后在内存中分页（各库数据量可控，仅作展示用途）
        int from = Math.min((p - 1) * s, views.size());
        int to = Math.min(from + s, views.size());
        return PageResult.of(new ArrayList<>(views.subList(from, to)), total, p, s);
    }

    // ---------- 分片上传 ----------

    @Override
    public ChunkInitResult initChunkUpload(ChunkInitRequest request, long userId) {
        // 分片初始化阶段就校验配额：这是用户决定"要不要传"的最后时机
        quotaService.ensure(userId, request.getTotalSize());
        String fileName = normalize(request.getFileName());
        String ext = extractExt(fileName);
        validateChunkInit(request, fileName, ext);

        long fileId = nextFileId();
        int shardIndex = shardRouter.route(fileId);
        Long deptId = resolveDeptId(userId);

        // 秒传：客户端提供内容指纹且命中已有文件时，直接复用存储、落一条新元数据
        if (request.getSha256() != null && !request.getSha256().isBlank() && request.getTotalSize() > 0) {
            FileMeta existing = findBySha256AcrossShards(request.getSha256(), request.getTotalSize());
            if (existing != null && Long.valueOf(userId).equals(existing.getCreateBy())) {
                FileMeta meta = buildMeta(fileId, shardIndex, fileName, ext, request.getContentType(),
                        existing.getSize(), existing.getStoragePath(), existing.getSha256(), userId, deptId);
                FileMeta persisted = persistMeta(meta);
                log.info("秒传命中 fileId={} sha256={} user={}", fileId, request.getSha256(), userId);
                return new ChunkInitResult(null, true, List.of(), FileView.from(persisted));
            }
        }

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        UploadSession session = new UploadSession();
        session.setUploadId(uploadId);
        session.setFileName(fileName);
        session.setExt(ext);
        session.setContentType(request.getContentType());
        session.setTotalChunks(request.getTotalChunks());
        session.setTotalSize(request.getTotalSize());
        session.setUserId(userId);
        session.setDeptId(deptId);
        session.setFileId(fileId);
        session.setShardIndex(shardIndex);
        session.setCreateTime(LocalDateTime.now(java.time.ZoneOffset.UTC));
        fileStorage.storeChunkSession(uploadId, encodeSession(session));
        log.info("分片上传初始化 uploadId={} fileId={} totalChunks={} user={}", uploadId, fileId, request.getTotalChunks(), userId);
        return new ChunkInitResult(uploadId, false, List.of(), null);
    }

    @Override
    public void uploadChunk(String uploadId, int index, MultipartFile chunk) {
        UploadSession session = loadSession(uploadId);
        if (index < 0 || index >= session.getTotalChunks()) {
            throw new BizException(ErrorCode.FILE_CHUNK_INDEX_INVALID,
                    "下标 " + index + "，总分片 " + session.getTotalChunks());
        }
        if (chunk == null || chunk.isEmpty()) {
            throw new BizException(ErrorCode.FILE_EMPTY, "分片内容为空");
        }
        long maxChunk = properties.getChunk().getMaxChunkSize();
        if (maxChunk > 0 && chunk.getSize() > maxChunk) {
            throw new BizException(ErrorCode.FILE_SIZE_EXCEED,
                    "单分片上限 " + maxChunk + " 字节，实际 " + chunk.getSize() + " 字节");
        }
        try (InputStream in = chunk.getInputStream()) {
            fileStorage.storeChunk(uploadId, index, in);
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_CHUNK_UPLOAD_FAILED, "读取分片流失败: " + e.getMessage());
        }
    }

    @Override
    public ChunkUploadStatus chunkStatus(String uploadId) {
        UploadSession session = loadSession(uploadId);
        Set<Integer> uploaded = fileStorage.listChunks(uploadId);
        return new ChunkUploadStatus(uploadId, session.getTotalChunks(), List.copyOf(uploaded));
    }

    @Override
    public FileView completeChunkUpload(String uploadId, long userId) {
        UploadSession session = loadSession(uploadId);
        if (session.getUserId() != userId) {
            throw new BizException(ErrorCode.FILE_ACCESS_DENIED, "上传会话不属于当前用户");
        }
        FileStorage.StoredObject merged =
                fileStorage.mergeChunks(uploadId, session.getTotalChunks(), session.getExt());

        // A3：合并后的真实大小必须与声明一致。声明值参与了配额与上限判断，
        // 不一致意味着要么客户端撒谎，要么分片缺失/被截断，两种都不能入库。
        long declaredSize = session.getTotalSize();
        if (declaredSize > 0 && merged.size() != declaredSize) {
            fileStorage.delete(merged.path());
            throw new BizException(ErrorCode.FILE_SIZE_EXCEED,
                    "合并后大小 " + merged.size() + " 与声明 " + declaredSize + " 不一致");
        }

        // 合并后再查重：并发上传的相同内容可复用已有存储，丢弃本次合并副本
        String storagePath = merged.path();
        boolean reused = false;
        ShardContextHolder.set(session.getShardIndex());
        try {
            FileMeta duplicate = fileMetaMapper.selectBySha256AndSize(merged.sha256(), merged.size());
            if (duplicate != null) {
                storagePath = duplicate.getStoragePath();
                reused = true;
            }
        } finally {
            ShardContextHolder.clear();
        }
        if (reused) {
            fileStorage.delete(merged.path());
        }

        FileMeta meta = buildMeta(session.getFileId(), session.getShardIndex(), session.getFileName(),
                session.getExt(), session.getContentType(), merged.size(), storagePath, merged.sha256(),
                session.getUserId(), session.getDeptId());
        try {
            FileMeta persisted = persistMeta(meta);
            quotaService.addUsage(session.getUserId(), merged.size());
            log.info("分片上传完成 fileId={} chunks={} size={} user={}",
                    session.getFileId(), session.getTotalChunks(), merged.size(), userId);
            return FileView.from(persisted);
        } catch (RuntimeException e) {
            if (!reused) {
                fileStorage.delete(merged.path());
            }
            throw e;
        }
    }

    @Override
    public void abortChunkUpload(String uploadId) {
        loadSession(uploadId);
        fileStorage.abortChunks(uploadId);
        log.info("分片上传已放弃 uploadId={}", uploadId);
    }

    @Override
    public int cleanupExpiredChunkUploads() {
        long ttl = properties.getChunk().getSessionTtlSeconds();
        LocalDateTime deadline = LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(ttl);
        int removed = 0;
        for (String uploadId : fileStorage.listChunkUploads()) {
            try {
                byte[] raw = fileStorage.loadChunkSession(uploadId);
                UploadSession session = raw == null ? null : decodeSession(raw);
                if (session == null || session.getCreateTime() == null
                        || session.getCreateTime().isBefore(deadline)) {
                    fileStorage.abortChunks(uploadId);
                    removed++;
                }
            } catch (RuntimeException e) {
                log.warn("清理上传会话失败 uploadId={} ({})", uploadId, e.getMessage());
            }
        }
        if (removed > 0) {
            log.info("清理过期分片上传会话 {} 个", removed);
        }
        return removed;
    }

    @Override
    public void requireChunkOwner(String uploadId,long userId) {
        if(!Long.valueOf(userId).equals(loadSession(uploadId).getUserId()))
            throw new BizException(ErrorCode.FILE_ACCESS_DENIED);
    }

    // ---------- 校验与工具 ----------

    /** 校验单次上传参数（空文件 / 大小上限 / 扩展名白名单）。 */
    /**
     * 高危扩展名一律拒绝（A5 的前置硬规则）。
     *
     * <p>扫描服务不可用时采用 fail-open（放行 + 标记待扫），但"已知可执行/脚本类"
     * 不属于"无法判定"，因此这条规则不随扫描可用性变化。
     *
     * @param ext 小写扩展名
     */
    private void rejectDangerous(String ext) {
        if (ext == null || ext.isEmpty()) {
            return;
        }
        List<String> dangerous = properties.getUpload().getDangerousExtensions();
        if (dangerous != null && dangerous.contains(ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "高危文件类型: " + ext);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.FILE_EMPTY);
        }
        String name=file.getOriginalFilename();
        if(name!=null && (name.length()>255 || name.codePoints().anyMatch(Character::isISOControl)))
            throw new BizException(ErrorCode.BAD_REQUEST,"文件名无效或过长");
        long maxSize = properties.getUpload().getMaxSize();
        if (maxSize > 0 && file.getSize() > maxSize) {
            throw new BizException(ErrorCode.FILE_SIZE_EXCEED,
                    "上限 " + maxSize + " 字节，实际 " + file.getSize() + " 字节");
        }
        List<String> allowed = properties.getUpload().getAllowedExtensions();
        if (allowed != null && !allowed.isEmpty()) {
            String ext = extractExt(file.getOriginalFilename());
            if (ext == null || !allowed.contains(ext)) {
                throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "扩展名: " + ext);
            }
        }
    }

    /** 校验分片上传初始化参数（文件名 / 分片数 / 总大小 / 扩展名白名单）。 */
    private void validateChunkInit(ChunkInitRequest request, String fileName, String ext) {
        if (fileName.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件名不能为空");
        }
        if (request.getTotalChunks() <= 0) {
            throw new BizException(ErrorCode.FILE_CHUNK_INDEX_INVALID, "分片总数必须大于 0");
        }
        // 分片总数必须有上界：客户端可伪造超大 totalChunks，配合稀疏下标把会话/元数据撑爆
        int maxChunks = properties.getChunk().getMaxChunks();
        if (maxChunks > 0 && request.getTotalChunks() > maxChunks) {
            throw new BizException(ErrorCode.FILE_CHUNK_INDEX_INVALID,
                    "分片总数 " + request.getTotalChunks() + " 超过上限 " + maxChunks);
        }
        // 分片数 × 单分片上限 必须能覆盖声明总大小，避免"1 个分片声称 5GB"这类畸形请求
        long singleMax = properties.getChunk().getMaxChunkSize();
        if (singleMax > 0 && request.getTotalSize() > (long) request.getTotalChunks() * singleMax) {
            throw new BizException(ErrorCode.FILE_SIZE_EXCEED,
                    "声明大小 " + request.getTotalSize() + " 与分片数 " + request.getTotalChunks() + " 不匹配");
        }
        long maxSize = properties.getChunk().getMaxSize();
        if (maxSize > 0 && request.getTotalSize() > maxSize) {
            throw new BizException(ErrorCode.FILE_SIZE_EXCEED,
                    "上限 " + maxSize + " 字节，实际 " + request.getTotalSize() + " 字节");
        }
        List<String> allowed = properties.getUpload().getAllowedExtensions();
        if (allowed != null && !allowed.isEmpty() && (ext == null || !allowed.contains(ext))) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "扩展名: " + ext);
        }
        rejectDangerous(ext);
    }

    /** 解析下载区间并打开对应内容流（{@code start} 为空表示整文件）。 */
    private FileRange resolveRange(FileMeta meta, Long start, Long end) {
        long total = meta.getSize() == null ? 0 : meta.getSize();
        if (start == null) {
            InputStream content = fileStorage.open(meta.getStoragePath());
            return new FileRange(meta.getFileName(), meta.getContentType(), total, 0,
                    Math.max(total - 1, 0), false, content);
        }
        if (start < 0 || start >= total) {
            throw new BizException(ErrorCode.FILE_RANGE_NOT_SATISFIABLE,
                    "起始偏移 " + start + "，文件大小 " + total);
        }
        long resolvedEnd = (end == null || end >= total) ? total - 1 : end;
        if (resolvedEnd < start) {
            throw new BizException(ErrorCode.FILE_RANGE_NOT_SATISFIABLE,
                    "区间 " + start + "-" + resolvedEnd + "，文件大小 " + total);
        }
        long length = resolvedEnd - start + 1;
        InputStream content = fileStorage.open(meta.getStoragePath(), start, length);
        boolean partial = !(start == 0 && resolvedEnd == total - 1);
        return new FileRange(meta.getFileName(), meta.getContentType(), total, start, resolvedEnd, partial, content);
    }

    /** 读取并校验上传会话。 */
    private UploadSession loadSession(String uploadId) {
        byte[] raw = fileStorage.loadChunkSession(uploadId);
        UploadSession session = raw == null ? null : decodeSession(raw);
        if (session == null) {
            throw new BizException(ErrorCode.FILE_UPLOAD_SESSION_NOT_FOUND);
        }
        return session;
    }

    private byte[] encodeSession(UploadSession session) {
        try {
            return objectMapper.writeValueAsBytes(session);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.FILE_UPLOAD_SESSION_NOT_FOUND, "序列化上传会话失败: " + e.getMessage());
        }
    }

    private UploadSession decodeSession(byte[] raw) {
        try {
            return objectMapper.readValue(raw, UploadSession.class);
        } catch (RuntimeException e) {
            log.warn("解析上传会话失败: {}", e.getMessage());
            return null;
        }
    }

    /** 跨物理库按内容指纹查重（秒传），命中返回任一有效元数据。 */
    private FileMeta findBySha256AcrossShards(String sha256, long size) {
        int physicalCount = properties.getShard().getPhysicalCount();
        Integer previous = ShardContextHolder.get();
        try {
            for (int index = 0; index < physicalCount; index++) {
                ShardContextHolder.set(index);
                FileMeta hit = fileMetaMapper.selectBySha256AndSize(sha256, size);
                if (hit != null) {
                    return hit;
                }
            }
        } finally {
            restoreShard(previous);
        }
        return null;
    }

    /** 跨物理库统计同一存储路径的有效引用数（保存 / 恢复分片上下文）。 */
    private long countStoragePathAcrossShards(String storagePath) {
        int physicalCount = properties.getShard().getPhysicalCount();
        Integer previous = ShardContextHolder.get();
        long total = 0;
        try {
            for (int index = 0; index < physicalCount; index++) {
                ShardContextHolder.set(index);
                total += fileMetaMapper.countByStoragePath(storagePath);
            }
        } finally {
            restoreShard(previous);
        }
        return total;
    }

    /** 恢复分片上下文（{@code previous} 为 {@code null} 表示此前无上下文）。 */
    private void restoreShard(Integer previous) {
        if (previous == null) {
            ShardContextHolder.clear();
        } else {
            ShardContextHolder.set(previous);
        }
    }

    /** 构建文件元数据实体。 */
    private FileMeta buildMeta(long fileId, int shardIndex, String fileName, String ext, String contentType,
                               long size, String storagePath, String sha256, long userId, Long deptId) {
        FileMeta meta = new FileMeta();
        meta.setFileId(fileId);
        meta.setShardIndex(shardIndex);
        meta.setFileName(fileName);
        meta.setExt(ext);
        meta.setContentType(contentType);
        meta.setSize(size);
        meta.setStorageType(fileStorage.storageType());
        meta.setStoragePath(storagePath);
        meta.setSha256(sha256);
        meta.setStatus(FileMeta.STATUS_NORMAL);
        meta.setCreateBy(userId);
        meta.setDeptId(deptId);
        return meta;
    }

    /** 落库并回读（设置 / 清理分片上下文）。 */
    private FileMeta persistMeta(FileMeta meta) {
        ShardContextHolder.set(meta.getShardIndex());
        try {
            fileMetaMapper.insert(meta);
            FileMeta persisted = fileMetaMapper.selectByFileId(meta.getFileId());
            return persisted == null ? meta : persisted;
        } finally {
            ShardContextHolder.clear();
        }
    }

    /** 提取小写扩展名（不含点），无扩展名返回 {@code null}。 */
    private String extractExt(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private static int normalizeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** 领取全局文件 ID（号段模式，ADR-001）。 */
    private long nextFileId() {
        Result<Long> result = metaIdClient.nextId(BIZ_TAG);
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "领取全局文件 ID 失败");
        }
        return result.getData();
    }

    /** 查询用户所属部门（失败降级为 {@code null}，不影响上传；「仅本人」范围仍可用）。 */
    private Long resolveDeptId(long userId) {
        try {
            Result<Long> result = permissionClient.userDept(userId);
            if (result != null && result.getCode() == 200) {
                return result.getData();
            }
            log.debug("用户未绑定部门 userId={}", userId);
        } catch (RuntimeException e) {
            log.warn("查询用户部门失败，deptId 置空 userId={} ({})", userId, e.getMessage());
        }
        return null;
    }

    /** 从权限中心解析数据范围，转换为数据权限上下文 Scope。 */
    private DataScopeContext.Scope resolveScope(long userId) {
        DataScopeView view;
        try {
            Result<DataScopeView> result = permissionClient.dataScope(userId);
            if (result == null || result.getCode() != 200 || result.getData() == null) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "解析数据范围失败");
            }
            view = result.getData();
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "调用权限中心解析数据范围失败: " + e.getMessage());
        }
        return new DataScopeContext.Scope(
                view.isAll(),
                view.getDeptIds() == null ? List.of() : view.getDeptIds(),
                view.getSelfUserId());
    }

    /** 按 fileId 读取元数据（分片上下文已由 {@link ShardRoute} 设置）并校验可见性。 */
    private FileMeta requireVisible(long fileId, long userId) {
        FileMeta meta = fileMetaMapper.selectByFileId(fileId);
        if (meta == null || meta.getStatus() == null || meta.getStatus() != FileMeta.STATUS_NORMAL) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!isVisible(resolveScope(userId), meta)) {
            throw new BizException(ErrorCode.FILE_ACCESS_DENIED);
        }
        return meta;
    }

    /** 与 DataScopeSqlBuilder 语义一致：全量可见 / 本人 / 部门范围内。 */
    private boolean isVisible(DataScopeContext.Scope scope, FileMeta meta) {
        if (scope.all()) {
            return true;
        }
        if (scope.selfUserId() != null && scope.selfUserId().equals(meta.getCreateBy())) {
            return true;
        }
        return meta.getDeptId() != null && scope.deptIds().contains(meta.getDeptId());
    }
}
