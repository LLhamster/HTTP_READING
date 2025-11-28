package com.example.httpreading.service;

import com.example.httpreading.domain.entity.Chapter;
import com.example.httpreading.repository.ChapterRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReadingService {

    private final ChapterRepository chapterRepository;

    public ReadingService(ChapterRepository chapterRepository) {
        this.chapterRepository = chapterRepository;
    }

    /**
     * 获取某本书的所有章节列表，按 chapterIndex 升序。
     */
    public List<Chapter> listChapters(Long bookId) {
        return chapterRepository.findByBookIdOrderByChapterIndexAsc(bookId);
    }

    /**
     * 获取某本书的某一章节内容，未找到时抛出 IllegalArgumentException。
     */
    public Chapter getChapter(Long bookId, int chapterIndex) {
        return chapterRepository.findByBookIdAndChapterIndex(bookId, chapterIndex)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Chapter not found, bookId=" + bookId + ", index=" + chapterIndex
                ));
    }
}
