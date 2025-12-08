package com.example.httpreading.service;

import com.example.httpreading.domain.entity.Chapter;
import com.example.httpreading.domain.user.ReadingProgress;
import com.example.httpreading.repository.ChapterRepository;
import com.example.httpreading.repository.ReadingProgressRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReadingService {

    private final ChapterRepository chapterRepository;
    private final ReadingProgressRepository readingProgressRepository;

    public ReadingService(ChapterRepository chapterRepository,
                          ReadingProgressRepository readingProgressRepository) {
        this.chapterRepository = chapterRepository;
        this.readingProgressRepository = readingProgressRepository;
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

    // 阅读进度相关方法
    public ReadingProgress getProgress(Long userId, Long bookId) {
        return readingProgressRepository.findByUserIdAndBookId(userId, bookId)
                .orElse(null);
    }

    public ReadingProgress updateProgress(Long userId, Long bookId, int chapterIndex, int offset) {
        ReadingProgress progress = readingProgressRepository
                .findByUserIdAndBookId(userId, bookId)
                .orElseGet(ReadingProgress::new);
        progress.setUserId(userId);
        progress.setBookId(bookId);
        progress.setChapterIndex(chapterIndex);
        progress.setOffset(offset);
        progress.setUpdatedAt(LocalDateTime.now());
        return readingProgressRepository.save(progress);
    }
}
