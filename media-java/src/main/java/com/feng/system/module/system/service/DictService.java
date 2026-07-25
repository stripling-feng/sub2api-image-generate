package com.feng.system.module.system.service;

import com.feng.system.common.api.PageResult;
import com.feng.system.module.system.dto.DictDataDTO;
import com.feng.system.module.system.dto.DictDataQueryDTO;
import com.feng.system.module.system.dto.DictTypeDTO;
import com.feng.system.module.system.dto.DictTypeQueryDTO;
import com.feng.system.module.system.entity.SysDictData;
import com.feng.system.module.system.entity.SysDictType;
import com.feng.system.module.system.vo.DictOptionVO;

import java.util.List;

public interface DictService {
    PageResult<SysDictType> typePage(DictTypeQueryDTO queryDTO);
    void saveType(DictTypeDTO dto);
    void updateType(DictTypeDTO dto);
    void deleteType(Long id);

    PageResult<SysDictData> dataPage(DictDataQueryDTO queryDTO);
    void saveData(DictDataDTO dto);
    void updateData(DictDataDTO dto);
    void deleteData(Long id);

    List<DictOptionVO> options(String typeCode);
}
