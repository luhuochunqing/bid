// checkstyle:off
package com.xiyu.bid.performance.infrastructure;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.performance.application.command.PerformanceUpsertCommand;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.service.CreatePerformanceAppService;
import com.xiyu.bid.performance.application.service.UpdatePerformanceAppService;
import com.xiyu.bid.performance.application.service.DeletePerformanceAppService;
import com.xiyu.bid.performance.application.service.ListPerformanceAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final CreatePerformanceAppService createService;
    private final UpdatePerformanceAppService updateService;
    private final DeletePerformanceAppService deleteService;
    private final ListPerformanceAppService listService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "CREATE", entityType = "Performance", description = "创建业绩")
    public ResponseEntity<ApiResponse<PerformanceDTO>> create(@Valid @RequestBody PerformanceUpsertCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("业绩创建成功", createService.create(command)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Performance", description = "获取业绩列表")
    public ResponseEntity<ApiResponse<List<PerformanceDTO>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String projectType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String territory,
            @RequestParam(required = false) String signingDateStart,
            @RequestParam(required = false) String signingDateEnd,
            @RequestParam(required = false) String expiryDateStart,
            @RequestParam(required = false) String expiryDateEnd,
            @RequestParam(required = false) Boolean hasBidNotice,
            @RequestParam(required = false) String projectManagerKeyword
    ) {
        java.time.LocalDate signingStart = signingDateStart != null && !signingDateStart.isBlank()
                ? java.time.LocalDate.parse(signingDateStart) : null;
        java.time.LocalDate signingEnd = signingDateEnd != null && !signingDateEnd.isBlank()
                ? java.time.LocalDate.parse(signingDateEnd) : null;
        java.time.LocalDate expiryStart = expiryDateStart != null && !expiryDateStart.isBlank()
                ? java.time.LocalDate.parse(expiryDateStart) : null;
        java.time.LocalDate expiryEnd = expiryDateEnd != null && !expiryDateEnd.isBlank()
                ? java.time.LocalDate.parse(expiryDateEnd) : null;
        var criteria = com.xiyu.bid.performance.application.command.PerformanceSearchCriteria.of(
                keyword, customerType, projectType, status,
                territory, signingStart, signingEnd, expiryStart, expiryEnd,
                hasBidNotice, projectManagerKeyword);
        return ResponseEntity.ok(ApiResponse.success("业绩列表获取成功", listService.list(criteria)));
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Auditable(action = "READ", entityType = "Performance", description = "获取业绩详情")
    public ResponseEntity<ApiResponse<PerformanceDTO>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("业绩详情获取成功", listService.get(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "UPDATE", entityType = "Performance", description = "更新业绩")
    public ResponseEntity<ApiResponse<PerformanceDTO>> update(@PathVariable Long id,
            @Valid @RequestBody PerformanceUpsertCommand command) {
        return ResponseEntity.ok(ApiResponse.success("业绩更新成功", updateService.update(id, command)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "DELETE", entityType = "Performance", description = "删除业绩")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deleteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
