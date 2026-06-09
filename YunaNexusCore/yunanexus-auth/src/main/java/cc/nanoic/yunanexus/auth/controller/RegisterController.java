package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.entity.DTO.RegisterRequest;
import cc.nanoic.yunanexus.auth.entity.DTO.RegisterResponse;
import cc.nanoic.yunanexus.auth.entity.DTO.SendCodeRequest;
import cc.nanoic.yunanexus.auth.service.AuthService;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/register")
public class RegisterController {

    @Resource
    private AuthService authService;

    @PostMapping("/send-code")
    public Result<?> sendCode(@RequestBody SendCodeRequest sendCodeRequest) {
        authService.sendEmailVerifyCode(sendCodeRequest.getEmail());
        return Result.success(null);
    }

    @PostMapping
    public Result<RegisterResponse> register(@RequestBody RegisterRequest registerRequest) {
        RegisterResponse registerResponse = authService.register(registerRequest);
        return Result.success(registerResponse);
    }
}
