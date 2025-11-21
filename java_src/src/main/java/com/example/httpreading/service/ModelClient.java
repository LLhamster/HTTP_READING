package com.example.httpreading.service;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class ModelClient {

    private static final OkHttpClient HTTP_CLIENT =
            new OkHttpClient.Builder()
                    .readTimeout(300, TimeUnit.SECONDS)
                    .build();

    @Value("${model.apiUrl}")
    private String apiUrl;

    @Value("${model.apiKey:}")
    private String apiKey;

    @Value("${model.appId:}")
    private String appId;

    public String chat(String question) {
        try {
            JSONObject root = new JSONObject();
            root.put("model", "deepseek-v3.1-250821");
            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", question);
            messages.put(userMsg);
            root.put("messages", messages);
            root.put("stream", false);
            root.put("enable_thinking", false);

            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, root.toString());

            Request.Builder builder = new Request.Builder()
                    .url(apiUrl)
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json");
            if (appId != null && !appId.isBlank()) {
                builder.addHeader("appid", appId);
            }
            if (apiKey != null && !apiKey.isBlank()) {
                builder.addHeader("Authorization", apiKey);
            }

            Request request = builder.build();
            Response response = HTTP_CLIENT.newCall(request).execute();

            if (!response.isSuccessful()) {
                System.out.println("模型 HTTP error: " + response.code() + " " + response.message());
                return "模型接口请求失败: " + response.code();
            }

            String raw = response.body().string();
            System.out.println("模型原始返回: " + raw);

            JSONObject json = new JSONObject(raw);
            JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject first = choices.getJSONObject(0);
                JSONObject msg = first.optJSONObject("message");
                if (msg != null) {
                    return msg.optString("content", "");
                }
            }
            return "模型返回格式不符合预期";
        } catch (IOException e) {
            e.printStackTrace();
            return "调用模型接口异常: " + e.getMessage();
        }
    }
}
