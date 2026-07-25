package com.feng.system.module.system.service;

import com.feng.system.module.system.vo.MenuTreeVO;

import java.util.List;

public interface DistrictService {

    /**
     * 从外部数据源同步行政区划数据（同步执行，立即返回结果）
     */
    void sync();

    /**
     * 从外部数据源异步同步行政区划数据（后台执行，立即返回）
     */
    void syncAsync();

    /**
     * 获取行政区划树形结构
     */
    List<MenuTreeVO> tree();
}
