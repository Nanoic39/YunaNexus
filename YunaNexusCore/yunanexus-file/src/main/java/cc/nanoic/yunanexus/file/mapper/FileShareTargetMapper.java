package cc.nanoic.yunanexus.file.mapper;

import cc.nanoic.yunanexus.file.entity.FileShareTarget;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FileShareTargetMapper extends BaseMapper<FileShareTarget> {

    @Select("SELECT fs.* FROM file_share fs " +
            "INNER JOIN file_share_target fst ON fs.id = fst.share_id " +
            "WHERE fst.target_uuid = #{targetUuid} AND fs.allow_preview = 1 AND fs.status = 1 " +
            "AND (fs.expire_at IS NULL OR fs.expire_at > NOW())")
    List<cc.nanoic.yunanexus.file.entity.FileShare> findActiveSharesWithPreview(String targetUuid);
}