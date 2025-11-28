package com.example.httpreading.service;

import com.example.httpreading.domain.user.User;
import com.example.httpreading.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 简单注册：如果用户名已存在则抛 IllegalArgumentException。
     * 暂时直接存明文密码，后续阶段再改为加密存储。
     */
    public User register(String username, String rawPassword) {
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new IllegalArgumentException("username already exists");
        });
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(rawPassword); // TODO: 后续改为加密
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }
}
