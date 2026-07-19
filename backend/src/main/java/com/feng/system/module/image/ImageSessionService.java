package com.feng.system.module.image;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feng.system.module.image.entity.ApiProfile;
import com.feng.system.module.image.entity.ApiSession;
import com.feng.system.module.image.mapper.ApiProfileMapper;
import com.feng.system.module.image.mapper.ApiSessionMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageSessionService {

    public static final String COOKIE = "sub2api_session";
    private final ApiProfileMapper profileMapper;
    private final ApiSessionMapper sessionMapper;
    private final Sub2apiBillingService billing;
    private final SecureRandom random = new SecureRandom();

    @Value("${image.session-days:30}") private long sessionDays;
    @Value("${image.cookie-secure:false}") private boolean cookieSecure;

    @Transactional
    public BoundProfile bind(String baseUrl, String apiKey, HttpServletResponse response) {
        validateBind(baseUrl, apiKey);
        Sub2apiBillingService.BillingAccount account = billing.validateApiKey(apiKey);
        String keyHash = hash(apiKey);
        ApiProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<ApiProfile>().eq(ApiProfile::getKeyHash, keyHash));
        LocalDateTime now = ImageTime.now();
        if (profile == null) {
            profile = new ApiProfile();
            profile.setId(id());
            profile.setCreatedAt(now);
            profile.setKeyHash(keyHash);
        }
        profile.setBaseUrl(baseUrl);
        profile.setEncryptedKey(apiKey);
        profile.setUpdatedAt(now);
        if (profileMapper.selectById(profile.getId()) == null) profileMapper.insert(profile); else profileMapper.updateById(profile);
        createSession(profile.getId(), response);
        return new BoundProfile(profile, account.balanceUsd(), account.availableBalanceUsd());
    }

    public ApiProfile requireProfile(HttpServletRequest request, HttpServletResponse response) {
        ApiProfile profile = resolveProfile(request, response);
        if (profile == null) throw new ImageApiException(401, "Not bound. Enter your sub2api URL and API Key first.");
        return profile;
    }

    public ApiProfile resolveProfile(HttpServletRequest request, HttpServletResponse response) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return profileMapper.selectOne(new LambdaQueryWrapper<ApiProfile>().eq(ApiProfile::getKeyHash, hash(apiKey)));
        }
        String token = cookie(request);
        if (token == null) return null;
        ApiSession session = sessionMapper.selectOne(new LambdaQueryWrapper<ApiSession>().eq(ApiSession::getTokenHash, hash(token)));
        if (session == null || session.getExpiresAt().isBefore(ImageTime.now())) {
            if (session != null) sessionMapper.deleteById(session.getId());
            clearCookie(response);
            return null;
        }
        return profileMapper.selectById(session.getProfileId());
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = cookie(request);
        if (token != null) sessionMapper.delete(new LambdaQueryWrapper<ApiSession>().eq(ApiSession::getTokenHash, hash(token)));
        clearCookie(response);
    }

    private void createSession(String profileId, HttpServletResponse response) {
        byte[] bytes = new byte[36];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ApiSession session = new ApiSession();
        session.setId(id());
        session.setTokenHash(hash(token));
        session.setProfileId(profileId);
        session.setCreatedAt(ImageTime.now());
        session.setExpiresAt(ImageTime.now().plusDays(sessionDays));
        sessionMapper.insert(session);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, Duration.ofDays(sessionDays)).toString());
    }

    private void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(cookieSecure).sameSite("Lax")
                .path("/").maxAge(maxAge).build();
    }

    private String cookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (COOKIE.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private void validateBind(String baseUrl, String apiKey) {
        SafeUpstreamUrl.requirePublicHttps(baseUrl);
        if (apiKey == null || apiKey.length() < 8) throw new ImageApiException(422, "Invalid request.");
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record BoundProfile(ApiProfile profile, String balanceUsd, String availableBalanceUsd) {}
}
