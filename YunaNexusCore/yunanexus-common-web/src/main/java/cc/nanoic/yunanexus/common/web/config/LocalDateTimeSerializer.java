package cc.nanoic.yunanexus.common.web.config;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class LocalDateTimeSerializer implements ObjectWriter<LocalDateTime> {

    private final ZoneId zoneId;

    public LocalDateTimeSerializer(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    @Override
    public void write(JSONWriter jsonWriter, Object object, Object fieldName, Type fieldType, long features) {
        if (object == null) {
            jsonWriter.writeNull();
            return;
        }
        LocalDateTime time = (LocalDateTime) object;
        long timestamp = time.atZone(zoneId).toInstant().toEpochMilli();
        jsonWriter.writeInt64(timestamp);
    }
}
