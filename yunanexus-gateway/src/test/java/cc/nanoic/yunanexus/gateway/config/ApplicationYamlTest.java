package cc.nanoic.yunanexus.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationYamlTest {
    @Test
    void productionApplicationYamlLoadsWithoutDuplicateKeys() throws Exception {
        var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"));
        assertFalse(sources.isEmpty());
        var properties = sources.getFirst();
        assertEquals("/api/v1/permission/me", properties.getProperty("yunanexus.gateway.permission.permit-paths[0]"));
        assertEquals("/api/v1/notice/inbox", properties.getProperty("yunanexus.gateway.permission.permit-paths[4]"));
        var businessPaths=new java.util.HashSet<String>();
        for(int i=0;properties.containsProperty("yunanexus.gateway.permission.permit-paths["+i+"]");i++)businessPaths.add(properties.getProperty("yunanexus.gateway.permission.permit-paths["+i+"]").toString());
        assertTrue(businessPaths.contains("/api/v1/user/profile"));
        assertTrue(businessPaths.contains("/api/v1/auth/application/permissions/check"));
        for (int i = 0; properties.containsProperty("yunanexus.gateway.permit-paths[" + i + "]"); i++) {
            assertFalse(properties.getProperty("yunanexus.gateway.permit-paths[" + i + "]").toString().startsWith("/api/v1/user/"));
        }
        assertTrue(properties.containsProperty("spring.application.name"));
    }
}
