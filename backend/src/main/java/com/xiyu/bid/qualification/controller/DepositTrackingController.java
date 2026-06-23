package com.xiyu.bid.qualification.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.qualification.infrastructure.DepositTracking;
import com.xiyu.bid.qualification.infrastructure.DepositTrackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/knowledge/deposit")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DepositTrackingController {

    private final DepositTrackingRepository depositTrackingRepository;

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDepositSummary() {
        BigDecimal totalPaid = depositTrackingRepository.sumTotalAmount();
        BigDecimal totalPending = depositTrackingRepository.sumPendingAmount();
        long pendingCount = depositTrackingRepository.countPendingDeposits();

        Map<String, Object> summary = Map.of(
                "totalPaid", totalPaid != null ? totalPaid : BigDecimal.ZERO,
                "totalPending", totalPending != null ? totalPending : BigDecimal.ZERO,
                "pendingCount", pendingCount
        );

        return ResponseEntity.ok(ApiResponse.success("Success", summary));
    }

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<DepositTracking>>> getDepositList() {
        return ResponseEntity.ok(ApiResponse.success("Success", depositTrackingRepository.findAllByOrderByPaymentDateDesc()));
    }

    @PostMapping("/return/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BIDADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')")
    public ResponseEntity<ApiResponse<DepositTracking>> markAsReturned(@PathVariable Long id) {
        Optional<DepositTracking> opt = depositTrackingRepository.findById(id);
        if (opt.isPresent()) {
            DepositTracking tracking = opt.get();
            tracking.setStatus("RETURNED");
            depositTrackingRepository.save(tracking);
            return ResponseEntity.ok(ApiResponse.success("Marked as returned", tracking));
        }
        return ResponseEntity.notFound().build();
    }
}
