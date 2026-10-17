package cc.nanoic.yunanexus.auth.domain;

import cc.nanoic.yunanexus.auth.entity.AuthUnlockAppeal;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 解锁申诉视图（管理后台）。<p>
 *
 * 由 {@link AuthUnlockAppeal} 转换而来，字段与表结构一致，不含敏感凭证。
 *
 * @author yunanexus
 */
@Data
public class AppealView {

    private Long id;
    private Long userId;
    private String username;
    private String appealReason;
    private String contact;
    private Integer status;
    private String auditor;
    private String auditRemark;
    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime auditTime;
    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime createTime;
    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime updateTime;

    /**
     * 由实体转换为视图。
     *
     * @param appeal 申诉实体
     * @return 视图对象
     */
    public static AppealView from(AuthUnlockAppeal appeal) {
        AppealView view = new AppealView();
        view.setId(appeal.getId());
        view.setUserId(appeal.getUserId());
        view.setUsername(appeal.getUsername());
        view.setAppealReason(appeal.getAppealReason());
        view.setContact(appeal.getContact());
        view.setStatus(appeal.getStatus());
        view.setAuditor(appeal.getAuditor());
        view.setAuditRemark(appeal.getAuditRemark());
        view.setAuditTime(appeal.getAuditTime());
        view.setCreateTime(appeal.getCreateTime());
        view.setUpdateTime(appeal.getUpdateTime());
        return view;
    }
}
