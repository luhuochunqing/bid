package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceDTO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChancePageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 标讯→商机匹配的纯函数集合（spec 037 从 {@link CrmChanceService} 拆出）。
 * <p>职责：
 * <ul>
 *   <li>解析报名截止/开标时间，生成目标日期列表</li>
 *   <li>按 EXACT/GROUP/ALL 策略构造 CRM page-list 请求体</li>
 * </ul>
 * <p>spec 037 Review 2.1：去掉 {@code @Service} + Logger，改为 {@code final} 工具类 + 静态方法。
 * 原实现标 {@code @Service} 但类注释自称"纯函数集合"，与 FP-Java 规范矛盾；
 * 现统一为纯函数工具类，无 Spring 依赖、无 IO 副作用、可独立单测。
 * {@code parseDate} 解析失败时返回 {@code Optional.empty()}，不再写 log（解析失败是业务正常分支，不应污染日志）。
 */
public final class CrmChanceTenderMatcher {

    private CrmChanceTenderMatcher() {
        // 工具类，禁止实例化
    }

    /**
     * 解析报名截止与开标时间，去重后返回目标日期列表。
     */
    public static List<LocalDate> parseTargetDates(String registrationDeadline, String bidOpeningTime) {
        List<LocalDate> dates = new ArrayList<>();
        parseDate(registrationDeadline).ifPresent(dates::add);
        parseDate(bidOpeningTime).ifPresent(dates::add);
        return dates.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 构造 EXACT 策略请求：按招标主体 + 单日 evaluationTime 范围查询。
     */
    public static CustomerChancePageRequest buildExactDateRequest(String tenderer, LocalDate targetDate,
                                                                   int pageIndex, int pageSize) {
        String start = targetDate.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String end = targetDate.atTime(23, 59, 59).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        CustomerChanceDTO body = new CustomerChanceDTO(
                List.of(tenderer), null, null, null, null, null, null, null,
                start, end, null, null, null, null, null, null, null, null);
        return new CustomerChancePageRequest(pageIndex, pageSize, body);
    }

    /**
     * 构造 GROUP 策略请求：按招标主体（groupName）查询。
     */
    public static CustomerChancePageRequest buildGroupRequest(String tenderer, int pageIndex, int pageSize) {
        CustomerChanceDTO body = new CustomerChanceDTO(
                List.of(tenderer), null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
        return new CustomerChancePageRequest(pageIndex, pageSize, body);
    }

    /**
     * 构造 ALL 策略请求：拉取全量商机（selectTag=true）。
     */
    public static CustomerChancePageRequest buildSelectAllRequest(int pageIndex, int pageSize) {
        CustomerChanceDTO body = new CustomerChanceDTO(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, true, null, null, null);
        return new CustomerChancePageRequest(pageIndex, pageSize, body);
    }

    /**
     * 解析单个日期字符串，支持多种格式。解析失败返回 empty（业务正常分支，不写日志）。
     */
    public static Optional<LocalDate> parseDate(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                DateTimeFormatter.ISO_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ISO_LOCAL_DATE
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter == DateTimeFormatter.ISO_LOCAL_DATE ||
                        (trimmed.length() <= 10 && !trimmed.contains("T"))) {
                    return Optional.of(LocalDate.parse(trimmed, formatter));
                }
                return Optional.of(LocalDateTime.parse(trimmed, formatter).toLocalDate());
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        return Optional.empty();
    }
}
