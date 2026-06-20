package cc.nanoic.yunanexus.file.mapper;

import cc.nanoic.yunanexus.file.entity.FileStorageQuota;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileStorageQuotaMapper extends BaseMapper<FileStorageQuota> {
}