package com.example.httpreading.controller;

import com.example.httpreading.api.CommonResponse;
import com.example.httpreading.domain.user.User;
import com.example.httpreading.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 注册接口：POST /api/auth/register
     * 简单接收 JSON：{"username":"xxx", "password":"yyy"}
     */
    @PostMapping("/register")
    public CommonResponse<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        User u = userService.register(username, password);
        return CommonResponse.success(Map.of(
                "id", u.getId(),
                "username", u.getUsername()
        ));
    }

    /**
     * 登录接口：POST /api/auth/login
     * 成功后在 Session 中保存 userId。
     */
    @PostMapping("/login")
    public CommonResponse<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                                     HttpServletRequest request) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("用户名或密码不能为空");
        }
        User u = userService.findByUsername(username)
                .filter(user -> password.equals(user.getPasswordHash())) // 现阶段使用明文/简单比对
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", u.getId());
        return CommonResponse.success(Map.of(
                "id", u.getId(),
                "username", u.getUsername()
        ));
    }

    /**
     * 简单检查当前是否已登录。
     */
    @GetMapping("/me")
    public CommonResponse<Map<String, Object>> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            throw new IllegalArgumentException("未登录");
        }
        Long userId = (Long) session.getAttribute("userId");
        // 这里简单返回 userId，后续可按 id 查询完整用户
        return CommonResponse.success(Map.of("id", userId));
    }
}
