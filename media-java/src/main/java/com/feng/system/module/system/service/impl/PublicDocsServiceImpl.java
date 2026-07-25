package com.feng.system.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.entity.SysConfig;
import com.feng.system.module.system.entity.SysUploadFile;
import com.feng.system.module.system.mapper.SysConfigMapper;
import com.feng.system.module.system.mapper.SysUploadFileMapper;
import com.feng.system.module.system.service.PublicDocsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PublicDocsServiceImpl implements PublicDocsService {
    private static final Map<String, String> DOC_CONFIG_KEYS = Map.of(
            "image", "docs.image.file-id",
            "video", "docs.video.file-id"
    );

    private final SysConfigMapper configMapper;
    private final SysUploadFileMapper uploadFileMapper;

    @Override
    public Document getDocument(String key) {
        String configKey = DOC_CONFIG_KEYS.get(key);
        if (configKey == null) throw new BusinessException("文档不存在");

        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getDeleted, 0)
                .eq(SysConfig::getConfigKey, configKey)
                .last("LIMIT 1"));
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            throw new BusinessException("文档未配置");
        }

        Long fileId = parseFileId(config.getConfigValue());
        SysUploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            throw new BusinessException("文档文件不存在");
        }
        if (!isMarkdown(file)) {
            throw new BusinessException("文档文件必须是 Markdown");
        }

        String content = readContent(file.getFilePath());
        return new Document(key, title(content, file), content, file.getUpdateTime());
    }

    private Long parseFileId(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException error) {
            throw new BusinessException("文档配置的文件 ID 不合法");
        }
    }

    private boolean isMarkdown(SysUploadFile file) {
        String original = safe(file.getOriginalName()).toLowerCase();
        String current = safe(file.getCurrentName()).toLowerCase();
        String type = safe(file.getFileType()).toLowerCase();
        return original.endsWith(".md") || current.endsWith(".md") || "md".equals(type)
                || "text/markdown".equals(type) || "text/x-markdown".equals(type);
    }

    private String readContent(String filePath) {
        if (!StringUtils.hasText(filePath)) throw new BusinessException("文档文件路径为空");
        try {
            if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
                try (InputStream input = URI.create(filePath).toURL().openStream()) {
                    return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            Path path = Path.of(filePath);
            if (!Files.exists(path) || !Files.isRegularFile(path)) throw new BusinessException("文档文件不存在");
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException("读取文档失败: " + error.getMessage());
        }
    }

    private String title(String content, SysUploadFile file) {
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) return trimmed.substring(2).trim();
        }
        String original = safe(file.getOriginalName());
        return original.endsWith(".md") ? original.substring(0, original.length() - 3) : original;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
