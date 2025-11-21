package com.example.httpreading.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PageService {

    private final BookService bookService;

    public PageService(BookService bookService) {
        this.bookService = bookService;
    }

    public String bookstoreHtml() throws IOException {
        // 对应 respond_html: 读取 web/bookstore.html
        Path path = Path.of("/home/hamster/coding/http_reading/web/bookstore.html");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public String coverHtml(String msg) {
        // 对应 ReturnCover: 使用 Redis/MySQL 查询路径并拼接
        String urlJpg = "/image?msg=/coding/http_reading/sample.jpg";
        String urlPdf = "https://www.baidu.com";
        String title = "图书库中没有此内容";

        if (msg != null && !msg.isBlank()) {
            var opt = bookService.findUrlByTitle(msg);
            if (opt.isPresent()) {
                String base = opt.get(); // 例如: "/books/1/"
                title = msg;
                // 注意这里加上 :8080，指向 Spring Boot 的 /books/{id}/main.pdf
                urlPdf = "http://127.0.0.1:8080" + base + "main.pdf";
                urlJpg = "/image?msg=/file" + base + "main.jpg";
            } else {
                title = msg;
            }
        }

        return "<!DOCTYPE html><html><body>" +
                "<h2>" + title + "</h2>" +
                "<a href=\"" + urlPdf + "\">" +
                "<img src='" + urlJpg + "' style='width:200px;border-radius:10px;'>" +
                "</a>" +
                "</body></html>";
    }
}
