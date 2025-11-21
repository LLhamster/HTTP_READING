package com.example.httpreading.controller;

import com.example.httpreading.service.ChatService;
import com.example.httpreading.service.ImageService;
import com.example.httpreading.service.PageService;
import com.example.httpreading.service.PdfService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
public class HttpController {

    private final PageService pageService;
    private final ImageService imageService;
    private final ChatService chatService;
    private final PdfService pdfService;

    public HttpController(PageService pageService,
                          ImageService imageService,
                          ChatService chatService,
                          PdfService pdfService) {
        this.pageService = pageService;
        this.imageService = imageService;
        this.chatService = chatService;
        this.pdfService = pdfService;
    }

    @GetMapping("/")
    public String index() throws IOException {
        return pageService.bookstoreHtml();
    }

    @GetMapping("/cover")
    public String cover(@RequestParam(value = "msg", required = false) String msg) {
        return pageService.coverHtml(msg);
    }

    @GetMapping(value = "/image", produces = MediaType.IMAGE_JPEG_VALUE)
    public byte[] image(@RequestParam(value = "msg", required = false) String msg) throws IOException {
        return imageService.loadImageBytes(msg);
    }

    @PostMapping("/chat")
    public String chat(@RequestParam("msg") String msg) {
        return chatService.answer(msg);
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@RequestParam("msg") String msg) throws IOException {
        byte[] data = pdfService.loadPdfBytes(msg);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"book.pdf\"");
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @GetMapping(value = "/books/{id}/main.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> bookPdf(@PathVariable("id") String id) throws IOException {
        String rel = "/file/books/" + id + "/main.pdf";
        byte[] data = pdfService.loadPdfBytes(rel);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"main.pdf\"");
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
