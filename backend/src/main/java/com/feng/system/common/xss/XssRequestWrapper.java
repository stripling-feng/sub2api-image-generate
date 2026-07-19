package com.feng.system.common.xss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class XssRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public XssRequestWrapper(HttpServletRequest request) {
        super(request);
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            String body = readBody(request);
            if (StringUtils.hasText(body)) {
                String sanitized = sanitizeJson(body);
                cachedBody = sanitized.getBytes(StandardCharsets.UTF_8);
            } else {
                cachedBody = null;
            }
        } else {
            cachedBody = null;
        }
    }

    private String readBody(HttpServletRequest request) {
        try {
            return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private String sanitizeJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            return mapper.writeValueAsString(sanitizeNode(root));
        } catch (Exception e) {
            return json;
        }
    }

    private JsonNode sanitizeNode(JsonNode node) {
        if (node.isTextual()) {
            return node.asText().isBlank() ? node : new com.fasterxml.jackson.databind.node.TextNode(XssSanitizer.clean(node.asText()));
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.fields().forEachRemaining(entry -> objectNode.set(entry.getKey(), sanitizeNode(entry.getValue())));
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                ((com.fasterxml.jackson.databind.node.ArrayNode) node).set(i, sanitizeNode(node.get(i)));
            }
        }
        return node;
    }

    @Override
    public String getParameter(String name) {
        return XssSanitizer.clean(super.getParameter(name));
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        return Arrays.stream(values)
                .map(XssSanitizer::clean)
                .toArray(String[]::new);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> parameterMap = super.getParameterMap();
        Map<String, String[]> sanitizedMap = new LinkedHashMap<>(parameterMap.size());
        parameterMap.forEach((key, values) -> sanitizedMap.put(key, Arrays.stream(values)
                .map(XssSanitizer::clean)
                .toArray(String[]::new)));
        return sanitizedMap;
    }

    @Override
    public String getHeader(String name) {
        return XssSanitizer.clean(super.getHeader(name));
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (cachedBody == null) {
            return super.getInputStream();
        }
        final ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override public int read() { return bais.read(); }
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) {}
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (cachedBody == null) {
            return super.getReader();
        }
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
    }

    @Override
    public long getContentLengthLong() {
        return cachedBody != null ? cachedBody.length : super.getContentLengthLong();
    }

    @Override
    public int getContentLength() {
        return cachedBody != null ? cachedBody.length : super.getContentLength();
    }
}
