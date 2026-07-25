package com.feng.system.module.system.controller;

import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.service.PublicDocsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class PublicDocsController {
    private static final Set<String> SUPPORTED_KEYS = Set.of("image", "video");

    private final PublicDocsService docsService;

    @GetMapping("/{key}")
    public ResponseEntity<?> document(@PathVariable String key) {
        if (!SUPPORTED_KEYS.contains(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "文档不存在"));
        }
        try {
            return ResponseEntity.ok(docsService.getDocument(key));
        } catch (BusinessException ex) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", ex.getMessage()));
        }
    }
}
