package com.example.httpreading.service;

import com.example.httpreading.domain.entity.Chapter;
import com.example.httpreading.domain.user.ReadingProgress;
import com.example.httpreading.repository.ChapterRepository;
import com.example.httpreading.repository.ReadingProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    @Transactional(readOnly = true)
    public List<Chapter> listChapters(Long bookId) {
        return chapterRepository.findByBookIdOrderByChapterIndexAsc(bookId);
    }

    /**
     * 获取某本书的某一章节内容，优先从文件读取，未找到时抛出 IllegalArgumentException。
     */
    @Transactional(readOnly = true)
    public Chapter getChapter(Long bookId, int chapterIndex) {
        Chapter chapter = chapterRepository.findByBookIdAndChapterIndex(bookId, chapterIndex)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Chapter not found, bookId=" + bookId + ", index=" + chapterIndex
                ));
        System.out.println("DEBUG contentFilePath = " + chapter.getContentFilePath());

        String path = chapter.getContentFilePath();
        if (path != null && !path.isBlank()) {
            try {
                String fileContent = Files.readString(Path.of(path), StandardCharsets.UTF_8);
                chapter.setContent(fileContent);
            } catch (IOException e) {
                throw new IllegalStateException("读取章节文件失败: " + path, e);
            }
        }
        return chapter;
        // // 临时调试：只保留 id、title、chapterIndex、content，其余字段不暴露
        // Chapter dto = new Chapter();
        // dto.setId(chapter.getId());
        // dto.setBookId(chapter.getBookId());
        // dto.setChapterIndex(chapter.getChapterIndex());
        // dto.setTitle(chapter.getTitle());
        // dto.setContent(chapter.getContent());
        // return dto;
    }

    // 阅读进度相关方法
    @Transactional(readOnly = true)
    public ReadingProgress getProgress(Long userId, Long bookId) {
        return readingProgressRepository.findByUserIdAndBookId(userId, bookId)
                .orElse(null);
    }

    @Transactional
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
