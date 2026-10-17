package cc.nanoic.yunanexus.permission.entity;

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
                for (Class<?> type : List.of(PermRole.class, PermPermission.class, PermMenu.class, PermDept.class, PermPolicy.class, PermResourceGrant.class, PermFieldRule.class, PermUserRole.class, PermUserDept.class, PermRoleDept.class, PermRoleMenu.class, PermRolePermission.class)) views.add(type.getDeclaredConstructor().newInstance());
                
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
    @Test
    void grantExpiryKeepsLegacyAndUtcInputCompatibility() throws Exception {
        var mapper = new ObjectMapper();
        for (String text : List.of("2026-10-17T00:30:00", "2026-10-17T00:30:00Z")) {
            var request = mapper.readValue("{\"expireTime\":\"" + text + "\"}",
                    cc.nanoic.yunanexus.permission.domain.ResourceGrantSaveRequest.class);
            assertEquals(LocalDateTime.of(2026, 10, 17, 0, 30), request.getExpireTime());
        }
    }
}
