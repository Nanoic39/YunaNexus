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
        assertTrue(properties.containsProperty("spring.application.name"));
    }
}
