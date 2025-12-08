package com.example.httpreading.service;

import com.example.httpreading.domain.entity.Book;
import com.example.httpreading.domain.user.Bookshelf;
import com.example.httpreading.repository.BookRepository;
import com.example.httpreading.repository.BookshelfRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BookshelfService {

    private final BookshelfRepository bookshelfRepository;
    private final BookRepository bookRepository;

    public BookshelfService(BookshelfRepository bookshelfRepository,
                            BookRepository bookRepository) {
        this.bookshelfRepository = bookshelfRepository;
        this.bookRepository = bookRepository;
    }

    /**
     * 获取用户书架上的图书列表。
     */
    public List<Book> listUserBookshelf(Long userId) {
        List<Bookshelf> records = bookshelfRepository.findByUserId(userId);
        List<Long> bookIds = records.stream()
                .map(Bookshelf::getBookId)
                .collect(Collectors.toList());
        if (bookIds.isEmpty()) {
            return List.of();
        }
        return bookRepository.findAllById(bookIds);
    }

    /**
     * 将图书加入用户书架（如果已存在则忽略）。
     */
    public void addToBookshelf(Long userId, Long bookId) {
        bookshelfRepository.findByUserIdAndBookId(userId, bookId)
                .ifPresent(existing -> {
                    // 已存在则直接返回
                    throw new IllegalStateException("book already in bookshelf");
                });
        Bookshelf bookshelf = new Bookshelf();
        bookshelf.setUserId(userId);
        bookshelf.setBookId(bookId);
        bookshelf.setCreatedAt(LocalDateTime.now());
        bookshelfRepository.save(bookshelf);
    }

    /**
     * 从用户书架移除图书（不存在时忽略）。
     */
    @Transactional
    public void removeFromBookshelf(Long userId, Long bookId) {
        bookshelfRepository.deleteByUserIdAndBookId(userId, bookId);
    }
}
