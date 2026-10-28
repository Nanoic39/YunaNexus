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
    /** 回收站：逻辑删除但可恢复，仍占用配额 */
    public static final int STATUS_TRASHED = 2;

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

    /** 归属类型：user 用户 / system 系统（如头像）/ app 应用 */
    private String ownerType;

    /** 所属应用 ID；空表示官方前端上传 */
    private String appId;

    /** 可见性：private 私有 / internal 系统内公开（所有登录用户可见） */
    private String visibility;

    /** 物理内容引用计数；归零才允许删除存储 */
    private Integer refCount;

    /** 进入回收站时间（30 天后物理清理） */
    private LocalDateTime deletedAt;

    /** 逻辑文件根 ID；首版等于自身 file_id */
    private Long rootId;

    /** 版本号，从 1 递增 */
    private Integer version;

    private LocalDateTime updateTime;
}
