package com.xiyu.bid.resources.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.resources.dto.MarginDTO;
import com.xiyu.bid.resources.service.MarginExportService;
import com.xiyu.bid.resources.service.MarginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Margin ledger REST controller. */
@RestController
@RequestMapping("/api/resource/margin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class MarginController {

    /** Default page size. */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** Default page number. */
    private static final int DEFAULT_PAGE = 1;

    /** Margin data service. */
    private final MarginService marginService;

    /** Margin Excel export service. */
    private final MarginExportService marginExportService;

    /**
     * Get margin summary statistics.
     *
     * @param auth current authentication
     * @return summary map with totalPaid, totalPending, etc.
     */
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(
            final Authentication auth) {
        Long uid = userId(auth);
        String role = roleTag(auth);
        Map<String, Object> s = marginService.getSummary(uid, role);
        return ResponseEntity.ok(ApiResponse.success("Success", s));
    }

    /**
     * Get margin ledger page.
     *
     * @param auth   current authentication
     * @param params all query parameters (page, size, filters)
     * @return paginated data with total
     */
    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getList(
            final Authentication auth,
            @RequestParam final Map<String, String> params) {
        Long uid = userId(auth);
        String role = roleTag(auth);
        Map<String, String> f = extractFilters(params);
        int page = intParam(params, "page", DEFAULT_PAGE);
        int size = intParam(params, "size", DEFAULT_PAGE_SIZE);
        List<MarginDTO> list = marginService.getList(uid, role, f, page, size);
        long total = marginService.getCount(uid, role, f);
        Map<String, Object> result = Map.of(
                "data", list, "total", total, "page", page, "size", size);
        return ResponseEntity.ok(ApiResponse.success("Success", result));
    }

    /**
     * Export margin ledger as Excel file.
     *
     * @param auth   current authentication
     * @param params query parameters (same filters as list endpoint)
     * @return .xlsx file download
     */
    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> export(
            final Authentication auth,
            @RequestParam final Map<String, String> params) {
        Long uid = userId(auth);
        String role = roleTag(auth);
        Map<String, String> f = extractFilters(params);
        byte[] excel = marginExportService.exportToExcel(uid, role, f);
        String filename = URLEncoder.encode(
                "保证金台账_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx",
                StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(excel);
    }

    private Map<String, String> extractFilters(final Map<String, String> p) {
        Map<String, String> f = new HashMap<>();
        copy(p, f, "projectName");
        copy(p, f, "ownerUnit");
        copy(p, f, "projectLeaderName");
        copy(p, f, "biddingLeaderName");
        copy(p, f, "paymentDateStart");
        copy(p, f, "paymentDateEnd");
        copy(p, f, "expectedReturnDateStart");
        copy(p, f, "expectedReturnDateEnd");
        copy(p, f, "status");
        return f;
    }

    private void copy(final Map<String, String> src,
                       final Map<String, String> dst, final String key) {
        String v = src.get(key);
        if (v != null) {
            dst.put(key, v);
        }
    }

    private int intParam(final Map<String, String> p, final String k,
                          final int def) {
        try {
            return Integer.parseInt(p.getOrDefault(k, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Long userId(final Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            log.warn("Auth name not parseable as Long: {}", auth.getName(), e);
            return null;
        }
    }

    private String roleTag(final Authentication auth) {
        if (auth == null) {
            return "anonymous";
        }
        return auth.getAuthorities().stream()
                .map(Object::toString)
                .filter(s -> s.startsWith("ROLE_"))
                .map(s -> s.substring("ROLE_".length()))
                .findFirst()
                .orElse("anonymous");
    }
}
