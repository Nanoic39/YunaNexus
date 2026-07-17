package cc.nanoic.yunanexus.file.entity.VO;

import lombok.Data;

/**
 * 文件/文件夹视图对象，用于 API 返回。
 * 字段命名兼顾业务语义与后端实体风格。
 */
@Data
public class UserFileVO {

    /** 文件/文件夹 UUID */
    private String fileUuid;

    /** 文件/文件夹显示名称 */
    private String name;

    /** 文件大小（字节），文件夹为 0 */
    private Long size;

    /** MIME 类型，文件夹为 folder */
    private String fileType;

    /** 扩展名（不含点），文件夹为空 */
    private String fileExt;

    /** 父文件夹 ID，null 表示根目录 */
    private String folderId;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

    /** 是否为文件夹 */
    private Boolean isFolder;

    /** 文件夹内的子项数量（仅文件夹有效） */
    private Integer childCount;
}
