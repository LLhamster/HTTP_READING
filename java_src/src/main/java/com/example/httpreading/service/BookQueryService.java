package com.example.httpreading.service;

import com.example.httpreading.domain.entity.Book;
import com.example.httpreading.repository.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class BookQueryService {

    private final BookRepository bookRepository;

    public BookQueryService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    /**
     * 分页搜索图书列表，按标题或作者模糊匹配。
     *
     * @param keyword  关键字，可为 null 或空，表示不筛选
     * @param page     第几页，从 0 开始
     * @param pageSize 每页大小
     */
    public Page<Book> searchBooks(String keyword, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        if (keyword == null || keyword.isBlank()) {
            return bookRepository.findAll(pageable);
        }
        return bookRepository
                .findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(
                        keyword, keyword, pageable
                );
    }

    /**
     * 根据主键获取图书详情，未找到时抛出 IllegalArgumentException。
     */
    public Book getBookDetail(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found, id=" + bookId));
    }
}
