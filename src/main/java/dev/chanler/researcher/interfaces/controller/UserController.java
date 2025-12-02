package dev.chanler.researcher.interfaces.controller;

import dev.chanler.researcher.infra.common.Result;
import dev.chanler.researcher.infra.common.Results;
import dev.chanler.researcher.interfaces.dto.req.LoginReqDTO;
import dev.chanler.researcher.interfaces.dto.req.RegisterReqDTO;
import dev.chanler.researcher.interfaces.dto.resp.LoginRespDTO;
import dev.chanler.researcher.interfaces.dto.resp.UserInfoRespDTO;
import dev.chanler.researcher.interfaces.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Chanler
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/api/v1/user/register")
    public Result<LoginRespDTO> register(@RequestBody RegisterReqDTO req) {
        return Results.success(userService.register(req));
    }

    @PostMapping("/api/v1/user/login")
    public Result<LoginRespDTO> login(@RequestBody LoginReqDTO req) {
        return Results.success(userService.login(req));
    }

    @GetMapping("/api/v1/user/google/callback")
    public Result<LoginRespDTO> handleGoogleCallback(@RequestParam String code) {
        return Results.success(userService.handleGoogleCallback(code));
    }

    @GetMapping("/api/v1/user/me")
    public Result<UserInfoRespDTO> getCurrentUser(@RequestAttribute("userId") Long userId) {
        return Results.success(userService.getUserInfo(userId));
    }
}
