package com.example.httpreading.controller;

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
    public User register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("username and password must not be blank");
        }
        return userService.register(username, password);
    }

    /**
     * 登录接口：POST /api/auth/login
     * 成功后在 Session 中保存 userId。
     */
    @PostMapping("/login")
    public String login(@RequestBody Map<String, String> body,
                        HttpServletRequest request) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("username and password must not be blank");
        }
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        if (!password.equals(user.getPasswordHash())) { // 简单对比，后续改为加密
            throw new IllegalArgumentException("invalid password");
        }
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", user.getId());
        return "ok";
    }

    /**
     * 简单检查当前是否已登录。
     */
    @GetMapping("/me")
    public User me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            return null;
        }
        Long userId = (Long) session.getAttribute("userId");
        return userService.findByUsername("dummy").orElse(null); // TODO: 可改为按 id 查询
    }
}
