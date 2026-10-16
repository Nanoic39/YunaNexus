package cc.nanoic.yunanexus.auth.client;

import cc.nanoic.yunanexus.common.core.model.Result;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "yunanexus-notice", contextId = "passwordMailClient")
public interface PasswordMailClient {
    record Mail(String email, String code, String purpose) {
        public Mail(String email, String code) {
            this(email, code, "RESET");
        }
    }

    @PostMapping("/internal/notice/password-code")
    Result<Void> send(@RequestBody Mail mail);
}
