package com.example.httpreading.controller;

import com.example.httpreading.api.CommonResponse;
import com.example.httpreading.domain.entity.Book;
import com.example.httpreading.service.BookshelfService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/bookshelf")
public class UserBookshelfController {

    private final BookshelfService bookshelfService;

    public UserBookshelfController(BookshelfService bookshelfService) {
        this.bookshelfService = bookshelfService;
    }

    private Long currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            throw new IllegalStateException("not logged in");
        }
        return (Long) session.getAttribute("userId");
    }

    /**
     * GET /api/user/bookshelf
     * 获取当前登录用户的书架列表。
     */
    @GetMapping
    public CommonResponse<List<Book>> listUserBookshelf(HttpServletRequest request) {
        Long userId = currentUserId(request);
        List<Book> books = bookshelfService.listUserBookshelf(userId);
        return CommonResponse.success(books);
    }

    /**
     * POST /api/user/bookshelf/{bookId}
     * 将某本书加入当前用户书架（幂等）。
     */
    @PostMapping("/{bookId}")
    public CommonResponse<Void> addToBookshelf(@PathVariable Long bookId,
                                               HttpServletRequest request) {
        Long userId = currentUserId(request);
        bookshelfService.addToBookshelf(userId, bookId);
        return CommonResponse.success(null);
    }

    /**
     * DELETE /api/user/bookshelf/{bookId}
     * 从当前用户书架移除某本书（幂等）。
     */
    @DeleteMapping("/{bookId}")
    public CommonResponse<Void> removeFromBookshelf(@PathVariable Long bookId,
                                                    HttpServletRequest request) {
        Long userId = currentUserId(request);
        bookshelfService.removeFromBookshelf(userId, bookId);
        return CommonResponse.success(null);
    }
}
