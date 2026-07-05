package com.xiyu.bid.brandauth.manufacturer.infrastructure;

import com.xiyu.bid.brandauth.manufacturer.application.service.ListManufacturerAuthAppService;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 品牌授权查询参数解析工具类.
 * 统一处理 Controller 层的 productLines/statuses 参数解析和 ListFilter 构建，
 * 消除 ManufacturerAuthorizationController 和 BrandAuthZipExportController 间的重复.
 */
public final class BrandAuthFilterMapper {

    private BrandAuthFilterMapper() {}

    /**
     * 从 RequestParam 构建 ListFilter.
     */
    public static ListManufacturerAuthAppService.ListFilter buildFilter(
            final List<String> productLines,
            final String brandId,
            final String brandName,
            final String importDomestic,
            final String manufacturerName,
            final LocalDate authStartFrom,
            final LocalDate authStartTo,
            final LocalDate authEndFrom,
            final LocalDate authEndTo,
            final List<String> statuses,
            final String keyword,
            final String authorizationType) {
        List<ProductLine> productLineEnums = parseProductLines(productLines);
        List<AuthStatus> statusEnums = parseStatuses(statuses);
        return new ListManufacturerAuthAppService.ListFilter(
                productLineEnums, brandId, brandName,
                importDomestic, manufacturerName,
                authStartFrom, authStartTo, authEndFrom, authEndTo,
                statusEnums, keyword, authorizationType);
    }

    private static List<ProductLine> parseProductLines(
            final List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<ProductLine> result = new ArrayList<>();
        for (String v : values) {
            ProductLine.fromStringOptional(v).ifPresentOrElse(
                    result::add,
                    () -> {
                        throw new IllegalArgumentException(
                                "无效的一级产线参数: " + v);
                    });
        }
        return result;
    }

    private static List<AuthStatus> parseStatuses(
            final List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of(AuthStatus.ACTIVE, AuthStatus.EXPIRING_SOON,
                    AuthStatus.EXPIRED);
        }
        try {
            return values.stream().map(AuthStatus::valueOf).toList();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的状态参数");
        }
    }
}
