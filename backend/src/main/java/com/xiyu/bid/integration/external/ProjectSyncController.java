package com.xiyu.bid.integration.external;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * CRM 外部 API — 项目同步接口。
 * 认证方式：X-API-Key Header（由 ApiKeyAuthenticationFilter 处理）。
 * scope 要求: project:read
 */
@RestController
@Tag(name = "项目同步（外部API）", description = "CRM 第三方系统对接接口，通过 X-API-Key 认证")
@RequestMapping("/api/external/projects")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('SCOPE_PROJECT_READ')")
public class ProjectSyncController {

    private final ProjectService projectService;

    /**
     * 增量拉取项目列表。支持按更新时间过滤 + 按标讯关联查询。
     * updatedSince 走 SQL 层过滤，避免全量加载。
     */
    @GetMapping
    @Operation(summary = "增量拉取项目列表", description = "支持按 updatedSince 增量同步 + tenderId 关联 + 分页")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listProjects(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedSince,
            @RequestParam(required = false) Long tenderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("EXTERNAL GET /api/external/projects - updatedSince={} tenderId={} page={} size={}",
                updatedSince, tenderId, page, size);
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);

        // SQL 层过滤：updatedSince 和 tenderId 二选一（tenderId 优先）
        List<ProjectDTO> all;
        if (tenderId != null) {
            all = projectService.getProjectsByTender(tenderId);
            // tenderId 查询暂不支持 updatedSince 联合过滤，在内存中处理
            if (updatedSince != null) {
                all = all.stream()
                        .filter(p -> p.getUpdatedAt() != null && !p.getUpdatedAt().isBefore(updatedSince))
                        .toList();
            }
        } else if (updatedSince != null) {
            all = projectService.getProjectsByUpdatedSince(updatedSince);
        } else {
            all = projectService.getAllProjects();
        }

        // 内存分页
        long totalCount = all.size();
        int fromIndex = Math.min(safePage * safeSize, all.size());
        int toIndex = Math.min(fromIndex + safeSize, all.size());
        List<ProjectDTO> pageList = all.subList(fromIndex, toIndex);
        boolean hasMore = toIndex < totalCount;

        Map<String, Object> data = Map.of(
                "content", (Object) pageList,
                "totalCount", totalCount,
                "page", safePage,
                "size", safeSize,
                "hasMore", hasMore
        );
        return ResponseEntity.ok(ApiResponse.success("Projects retrieved", data));
    }

    /**
     * 获取单个项目详情。
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectDTO>> getProject(@PathVariable Long id) {
        log.info("EXTERNAL GET /api/external/projects/{}", id);
        ProjectDTO project = projectService.getProjectById(id);
        return ResponseEntity.ok(ApiResponse.success("Project retrieved", project));
    }
}
