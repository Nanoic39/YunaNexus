package cc.nanoic.yunanexus.file.mapper;

import cc.nanoic.yunanexus.common.datascope.DataScope;
import cc.nanoic.yunanexus.file.entity.FileMeta;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文件元数据 Mapper。
 *
 * <p>单条查询均以 {@code file_id} 为条件，调用前须已由 {@code @ShardRoute} 设定分片路由； {@link #selectVisible()} 标注
 * {@link DataScope}，在数据范围上下文下由拦截器注入 行级过滤条件（L4 数据权限试点）。
 *
 * @author yunanexus
 */
public interface FileMetaMapper extends BaseMapper<FileMeta> {

    /**
     * 按全局文件 ID 查询元数据（含已删除，由调用方判定状态）。
     *
     * @param fileId 全局文件 ID
     * @return 文件元数据，不存在返回 {@code null}
     */
    @Select("SELECT * FROM file_meta WHERE file_id = #{fileId}")
    FileMeta selectByFileId(@Param("fileId") long fileId);

    /**
     * 按内容指纹查询已存在的文件（秒传去重）。
     *
     * <p>命中则说明同一物理内容已在本物理库中落库，可复用其 {@code storage_path}， 无需重复写入；调用前须已由调用方选定物理库（{@code
     * ShardContextHolder.set}）。
     *
     * @param sha256 内容 SHA-256（十六进制小写）
     * @param size 字节数
     * @return 命中的文件元数据（取最新一条），不存在返回 {@code null}
     */
    @Select(
            "SELECT * FROM file_meta WHERE sha256 = #{sha256} AND size = #{size} AND status = 1"
                + " LIMIT 1")
    FileMeta selectBySha256AndSize(@Param("sha256") String sha256, @Param("size") long size);

    /**
     * 统计引用同一存储路径的有效文件数（删除引用计数）。
     *
     * <p>秒传复用 {@code storage_path} 时多个元数据指向同一物理内容，删除需计数为 0 才可物理清除。
     *
     * @param storagePath 存储路径
     * @return 有效（未删除）文件数
     */
    @Select("SELECT COUNT(*) FROM file_meta WHERE storage_path = #{storagePath} AND status = 1")
    long countByStoragePath(@Param("storagePath") String storagePath);

    /**
     * 查询当前数据范围内可见的文件（数据权限试点）。
     *
     * <p>路由上下文由调用方按物理库逐个设置；数据范围条件由 {@code DataScopeInterceptor} 依据 {@link DataScope} 注入。
     *
     * @return 该物理库内可见的文件列表
     */
    @DataScope(deptColumn = "dept_id", createByColumn = "create_by")
    @Select("SELECT * FROM file_meta WHERE status = 1 ORDER BY id DESC")
    List<FileMeta> selectVisible();

    @Select(
            "SELECT * FROM file_meta WHERE status=1 AND create_by=#{userId} AND file_name LIKE"
                + " CONCAT('%',#{search},'%') ORDER BY file_id DESC LIMIT #{limit}")
    List<FileMeta> selectOwned(
            @Param("userId") long userId,
            @Param("search") String search,
            @Param("limit") int limit);

    @Select(
            "SELECT COUNT(*) FROM file_meta WHERE status=1 AND create_by=#{userId} AND file_name"
                + " LIKE CONCAT('%',#{search},'%')")
    long countOwned(@Param("userId") long userId, @Param("search") String search);

    @org.apache.ibatis.annotations.Update(
            "UPDATE file_meta SET file_name=#{name}, update_time=NOW() WHERE file_id=#{fileId} AND"
                + " create_by=#{userId} AND status=1")
    int renameOwned(
            @Param("fileId") long fileId, @Param("userId") long userId, @Param("name") String name);

    @Select({
        "<script>",
        "SELECT file_id,file_name FROM file_meta WHERE status=1 AND create_by=#{userId} AND file_id"
            + " IN",
        "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        "</script>"
    })
    List<FileMeta> selectOwnedNames(
            @Param("userId") long userId, @Param("ids") java.util.Collection<Long> ids);
}
