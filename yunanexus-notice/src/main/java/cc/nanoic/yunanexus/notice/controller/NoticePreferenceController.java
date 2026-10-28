package cc.nanoic.yunanexus.notice.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.notice.entity.NoticePreference;
import cc.nanoic.yunanexus.notice.mapper.NoticePreferenceMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通知偏好管理（方案 B）。
 *
 * <p>没有这组接口，偏好表就是死数据——用户无法自助关闭通知或设置免打扰。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/notice/preferences")
public class NoticePreferenceController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final NoticePreferenceMapper preferenceMapper;

    public NoticePreferenceController(NoticePreferenceMapper preferenceMapper) {
        this.preferenceMapper = preferenceMapper;
    }

    /**
     * 查询当前用户的全部偏好。
     *
     * @param userId 网关注入的用户 ID
     * @return 偏好列表
     */
    @GetMapping
    public Result<List<NoticePreference>> list(@RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        long uid = requireUser(userId);
        return Result.success(preferenceMapper.selectByUser(uid));
    }

    /**
     * 保存一条偏好（存在则覆盖）。
     *
     * @param preference 偏好
     * @param userId     网关注入的用户 ID
     * @return 保存后的偏好
     */
    @PutMapping
    public Result<NoticePreference> save(@RequestBody NoticePreference preference,
                                         @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        long uid = requireUser(userId);
        preference.setUserId(uid);
        if (preference.getCategory() == null || preference.getCategory().isBlank()) {
            preference.setCategory(NoticePreference.ANY_CATEGORY);
        }
        if (preference.getChannel() == null) {
            throw new cc.nanoic.yunanexus.common.web.exception.BizException(
                    cc.nanoic.yunanexus.common.web.constant.ErrorCode.BAD_REQUEST, "通道不能为空");
        }
        // 组合主键（user_id, channel, category）：先按三键删除再插入，等价于 upsert
        preferenceMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<NoticePreference>()
                .eq("user_id", uid)
                .eq("channel", preference.getChannel())
                .eq("category", preference.getCategory()));
        preferenceMapper.insert(preference);
        return Result.success(preference);
    }

    private long requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new cc.nanoic.yunanexus.common.web.exception.BizException(
                    cc.nanoic.yunanexus.common.web.constant.ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
