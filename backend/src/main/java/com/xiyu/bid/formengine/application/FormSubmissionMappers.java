// Input: Map<String, Object> (raw form data from dynamic form engine)
// Output: Business DTOs (TenderDTO, ProjectDTO, ExpenseCreateRequest, QualificationDTO)
// Pos: Application 层（纯数据转换，无 I/O，无状态）
// 维护声明: 仅做类型映射和数据清洗，不做验证（验证在 FormFieldValidator / Service 层）
package com.xiyu.bid.formengine.application;

import com.xiyu.bid.contractborrow.application.command.CreateContractBorrowCommand;
import com.xiyu.bid.project.dto.ProjectDTO;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import com.xiyu.bid.resources.dto.BarCertificateCreateRequest;
import com.xiyu.bid.resources.dto.ExpenseCreateRequest;
import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.tender.dto.TenderDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 表单数据映射器：将前端动态表单 Map&lt;String, Object&gt; 映射为业务 DTO。
 * <p>
 * 本类是纯转换逻辑（无副作用），每个方法均为 static，
 * 可直接单元测试。
 */
public final class FormSubmissionMappers {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private FormSubmissionMappers() {}

    // ==================== TenderDTO ====================

    public static TenderDTO toTenderDTO(Map<String, Object> formData) {
        TenderDTO.TenderDTOBuilder b = TenderDTO.builder();

        putStr(formData, "title", b::title);
        putStr(formData, "source", b::source);
        putBd(formData, "budget", b::budget);
        putStr(formData, "region", b::region);
        putStr(formData, "industry", b::industry);
        putStr(formData, "tenderAgency", b::tenderAgency);
        putStr(formData, "purchaserName", b::purchaserName);
        putStr(formData, "contactName", b::contactName);
        putStr(formData, "contactPhone", b::contactPhone);
        putStr(formData, "contactTel", b::contactTel);
        putStr(formData, "contactMail", b::contactMail);
        putStr(formData, "customerType", b::customerType);
        putStr(formData, "priority", b::priority);
        putStr(formData, "description", b::description);
        putStr(formData, "originalUrl", b::originalUrl);

        List<String> tags = extractTags(formData.get("tags"));
        if (tags != null && !tags.isEmpty()) b.tags(tags);

        putDateTime(formData, "deadline", b::deadline);
        putDate(formData, "publishDate", b::publishDate);
        putDateTime(formData, "bidOpeningTime", b::bidOpeningTime);
        putDateTime(formData, "registrationDeadline", b::registrationDeadline);

        return b.build();
    }

    // ==================== ProjectDTO ====================

    public static ProjectDTO toProjectDTO(Map<String, Object> formData) {
        ProjectDTO.ProjectDTOBuilder b = ProjectDTO.builder();

        putStr(formData, "name", b::name);
        putStr(formData, "customer", b::customer);
        putBd(formData, "budget", b::budget);
        putStr(formData, "industry", b::industry);
        putStr(formData, "region", b::region);
        putStr(formData, "description", b::description);
        putStr(formData, "remark", b::remark);
        putStr(formData, "platform", b::platform);
        putStr(formData, "projectType", b::projectType);

        String tagsJson = buildTagsJson(formData.get("tags"));
        if (tagsJson != null) b.tagsJson(tagsJson);

        putLong(formData, "tenderId", b::tenderId);
        putLong(formData, "managerId", b::managerId);
        putLongList(formData, "teamMembers", b::teamMembers);

        putDateTime(formData, "startDate", b::startDate);
        putDateTime(formData, "endDate", b::endDate);
        putDate(formData, "deadline", b::deadline);

        return b.build();
    }

    // ==================== ExpenseCreateRequest ====================

    public static ExpenseCreateRequest toExpenseRequest(Map<String, Object> formData, String username) {
        ExpenseCreateRequest req = new ExpenseCreateRequest();
        req.setProjectId(toLong(formData.get("projectId")));
        req.setCreatedBy(username);
        putStr(formData, "expenseType", req::setExpenseType);
        putStr(formData, "description", req::setDescription);
        putDate(formData, "date", req::setDate);
        putDate(formData, "expectedReturnDate", req::setExpectedReturnDate);
        putBd(formData, "amount", req::setAmount);

        String catStr = toStr(formData.get("category"));
        if (!catStr.isBlank()) {
            try { req.setCategory(Expense.ExpenseCategory.valueOf(catStr)); }
            catch (IllegalArgumentException ignored) {}
        }
        return req;
    }

    // ==================== BarCertificateCreateRequest ====================

    public static BarCertificateCreateRequest toBarCertificateRequest(Map<String, Object> formData) {
        BarCertificateCreateRequest req = new BarCertificateCreateRequest();
        putStr(formData, "type", req::setType);
        putStr(formData, "provider", req::setProvider);
        putStr(formData, "serialNo", req::setSerialNo);
        putStr(formData, "holder", req::setHolder);
        putStr(formData, "location", req::setLocation);
        putStr(formData, "remark", req::setRemark);
        putDate(formData, "expiryDate", req::setExpiryDate);
        return req;
    }

    // ==================== CreateContractBorrowCommand ====================

    public static CreateContractBorrowCommand toContractBorrowCommand(Map<String, Object> formData) {
        return new CreateContractBorrowCommand(
                toStr(formData.get("contractNo")),
                toStr(formData.get("contractName")),
                toStr(formData.get("sourceName")),
                toStr(formData.get("borrowerName")),
                toStr(formData.get("borrowerDept")),
                toStr(formData.get("customerName")),
                toStr(formData.get("purpose")),
                toStr(formData.get("borrowType")),
                toLocalDate(formData.get("expectedReturnDate"))
        );
    }

    // ==================== QualificationDTO ====================

    public static QualificationDTO toQualificationDTO(Map<String, Object> formData) {
        QualificationDTO dto = new QualificationDTO();
        putStr(formData, "name", dto::setName);
        putDate(formData, "issueDate", dto::setIssueDate);
        putDate(formData, "expiryDate", dto::setExpiryDate);
        return dto;
    }

    // ==================== 类型转换工具 ====================

    private static void putStr(Map<String, Object> data, String key, Consumer<String> setter) {
        Object val = data.get(key);
        if (val != null) setter.accept(val.toString().trim());
    }

    public static String toStr(Object val) {
        if (val == null) return "";
        return val.toString().trim();
    }

    public static Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        String s = val.toString().trim();
        if (s.isEmpty()) return null;
        try { return Long.parseLong(s); }
        catch (NumberFormatException e) { return null; }
    }

    public static LocalDate toLocalDate(Object val) {
        if (val == null) return null;
        String s = toStr(val);
        if (s.isEmpty()) return null;
        try { return LocalDate.parse(s.substring(0, 10), DATE_FMT); }
        catch (DateTimeParseException e) { return null; }
    }

    private static void putBd(Map<String, Object> data, String key, Consumer<BigDecimal> setter) {
        Object val = data.get(key);
        if (val == null) return;
        if (val instanceof BigDecimal bd) setter.accept(bd);
        else if (val instanceof Number n) setter.accept(new BigDecimal(n.toString()));
        else {
            String s = val.toString().trim();
            if (!s.isEmpty()) setter.accept(new BigDecimal(s));
        }
    }

    private static void putLong(Map<String, Object> data, String key, Consumer<Long> setter) {
        Object val = data.get(key);
        if (val == null) return;
        try {
            if (val instanceof Number n) setter.accept(n.longValue());
            else {
                String s = val.toString().trim();
                if (!s.isEmpty()) setter.accept(Long.parseLong(s));
            }
        } catch (NumberFormatException ignored) {}
    }

    private static void putDate(Map<String, Object> data, String key, Consumer<LocalDate> setter) {
        Object val = data.get(key);
        if (val == null) return;
        String s = val.toString().trim();
        if (s.isEmpty()) return;
        try { setter.accept(LocalDate.parse(s.substring(0, 10), DATE_FMT)); }
        catch (DateTimeParseException ignored) {}
    }

    private static void putDateTime(Map<String, Object> data, String key, Consumer<LocalDateTime> setter) {
        Object val = data.get(key);
        if (val == null) return;
        String s = val.toString().trim();
        if (s.isEmpty()) return;
        try { setter.accept(LocalDateTime.parse(s, DATETIME_FMT)); }
        catch (DateTimeParseException ignored) {
            try { setter.accept(LocalDate.parse(s.substring(0, 10), DATE_FMT).atStartOfDay()); }
            catch (DateTimeParseException ignored2) {}
        }
    }

    private static void putLongList(Map<String, Object> data, String key, Consumer<List<Long>> setter) {
        Object val = data.get(key);
        if (val == null) return;
        if (val instanceof List<?> list) {
            List<Long> ids = list.stream()
                    .filter(Number.class::isInstance)
                    .map(o -> ((Number) o).longValue())
                    .toList();
            setter.accept(ids);
        }
    }

    private static List<String> extractTags(Object tagsValue) {
        if (tagsValue == null) return null;
        if (tagsValue instanceof List<?> list) {
            List<String> tags = list.stream()
                    .filter(o -> o != null && !o.toString().isBlank())
                    .map(o -> o.toString().trim())
                    .toList();
            return tags.isEmpty() ? null : tags;
        }
        if (tagsValue instanceof String s && !s.isBlank()) {
            List<String> tags = List.of(s.split(",")).stream()
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .toList();
            return tags.isEmpty() ? null : tags;
        }
        return null;
    }

    private static String buildTagsJson(Object tagsValue) {
        if (tagsValue == null) return null;
        if (tagsValue instanceof List<?> list) {
            String json = list.stream()
                    .filter(o -> o != null && !o.toString().isBlank())
                    .map(o -> "\"" + o.toString().trim().replace("\"", "\\\"") + "\"")
                    .reduce((a, b) -> a + "," + b)
                    .orElse(null);
            return json != null ? "[" + json + "]" : null;
        }
        if (tagsValue instanceof String s && !s.isBlank()) {
            String json = List.of(s.split(",")).stream()
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .map(t -> "\"" + t.replace("\"", "\\\"") + "\"")
                    .reduce((a, b) -> a + "," + b)
                    .orElse(null);
            return json != null ? "[" + json + "]" : null;
        }
        return null;
    }
}
