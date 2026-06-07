// Input: resources repositories, DTOs, and support services
// Output: Bar Asset business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.resources.service;

import com.xiyu.bid.demo.service.DemoDataProvider;
import com.xiyu.bid.demo.service.DemoFusionService;
import com.xiyu.bid.demo.service.DemoModeService;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.resources.dto.BarAssetCreateRequest;
import com.xiyu.bid.resources.dto.BarAssetResponseDTO;
import com.xiyu.bid.resources.dto.BarAssetUpdateRequest;
import com.xiyu.bid.resources.dto.ResourceResponseMapper;
import com.xiyu.bid.resources.entity.BarAsset;
import com.xiyu.bid.resources.repository.BarAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BarAssetService {

    private final BarAssetRepository barAssetRepository;
    private final DemoModeService demoModeService;
    private final DemoDataProvider demoDataProvider;
    private final DemoFusionService demoFusionService;

    @Transactional
    public BarAssetResponseDTO createBarAsset(BarAssetCreateRequest request) {
        // Validation
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (request.getType() == null) {
            throw new IllegalArgumentException("Type is required");
        }
        if (request.getValue() == null || request.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Value must be positive");
        }
        if (request.getStatus() == null) {
            throw new IllegalArgumentException("Status is required");
        }
        if (request.getAcquireDate() == null) {
            throw new IllegalArgumentException("Acquire date is required");
        }
        if (request.getAcquireDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Acquire date cannot be in the future");
        }

        BarAsset asset = BarAsset.builder()
                .name(request.getName())
                .type(request.getType())
                .value(request.getValue())
                .status(request.getStatus())
                .acquireDate(request.getAcquireDate())
                .remark(request.getRemark())
                .build();

        return ResourceResponseMapper.toDto(barAssetRepository.save(asset));
    }

    public BarAssetResponseDTO getBarAssetById(Long id) {
        if (isDemoEntityId(id)) {
            return demoDataProvider.findDemoBarAssetById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("BarAsset", id.toString()));
        }
        return barAssetRepository.findById(id)
                .map(ResourceResponseMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("BarAsset", id.toString()));
    }

    public Page<BarAssetResponseDTO> getAllBarAssets(Pageable pageable) {
        Page<BarAssetResponseDTO> realPage = barAssetRepository.findAll(pageable).map(ResourceResponseMapper::toDto);
        if (!demoModeService.isEnabled()) {
            return realPage;
        }
        return demoFusionService.mergePage(realPage, demoDataProvider.getDemoBarAssets(), BarAssetResponseDTO::getId);
    }

    public Page<BarAssetResponseDTO> getBarAssetsByType(String type, Pageable pageable) {
        return barAssetRepository.findByType(BarAsset.AssetType.valueOf(type), pageable)
                .map(ResourceResponseMapper::toDto);
    }

    public Page<BarAssetResponseDTO> getBarAssetsByStatus(String status, Pageable pageable) {
        return barAssetRepository.findByStatus(BarAsset.AssetStatus.valueOf(status), pageable)
                .map(ResourceResponseMapper::toDto);
    }

    public Page<BarAssetResponseDTO> getBarAssetsByValueRange(BigDecimal minValue, BigDecimal maxValue, Pageable pageable) {
        return barAssetRepository.findByValueBetween(minValue, maxValue, pageable)
                .map(ResourceResponseMapper::toDto);
    }

    public Page<BarAssetResponseDTO> getBarAssetsByAcquireDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return barAssetRepository.findByAcquireDateBetween(startDate, endDate, pageable)
                .map(ResourceResponseMapper::toDto);
    }

    public Page<BarAssetResponseDTO> searchBarAssets(String keyword, Pageable pageable) {
        return barAssetRepository.searchByNameContainingIgnoreCase(keyword, pageable)
                .map(ResourceResponseMapper::toDto);
    }

    @Transactional
    public BarAssetResponseDTO updateBarAsset(Long id, BarAssetUpdateRequest request) {
        rejectDemoEntityMutation(id);
        BarAsset asset = getBarAssetEntityById(id);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            asset.setName(request.getName());
        }
        if (request.getType() != null) {
            asset.setType(request.getType());
        }
        if (request.getValue() != null) {
            if (request.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Value must be positive");
            }
            asset.setValue(request.getValue());
        }
        if (request.getStatus() != null) {
            asset.setStatus(request.getStatus());
        }
        if (request.getAcquireDate() != null) {
            if (request.getAcquireDate().isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("Acquire date cannot be in the future");
            }
            asset.setAcquireDate(request.getAcquireDate());
        }
        if (request.getRemark() != null) {
            asset.setRemark(request.getRemark());
        }

        return ResourceResponseMapper.toDto(barAssetRepository.save(asset));
    }

    @Transactional
    public void deleteBarAsset(Long id) {
        rejectDemoEntityMutation(id);
        if (!barAssetRepository.existsById(id)) {
            throw new ResourceNotFoundException("BarAsset", id.toString());
        }
        barAssetRepository.deleteById(id);
    }

    public BigDecimal getTotalAssetValue() {
        BigDecimal total = barAssetRepository.sumTotalValue();
        BigDecimal normalized = total != null ? total : BigDecimal.ZERO;
        if (!demoModeService.isEnabled()) {
            return normalized;
        }
        BigDecimal demoTotal = demoDataProvider.getDemoBarAssets().stream()
                .map(BarAssetResponseDTO::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return normalized.add(demoTotal);
    }

    public Map<String, Object> getAssetStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalAssets", barAssetRepository.count());
        statistics.put("totalValue", getTotalAssetValue());

        for (BarAsset.AssetType type : BarAsset.AssetType.values()) {
            statistics.put(type.name().toLowerCase() + "Count", barAssetRepository.countByType(type));
        }
        if (demoModeService.isEnabled()) {
            long demoCount = demoDataProvider.getDemoBarAssets().size();
            Number existing = (Number) statistics.getOrDefault("totalAssets", 0L);
            statistics.put("totalAssets", existing.longValue() + demoCount);
        }

        return statistics;
    }

    private BarAsset getBarAssetEntityById(Long id) {
        return barAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BarAsset", id.toString()));
    }

    private boolean isDemoEntityId(Long id) {
        return demoModeService.isEnabled() && id != null && id < 0;
    }

    private void rejectDemoEntityMutation(Long id) {
        if (isDemoEntityId(id)) {
            throw new IllegalArgumentException("Demo records are read-only in e2e mode");
        }
    }
}
