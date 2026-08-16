package com.xiyu.bid.scoreparse.dto;

import java.util.List;

/**
 * 人员命中摘要（person/match 响应条目）。单人多证只计一次，命中证书名聚合展示。
 */
public record PersonMatchedItem(
        Long id,
        String name,
        String employeeNumber,
        String technicalTitle,
        List<String> hitCertificates
) {
}
