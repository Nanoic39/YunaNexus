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
    public PersonalFileService(FileMetaMapper mapper, ShardRouter router, FileProperties properties,
                               FileStorage storage, FileService files,cc.nanoic.yunanexus.file.mapper.FileShareMapper shares) {
        this.mapper=mapper; this.router=router; this.properties=properties; this.storage=storage; this.files=files; this.shares=shares;
    }
    public <T> T inShard(int shard, Supplier<T> action) {
        Integer previous=ShardContextHolder.get();
        ShardContextHolder.set(shard);
        try { return action.get(); }
        finally { if(previous==null) ShardContextHolder.clear(); else ShardContextHolder.set(previous); }
    }
    public FileMeta requireOwned(long fileId, long userId) {
        return inShard(router.route(fileId), () -> {
            FileMeta meta=mapper.selectByFileId(fileId);
            if(meta==null || !Integer.valueOf(1).equals(meta.getStatus()) || !Long.valueOf(userId).equals(meta.getCreateBy()))
                throw new BizException(ErrorCode.FILE_NOT_FOUND);
            return meta;
        });
    }
    public PageResult<FileView> list(long userId, int page, int size, String search) {
        if(page<1 || page>500 || size<1 || size>100 || search.length()>100)
            throw new BizException(ErrorCode.BAD_REQUEST, "分页或搜索条件无效");
        int limit=page*size; long total=0; List<FileMeta> rows=new ArrayList<>();
        for(int i=0;i<properties.getShard().getPhysicalCount();i++) {
            final int index=i;
            total+=inShard(index,()->mapper.countOwned(userId,search));
            rows.addAll(inShard(index,()->mapper.selectOwned(userId,search,limit)));
        }
        rows.sort(Comparator.comparing(FileMeta::getFileId).reversed());
        return PageResult.of(rows.stream().skip((long)(page-1)*size).limit(size).map(FileView::from).toList(),total,page,size);
    }
    public FileRange download(long fileId,long userId) {
        FileMeta meta=requireOwned(fileId,userId);
        return new FileRange(meta.getFileName(),meta.getContentType(),meta.getSize(),0,meta.getSize()-1,false,storage.open(meta.getStoragePath()));
    }
    public void delete(long fileId,long userId) {
        requireOwned(fileId,userId);
        files.deleteOwned(fileId,userId);
        inShard(0,()->shares.revokeFile(fileId,userId));
    }
}
