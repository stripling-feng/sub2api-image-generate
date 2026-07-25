package com.feng.system.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feng.system.common.api.PageResult;
import com.feng.system.common.exception.BusinessException;
import com.feng.system.module.system.dto.DictDataDTO;
import com.feng.system.module.system.dto.DictDataQueryDTO;
import com.feng.system.module.system.dto.DictTypeDTO;
import com.feng.system.module.system.dto.DictTypeQueryDTO;
import com.feng.system.module.system.entity.SysDictData;
import com.feng.system.module.system.entity.SysDictType;
import com.feng.system.module.system.mapper.SysDictDataMapper;
import com.feng.system.module.system.mapper.SysDictTypeMapper;
import com.feng.system.module.system.service.DictService;
import com.feng.system.module.system.vo.DictOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    @Override
    public PageResult<SysDictType> typePage(DictTypeQueryDTO queryDTO) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDeleted, 0)
                .like(StringUtils.hasText(queryDTO.getTypeName()), SysDictType::getTypeName, queryDTO.getTypeName())
                .like(StringUtils.hasText(queryDTO.getTypeCode()), SysDictType::getTypeCode, queryDTO.getTypeCode())
                .orderByDesc(SysDictType::getId);
        Page<SysDictType> page = dictTypeMapper.selectPage(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveType(DictTypeDTO dto) {
        validateTypeCodeUnique(dto.getTypeCode(), null);
        SysDictType dictType = new SysDictType();
        BeanUtils.copyProperties(dto, dictType);
        dictTypeMapper.insert(dictType);
        dto.setId(dictType.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateType(DictTypeDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("字典类型ID不能为空");
        }
        ensureTypeExists(dto.getId());
        validateTypeCodeUnique(dto.getTypeCode(), dto.getId());
        SysDictType dictType = new SysDictType();
        BeanUtils.copyProperties(dto, dictType);
        dictTypeMapper.updateById(dictType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        ensureTypeExists(id);
        long dataCount = dictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getTypeId, id)
                .eq(SysDictData::getDeleted, 0));
        if (dataCount > 0) {
            throw new BusinessException("该字典类型下存在字典数据，不能删除");
        }
        dictTypeMapper.deleteById(id);
    }

    @Override
    public PageResult<SysDictData> dataPage(DictDataQueryDTO queryDTO) {
        Long typeId = resolveTypeId(queryDTO.getTypeId(), queryDTO.getTypeCode());
        if (typeId == null) {
            return PageResult.empty(queryDTO.getPageNum(), queryDTO.getPageSize());
        }
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDeleted, 0)
                .eq(SysDictData::getTypeId, typeId)
                .like(StringUtils.hasText(queryDTO.getDictLabel()), SysDictData::getDictLabel, queryDTO.getDictLabel())
                .like(StringUtils.hasText(queryDTO.getDictValue()), SysDictData::getDictValue, queryDTO.getDictValue())
                .orderByAsc(SysDictData::getDictSort)
                .orderByDesc(SysDictData::getId);
        Page<SysDictData> page = dictDataMapper.selectPage(new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveData(DictDataDTO dto) {
        ensureTypeExists(dto.getTypeId());
        validateDictValueUnique(dto.getTypeId(), dto.getDictValue(), null);
        SysDictData dictData = new SysDictData();
        BeanUtils.copyProperties(dto, dictData);
        dictDataMapper.insert(dictData);
        dto.setId(dictData.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateData(DictDataDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("字典数据ID不能为空");
        }
        ensureTypeExists(dto.getTypeId());
        SysDictData current = dictDataMapper.selectById(dto.getId());
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("字典数据不存在");
        }
        validateDictValueUnique(dto.getTypeId(), dto.getDictValue(), dto.getId());
        SysDictData dictData = new SysDictData();
        BeanUtils.copyProperties(dto, dictData);
        dictDataMapper.updateById(dictData);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteData(Long id) {
        SysDictData current = dictDataMapper.selectById(id);
        if (current == null || current.getDeleted() == 1) {
            throw new BusinessException("字典数据不存在");
        }
        dictDataMapper.deleteById(id);
    }

    @Override
    public List<DictOptionVO> options(String typeCode) {
        Long typeId = resolveTypeId(null, typeCode);
        if (typeId == null) {
            return List.of();
        }
        return dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getDeleted, 0)
                        .eq(SysDictData::getTypeId, typeId)
                        .orderByAsc(SysDictData::getDictSort)
                        .orderByAsc(SysDictData::getId))
                .stream()
                .map(item -> {
                    DictOptionVO vo = new DictOptionVO();
                    vo.setLabel(item.getDictLabel());
                    vo.setValue(item.getDictValue());
                    vo.setTagType(item.getTagType());
                    vo.setCssClass(item.getCssClass());
                    return vo;
                })
                .toList();
    }

    private void validateTypeCodeUnique(String typeCode, Long excludeId) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTypeCode, typeCode)
                .eq(SysDictType::getDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(SysDictType::getId, excludeId);
        }
        if (dictTypeMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("字典类型编码已存在");
        }
    }

    private void validateDictValueUnique(Long typeId, String dictValue, Long excludeId) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getTypeId, typeId)
                .eq(SysDictData::getDictValue, dictValue)
                .eq(SysDictData::getDeleted, 0);
        if (excludeId != null) {
            wrapper.ne(SysDictData::getId, excludeId);
        }
        if (dictDataMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("同一字典类型下键值不能重复");
        }
    }

    private void ensureTypeExists(Long typeId) {
        SysDictType dictType = dictTypeMapper.selectById(typeId);
        if (dictType == null || dictType.getDeleted() == 1) {
            throw new BusinessException("字典类型不存在");
        }
    }

    private Long resolveTypeId(Long typeId, String typeCode) {
        if (typeId != null) {
            return typeId;
        }
        if (!StringUtils.hasText(typeCode)) {
            return null;
        }
        SysDictType dictType = dictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDeleted, 0)
                .eq(SysDictType::getTypeCode, typeCode)
                .last("limit 1"));
        return dictType == null ? null : dictType.getId();
    }
}