package cc.nanoic.yunanexus.file.domain;

import cc.nanoic.yunanexus.file.entity.FileMeta;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件元数据视图（对外返回，不含存储路径等内部信息）。
 *
 * @author yunanexus
 */
@Data
public class FileView {

    /** 全局文件 ID */
    private Long fileId;

    /** 原始文件名 */
    private String fileName;

    /** 扩展名 */
    private String ext;

    /** MIME 类型 */
    private String contentType;

    /** 字节数 */
    private Long size;

    /** 内容 SHA-256 */
    private String sha256;

    /** 上传者用户 ID */
    private Long createBy;

    /** 所属部门 ID */
    private Long deptId;

    /** 上传时间 */
    private LocalDateTime createTime;

    public static FileView from(FileMeta meta) {
        FileView view = new FileView();
        view.setFileId(meta.getFileId());
        view.setFileName(meta.getFileName());
        view.setExt(meta.getExt());
        view.setContentType(meta.getContentType());
        view.setSize(meta.getSize());
        view.setSha256(meta.getSha256());
        view.setCreateBy(meta.getCreateBy());
        view.setDeptId(meta.getDeptId());
        view.setCreateTime(meta.getCreateTime());
        return view;
    }
}
