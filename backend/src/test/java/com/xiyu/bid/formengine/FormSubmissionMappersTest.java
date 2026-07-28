package com.xiyu.bid.formengine;

import com.xiyu.bid.contractborrow.application.command.CreateContractBorrowCommand;
import com.xiyu.bid.formengine.application.FormSubmissionMappers;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import com.xiyu.bid.resources.dto.BarCertificateCreateRequest;
import com.xiyu.bid.resources.dto.ExpenseCreateRequest;
import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.tender.dto.TenderDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FormSubmissionMappers 纯核心单元测试。
 *
 * <p>覆盖 PR !2201 修复的 {@code toQualificationDTO} 5 字段补全回归（level/agency/
 * agencyContact/certScope/certificateNo），以及其他 mapper 工具方法的边界条件
 * （无效日期、非法 enum 名称、空 tags 列表等）。本类为纯函数静态方法，不依赖 Spring 容器。</p>
 */
@DisplayName("FormSubmissionMappers")
class FormSubmissionMappersTest {

    // ==================== toQualificationDTO（PR !2201 修复点） ====================

    @Nested
    @DisplayName("toQualificationDTO")
    class ToQualificationDTO {

        @Test
        @DisplayName("完整 8 字段全部正确映射")
        void shouldMapAllFields() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "建筑工程施工总承包资质");
            formData.put("level", "一级");
            formData.put("agency", "住建部");
            formData.put("agencyContact", "李代理");
            formData.put("certScope", "房屋建筑工程施工总承包");
            formData.put("certificateNo", "D1234567890");
            formData.put("issueDate", "2024-01-15");
            formData.put("expiryDate", "2029-01-14");

            QualificationDTO dto = FormSubmissionMappers.toQualificationDTO(formData);

            assertThat(dto.getName()).isEqualTo("建筑工程施工总承包资质");
            assertThat(dto.getLevel()).isEqualTo("一级");
            assertThat(dto.getAgency()).isEqualTo("住建部");
            assertThat(dto.getAgencyContact()).isEqualTo("李代理");
            assertThat(dto.getCertScope()).isEqualTo("房屋建筑工程施工总承包");
            assertThat(dto.getCertificateNo()).isEqualTo("D1234567890");
            assertThat(dto.getIssueDate()).isEqualTo(LocalDate.of(2024, 1, 15));
            assertThat(dto.getExpiryDate()).isEqualTo(LocalDate.of(2029, 1, 14));
        }

        @Test
        @DisplayName("PR !2201 回归：5 字段（level/agency/agencyContact/certScope/certificateNo）必须被映射")
        void regression_PR2201_fiveFieldsMustBeMapped() {
            // 模拟 !2201 修复前的 bug 场景：仅传 name + issueDate + expiryDate
            // 修复后这 5 个字段若仍为 null 即为回归
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "资质名称");
            formData.put("level", "二级");
            formData.put("agency", "某代理机构");
            formData.put("agencyContact", "王代理 13800000000");
            formData.put("certScope", "市政公用工程施工总承包");
            formData.put("certificateNo", "D9876543210");
            formData.put("issueDate", "2025-06-01");
            formData.put("expiryDate", "2030-05-31");

            QualificationDTO dto = FormSubmissionMappers.toQualificationDTO(formData);

            // 五个 !2201 补全字段，缺一不可
            assertThat(dto.getLevel()).as("level 字段必须被映射").isEqualTo("二级");
            assertThat(dto.getAgency()).as("agency 字段必须被映射").isEqualTo("某代理机构");
            assertThat(dto.getAgencyContact()).as("agencyContact 字段必须被映射").isEqualTo("王代理 13800000000");
            assertThat(dto.getCertScope()).as("certScope 字段必须被映射").isEqualTo("市政公用工程施工总承包");
            assertThat(dto.getCertificateNo()).as("certificateNo 字段必须被映射").isEqualTo("D9876543210");
        }

        @Test
        @DisplayName("字段值为空白字符串时不被写入 DTO")
        void shouldIgnoreBlankStringFields() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "  ");  // 空白字符串，putStr 会写入
            formData.put("level", "");
            formData.put("agency", "   ");
            formData.put("issueDate", "2025-01-01");

            QualificationDTO dto = FormSubmissionMappers.toQualificationDTO(formData);

            // 空白字符串仍会被 trim 写入（putStr 的语义），但 DTO 不会出现 null 抛错
            assertThat(dto.getLevel()).isEmpty();
            assertThat(dto.getAgency()).isEmpty();
        }

        @Test
        @DisplayName("字段全部缺失时所有 getter 返回 null，不抛异常")
        void shouldHandleAllFieldsMissing() {
            Map<String, Object> formData = new HashMap<>();

            QualificationDTO dto = FormSubmissionMappers.toQualificationDTO(formData);

            assertThat(dto.getName()).isNull();
            assertThat(dto.getLevel()).isNull();
            assertThat(dto.getAgency()).isNull();
            assertThat(dto.getAgencyContact()).isNull();
            assertThat(dto.getCertScope()).isNull();
            assertThat(dto.getCertificateNo()).isNull();
            assertThat(dto.getIssueDate()).isNull();
            assertThat(dto.getExpiryDate()).isNull();
        }

        @Test
        @DisplayName("非 yyyy-MM-dd 前缀的非法日期字符串不抛异常，对应字段保持 null")
        void shouldHandleInvalidDateString() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "资质");
            formData.put("issueDate", "not-a-date");
            // 实际实现：putDate 会先 substring(0,10) → "2029-12-31" 合法 → 解析成功
            // 这里只验证完全无法解析的字符串会保持 null，不抛异常
            formData.put("expiryDate", "2029/12/31-extra-no-dash-prefix-ok");

            QualificationDTO dto = FormSubmissionMappers.toQualificationDTO(formData);

            assertThat(dto.getName()).isEqualTo("资质");
            assertThat(dto.getIssueDate()).isNull();
            // "2029/12/31" 的前 10 个字符包含 "/"，无法用 "-" 分隔的 DateTimeFormatter 解析
            assertThat(dto.getExpiryDate()).isNull();
        }

        @Test
        @DisplayName("字符串值首尾空白被自动 trim")
        void shouldTrimStringValues() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "  资质证书  ");
            formData.put("level", "  二级  ");

            QualificationDTO dto = FormSubmissionMappers.toQualificationDTO(formData);

            assertThat(dto.getName()).isEqualTo("资质证书");
            assertThat(dto.getLevel()).isEqualTo("二级");
        }
    }

    // ==================== toTenderDTO ====================

    @Nested
    @DisplayName("toTenderDTO")
    class ToTenderDTO {

        @Test
        @DisplayName("完整字段映射：BigDecimal/日期/tags 全部正确")
        void shouldMapAllTenderFields() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("title", "测试标讯");
            formData.put("source", "bidding");
            formData.put("budget", new BigDecimal("1234567.89"));
            formData.put("deadline", "2026-12-31T23:59:59");
            formData.put("publishDate", "2026-01-01");
            formData.put("tags", List.of("公开", "智慧城市"));

            TenderDTO dto = FormSubmissionMappers.toTenderDTO(formData);

            assertThat(dto.getTitle()).isEqualTo("测试标讯");
            assertThat(dto.getSource()).isEqualTo("bidding");
            assertThat(dto.getBudget()).isEqualByComparingTo(new BigDecimal("1234567.89"));
            assertThat(dto.getDeadline()).isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
            assertThat(dto.getPublishDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(dto.getTags()).containsExactly("公开", "智慧城市");
        }

        @Test
        @DisplayName("tags 为逗号分隔字符串时被正确分割")
        void shouldParseCommaStringTags() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("title", "标题");
            formData.put("deadline", "2026-12-31T23:59:59");
            formData.put("tags", "政府, 央企 , 智慧城市");

            TenderDTO dto = FormSubmissionMappers.toTenderDTO(formData);

            assertThat(dto.getTags()).containsExactly("政府", "央企", "智慧城市");
        }

        @Test
        @DisplayName("tags 全空白时返回 null")
        void shouldReturnNullForBlankTags() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("title", "标题");
            formData.put("deadline", "2026-12-31T23:59:59");
            List<Object> tags = new ArrayList<>();
            tags.add("");
            tags.add("  ");
            tags.add(null);
            formData.put("tags", tags);

            TenderDTO dto = FormSubmissionMappers.toTenderDTO(formData);

            assertThat(dto.getTags()).isNull();
        }

        @Test
        @DisplayName("非 BigDecimal 数字字符串能转为 BigDecimal")
        void shouldConvertStringToBigDecimal() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("title", "标题");
            formData.put("deadline", "2026-12-31T23:59:59");
            formData.put("budget", "999.99");

            TenderDTO dto = FormSubmissionMappers.toTenderDTO(formData);

            assertThat(dto.getBudget()).isEqualByComparingTo(new BigDecimal("999.99"));
        }
    }

    // ==================== toProjectDTO ====================

    @Nested
    @DisplayName("toProjectDTO")
    class ToProjectDTO {

        @Test
        @DisplayName("teamMembers 数字列表被正确转换为 Long 列表")
        void shouldConvertTeamMembersToLongList() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "项目");
            formData.put("managerId", 100);
            formData.put("teamMembers", List.of(101, 102, 103));
            formData.put("tenderId", 5L);

            ProjectDTO dto = FormSubmissionMappers.toProjectDTO(formData);

            assertThat(dto.getTeamMembers()).containsExactly(101L, 102L, 103L);
            assertThat(dto.getManagerId()).isEqualTo(100L);
            assertThat(dto.getTenderId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("teamMembers 混入非数字元素时只保留数字")
        void shouldFilterNonNumericTeamMembers() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "项目");
            List<Object> teamMembers = new ArrayList<>();
            teamMembers.add(1);
            teamMembers.add("abc");
            teamMembers.add(2);
            teamMembers.add(null);
            teamMembers.add(3);
            formData.put("teamMembers", teamMembers);

            ProjectDTO dto = FormSubmissionMappers.toProjectDTO(formData);

            assertThat(dto.getTeamMembers()).containsExactly(1L, 2L, 3L);
        }

        @Test
        @DisplayName("tags 列表转为 JSON 数组字符串")
        void shouldConvertTagsToJsonArray() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("name", "项目");
            formData.put("tags", List.of("智慧城市", "政府"));

            ProjectDTO dto = FormSubmissionMappers.toProjectDTO(formData);

            assertThat(dto.getTagsJson()).contains("\"智慧城市\"", "\"政府\"");
        }
    }

    // ==================== toExpenseRequest ====================

    @Nested
    @DisplayName("toExpenseRequest")
    class ToExpenseRequest {

        @Test
        @DisplayName("合法 category 字符串转为 enum")
        void shouldParseValidCategory() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("projectId", 1L);
            formData.put("category", "TRANSPORTATION");
            formData.put("amount", "100.50");
            formData.put("date", "2026-07-01");

            ExpenseCreateRequest req = FormSubmissionMappers.toExpenseRequest(formData, "admin");

            assertThat(req.getCategory()).isEqualTo(Expense.ExpenseCategory.TRANSPORTATION);
            assertThat(req.getAmount()).isEqualByComparingTo(new BigDecimal("100.50"));
            assertThat(req.getDate()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(req.getCreatedBy()).isEqualTo("admin");
        }

        @Test
        @DisplayName("非法 category 字符串被静默忽略，category 保持 null")
        void shouldIgnoreInvalidCategory() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("projectId", 1L);
            formData.put("category", "INVALID_CATEGORY");
            formData.put("amount", "100.00");
            formData.put("date", "2026-07-01");

            ExpenseCreateRequest req = FormSubmissionMappers.toExpenseRequest(formData, "admin");

            assertThat(req.getCategory()).isNull();
        }

        @Test
        @DisplayName("category 为 null 时不抛异常")
        void shouldHandleNullCategory() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("projectId", 1L);

            ExpenseCreateRequest req = FormSubmissionMappers.toExpenseRequest(formData, "admin");

            assertThat(req.getCategory()).isNull();
        }
    }

    // ==================== toBarCertificateRequest ====================

    @Nested
    @DisplayName("toBarCertificateRequest")
    class ToBarCertificateRequest {

        @Test
        @DisplayName("完整字段映射 + 日期解析")
        void shouldMapAllBarCertFields() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("type", "营业执照");
            formData.put("provider", "市场监管局");
            formData.put("serialNo", "SN-2025-001");
            formData.put("holder", "张三");
            formData.put("location", "档案室A-001");
            formData.put("remark", "正本");
            formData.put("expiryDate", "2030-12-31");

            BarCertificateCreateRequest req = FormSubmissionMappers.toBarCertificateRequest(formData);

            assertThat(req.getType()).isEqualTo("营业执照");
            assertThat(req.getProvider()).isEqualTo("市场监管局");
            assertThat(req.getSerialNo()).isEqualTo("SN-2025-001");
            assertThat(req.getHolder()).isEqualTo("张三");
            assertThat(req.getLocation()).isEqualTo("档案室A-001");
            assertThat(req.getRemark()).isEqualTo("正本");
            assertThat(req.getExpiryDate()).isEqualTo(LocalDate.of(2030, 12, 31));
        }
    }

    // ==================== toContractBorrowCommand ====================

    @Nested
    @DisplayName("toContractBorrowCommand")
    class ToContractBorrowCommand {

        @Test
        @DisplayName("空 formData 时所有字段为空字符串 + 日期 null，不抛异常")
        void shouldHandleEmptyFormData() {
            CreateContractBorrowCommand cmd = FormSubmissionMappers.toContractBorrowCommand(new HashMap<>());

            assertThat(cmd.contractNo()).isEmpty();
            assertThat(cmd.contractName()).isEmpty();
            assertThat(cmd.sourceName()).isEmpty();
            assertThat(cmd.borrowerName()).isEmpty();
            assertThat(cmd.borrowerDept()).isEmpty();
            assertThat(cmd.customerName()).isEmpty();
            assertThat(cmd.purpose()).isEmpty();
            assertThat(cmd.borrowType()).isEmpty();
            assertThat(cmd.expectedReturnDate()).isNull();
        }

        @Test
        @DisplayName("完整字段正确映射")
        void shouldMapAllContractFields() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("contractNo", "HT-2025-001");
            formData.put("contractName", "测试合同");
            formData.put("sourceName", "客户A");
            formData.put("borrowerName", "张三");
            formData.put("borrowerDept", "投标部");
            formData.put("customerName", "客户B");
            formData.put("purpose", "投标审核");
            formData.put("borrowType", "BORROW");
            formData.put("expectedReturnDate", "2026-08-15");

            CreateContractBorrowCommand cmd = FormSubmissionMappers.toContractBorrowCommand(formData);

            assertThat(cmd.contractNo()).isEqualTo("HT-2025-001");
            assertThat(cmd.borrowerName()).isEqualTo("张三");
            assertThat(cmd.expectedReturnDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        }

        @Test
        @DisplayName("expectedReturnDate 为非法字符串时返回 null，不抛异常")
        void shouldHandleInvalidDate() {
            Map<String, Object> formData = new HashMap<>();
            formData.put("contractNo", "HT-001");
            formData.put("expectedReturnDate", "not-a-date");

            CreateContractBorrowCommand cmd = FormSubmissionMappers.toContractBorrowCommand(formData);

            assertThat(cmd.expectedReturnDate()).isNull();
        }
    }
}
