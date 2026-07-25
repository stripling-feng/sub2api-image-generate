package com.feng.system.common.xss;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class XssFilter extends OncePerRequestFilter {

    private final XssProperties xssProperties;
    private final RequestMappingHandlerMapping handlerMapping;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ConcurrentHashMap<String, Boolean> pathXssIgnoreCache = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = request.getServletPath();

        // Fast path: URL pattern exclusion (no reflection needed)
        boolean urlExcluded = xssProperties.getExcludeUrls().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, requestPath));
        if (urlExcluded) {
            return true;
        }

        // Cached annotation check: avoid repeated handler resolution
        return pathXssIgnoreCache.computeIfAbsent(requestPath, p -> {
            try {
                Object handler = handlerMapping.getHandler(request).getHandler();
                if (handler instanceof HandlerMethod hm) {
                    return hm.hasMethodAnnotation(XssIgnore.class)
                            || hm.getBeanType().isAnnotationPresent(XssIgnore.class);
                }
            } catch (Exception ignored) {
            }
            return Boolean.FALSE;
        });
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(new XssRequestWrapper(request), response);
    }
}
