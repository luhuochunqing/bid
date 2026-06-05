// Output: Excel export file data and filename for project list downloads
// Pos: Service/导出专用

package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectExportService {

    private final ProjectRepository projectRepository;
    private final ProjectInitiationDetailsRepository initiationDetailsRepository;

    private static final int MAX_EXPORT_ROWS = 5000;

    public ExportResult exportProjectsAsExcel(String status, String name, String ownerUnit, String projectType, String customerType, String priority, String sourceModule, String bidStatus, String stage, String projectLeaderName, String biddingLeaderName, String leaderDepartment, String region, String biddingPlatform, String bidMonth) {
        List<Project> all = projectRepository.findAll();
        if (status != null && !status.isBlank()) {
            all = all.stream().filter(p -> status.equalsIgnoreCase(p.getStage())).collect(Collectors.toList());
        }
        if (all.size() > MAX_EXPORT_ROWS) {
            all = all.subList(0, MAX_EXPORT_ROWS);
        }
        Map<Long, ProjectInitiationDetails> detailsMap = initiationDetailsRepository.findAll().stream()
                .filter(d -> d.getProjectId() != null)
                .collect(Collectors.toMap(ProjectInitiationDetails::getProjectId, d -> d, (a, b) -> a));
        var wb = new XSSFWorkbook();
        var sheet = wb.createSheet("投标项目列表");
        String[] cols = {"项目名称", "业主单位", "入围家数", "创建时间", "开标时间", "投标月份", "项目类型", "客户类型", "客户等级", "投标状态", "项目负责人", "负责人部门", "投标负责人", "中标状态", "投标平台"};
        var header = sheet.createRow(0);
        for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
        var df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        int r = 1;
        for (Project p : all) {
            var det = detailsMap.get(p.getId());
            if (name != null && !name.isBlank() && !containsIgnoreCase(p.getName(), name)) continue;
            if (ownerUnit != null && !ownerUnit.isBlank() && !containsIgnoreCase(det != null ? det.getOwnerUnit() : null, ownerUnit)) continue;
            if (projectType != null && !projectType.isBlank() && !projectType.equals(det != null ? det.getProjectType() : null)) continue;
            if (customerType != null && !customerType.isBlank() && !customerType.equals(det != null ? det.getCustomerType() : null)) continue;
            if (priority != null && !priority.isBlank()) {
                // priority 字段已从 Project 实体移除，跳过此过滤条件
            }
            if (sourceModule != null && !sourceModule.isBlank() && !sourceModule.equalsIgnoreCase(p.getSourceModule())) continue;
            if (bidStatus != null && !bidStatus.isBlank() && !bidStatus.equalsIgnoreCase(det != null ? det.getBidStatus() : null)) continue;
            if (stage != null && !stage.isBlank() && !stage.equalsIgnoreCase(p.getStage())) continue;
            if (projectLeaderName != null && !projectLeaderName.isBlank() && !containsIgnoreCase(det != null ? det.getProjectLeaderName() : null, projectLeaderName)) continue;
            if (biddingLeaderName != null && !biddingLeaderName.isBlank() && !containsIgnoreCase(det != null ? det.getBiddingLeaderName() : null, biddingLeaderName)) continue;
            if (leaderDepartment != null && !leaderDepartment.isBlank() && !leaderDepartment.equals(det != null ? det.getLeaderDepartment() : null)) continue;
            if (region != null && !region.isBlank() && !containsIgnoreCase(p.getRegion(), region)) continue;
            if (biddingPlatform != null && !biddingPlatform.isBlank() && !containsIgnoreCase(det != null ? det.getBiddingPlatform() : null, biddingPlatform)) continue;
            if (bidMonth != null && !bidMonth.isBlank() && !bidMonth.equals(det != null ? det.getBidMonth() : null)) continue;
            var row = sheet.createRow(r++);
            row.createCell(0).setCellValue(coalesce(p.getName()));
            row.createCell(1).setCellValue(det != null ? coalesce(det.getOwnerUnit()) : "");
            row.createCell(2).setCellValue(det != null && det.getExpectedBidders() != null ? det.getExpectedBidders().doubleValue() : 0);
            row.createCell(3).setCellValue(p.getCreatedAt() != null ? p.getCreatedAt().format(df) : "");
            row.createCell(4).setCellValue(det != null && det.getBidOpenTime() != null ? det.getBidOpenTime().format(df) : "");
            row.createCell(5).setCellValue(det != null ? coalesce(det.getBidMonth()) : "");
            row.createCell(6).setCellValue(det != null ? coalesce(det.getProjectType()) : "");
            row.createCell(7).setCellValue(det != null ? coalesce(det.getCustomerType()) : "");
            row.createCell(8).setCellValue(det != null ? coalesce(det.getCustomerGrade()) : "");
            row.createCell(9).setCellValue(coalesce(det != null ? det.getBidStatus() : null));
            row.createCell(10).setCellValue(det != null ? coalesce(det.getProjectLeaderName()) : "");
            row.createCell(11).setCellValue(det != null ? coalesce(det.getLeaderDepartment()) : "");
            row.createCell(12).setCellValue(det != null ? coalesce(det.getBiddingLeaderName()) : "");
            row.createCell(13).setCellValue(det != null ? coalesce(det.getBidResultStatus()) : "");
            row.createCell(14).setCellValue(det != null ? coalesce(det.getBiddingPlatform()) : "");
        }
        for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
        try (var out = new ByteArrayOutputStream()) {
            wb.write(out);
            wb.close();
            var now = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            return new ExportResult(new ByteArrayInputStream(out.toByteArray()), now + ".xlsx");
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to generate export Excel", e);
        }
    }

    public record ExportResult(java.io.InputStream data, String filename) {}

    private static String coalesce(String v) { return v != null ? v : ""; }
    private static boolean containsIgnoreCase(String source, String needle) { return source != null && needle != null && source.toLowerCase().contains(needle.toLowerCase()); }
}
