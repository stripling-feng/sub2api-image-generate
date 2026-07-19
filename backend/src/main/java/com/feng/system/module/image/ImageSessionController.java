package com.feng.system.module.image;

import com.feng.system.module.image.entity.ApiProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class ImageSessionController {
    private final ImageSessionService sessions;

    @PostMapping("/bind")
    public Map<String, Object> bind(@Valid @RequestBody BindRequest input, HttpServletResponse response) {
        ImageSessionService.BoundProfile bound = sessions.bind(input.baseUrl(), input.apiKey(), response);
        return Map.of("profile", profile(bound.profile(), bound.balanceUsd(), bound.availableBalanceUsd()));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(request, response);
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request, HttpServletResponse response) {
        ApiProfile profile = sessions.resolveProfile(request, response);
        return java.util.Collections.singletonMap("profile", profile == null ? null : profile(profile, null, null));
    }

    private Map<String, Object> profile(ApiProfile profile, String balance, String available) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", profile.getId());
        result.put("baseUrl", profile.getBaseUrl());
        result.put("keyHashPreview", profile.getKeyHash().substring(0, Math.min(8, profile.getKeyHash().length())) + "...");
        if (balance != null) result.put("balanceUsd", balance);
        if (available != null) result.put("availableBalanceUsd", available);
        return result;
    }

    public record BindRequest(@NotBlank String baseUrl, @NotBlank @Size(min = 8) String apiKey) {}
}
