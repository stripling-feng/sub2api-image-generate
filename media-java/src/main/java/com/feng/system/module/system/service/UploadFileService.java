package com.feng.system.module.system.service;

import com.feng.system.common.api.PageResult;
import com.feng.system.module.system.dto.UploadFileQueryDTO;
import com.feng.system.module.system.vo.UploadFileVO;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface UploadFileService {

    PageResult<UploadFileVO> page(UploadFileQueryDTO queryDTO);

    UploadFileVO upload(MultipartFile file);

    ResponseEntity<Resource> getContent(Long id, boolean download);
}
