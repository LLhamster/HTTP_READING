package com.example.httpreading.controller;

import com.example.httpreading.domain.entity.Book;
import com.example.httpreading.domain.entity.Chapter;
import com.example.httpreading.service.BookQueryService;
import com.example.httpreading.service.ReadingService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookQueryService bookQueryService;
    private final ReadingService readingService;

    public BookController(BookQueryService bookQueryService,
                          ReadingService readingService) {
        this.bookQueryService = bookQueryService;
        this.readingService = readingService;
    }

    /**
     * GET /api/books
     * 分页+关键字搜索书籍列表，游客可用。
     * 示例：/api/books?page=0&size=10&keyword=Java
     */
    @GetMapping
    public Page<Book> listBooks(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size,
                                @RequestParam(required = false) String keyword) {
        return bookQueryService.searchBooks(keyword, page, size);
    }

    /**
     * GET /api/books/{bookId}
     * 获取书籍详情。
     */
    @GetMapping("/{bookId}")
    public Book getBookDetail(@PathVariable Long bookId) {
        return bookQueryService.getBookDetail(bookId);
    }

    /**
     * GET /api/books/{bookId}/chapters
     * 获取某本书的章节列表。
     */
    @GetMapping("/{bookId}/chapters")
    public List<Chapter> listChapters(@PathVariable Long bookId) {
        return readingService.listChapters(bookId);
    }

    /**
     * GET /api/books/{bookId}/chapters/{index}
     * 获取某本书的某一章节内容。
     */
    @GetMapping("/{bookId}/chapters/{index}")
    public Chapter getChapter(@PathVariable Long bookId,
                              @PathVariable int index) {
        return readingService.getChapter(bookId, index);
    }
}
