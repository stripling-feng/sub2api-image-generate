package com.feng.system.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.feng.system.module.system.entity.SysDistrict;
import com.feng.system.module.system.mapper.SysDistrictMapper;
import com.feng.system.module.system.service.DistrictService;
import com.feng.system.module.system.vo.MenuTreeVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistrictServiceImpl implements DistrictService {

    private static final String DISTRICT_DATA_URL = "https://raw.githubusercontent.com/modood/Administrative-divisions-of-China/master/dist/pca-code.json";

    private final SysDistrictMapper districtMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void sync() {
        log.info("开始同步行政区划数据");
        String json = restTemplate.getForObject(DISTRICT_DATA_URL, String.class);
        if (json == null || json.isBlank()) {
            throw new RuntimeException("获取外部行政区划数据失败");
        }

        districtMapper.delete(new LambdaQueryWrapper<>());

        List<RawNode> rawList;
        try {
            rawList = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("解析行政区划数据失败", e);
        }

        int[] count = {0};
        for (RawNode province : rawList) {
            count[0] += insertRecursive(province.code(), province.name(), null, province.children(), 1);
        }

        log.info("行政区划数据同步完成，共 {} 条", count[0]);
    }

    @Async
    @Override
    public void syncAsync() {
        sync();
    }

    private int insertRecursive(String code, String name, String parentCode, List<RawNode> children, int level) {
        SysDistrict entity = new SysDistrict();
        entity.setCode(code);
        entity.setName(name);
        entity.setParentCode(parentCode);
        entity.setLevel(level);
        districtMapper.insert(entity);

        int count = 1;
        if (children != null && !children.isEmpty()) {
            for (RawNode child : children) {
                count += insertRecursive(child.code(), child.name(), code, child.children(), level + 1);
            }
        }
        return count;
    }

    @Override
    public List<MenuTreeVO> tree() {
        List<SysDistrict> all = districtMapper.selectList(
                new LambdaQueryWrapper<SysDistrict>().orderByAsc(SysDistrict::getCode));

        if (all.isEmpty()) {
            return List.of();
        }

        Map<String, MenuTreeVO> voMap = new HashMap<>();
        for (SysDistrict d : all) {
            MenuTreeVO vo = new MenuTreeVO();
            vo.setId(d.getId());
            vo.setMenuName(d.getName());
            vo.setMenuSort(Integer.parseInt(d.getCode()));
            vo.setLevel(d.getLevel());
            voMap.put(d.getCode(), vo);
        }

        List<MenuTreeVO> roots = new ArrayList<>();
        for (SysDistrict d : all) {
            MenuTreeVO vo = voMap.get(d.getCode());
            String parentCode = d.getParentCode();
            if (parentCode == null || parentCode.isBlank()) {
                vo.setParentId(0L);
                roots.add(vo);
            } else {
                MenuTreeVO parent = voMap.get(parentCode);
                if (parent != null) {
                    vo.setParentId(parent.getId());
                } else {
                    vo.setParentId(0L);
                    roots.add(vo);
                }
            }
        }

        Map<Long, List<MenuTreeVO>> childrenMap = voMap.values().stream()
                .filter(vo -> vo.getParentId() != 0L)
                .collect(Collectors.groupingBy(MenuTreeVO::getParentId));
        fillTree(roots, childrenMap);
        roots.sort(Comparator.comparing(MenuTreeVO::getMenuSort).thenComparing(MenuTreeVO::getId));
        return roots;
    }

    private void fillTree(List<MenuTreeVO> parents, Map<Long, List<MenuTreeVO>> childrenMap) {
        for (MenuTreeVO parent : parents) {
            List<MenuTreeVO> children = new ArrayList<>(
                    childrenMap.getOrDefault(parent.getId(), List.of()));
            children.sort(Comparator.comparing(MenuTreeVO::getMenuSort).thenComparing(MenuTreeVO::getId));
            parent.setChildren(children);
            fillTree(children, childrenMap);
        }
    }

    private record RawNode(String code, String name, List<RawNode> children) {}
}
