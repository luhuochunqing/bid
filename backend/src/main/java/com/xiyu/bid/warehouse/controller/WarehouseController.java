// checkstyle:off
package com.xiyu.bid.warehouse.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.domain.WarehouseType;
import com.xiyu.bid.warehouse.domain.WarehouseActionType;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import com.xiyu.bid.warehouse.dto.WarehouseDetailDTO;
import com.xiyu.bid.warehouse.dto.WarehouseOperationLogDTO;
import com.xiyu.bid.warehouse.dto.WarehouseDTO;
import com.xiyu.bid.warehouse.dto.CloseWarehouseRequest;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseOperationLogRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseOperationLogEntity;
import com.xiyu.bid.warehouse.service.WarehouseFilterService;
import com.xiyu.bid.warehouse.service.WarehouseLogService;
import com.xiyu.bid.warehouse.service.WarehouseMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.Arrays;

@RestController
@RequestMapping("/api/knowledge/warehouses")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WarehouseController {

    private static final String PERM = RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION;

    private final WarehouseRepository repo;
    private final WarehouseFilterService filterService;
    private final WarehouseAttachmentRepository attachmentRepo;
    private final WarehouseOperationLogRepository oplogRepo;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseLogService warehouseLogService;
    private final UserResolver userResolver;

    // ── List (multi-dimensional filter) ─────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String types,
            @RequestParam(required = false) String statuses, @RequestParam(required = false) String regions,
            @RequestParam(required = false) String provinces,
            @RequestParam(required = false) LocalDate endDateFrom, @RequestParam(required = false) LocalDate endDateTo,
            @RequestParam(required = false) Boolean hasPropertyCert, @RequestParam(required = false) Boolean hasInvoice,
            @RequestParam(required = false) Boolean hasPhotos, @RequestParam(required = false) String contactPersonKeyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "15") int size) {

        Pageable p = PageRequest.of(page, size, Sort.by("createdAt").descending());
        WarehouseFilterDTO filter = new WarehouseFilterDTO(
                keyword,
                parseEnums(types, WarehouseType.class),
                parseEnums(statuses, WarehouseStatus.class),
                parseCsv(regions),
                parseCsv(provinces),
                endDateFrom, endDateTo,
                hasPropertyCert, hasInvoice, hasPhotos,
                contactPersonKeyword
        );
        Page<WarehouseEntity> result = filterService.filter(filter, p);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "number", result.getNumber(),
                "size", result.getSize()
        )));
    }

    // ── Detail (with attachments + logs + closeReason) ─────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<WarehouseDetailDTO>> detail(@PathVariable Long id) {
        return repo.findById(id)
                .map(e -> {
                    List<WarehouseAttachmentEntity> attachments = attachmentRepo.findByWarehouseId(id);
                    Page<WarehouseOperationLogEntity> logs = oplogRepo
                            .findByWarehouseIdOrderByCreatedAtDesc(id, PageRequest.of(0, 20));
                    return ResponseEntity.ok(ApiResponse.success(warehouseMapper.toDetailDTO(e, attachments, logs.getContent())));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Logs (paginated) ─────────────────────────────────────────────────────

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    public ResponseEntity<ApiResponse<Page<WarehouseOperationLogDTO>>> logs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        Page<WarehouseOperationLogEntity> logs = oplogRepo
                .findByWarehouseIdOrderByCreatedAtDesc(id, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(logs.map(warehouseMapper::toLogDTO)));
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('" + PERM + "')")
    @Auditable(action = "CREATE", entityType = "Warehouse", description = "新增仓库")
    public ResponseEntity<ApiResponse<WarehouseEntity>> create(@Valid @RequestBody WarehouseDTO dto) {
        if (!dto.getEndDate().isAfter(dto.getStartDate()))
            return ResponseEntity.badRequest().body(ApiResponse.error("结束时间必须晚于开始时间"));
        WarehouseEntity e = warehouseMapper.toEntity(dto);
        e.setStatus(WarehouseStatus.IN_USE);
        WarehouseEntity saved = repo.save(e);
        
        com.xiyu.bid.entity.User user = getCurrentUser();
        String operatorUsername = user != null ? user.getFullName() + "(" + user.getUsername() + ")" : "system";
        Long operatorId = user != null ? user.getId() : null;
        
        warehouseLogService.saveLog(saved, WarehouseActionType.CREATE, null, null, null, "新增仓库 - " + saved.getName(), operatorUsername, operatorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("创建成功", saved));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    @Auditable(action = "UPDATE", entityType = "Warehouse", description = "编辑仓库")
    public ResponseEntity<ApiResponse<WarehouseEntity>> update(@PathVariable Long id, @Valid @RequestBody WarehouseDTO dto) {
        return repo.findById(id).map(e -> {
            if (!dto.getEndDate().isAfter(dto.getStartDate()))
                return ResponseEntity.badRequest().body(ApiResponse.<WarehouseEntity>error("结束时间必须晚于开始时间"));
            
            WarehouseEntity oldVal = WarehouseEntity.builder()
                    .name(e.getName()).type(e.getType()).region(e.getRegion()).province(e.getProvince())
                    .address(e.getAddress()).area(e.getArea()).contactPerson(e.getContactPerson()).remarks(e.getRemarks())
                    .startDate(e.getStartDate()).endDate(e.getEndDate()).lessor(e.getLessor()).lessee(e.getLessee())
                    .invoicePeriod(e.getInvoicePeriod()).closePlan(e.getClosePlan())
                    .hasPropertyCert(e.getHasPropertyCert()).hasInvoice(e.getHasInvoice()).hasPhotos(e.getHasPhotos())
                    .certRemarks(e.getCertRemarks()).build();

            com.xiyu.bid.entity.User user = getCurrentUser();
            String operatorUsername = user != null ? user.getFullName() + "(" + user.getUsername() + ")" : "system";
            Long operatorId = user != null ? user.getId() : null;

            warehouseMapper.mergeEntity(e, dto);
            e.setUpdatedBy(operatorId);
            WarehouseEntity saved = repo.save(e);
            warehouseLogService.logEntityChanges(oldVal, saved, operatorUsername, operatorId);
            return ResponseEntity.ok(ApiResponse.success("更新成功", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Close ────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    @Auditable(action = "UPDATE", entityType = "Warehouse", description = "关仓")
    public ResponseEntity<ApiResponse<WarehouseEntity>> close(
            @PathVariable Long id,
            @Valid @RequestBody CloseWarehouseRequest req) {
        return repo.findById(id).map(e -> {
            if (e.getStatus() == WarehouseStatus.CLOSED)
                return ResponseEntity.badRequest().body(ApiResponse.<WarehouseEntity>error("仓库已是关仓状态"));
            
            com.xiyu.bid.entity.User user = getCurrentUser();
            String operatorUsername = user != null ? user.getFullName() + "(" + user.getUsername() + ")" : "system";
            Long operatorId = user != null ? user.getId() : null;

            e.setCloseReason(req.reason());
            e.setStatus(WarehouseStatus.CLOSED);
            WarehouseEntity saved = repo.save(e);
            warehouseLogService.saveLog(saved, WarehouseActionType.CLOSE, null, null, null, "关仓原因：" + req.reason(), operatorUsername, operatorId);
            return ResponseEntity.ok(ApiResponse.success("已关仓", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Restore ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('" + PERM + "')")
    @Auditable(action = "UPDATE", entityType = "Warehouse", description = "恢复仓库")
    public ResponseEntity<ApiResponse<WarehouseEntity>> restore(@PathVariable Long id) {
        return repo.findById(id).map(e -> {
            if (e.getStatus() != WarehouseStatus.CLOSED)
                return ResponseEntity.badRequest().body(ApiResponse.<WarehouseEntity>error("仓库未关仓"));

            com.xiyu.bid.entity.User user = getCurrentUser();
            String operatorUsername = user != null ? user.getFullName() + "(" + user.getUsername() + ")" : "system";
            Long operatorId = user != null ? user.getId() : null;

            // 恢复后系统按 endDate 重新计算状态（IN_USE / EXPIRING / EXPIRED）
            WarehouseStatus recomputed = com.xiyu.bid.warehouse.domain.WarehouseStatusCalculator.recompute(e);
            e.setStatus(recomputed);
            e.setCloseReason(null);
            WarehouseEntity saved = repo.save(e);
            warehouseLogService.saveLog(saved, WarehouseActionType.RESTORE, null, null, null,
                    "恢复仓库使用，状态重新计算为 " + recomputed.getDisplayName(),
                    operatorUsername, operatorId);
            return ResponseEntity.ok(ApiResponse.success("已恢复", saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Attachment endpoints moved to WarehouseAttachmentController ─────────

    private com.xiyu.bid.entity.User getCurrentUser() {
        return userResolver.resolveCurrentUser();
    }

    private <E extends Enum<E>> List<E> parseEnums(String csv, Class<E> enumClass) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isBlank()).map(s -> Enum.valueOf(enumClass, s.trim().toUpperCase())).toList();
    }

    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }
}
