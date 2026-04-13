package cc.nanoic.yunanexus.user.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 时间工具类
 * Java21+ 线程安全 | 时区从配置文件读取 | 兼容Fastjson2序列化/反序列化
 */
@Component
public class FormatTime {

    /**
     * 从配置文件读取时区，默认值为Asia/Shanghai
     */
    public static String TIME_ZONE = "Asia/Shanghai";

    @Value("${custom.time.time-zone:Asia/Shanghai}")
    public void setTimeZone(String timeZone) {
        FormatTime.TIME_ZONE = timeZone;
    }

    // 配置
    public static final String PATTERN_DEFAULT = "yyyy-MM-dd HH:mm:ss";
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(PATTERN_DEFAULT, Locale.CHINA);

    public static ZoneId getZoneId() {
        return ZoneId.of(TIME_ZONE);
    }

    /**
     * LocalDateTime → 13位毫秒时间戳
     */
    public static long toTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0L;
        }
        return dateTime.atZone(getZoneId()).toInstant().toEpochMilli();
    }

    /**
     * 13位时间戳 → LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(long timestamp) {
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                getZoneId());
    }

    /**
     * 时间字符串 → LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(String timeStr) {
        return LocalDateTime.parse(timeStr, FORMATTER);
    }

    /**
     * // * 13位时间戳 -> 【天 时 分 秒】格式的字符，如：0天 7时 2分 1秒
     * 
     * @param millis 13位时间戳
     */
    public String formatMillis2String(long millis) {
        long days = millis / (1000 * 60 * 60 * 24);
        long hours = (millis % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
        long minutes = (millis % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (millis % (1000 * 60)) / 1000;
        return days + "天 " + hours + "时 " + minutes + "分 " + seconds + "秒";
    }
}
