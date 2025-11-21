package com.example.httpreading.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ImageService {
    // 迁移自 C++ 的 SendImage 逻辑：从磁盘读取二进制并返回
    public byte[] loadImageBytes(String msg) throws IOException {
        String rel = (msg == null || msg.isBlank()) ? "/coding/http_reading/sample.jpg" : msg;
        Path path = Path.of("/home/hamster" + rel);
        return Files.readAllBytes(path);
    }
}
