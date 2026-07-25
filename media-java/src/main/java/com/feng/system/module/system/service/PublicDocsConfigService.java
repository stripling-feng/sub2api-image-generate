package com.feng.system.module.system.service;

import com.feng.system.module.system.dto.PublicDocsConfigDTO;
import com.feng.system.module.system.vo.PublicDocsConfigVO;

public interface PublicDocsConfigService {
    PublicDocsConfigVO detail();

    void save(PublicDocsConfigDTO dto);
}
