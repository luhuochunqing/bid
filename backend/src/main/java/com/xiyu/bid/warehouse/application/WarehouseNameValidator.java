package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 仓库名称唯一性校验器。
 *
 * <p>统一封装单条查询与批量预加载两种策略：
 * <ul>
 *   <li>单条新增：{@link #isNameTaken(String)} 直接查库，简单明确</li>
 *   <li>批量导入：{@link #loadExistingNames()} 一次性预加载全量名称，避免 N+1 查询</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WarehouseNameValidator {

    private final WarehouseRepository warehouseRepo;

    public boolean isNameTaken(String name) {
        return warehouseRepo.existsByName(name);
    }

    public Set<String> loadExistingNames() {
        return warehouseRepo.findAll().stream()
                .map(WarehouseEntity::getName)
                .collect(Collectors.toSet());
    }
}
