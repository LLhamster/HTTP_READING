package com.example.httpreading.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@RestController
public class SseController {

    private final Set<SseEmitter> clients = new CopyOnWriteArraySet<>();

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        try {
            emitter.send(SseEmitter.event().data("[系统] 欢迎新用户进入聊天室！"));
        } catch (IOException ignored) {}
        return emitter;
    }

    // 简单广播接口，其他地方可注入本控制器调用 send
    public void send(String data) {
        clients.forEach(e -> {
            try {
                e.send(SseEmitter.event().data(data));
            } catch (IOException ex) {
                e.complete();
                clients.remove(e);
            }
        });
    }
}
