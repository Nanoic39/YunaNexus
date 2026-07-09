package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.entity.DTO.LoginRequest;
import cc.nanoic.yunanexus.auth.entity.DTO.LoginResponse;
import cc.nanoic.yunanexus.auth.entity.DTO.RefreshRequest;
import cc.nanoic.yunanexus.auth.service.AuthService;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/login")
public class LoginController {

    @Resource
    private AuthService authService;

    @PostMapping
    public Result<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        LoginResponse loginResponse = authService.login(loginRequest);
        return Result.success(loginResponse);
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody RefreshRequest refreshRequest) {
        LoginResponse loginResponse = authService.refresh(refreshRequest);
        return Result.success(loginResponse);
    }

    @PostMapping("/logout")
    public Result<?> logout(@RequestBody Map<String, String> body) {
        authService.logout(body.get("refreshToken"));
        return Result.success(null);
    }

    @PostMapping("/logout-all")
    public Result<?> logoutAll() {
        authService.logoutAll();
        return Result.success(null);
    }
}