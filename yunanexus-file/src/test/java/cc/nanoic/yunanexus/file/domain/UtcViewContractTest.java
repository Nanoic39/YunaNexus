package cc.nanoic.yunanexus.file.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UtcViewContractTest {
    @Test
    void responseInstantsRemainStableAcrossHostTimeZones() throws Exception {
        var original = TimeZone.getDefault();
        var time = LocalDateTime.of(2026, 10, 17, 0, 30);
        try {
            for (String zone : List.of("UTC", "Asia/Shanghai", "America/New_York")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                var views = new ArrayList<Object>();
                for (Class<?> type : List.of(FileView.class)) views.add(type.getDeclaredConstructor().newInstance());
                views.add(new cc.nanoic.yunanexus.file.service.FileShareService.View("share", 1L, "file", false, time, time, false));
                for (var view : views) {
                    for (var field : view.getClass().getDeclaredFields()) {
                        if (field.getType() != LocalDateTime.class) continue;
                        field.setAccessible(true);
                        if (!Modifier.isFinal(field.getModifiers())) field.set(view, time);
                    }
                    var mapper = new ObjectMapper();
                    var json = mapper.readTree(mapper.writeValueAsString(view));
                    for (var field : view.getClass().getDeclaredFields()) {
                        if (field.getType() != LocalDateTime.class) continue;
                        String value = json.get(field.getName()).asText();
                        assertTrue(value.endsWith("Z"), view.getClass().getSimpleName()+"."+field.getName()+" "+zone+": "+value);
                        assertEquals(time.toInstant(ZoneOffset.UTC), Instant.parse(value));
                    }
                }
            }
        } finally { TimeZone.setDefault(original); }
    }
}
