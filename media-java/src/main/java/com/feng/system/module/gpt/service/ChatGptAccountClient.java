package com.feng.system.module.gpt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feng.system.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class ChatGptAccountClient {
    static final String STATUS_URL = "https://chatgpt.com/backend-api/accounts/check/v4-2023-04-27";

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final ChatGptAccountStatusParser parser;

    @Value("${gpt.account.status-cookie:}")
    private String statusCookie = "";

    public CheckedAccount check(String rawToken) {
        String token = normalize(rawToken);
        ChatGptAccountStatusParser.TokenClaims claims = parseClaims(token);
        if (claims.expiresAt() != null && claims.expiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Access Token 已过期");
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(STATUS_URL)
                .get()
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("User-Agent", "Apifox/1.0.0 (https://apifox.com)")
                .addHeader("Accept", "*/*")
                .addHeader("Host", "chatgpt.com")
                .addHeader("Connection", "keep-alive");
        if (StringUtils.hasText(statusCookie)) {
            requestBuilder.addHeader("Cookie", statusCookie.trim());
        }

        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            int status = response.code();
            ResponseBody responseBody = response.body();
            String body = responseBody == null ? "" : responseBody.string();
            if (status == 401) {
                throw new BusinessException("Access Token 无效、已过期或无权访问账号状态");
            }
            if (status == 403) {
                throw new BusinessException("ChatGPT 状态接口拒绝访问，请检查 Cloudflare Cookie 是否有效");
            }
            if (status == 429) {
                throw new BusinessException("ChatGPT 状态接口请求过于频繁，请稍后重试");
            }
            if (status < 200 || status >= 300) {
                throw new BusinessException("ChatGPT 状态接口返回 HTTP " + status);
            }
            JsonNode json = objectMapper.readTree(body);
            return new CheckedAccount(token, parser.parse(json, claims));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("无法读取 ChatGPT 账号状态，请稍后重试");
        }
    }

    String normalize(String rawToken) {
        if (rawToken == null) throw new BusinessException("Access Token 不能为空");
        String token = rawToken.trim();
        if (token.regionMatches(true, 0, "Bearer", 0, 6)) {
            token = token.substring(6).trim();
        }
        if (token.length() < 32 || token.contains(" ") || token.contains("\n") || token.contains("\r")) {
            throw new BusinessException("Access Token 格式无效");
        }
        return token;
    }

    private ChatGptAccountStatusParser.TokenClaims parseClaims(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return new ChatGptAccountStatusParser.TokenClaims("", "", "", null);
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
            JsonNode profile = payload.path("https://api.openai.com/profile");
            JsonNode auth = payload.path("https://api.openai.com/auth");
            long exp = payload.path("exp").asLong(0);
            LocalDateTime expiresAt = exp > 0
                    ? LocalDateTime.ofInstant(Instant.ofEpochSecond(exp), ZoneId.systemDefault()) : null;
            return new ChatGptAccountStatusParser.TokenClaims(
                    auth.path("user_id").asText(""),
                    profile.path("email").asText(""),
                    profile.path("name").asText(""),
                    expiresAt
            );
        } catch (Exception ex) {
            return new ChatGptAccountStatusParser.TokenClaims("", "", "", null);
        }
    }

    public record CheckedAccount(String token, ChatGptAccountStatusParser.ParsedAccount status) {
    }
}
