package com.example.httpreading.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PdfService {

    // relPath 例如：/file/books/1/main.pdf
    public byte[] loadPdfBytes(String relPath) throws IOException {
        Path path = Path.of("/home/hamster" + relPath);
        return Files.readAllBytes(path);
    }
}
