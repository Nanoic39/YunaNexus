package cc.nanoic.yunanexus.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件元数据实体（{@code file_meta}，每个物理分库各一张）。<p>
 *
 * {@code file_id} 为全局文件 ID（号段服务分配，不含分库信息），也是分库路由键；
 * {@code storage_path} 为内容存储键（本地相对路径，或对象存储 key）。
 *
 * @author yunanexus
 */
@Data
@TableName("file_meta")
public class FileMeta {

    /** 状态：正常 */
    public static final int STATUS_NORMAL = 1;
    /** 状态：已删除 */
    public static final int STATUS_DELETED = 0;

    /** 存储类型：本地 */
    public static final int STORAGE_LOCAL = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局文件 ID */
    private Long fileId;

    /** 逻辑分片编号（0~15） */
    private Integer shardIndex;

    /** 原始文件名 */
    private String fileName;

    /** 扩展名（小写、不含点） */
    private String ext;

    /** MIME 类型 */
    private String contentType;

    /** 文件字节数 */
    private Long size;

    /** 存储类型：1-本地 2-MinIO 3-OSS */
    private Integer storageType;

    /** 存储路径 / 对象键 */
    private String storagePath;

    /** 文件内容 SHA-256 */
    private String sha256;

    /** 状态：1-正常 0-已删除 */
    private Integer status;

    /** 上传者用户 ID */
    private Long createBy;

    /** 所属部门 ID（数据权限试点字段） */
    private Long deptId;

    private LocalDateTime createTime;

    /** 扫描状态：pending 待扫 / clean 通过 / infected 检出 */
    private String scanStatus;

    private LocalDateTime updateTime;
}
