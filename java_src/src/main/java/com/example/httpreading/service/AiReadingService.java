package com.example.httpreading.service;

import com.example.httpreading.domain.entity.Chapter;
import com.example.httpreading.repository.ChapterRepository;
import org.springframework.stereotype.Service;

@Service
public class AiReadingService {

    private final ChapterRepository chapterRepository;
    private final ChatService chatService;

    public AiReadingService(ChapterRepository chapterRepository,
                            ChatService chatService) {
        this.chapterRepository = chapterRepository;
        this.chatService = chatService;
    }

    /**
     * 根据书和章节，加上用户问题，构造 prompt 并调用大模型。
     */
    public String askQuestion(Long bookId, Integer chapterIndex, String question) {
        Chapter chapter = chapterRepository
                .findByBookIdAndChapterIndex(bookId, chapterIndex)
                .orElseThrow(() -> new IllegalArgumentException("章节不存在"));

        String content = chapter.getContent();
        if (content == null) {
            content = "";
        }
        // 简单截断，避免 prompt 过长（可以按照需要调整）
        int maxLen = 1500;
        if (content.length() > maxLen) {
            content = content.substring(0, maxLen) + "...";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个帮助用户理解技术书籍的助教，现在用户正在阅读一本关于 HTTP 的书。\n");
        prompt.append("下面是当前章节的部分内容，请基于这些内容，尽量用通俗易懂的方式回答用户的问题。\n\n");
        prompt.append("【章节标题】" + chapter.getTitle() + "\n");
        prompt.append("【章节内容节选】\n");
        prompt.append(content).append("\n\n");
        prompt.append("【用户问题】" + question + "\n");
        prompt.append("请用中文回答，如果问题和上下文无关，也要尽量结合 HTTP / 网络基础知识来解释。\n");

        return chatService.answer(prompt.toString());
    }
}
