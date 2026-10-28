package cc.nanoic.yunanexus.notice.service;

import cc.nanoic.yunanexus.notice.entity.NoticePreference;
import cc.nanoic.yunanexus.notice.mapper.NoticePreferenceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

/**
 * 通知偏好与免打扰判定（方案 B）。
 *
 * <p>判定顺序：精确类别 > 通配类别；再叠加免打扰时段。
 * 免打扰命中时返回 {@link Decision#DEFERRED}，由调用方顺延重投，而不是直接丢弃——
 * "因为你在睡觉所以这条通知丢了"是不可接受的。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class NoticePreferenceService {

    /** 判定结果 */
    public enum Decision {
        /** 立即发送 */
        ALLOW,
        /** 用户关闭了该类别 */
        BLOCKED,
        /** 处于免打扰时段，应顺延 */
        DEFERRED
    }

    private final NoticePreferenceMapper preferenceMapper;

    public NoticePreferenceService(NoticePreferenceMapper preferenceMapper) {
        this.preferenceMapper = preferenceMapper;
    }

    /**
     * 判定某条通知是否应当发送。
     *
     * @param userId     接收人
     * @param channel    通道码
     * @param category   模板代码
     * @param localTime  接收人所在时区的当前时间
     * @return 判定结果
     */
    public Decision decide(long userId, int channel, String category, LocalTime localTime) {
        List<NoticePreference> preferences = preferenceMapper.selectByUserChannel(userId, channel);
        if (preferences == null || preferences.isEmpty()) {
            return Decision.ALLOW;
        }
        NoticePreference exact = null;
        NoticePreference wildcard = null;
        for (NoticePreference preference : preferences) {
            if (category != null && category.equals(preference.getCategory())) {
                exact = preference;
            } else if (NoticePreference.ANY_CATEGORY.equals(preference.getCategory())) {
                wildcard = preference;
            }
        }
        NoticePreference effective = exact != null ? exact : wildcard;
        if (effective == null) {
            return Decision.ALLOW;
        }
        if (Boolean.FALSE.equals(effective.getEnabled())) {
            return Decision.BLOCKED;
        }
        return inQuietHours(effective, localTime) ? Decision.DEFERRED : Decision.ALLOW;
    }

    /**
     * 是否处于免打扰时段。支持跨零点（如 22:00–08:00）。
     *
     * @param preference 偏好
     * @param now        当前时间
     * @return 处于免打扰返回 true
     */
    boolean inQuietHours(NoticePreference preference, LocalTime now) {
        Integer start = preference.getQuietStart();
        Integer end = preference.getQuietEnd();
        if (start == null || end == null || start.equals(end)) {
            return false;
        }
        int minute = now.getHour() * 60 + now.getMinute();
        if (start < end) {
            return minute >= start && minute < end;
        }
        // 跨零点区间：如 1320(22:00) ~ 480(08:00)
        return minute >= start || minute < end;
    }
}
