package com.example.httpreading.repository;

import com.example.httpreading.domain.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {

    List<Chapter> findByBookIdOrderByChapterIndexAsc(Long bookId);

    Optional<Chapter> findByBookIdAndChapterIndex(Long bookId, Integer chapterIndex);
}
