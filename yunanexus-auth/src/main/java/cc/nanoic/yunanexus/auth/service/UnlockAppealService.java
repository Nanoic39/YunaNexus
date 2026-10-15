package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.domain.UnlockAppealRequest;

/**
 * 解锁申诉服务。<p>
 *
 * 账号锁定后用户可提交申诉（需验证密码证明归属）。申诉状态流转：待处理（0）→
 * 已通过（1）/ 已拒绝（2），由管理后台审核。
 *
 * @author yunanexus
 */
public interface UnlockAppealService {

    /**
     * 提交解锁申诉。
     *
     * @param request 申诉请求（含账号、密码、理由、联系方式）
     * @return 申诉记录 ID
     */
    long submit(UnlockAppealRequest request);
}
