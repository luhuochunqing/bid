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

    private WarehouseFilterCriteria toCriteria(WarehouseFilterDTO dto) {
        if (dto == null) {
            return WarehouseFilterCriteria.empty();
        }
        return new WarehouseFilterCriteria(
                dto.keyword(),
                dto.types() != null ? dto.types() : List.of(),
                dto.statuses() != null ? dto.statuses() : List.of(),
                dto.province(),
                dto.endDateFrom(),
                dto.endDateTo(),
                dto.hasPropertyCert(),
                dto.hasInvoice(),
                dto.hasPhotos(),
                dto.contactPersonKeyword()
        );
    }
}
