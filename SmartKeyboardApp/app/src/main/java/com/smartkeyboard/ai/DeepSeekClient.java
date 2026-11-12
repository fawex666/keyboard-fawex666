package com.smartkeyboard.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DeepSeekClient {

    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions";

    public static String punctuateText(String rawText, String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return offlineSmartPunctuate(rawText);
        }

        try {
            JSONObject json = new JSONObject();
            json.put("model", "deepseek-chat");
            json.put("temperature", 0.2);

            JSONArray messages = new JSONArray();
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", "Расставь знаки препинания и заглавные буквы в начале предложений. Верни ТОЛЬКО готовый текст без пояснений.");
            messages.put(system);

            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", rawText);
            messages.put(user);

            json.put("messages", messages);

            URL url = new URL(DEEPSEEK_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                JSONObject resp = new JSONObject(sb.toString());
                JSONArray choices = resp.getJSONArray("choices");
                if (choices.length() > 0) {
                    return choices.getJSONObject(0).getJSONObject("message").getString("content").trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return offlineSmartPunctuate(rawText);
    }

    public static String translateText(String rawText, String targetLang, String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "[Перевод]: " + rawText;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("model", "deepseek-chat");
            json.put("temperature", 0.3);

            JSONArray messages = new JSONArray();
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", "Переведи на " + targetLang + " без пояснений:\n" + rawText);
            messages.put(user);
            json.put("messages", messages);

            URL url = new URL(DEEPSEEK_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                JSONObject resp = new JSONObject(sb.toString());
                return resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
            }
        } catch (Exception ignored) {}

        return rawText;
    }

    private static String offlineSmartPunctuate(String text) {
        if (text == null || text.trim().isEmpty()) return text;
        String trimmed = text.trim();
        String[] words = trimmed.split("\\s+");
        if (words.length == 0) return text;

        if (words[0].length() > 0) {
            words[0] = Character.toUpperCase(words[0].charAt(0)) + (words[0].length() > 1 ? words[0].substring(1) : "");
        }

        Set<String> conjunctions = new HashSet<>(Arrays.asList("что", "чтобы", "как", "потому", "если", "когда", "хотя", "но", "а", "где", "куда"));
        for (int i = 1; i < words.length; i++) {
            if (conjunctions.contains(words[i].toLowerCase())) {
                String prev = words[i - 1];
                if (!prev.endsWith(",") && !prev.endsWith(".") && !prev.endsWith("!") && !prev.endsWith("?")) {
                    words[i - 1] = prev + ",";
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(words[i]);
        }
        String res = sb.toString();

        Set<String> questions = new HashSet<>(Arrays.asList("как", "где", "куда", "когда", "почему", "зачем", "кто", "что", "сколько"));
        if (questions.contains(words[0].toLowerCase()) || res.contains("?")) {
            if (!res.endsWith("?")) res += "?";
        } else {
            if (!res.endsWith(".") && !res.endsWith("!")) res += ".";
        }
        return res;
    }
}
