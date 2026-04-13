package cc.nanoic.yunanexus.user.config;

import cc.nanoic.yunanexus.user.utils.FormatTime;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;

import java.lang.reflect.Type;
import java.time.LocalDateTime;

/**
 * LocalDateTime序列化器
 * LocalDateTime → 13 位时间戳
 */
public class LocalDateTimeSerializer implements ObjectWriter<LocalDateTime> {

    @Override
    public void write(JSONWriter jsonWriter, Object object, Object fieldName, Type type, long features) {
        if (object == null) {
            jsonWriter.writeNull();
            return;
        }
        LocalDateTime time = (LocalDateTime) object;
        long timestamp = FormatTime.toTimestamp(time);
        jsonWriter.writeInt64(timestamp); // 正确方法：writeInt64
    }
}