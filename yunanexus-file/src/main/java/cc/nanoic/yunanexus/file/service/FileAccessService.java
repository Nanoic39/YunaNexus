package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.config.FileAccessContext;
import cc.nanoic.yunanexus.file.entity.FileMeta;
import org.springframework.stereotype.Service;

/**
 * 文件访问判定（多应用隔离的唯一入口）。
 *
 * <p>所有读/写/删/列表都必须经过这里，否则隔离就会出现"某个入口忘了判"的漏洞。
 *
 * <p>规则：
 * <ul>
 *   <li>官方前端：该用户的全部文件（任意 app_id）</li>
 *   <li>三方应用 A：仅 {@code create_by = 当前用户 AND app_id = A} 的文件</li>
 *   <li>{@code visibility = internal}：任何登录用户只读（头像、平台物料等）</li>
 * </ul>
 *
 * @author yunanexus
 */
@Service
public class FileAccessService {

    /** 系统内公开 */
    public static final String VISIBILITY_INTERNAL = "internal";

    /** 系统归属（如头像），不计入个人配额 */
    public static final String OWNER_SYSTEM = "system";

    /**
     * 校验读权限。
     *
     * @param meta   文件元数据
     * @param userId 当前用户
     * @throws BizException 无权访问
     */
    public void checkRead(FileMeta meta, long userId) {
        if (isInternalPublic(meta)) {
            return;
        }
        checkOwnedByApp(meta, userId);
    }

    /**
     * 校验写/删权限（系统公开文件不允许三方应用或个人改写）。
     *
     * @param meta   文件元数据
     * @param userId 当前用户
     * @throws BizException 无权操作
     */
    public void checkWrite(FileMeta meta, long userId) {
        if (OWNER_SYSTEM.equals(meta.getOwnerType())) {
            // 系统物料只允许官方前端维护
            if (!FileAccessContext.isOfficial()) {
                throw new BizException(ErrorCode.FILE_ACCESS_DENIED, "系统文件不可由第三方应用修改");
            }
            return;
        }
        checkOwnedByApp(meta, userId);
    }

    /**
     * 该文件是否计入用户配额（系统公开文件不计）。
     *
     * @param meta 文件元数据
     * @return 计入返回 true
     */
    public boolean countsAgainstQuota(FileMeta meta) {
        return !OWNER_SYSTEM.equals(meta.getOwnerType());
    }

    private void checkOwnedByApp(FileMeta meta, long userId) {
        if (!Long.valueOf(userId).equals(meta.getCreateBy())) {
            throw new BizException(ErrorCode.FILE_ACCESS_DENIED);
        }
        String appId = FileAccessContext.currentAppId();
        if (appId != null && !appId.equals(meta.getAppId())) {
            // 三方应用只能访问本应用下的文件
            throw new BizException(ErrorCode.FILE_ACCESS_DENIED, "文件不属于当前应用");
        }
    }

    private boolean isInternalPublic(FileMeta meta) {
        return VISIBILITY_INTERNAL.equals(meta.getVisibility());
    }
}
