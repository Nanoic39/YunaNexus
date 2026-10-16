package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.config.FileProperties;
import cc.nanoic.yunanexus.file.config.ShardContextHolder;
import cc.nanoic.yunanexus.file.domain.FileRange;
import cc.nanoic.yunanexus.file.domain.FileView;
import cc.nanoic.yunanexus.file.entity.FileMeta;
import cc.nanoic.yunanexus.file.mapper.FileMetaMapper;
import cc.nanoic.yunanexus.file.shard.ShardRouter;
import cc.nanoic.yunanexus.file.storage.FileStorage;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Supplier;

/** 本人文件边界：不继承管理员或部门可见范围。 */
@Service
public class PersonalFileService {
    private final FileMetaMapper mapper;
    private final ShardRouter router;
    private final FileProperties properties;
    private final FileStorage storage;
    private final FileService files;
    private final cc.nanoic.yunanexus.file.mapper.FileShareMapper shares;

    public PersonalFileService(
            FileMetaMapper mapper,
            ShardRouter router,
            FileProperties properties,
            FileStorage storage,
            FileService files,
            cc.nanoic.yunanexus.file.mapper.FileShareMapper shares) {
        this.mapper = mapper;
        this.router = router;
        this.properties = properties;
        this.storage = storage;
        this.files = files;
        this.shares = shares;
    }

    public <T> T inShard(int shard, Supplier<T> action) {
        Integer previous = ShardContextHolder.get();
        ShardContextHolder.set(shard);
        try {
            return action.get();
        } finally {
            if (previous == null) ShardContextHolder.clear();
            else ShardContextHolder.set(previous);
        }
    }

    public FileMeta requireOwned(long fileId, long userId) {
        return inShard(
                router.route(fileId),
                () -> {
                    FileMeta meta = mapper.selectByFileId(fileId);
                    if (meta == null
                            || !Integer.valueOf(1).equals(meta.getStatus())
                            || !Long.valueOf(userId).equals(meta.getCreateBy()))
                        throw new BizException(ErrorCode.FILE_NOT_FOUND);
                    return meta;
                });
    }

    public PageResult<FileView> list(long userId, int page, int size, String search) {
        if (page < 1 || page > 500 || size < 1 || size > 100 || search.length() > 100)
            throw new BizException(ErrorCode.BAD_REQUEST, "分页或搜索条件无效");
        int limit = page * size;
        long total = 0;
        List<FileMeta> rows = new ArrayList<>();
        for (int i = 0; i < properties.getShard().getPhysicalCount(); i++) {
            final int index = i;
            total += inShard(index, () -> mapper.countOwned(userId, search));
            rows.addAll(inShard(index, () -> mapper.selectOwned(userId, search, limit)));
        }
        rows.sort(Comparator.comparing(FileMeta::getFileId).reversed());
        return PageResult.of(
                rows.stream()
                        .skip((long) (page - 1) * size)
                        .limit(size)
                        .map(FileView::from)
                        .toList(),
                total,
                page,
                size);
    }

    /** 修改本人文件的展示名称，存储路径、内容及扩展名保持不变。 */
    public FileView rename(long fileId, long userId, String requestedName) {
        if (fileId <= 0 || userId <= 0) throw new BizException(ErrorCode.FILE_NOT_FOUND);
        String name = requestedName == null ? "" : requestedName.strip();
        if (name.isBlank()
                || name.length() > 255
                || name.equals(".")
                || name.equals("..")
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件名须为 1~255 个字符，不能包含路径或控制字符");
        }
        FileMeta meta = requireOwned(fileId, userId);
        if (!extension(meta.getFileName()).equals(extension(name))) {
            throw new BizException(ErrorCode.BAD_REQUEST, "重命名时请保留原文件扩展名");
        }
        if (!name.equals(meta.getFileName())) {
            int changed =
                    inShard(router.route(fileId), () -> mapper.renameOwned(fileId, userId, name));
            if (changed == 0) throw new BizException(ErrorCode.FILE_NOT_FOUND);
            meta.setFileName(name);
        }
        return FileView.from(meta);
    }

    private String extension(String name) {
        int at = name.lastIndexOf('.');
        return at > 0 ? name.substring(at).toLowerCase(Locale.ROOT) : "";
    }

    /** 分享列表按分片批量读取当前文件名，避免重命名后的旧名称和逐条查询。 */
    public Map<Long, String> ownedNames(long userId, Collection<Long> ids) {
        if (ids.size() > 200) throw new BizException(ErrorCode.BAD_REQUEST);
        Map<Integer, List<Long>> groups = new HashMap<>();
        for (Long id : new HashSet<>(ids))
            groups.computeIfAbsent(router.route(id), key -> new ArrayList<>()).add(id);
        Map<Long, String> names = new HashMap<>();
        groups.forEach(
                (shard, group) ->
                        inShard(
                                shard,
                                () -> {
                                    for (FileMeta file : mapper.selectOwnedNames(userId, group))
                                        names.put(file.getFileId(), file.getFileName());
                                    return null;
                                }));
        return names;
    }

    public FileRange download(long fileId, long userId) {
        FileMeta meta = requireOwned(fileId, userId);
        return new FileRange(
                meta.getFileName(),
                meta.getContentType(),
                meta.getSize(),
                0,
                meta.getSize() - 1,
                false,
                storage.open(meta.getStoragePath()));
    }

    public void delete(long fileId, long userId) {
        requireOwned(fileId, userId);
        files.deleteOwned(fileId, userId);
        inShard(0, () -> shares.revokeFile(fileId, userId));
    }
}
