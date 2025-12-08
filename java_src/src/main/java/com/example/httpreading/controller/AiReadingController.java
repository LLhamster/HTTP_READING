package com.example.httpreading.controller;

import com.example.httpreading.service.AiReadingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiReadingController {

    private final AiReadingService aiReadingService;

    public AiReadingController(AiReadingService aiReadingService) {
        this.aiReadingService = aiReadingService;
    }

    /**
     * POST /api/ai/ask
     * 请求体示例：{"bookId":1, "chapterIndex":1, "question":"请解释一下这一节在讲什么？"}
     */
    @PostMapping("/ask")
    public Map<String, String> ask(@RequestBody Map<String, Object> body) {
        Long bookId = ((Number) body.get("bookId")).longValue();
        Integer chapterIndex = ((Number) body.get("chapterIndex")).intValue();
        String question = (String) body.get("question");
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        String answer = aiReadingService.askQuestion(bookId, chapterIndex, question);
        return Map.of("answer", answer);
    }
}
