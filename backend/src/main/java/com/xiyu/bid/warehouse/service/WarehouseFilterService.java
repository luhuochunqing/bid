package com.xiyu.bid.warehouse.service;

import com.xiyu.bid.warehouse.domain.WarehouseFilterCriteria;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseFilterService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseFilterSpec warehouseFilterSpec;

    public Page<WarehouseEntity> filter(WarehouseFilterDTO dto, Pageable pageable) {
        WarehouseFilterCriteria criteria = toCriteria(dto);
        return warehouseRepository.findAll(warehouseFilterSpec.toSpec(criteria), pageable);
    }

    /**
     * 不分页返回所有符合条件的数据，用于导出场景。
     */
    public List<WarehouseEntity> filterAll(WarehouseFilterDTO dto) {
        WarehouseFilterCriteria criteria = toCriteria(dto);
        return warehouseRepository.findAll(warehouseFilterSpec.toSpec(criteria));
    }

    /**
     * 按 ID 列表加载仓库实体，用于批量导出场景。
     */
    public List<WarehouseEntity> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return warehouseRepository.findAllById(ids);
    }

    private WarehouseFilterCriteria toCriteria(WarehouseFilterDTO dto) {
        if (dto == null) {
            return WarehouseFilterCriteria.empty();
        }
        return new WarehouseFilterCriteria(
                dto.keyword(),
                dto.types() != null ? dto.types() : List.of(),
                dto.statuses() != null ? dto.statuses() : List.of(),
                dto.regions() != null ? dto.regions() : List.of(),
                dto.provinces() != null ? dto.provinces() : List.of(),
                dto.endDateFrom(),
                dto.endDateTo(),
                dto.hasPropertyCert(),
                dto.hasInvoice(),
                dto.hasPhotos(),
                dto.contactPersonKeyword()
        );
    }
}
