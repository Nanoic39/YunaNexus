package cc.nanoic.yunanexus.auth.security;

import cc.nanoic.yunanexus.common.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MissingResourceHttpTest {
    @RestController
    static class MissingEndpoint {
        @GetMapping("/api/v1/auth/account/oauth-apps")
        void missing() throws NoResourceFoundException {
            throw mock(NoResourceFoundException.class);
        }
    }
    @Test
    void missingApplicationEndpointReturns404WithoutInternalDetails() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new MissingEndpoint())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/auth/account/oauth-apps"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("资源不存在"));
    }
}
