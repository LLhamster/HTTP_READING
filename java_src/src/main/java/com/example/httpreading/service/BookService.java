package com.example.httpreading.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class BookService {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redis;

    public BookService(JdbcTemplate jdbcTemplate, StringRedisTemplate redis) {
        this.jdbcTemplate = jdbcTemplate;
        this.redis = redis;
    }

    public Optional<String> findUrlByTitle(String title) {
        // 先读 Redis
        String cache = redis.opsForValue().get(title);
        if (cache != null && !cache.isEmpty()) {
            return Optional.of(cache);
        }
        // 查 MySQL
        String sql = "SELECT url FROM books_simple WHERE title = ? LIMIT 1";
        var list = jdbcTemplate.query(sql, ps -> ps.setString(1, title),
                (rs, i) -> rs.getString("url"));
        if (!list.isEmpty()) {
            String url = list.get(0);
            // 回写缓存，1 小时
            redis.opsForValue().set(title, url, Duration.ofHours(1));
            return Optional.of(url);
        }
        return Optional.empty();
    }
}
