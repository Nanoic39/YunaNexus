package cc.nanoic.yunanexus.user.controller;

import cc.nanoic.yunanexus.user.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyPair;
import java.util.Base64;

@RestController
@RequestMapping("/security/rsa")
public class RSAController {

    private final KeyPair keyPair;

    public RSAController(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    @GetMapping("/public-key")
    public Result<String> getPublicKey() {
        return Result.success(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()), null);
    }
}
