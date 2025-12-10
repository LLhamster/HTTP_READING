package com.example.httpreading.controller;

import com.example.httpreading.api.CommonResponse;
import com.example.httpreading.domain.user.ReadingProgress;
import com.example.httpreading.service.ReadingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user/books")
public class ReadingController {

    private final ReadingService readingService;

    public ReadingController(ReadingService readingService) {
        this.readingService = readingService;
    }

    private Long currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            throw new IllegalStateException("not logged in");
        }
        return (Long) session.getAttribute("userId");
    }

    /**
     * GET /api/user/books/{bookId}/progress
     * 获取当前用户在某本书的阅读进度。
     */
    @GetMapping("/{bookId}/progress")
    public CommonResponse<ReadingProgress> getProgress(@PathVariable Long bookId,
                                                       HttpServletRequest request) {
        Long userId = currentUserId(request);
        ReadingProgress progress = readingService.getProgress(userId, bookId);
        return CommonResponse.success(progress);
    }

    /**
     * POST /api/user/books/{bookId}/progress
     * 更新当前用户在某本书的阅读进度。
     * 请求体示例：{"chapterIndex":1, "offset":0}
     */
    @PostMapping("/{bookId}/progress")
    public CommonResponse<ReadingProgress> updateProgress(@PathVariable Long bookId,
                                                          @RequestBody Map<String, Integer> body,
                                                          HttpServletRequest request) {
        Long userId = currentUserId(request);
        Integer chapterIndex = body.get("chapterIndex");
        Integer offset = body.getOrDefault("offset", 0);
        if (chapterIndex == null) {
            throw new IllegalArgumentException("chapterIndex 不能为空");
        }
        ReadingProgress progress = readingService.updateProgress(userId, bookId, chapterIndex, offset);
        return CommonResponse.success(progress);
    }
}
