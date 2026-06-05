package com.xiyu.bid.casework.controller;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.casework.application.QualificationBorrowCheckService;
import com.xiyu.bid.casework.application.QualificationBorrowCheckService.BorrowCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/qualification")
@RequiredArgsConstructor
public class QualificationBorrowController {

    private final QualificationBorrowCheckService borrowCheckService;

    @GetMapping("/{id}/check-borrow")
    @Auditable(action = "PREVIEW", entityType = "Qualification", description = "预览资质文件")
    public ResponseEntity<Map<String, Object>> checkBorrow(
            @PathVariable Long id,
            @RequestParam Long projectId) {

        BorrowCheckResult result = borrowCheckService.checkBorrow(id, projectId);

        if (result.allowed()) {
            return ResponseEntity.ok(Map.of(
                    "allowed", true,
                    "reason", result.reason(),
                    "borrowRecordId", result.borrowRecordId()
            ));
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "allowed", false,
                        "reason", result.reason()
                ));
    }
}

