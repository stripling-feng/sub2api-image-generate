package com.feng.system.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.dto.SystemConfigDTO;
import com.feng.system.module.system.entity.SysConfig;
import com.feng.system.module.system.mapper.SysConfigMapper;
import com.feng.system.module.system.service.SystemConfigService;
import com.feng.system.module.system.vo.PublicSystemConfigVO;
import com.feng.system.module.system.vo.SystemConfigVO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SysConfigMapper configMapper;

    private static final Map<String, ConfigDefinition> DEFINITIONS = new LinkedHashMap<>();
    private final Map<String, String> configCache = new ConcurrentHashMap<>();
    private volatile Instant lastLoadTime = Instant.MIN;

    @Value("${system.config.refresh-interval-seconds:60}")
    private int refreshIntervalSeconds;

    static {
        register(new ConfigDefinition("site.name", "网站名称", "Feng AI Admin", "site", "系统站点全称"));
        register(new ConfigDefinition("site.copyright", "版权信息", "© 2026 Feng AI Admin. All rights reserved.", "site", "登录页和系统版权"));
        register(new ConfigDefinition("security.default-password", "默认密码", "admin123", "security", "新建用户和重置密码时使用"));
        register(new ConfigDefinition("security.login-fail-max-attempts", "登录失败限制次数", "5", "security", "超出后触发锁定"));
        register(new ConfigDefinition("security.login-fail-window-minutes", "登录失败统计时间", "5", "security", "失败次数统计窗口"));
        register(new ConfigDefinition("security.login-fail-lock-minutes", "登录锁定时间", "5", "security", "达到阈值后的锁定时长"));
        register(new ConfigDefinition("upload.provider", "上传存储方式", "server", "upload", "server/minio/aliyun-oss/cloudflare-r2"));
        register(new ConfigDefinition("upload.server.base-path", "服务器存储目录", "uploads", "upload", "相对于后端工作目录的存储路径"));
        register(new ConfigDefinition("upload.server.base-url", "服务器访问前缀", "", "upload", "本地文件访问前缀"));
        register(new ConfigDefinition("upload.oss.endpoint", "阿里云OSS Endpoint", "", "upload", "例如 oss-cn-hangzhou.aliyuncs.com"));
        register(new ConfigDefinition("upload.oss.bucket", "阿里云OSS Bucket", "", "upload", "阿里云OSS桶名称"));
        register(new ConfigDefinition("upload.oss.access-key-id", "阿里云OSS AccessKeyId", "", "upload", "阿里云访问密钥ID"));
        register(new ConfigDefinition("upload.oss.access-key-secret", "阿里云OSS AccessKeySecret", "", "upload", "阿里云访问密钥Secret"));
        register(new ConfigDefinition("upload.oss.domain", "阿里云OSS 自定义域名", "", "upload", "可选"));
        register(new ConfigDefinition("upload.minio.endpoint", "MinIO Endpoint", "", "upload", "例如 http://127.0.0.1:9000"));
        register(new ConfigDefinition("upload.minio.bucket", "MinIO Bucket", "", "upload", "MinIO桶名称"));
        register(new ConfigDefinition("upload.minio.access-key", "MinIO AccessKey", "", "upload", "MinIO访问账号"));
        register(new ConfigDefinition("upload.minio.secret-key", "MinIO SecretKey", "", "upload", "MinIO访问密钥"));
        register(new ConfigDefinition("upload.minio.domain", "MinIO 自定义域名", "", "upload", "可选"));
        register(new ConfigDefinition("upload.r2.endpoint", "Cloudflare R2 Endpoint", "", "upload", "https://<account-id>.r2.cloudflarestorage.com"));
        register(new ConfigDefinition("upload.r2.bucket", "Cloudflare R2 Bucket", "", "upload", "R2 Bucket"));
        register(new ConfigDefinition("upload.r2.access-key-id", "Cloudflare R2 AccessKeyId", "", "upload", "R2 S3 Access Key ID"));
        register(new ConfigDefinition("upload.r2.access-key-secret", "Cloudflare R2 AccessKeySecret", "", "upload", "R2 S3 Secret Access Key"));
        register(new ConfigDefinition("upload.r2.domain", "Cloudflare R2 Domain", "", "upload", "Optional public domain"));
    }

    @Override
    public SystemConfigVO getManageConfig() {
        Map<String, String> values = loadConfigValues();
        SystemConfigVO vo = new SystemConfigVO();
        vo.setSiteName(values.get("site.name"));
        vo.setCopyright(values.get("site.copyright"));
        vo.setDefaultPassword(values.get("security.default-password"));
        vo.setLoginFailMaxAttempts(parsePositiveInt(values.get("security.login-fail-max-attempts"), 5));
        vo.setLoginFailWindowMinutes(parsePositiveInt(values.get("security.login-fail-window-minutes"), 5));
        vo.setLoginFailLockMinutes(parsePositiveInt(values.get("security.login-fail-lock-minutes"), 5));
        vo.setUploadProvider(values.get("upload.provider"));
        vo.setUploadServerBasePath(values.get("upload.server.base-path"));
        vo.setUploadServerBaseUrl(values.get("upload.server.base-url"));
        vo.setUploadOssEndpoint(values.get("upload.oss.endpoint"));
        vo.setUploadOssBucket(values.get("upload.oss.bucket"));
        vo.setUploadOssAccessKeyId(values.get("upload.oss.access-key-id"));
        vo.setUploadOssAccessKeySecret(values.get("upload.oss.access-key-secret"));
        vo.setUploadOssDomain(values.get("upload.oss.domain"));
        vo.setUploadMinioEndpoint(values.get("upload.minio.endpoint"));
        vo.setUploadMinioBucket(values.get("upload.minio.bucket"));
        vo.setUploadMinioAccessKey(values.get("upload.minio.access-key"));
        vo.setUploadMinioSecretKey(values.get("upload.minio.secret-key"));
        vo.setUploadMinioDomain(values.get("upload.minio.domain"));
        vo.setUploadR2Endpoint(values.get("upload.r2.endpoint"));
        vo.setUploadR2Bucket(values.get("upload.r2.bucket"));
        vo.setUploadR2AccessKeyId(values.get("upload.r2.access-key-id"));
        vo.setUploadR2AccessKeySecret(values.get("upload.r2.access-key-secret"));
        vo.setUploadR2Domain(values.get("upload.r2.domain"));
        return vo;
    }

    @Override
    public PublicSystemConfigVO getPublicConfig() {
        SystemConfigVO manageConfig = getManageConfig();
        PublicSystemConfigVO vo = new PublicSystemConfigVO();
        vo.setSiteName(manageConfig.getSiteName());
        vo.setCopyright(manageConfig.getCopyright());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(SystemConfigDTO dto) {
        validateConfig(dto);
        Map<String, SysConfig> existing = configMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getDeleted, 0)
                        .in(SysConfig::getConfigKey, DEFINITIONS.keySet()))
                .stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, item -> item, (left, right) -> left));

        persist("site.name", dto.getSiteName(), existing);
        persist("site.copyright", dto.getCopyright(), existing);
        persist("security.default-password", dto.getDefaultPassword(), existing);
        persist("security.login-fail-max-attempts", String.valueOf(dto.getLoginFailMaxAttempts()), existing);
        persist("security.login-fail-window-minutes", String.valueOf(dto.getLoginFailWindowMinutes()), existing);
        persist("security.login-fail-lock-minutes", String.valueOf(dto.getLoginFailLockMinutes()), existing);
        persist("upload.provider", dto.getUploadProvider(), existing);
        persist("upload.server.base-path", safe(dto.getUploadServerBasePath()), existing);
        persist("upload.server.base-url", safe(dto.getUploadServerBaseUrl()), existing);
        persist("upload.oss.endpoint", safe(dto.getUploadOssEndpoint()), existing);
        persist("upload.oss.bucket", safe(dto.getUploadOssBucket()), existing);
        persist("upload.oss.access-key-id", safe(dto.getUploadOssAccessKeyId()), existing);
        persist("upload.oss.access-key-secret", safe(dto.getUploadOssAccessKeySecret()), existing);
        persist("upload.oss.domain", safe(dto.getUploadOssDomain()), existing);
        persist("upload.minio.endpoint", safe(dto.getUploadMinioEndpoint()), existing);
        persist("upload.minio.bucket", safe(dto.getUploadMinioBucket()), existing);
        persist("upload.minio.access-key", safe(dto.getUploadMinioAccessKey()), existing);
        persist("upload.minio.secret-key", safe(dto.getUploadMinioSecretKey()), existing);
        persist("upload.minio.domain", safe(dto.getUploadMinioDomain()), existing);
        persist("upload.r2.endpoint", safe(dto.getUploadR2Endpoint()), existing);
        persist("upload.r2.bucket", safe(dto.getUploadR2Bucket()), existing);
        persist("upload.r2.access-key-id", safe(dto.getUploadR2AccessKeyId()), existing);
        persist("upload.r2.access-key-secret", safe(dto.getUploadR2AccessKeySecret()), existing);
        persist("upload.r2.domain", safe(dto.getUploadR2Domain()), existing);
        refreshCache();
    }

    @Override
    public String getDefaultPassword() {
        return loadConfigValues().get("security.default-password");
    }

    @Override
    public int getLoginFailMaxAttempts() {
        return parsePositiveInt(loadConfigValues().get("security.login-fail-max-attempts"), 5);
    }

    @Override
    public int getLoginFailWindowMinutes() {
        return parsePositiveInt(loadConfigValues().get("security.login-fail-window-minutes"), 5);
    }

    @Override
    public int getLoginFailLockMinutes() {
        return parsePositiveInt(loadConfigValues().get("security.login-fail-lock-minutes"), 5);
    }

    @Override
    public String getUploadProvider() {
        return loadConfigValues().get("upload.provider");
    }

    @Override
    public String getUploadServerBasePath() {
        String configured = loadConfigValues().get("upload.server.base-path");
        return StringUtils.hasText(configured) ? configured : "uploads";
    }

    @Override
    public String getConfigValue(String key) {
        return loadConfigValues().getOrDefault(key, "");
    }

    private static void register(ConfigDefinition definition) {
        DEFINITIONS.put(definition.getKey(), definition);
    }

    @PostConstruct
    public void init() {
        refreshCache();
    }

    private void refreshCache() {
        try {
            List<SysConfig> configs = configMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getDeleted, 0)
                    .in(SysConfig::getConfigKey, DEFINITIONS.keySet()));
            Map<String, String> values = configs.stream()
                    .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue, (left, right) -> left, LinkedHashMap::new));
            DEFINITIONS.forEach((key, definition) -> values.putIfAbsent(key, definition.getDefaultValue()));
            configCache.clear();
            configCache.putAll(values);
            lastLoadTime = Instant.now();
        } catch (Exception e) {
            log.warn("Failed to refresh system config cache", e);
        }
    }

    private Map<String, String> loadConfigValues() {
        if (Duration.between(lastLoadTime, Instant.now()).getSeconds() > refreshIntervalSeconds) {
            synchronized (this) {
                if (Duration.between(lastLoadTime, Instant.now()).getSeconds() > refreshIntervalSeconds) {
                    refreshCache();
                }
            }
        }
        return configCache;
    }

    private void persist(String key, String value, Map<String, SysConfig> existing) {
        ConfigDefinition definition = DEFINITIONS.get(key);
        SysConfig config = existing.get(key);
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigName(definition.getName());
            config.setConfigGroup(definition.getGroup());
            config.setRemark(definition.getRemark());
            config.setConfigValue(value);
            configMapper.insert(config);
            return;
        }
        config.setConfigName(definition.getName());
        config.setConfigGroup(definition.getGroup());
        config.setRemark(definition.getRemark());
        config.setConfigValue(value);
        configMapper.updateById(config);
    }

    private void validateConfig(SystemConfigDTO dto) {
        if (dto.getDefaultPassword().trim().length() < 6) {
            throw new BusinessException("默认密码长度不能少于6位");
        }
        if (!StringUtils.hasText(dto.getUploadProvider())) {
            throw new BusinessException("上传存储方式不能为空");
        }
        switch (dto.getUploadProvider()) {
            case "server" -> {
                if (!StringUtils.hasText(dto.getUploadServerBasePath())) {
                    throw new BusinessException("服务器存储目录不能为空");
                }
            }
            case "aliyun-oss" -> {
                if (!StringUtils.hasText(dto.getUploadOssEndpoint()) ||
                        !StringUtils.hasText(dto.getUploadOssBucket()) ||
                        !StringUtils.hasText(dto.getUploadOssAccessKeyId()) ||
                        !StringUtils.hasText(dto.getUploadOssAccessKeySecret())) {
                    throw new BusinessException("阿里云OSS配置不完整");
                }
            }
            case "minio" -> {
                if (!StringUtils.hasText(dto.getUploadMinioEndpoint()) ||
                        !StringUtils.hasText(dto.getUploadMinioBucket()) ||
                        !StringUtils.hasText(dto.getUploadMinioAccessKey()) ||
                        !StringUtils.hasText(dto.getUploadMinioSecretKey())) {
                    throw new BusinessException("MinIO配置不完整");
                }
            }
            case "cloudflare-r2" -> {
                if (!StringUtils.hasText(dto.getUploadR2Endpoint()) ||
                        !StringUtils.hasText(dto.getUploadR2Bucket()) ||
                        !StringUtils.hasText(dto.getUploadR2AccessKeyId()) ||
                        !StringUtils.hasText(dto.getUploadR2AccessKeySecret())) {
                    throw new BusinessException("Cloudflare R2 config is incomplete");
                }
            }
            default -> throw new BusinessException("不支持的上传存储方式");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int parsePositiveInt(String value, int defaultValue) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @Getter
    private static class ConfigDefinition {
        private final String key;
        private final String name;
        private final String defaultValue;
        private final String group;
        private final String remark;

        private ConfigDefinition(String key, String name, String defaultValue, String group, String remark) {
            this.key = key;
            this.name = name;
            this.defaultValue = defaultValue;
            this.group = group;
            this.remark = remark;
        }
    }
}
