package cc.nanoic.yunanexus.file.mapper;

import cc.nanoic.yunanexus.file.entity.FileObject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileObjectMapper extends BaseMapper<FileObject> {

    @Update("UPDATE file_object SET ref_count = ref_count + 1 WHERE id = #{id}")
    int incrementRefCount(Long id);
}