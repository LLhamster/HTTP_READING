package com.example.httpreading.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class SseHub {

    private final Set<SseEmitter> clients = new CopyOnWriteArraySet<>();

    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        try {
            emitter.send(SseEmitter.event().data("[系统] 欢迎新用户进入聊天室！").name("message").id(String.valueOf(System.currentTimeMillis())).reconnectTime(3000));
        } catch (IOException ignored) {}
        return emitter;
    }

    public void send(String data) {
        for (SseEmitter e : clients) {
            try {
                e.send(SseEmitter.event().data(data).name("message"));
            } catch (IOException ex) {
                e.complete();
                clients.remove(e);
            }
        }
    }
}
