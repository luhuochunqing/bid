// Input: HTTP 请求、路径参数、认证上下文和 DTO
// Output: 标准化 API 响应和用例入口
// Pos: Controller/接口适配层
// 维护声明: 仅维护协议适配与参数校验；业务规则下沉到 service.
package com.xiyu.bid.project.controller;

import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.project.dto.ProjectRequest;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.project.dto.ProjectImportRequest;
import com.xiyu.bid.project.service.ProjectExportService;
import com.xiyu.bid.project.service.ProjectService;
import com.xiyu.bid.util.InputSanitizer;
import com.xiyu.bid.annotation.DataScope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectExportService projectExportService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @DataScope(deptAlias = "department_id", userAlias = "manager_id")
    public ResponseEntity<ApiResponse<List<ProjectDTO>>> getAllProjects(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String ownerUnit,
            @RequestParam(required = false) String projectType,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String sourceModule,
            @RequestParam(required = false) String bidStatus,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String projectLeaderName,
            @RequestParam(required = false) String biddingLeaderName,
            @RequestParam(required = false) String leaderDepartment,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String biddingPlatform,
            @RequestParam(required = false) String bidMonth,
            @RequestParam(required = false) @Min(value = 0, message = "page 必须 >= 0") @Max(value = 100000, message = "page 超出上限") Integer page,
            @RequestParam(required = false) @Min(value = 1, message = "size 必须 >= 1") @Max(value = 200, message = "size 必须 <= 200") Integer size) {
        log.info("GET /api/projects - Fetching all projects (page={}, size={})", page, size);
        List<ProjectDTO> projects = projectService.getAllProjects();
        if (name != null && !name.isBlank()) projects = projects.stream().filter(p -> containsIgnoreCase(p.getName(), name) || containsIgnoreCase(p.getCustomer(), name)).toList();
        if (ownerUnit != null && !ownerUnit.isBlank()) projects = projects.stream().filter(p -> containsIgnoreCase(p.getOwnerUnit(), ownerUnit)).toList();
        if (projectType != null && !projectType.isBlank()) projects = projects.stream().filter(p -> projectType.equals(p.getProjectType())).toList();
        if (customerType != null && !customerType.isBlank()) projects = projects.stream().filter(p -> customerType.equals(p.getCustomerType())).toList();
        if (priority != null && !priority.isBlank()) projects = projects.stream().filter(p -> priority.equals(p.getPriority())).toList();
        if (sourceModule != null && !sourceModule.isBlank()) projects = projects.stream().filter(p -> sourceModule.equals(p.getSourceModule())).toList();
        if (bidStatus != null && !bidStatus.isBlank()) projects = projects.stream().filter(p -> bidStatus.equals(p.getBidStatus())).toList();
        if (stage != null && !stage.isBlank()) projects = projects.stream().filter(p -> stage.equals(p.getStage())).toList();
        if (projectLeaderName != null && !projectLeaderName.isBlank()) projects = projects.stream().filter(p -> containsIgnoreCase(p.getProjectLeaderName(), projectLeaderName)).toList();
        if (biddingLeaderName != null && !biddingLeaderName.isBlank()) projects = projects.stream().filter(p -> containsIgnoreCase(p.getBiddingLeaderName(), biddingLeaderName)).toList();
        if (leaderDepartment != null && !leaderDepartment.isBlank()) projects = projects.stream().filter(p -> leaderDepartment.equals(p.getLeaderDepartment())).toList();
        if (region != null && !region.isBlank()) projects = projects.stream().filter(p -> containsIgnoreCase(p.getRegion(), region)).toList();
        if (biddingPlatform != null && !biddingPlatform.isBlank()) projects = projects.stream().filter(p -> containsIgnoreCase(p.getBiddingPlatform(), biddingPlatform)).toList();
        if (bidMonth != null && !bidMonth.isBlank()) projects = projects.stream().filter(p -> bidMonth.equals(p.getBidMonth())).toList();
        if (size != null) {
            long fromLong = (page == null ? 0L : (long) page) * (long) size;
            int fromIndex = (int) Math.min(fromLong, (long) projects.size());
            int toIndex = Math.min(fromIndex + size, projects.size());
            projects = projects.subList(fromIndex, toIndex);
        }
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved projects", projects));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectDTO>> getProjectById(@PathVariable Long id) {
        log.info("GET /api/projects/{} - Fetching project", id);
        ProjectDTO project = projectService.getProjectById(id);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved project", project));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ProjectDTO>> createProject(@Valid @RequestBody ProjectRequest projectRequest) {
        log.info("POST /api/projects - Creating new project: {}", projectRequest.getName());
        sanitizeProjectRequest(projectRequest);
        ProjectDTO projectDTO = convertRequestToDTO(projectRequest);
        ProjectDTO createdProject = projectService.createProject(projectDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Project created successfully", createdProject));
    }

    /**
     * 历史档案批量导入接口。
     * 允许管理员指定立项/评标/结项时间戳，用于导入历史项目档案。
     * POST /api/projects/import
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'BIDADMIN')")
    public ResponseEntity<ApiResponse<ProjectDTO>> importProject(@Valid @RequestBody ProjectImportRequest request) {
        log.info("POST /api/projects/import - Importing historical project: {}", request.getName());
        ProjectDTO imported = projectService.importProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Project imported successfully", imported));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ProjectDTO>> updateProject(@PathVariable Long id, @Valid @RequestBody ProjectRequest projectRequest) {
        log.info("PUT /api/projects/{} - Updating project", id);
        sanitizeProjectRequest(projectRequest);
        ProjectDTO projectDTO = convertRequestToDTO(projectRequest);
        ProjectDTO updatedProject = projectService.updateProject(id, projectDTO);
        return ResponseEntity.ok(ApiResponse.success("Project updated successfully", updatedProject));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable Long id) {
        log.info("DELETE /api/projects/{} - Deleting project", id);
        projectService.deleteProject(id);
        return ResponseEntity.ok(ApiResponse.success("Project deleted successfully", null));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ProjectDTO>> updateProjectStatus(@PathVariable Long id, @RequestParam com.xiyu.bid.entity.Project.Status status) {
        log.info("PUT /api/projects/{}/status - Updating status to {}", id, status);
        ProjectDTO updatedProject = projectService.updateProjectStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Project status updated successfully", updatedProject));
    }

    @PutMapping("/{id}/team")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ProjectDTO>> updateProjectTeam(@PathVariable Long id, @RequestBody List<Long> teamMembers) {
        log.info("PUT /api/projects/{}/team - Updating team members", id);
        ProjectDTO updatedProject = projectService.updateProjectTeam(id, teamMembers);
        return ResponseEntity.ok(ApiResponse.success("Project team updated successfully", updatedProject));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ProjectDTO>>> getProjectsByStatus(@PathVariable com.xiyu.bid.entity.Project.Status status) {
        log.info("GET /api/projects/status/{} - Fetching projects by status", status);
        List<ProjectDTO> projects = projectService.getProjectsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved projects", projects));
    }

    @GetMapping("/manager/{managerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ProjectDTO>>> getProjectsByManager(@PathVariable Long managerId) {
        log.info("GET /api/projects/manager/{} - Fetching projects by manager", managerId);
        List<ProjectDTO> projects = projectService.getProjectsByManager(managerId);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved projects", projects));
    }

    @GetMapping("/tender/{tenderId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ProjectDTO>>> getProjectsByTender(@PathVariable Long tenderId) {
        log.info("GET /api/projects/tender/{} - Fetching projects by tender", tenderId);
        List<ProjectDTO> projects = projectService.getProjectsByTender(tenderId);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved projects", projects));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ProjectDTO>>> getActiveProjects() {
        log.info("GET /api/projects/active - Fetching active projects");
        List<ProjectDTO> projects = projectService.getActiveProjects();
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved active projects", projects));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ProjectDTO>>> searchProjects(@RequestParam String name) {
        log.info("GET /api/projects/search?name={} - Searching projects", name);
        String sanitizedName = InputSanitizer.sanitizeString(name, 100);
        List<ProjectDTO> projects = projectService.searchProjectsByName(sanitizedName);
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved projects", projects));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<com.xiyu.bid.entity.Project.Status, Long>>> getStatistics() {
        log.info("GET /api/projects/statistics - Fetching project statistics");
        Map<com.xiyu.bid.entity.Project.Status, Long> statistics = projectService.getProjectStatistics();
        return ResponseEntity.ok(ApiResponse.success("Successfully retrieved statistics", statistics));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<InputStreamResource> exportProjects(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String ownerUnit,
            @RequestParam(required = false) String projectType,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String sourceModule,
            @RequestParam(required = false) String bidStatus,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String projectLeaderName,
            @RequestParam(required = false) String biddingLeaderName,
            @RequestParam(required = false) String leaderDepartment,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String biddingPlatform,
            @RequestParam(required = false) String bidMonth) throws Exception {
        log.info("GET /api/projects/export - Exporting projects (status={})", status);
        var result = projectExportService.exportProjectsAsExcel(status, name, ownerUnit, projectType, customerType, priority, sourceModule, bidStatus, stage, projectLeaderName, biddingLeaderName, leaderDepartment, region, biddingPlatform, bidMonth);
        var headers = new HttpHeaders();
        headers.setContentDispositionFormData("attachment", "投标项目列表_" + result.filename());
        return ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_OCTET_STREAM).body(new InputStreamResource(result.data()));
    }

    private ProjectDTO convertRequestToDTO(ProjectRequest request) {
        return ProjectDTO.builder()
                .name(request.getName())
                .tenderId(request.getTenderId())
                .status(request.getStatus())
                .managerId(request.getManagerId())
                .teamMembers(request.getTeamMembers())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .sourceModule(request.getSourceModule())
                .sourceCustomerId(request.getSourceCustomerId())
                .sourceCustomer(request.getSourceCustomer())
                .sourceOpportunityId(request.getSourceOpportunityId())
                .sourceReasoningSummary(request.getSourceReasoningSummary())
                .competitorAnalysisJson(request.getCompetitorAnalysisJson())
                .tasksJson(request.getTasksJson())
                .aiAnalysisJson(request.getAiAnalysisJson())
                .customer(request.getCustomer())
                .budget(request.getBudget())
                .industry(request.getIndustry())
                .customerType(request.getCustomerType())
                .region(request.getRegion())
                .platform(request.getPlatform())
                .deadline(request.getDeadline())
                .description(request.getDescription())
                .remark(request.getRemark())
                .tagsJson(request.getTagsJson())
                .customerManager(request.getCustomerManager())
                .customerManagerId(request.getCustomerManagerId())
                .build();
    }

    private void sanitizeProjectRequest(ProjectRequest request) {
        if (request.getName() != null) request.setName(InputSanitizer.sanitizeString(request.getName(), 200));
        if (request.getCustomer() != null) request.setCustomer(InputSanitizer.sanitizeString(request.getCustomer(), 255));
        if (request.getIndustry() != null) request.setIndustry(InputSanitizer.sanitizeString(request.getIndustry(), 50));
        if (request.getCustomerType() != null) request.setCustomerType(InputSanitizer.sanitizeString(request.getCustomerType(), 100));
        if (request.getRegion() != null) request.setRegion(InputSanitizer.sanitizeString(request.getRegion(), 100));
        if (request.getPlatform() != null) request.setPlatform(InputSanitizer.sanitizeString(request.getPlatform(), 255));
        if (request.getDescription() != null) request.setDescription(InputSanitizer.sanitizeString(request.getDescription(), 5000));
        if (request.getRemark() != null) request.setRemark(InputSanitizer.sanitizeString(request.getRemark(), 5000));
    }

    private boolean containsIgnoreCase(String source, String needle) {
        return source != null && needle != null && source.toLowerCase().contains(needle.toLowerCase());
    }
}
