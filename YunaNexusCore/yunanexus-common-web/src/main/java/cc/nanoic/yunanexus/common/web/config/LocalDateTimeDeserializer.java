package cc.nanoic.yunanexus.common.web.config;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.reader.ObjectReader;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class LocalDateTimeDeserializer implements ObjectReader<LocalDateTime> {

    private final ZoneId zoneId;

    public LocalDateTimeDeserializer(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    @Override
    public LocalDateTime readObject(JSONReader jsonReader, Type fieldType, Object fieldName, long features) {
        Object value = jsonReader.readObject();
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(number.longValue()), zoneId);
        }

        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.matches("^\\d+$")) {
            long timestamp = Long.parseLong(text);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId);
        }
        return LocalDateTime.parse(text);
    }
}
