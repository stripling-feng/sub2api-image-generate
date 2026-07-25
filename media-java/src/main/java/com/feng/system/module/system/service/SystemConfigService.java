package com.feng.system.module.system.service;

import com.feng.system.module.system.dto.SystemConfigDTO;
import com.feng.system.module.system.vo.PublicSystemConfigVO;
import com.feng.system.module.system.vo.SystemConfigVO;

public interface SystemConfigService {
    SystemConfigVO getManageConfig();
    PublicSystemConfigVO getPublicConfig();
    void saveConfig(SystemConfigDTO dto);
    String getDefaultPassword();
    int getLoginFailMaxAttempts();
    int getLoginFailWindowMinutes();
    int getLoginFailLockMinutes();
    String getUploadProvider();
    String getUploadServerBasePath();
    String getConfigValue(String key);
}
