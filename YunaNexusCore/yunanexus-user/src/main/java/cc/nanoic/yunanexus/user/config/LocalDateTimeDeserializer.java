package cc.nanoic.yunanexus.user.config;

import cc.nanoic.yunanexus.user.utils.FormatTime;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;

import java.lang.reflect.Type;
import java.time.LocalDateTime;

/**
 * LocalDateTime反序列化器
 * 时间戳 / 字符串 → LocalDateTime
 */
public class LocalDateTimeDeserializer implements ObjectReader<LocalDateTime> {

    @Override
    public LocalDateTime readObject(JSONReader jsonReader, Type type, Object fieldName, long features) {
        Object value = jsonReader.readObject();
        if (value == null) return null;

        if (value instanceof Number number) {
            return FormatTime.toLocalDateTime(number.longValue());
        }

        return FormatTime.toLocalDateTime(value.toString().trim());
    }
}