package com.feng.system.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.dto.PublicDocsConfigDTO;
import com.feng.system.module.system.entity.SysConfig;
import com.feng.system.module.system.entity.SysUploadFile;
import com.feng.system.module.system.mapper.SysConfigMapper;
import com.feng.system.module.system.mapper.SysUploadFileMapper;
import com.feng.system.module.system.service.PublicDocsConfigService;
import com.feng.system.module.system.vo.PublicDocsConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicDocsConfigServiceImpl implements PublicDocsConfigService {
    private static final String IMAGE_CONFIG_KEY = "docs.image.file-id";
    private static final String VIDEO_CONFIG_KEY = "docs.video.file-id";

    private final SysConfigMapper configMapper;
    private final SysUploadFileMapper uploadFileMapper;

    @Override
    public PublicDocsConfigVO detail() {
        Map<String, String> values = configMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getDeleted, 0)
                        .in(SysConfig::getConfigKey, IMAGE_CONFIG_KEY, VIDEO_CONFIG_KEY))
                .stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue, (left, right) -> left));

        PublicDocsConfigVO vo = new PublicDocsConfigVO();
        vo.setImage(toDocFile("image", IMAGE_CONFIG_KEY, values.get(IMAGE_CONFIG_KEY)));
        vo.setVideo(toDocFile("video", VIDEO_CONFIG_KEY, values.get(VIDEO_CONFIG_KEY)));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(PublicDocsConfigDTO dto) {
        Long imageFileId = validateMarkdownFile(dto.getImageFileId(), "图片文档");
        Long videoFileId = validateMarkdownFile(dto.getVideoFileId(), "视频文档");
        persist(IMAGE_CONFIG_KEY, "图片接口文档文件ID", imageFileId);
        persist(VIDEO_CONFIG_KEY, "视频接口文档文件ID", videoFileId);
    }

    private Long validateMarkdownFile(Long fileId, String label) {
        if (fileId == null) return null;
        SysUploadFile file = uploadFileMapper.selectById(fileId);
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            throw new BusinessException(label + "文件不存在");
        }
        if (!isMarkdown(file)) {
            throw new BusinessException(label + "必须绑定 Markdown 文件");
        }
        return fileId;
    }

    private PublicDocsConfigVO.DocFile toDocFile(String key, String configKey, String rawFileId) {
        PublicDocsConfigVO.DocFile docFile = new PublicDocsConfigVO.DocFile();
        docFile.setKey(key);
        docFile.setConfigKey(configKey);
        if (!StringUtils.hasText(rawFileId)) return docFile;

        try {
            Long fileId = Long.valueOf(rawFileId.trim());
            docFile.setFileId(fileId);
            SysUploadFile file = uploadFileMapper.selectById(fileId);
            if (file == null || Integer.valueOf(1).equals(file.getDeleted())) return docFile;
            docFile.setOriginalName(file.getOriginalName());
            docFile.setCurrentName(file.getCurrentName());
            docFile.setFileType(file.getFileType());
            docFile.setFileSize(file.getFileSize());
            docFile.setUpdatedAt(file.getUpdateTime());
            return docFile;
        } catch (NumberFormatException ignored) {
            return docFile;
        }
    }

    private void persist(String key, String name, Long fileId) {
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getDeleted, 0)
                .eq(SysConfig::getConfigKey, key)
                .last("LIMIT 1"));
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigName(name);
            config.setConfigGroup("docs");
            config.setRemark("前台 /index 文档弹窗绑定的 Markdown 上传文件 ID");
            config.setConfigValue(fileId == null ? "" : String.valueOf(fileId));
            configMapper.insert(config);
            return;
        }
        config.setConfigName(name);
        config.setConfigGroup("docs");
        config.setRemark("前台 /index 文档弹窗绑定的 Markdown 上传文件 ID");
        config.setConfigValue(fileId == null ? "" : String.valueOf(fileId));
        configMapper.updateById(config);
    }

    private boolean isMarkdown(SysUploadFile file) {
        String original = safe(file.getOriginalName()).toLowerCase();
        String current = safe(file.getCurrentName()).toLowerCase();
        String type = safe(file.getFileType()).toLowerCase();
        return original.endsWith(".md") || current.endsWith(".md") || "md".equals(type)
                || "text/markdown".equals(type) || "text/x-markdown".equals(type);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
