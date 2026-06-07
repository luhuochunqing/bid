package com.xiyu.bid.biddraftagent.domain.validation;

import java.time.LocalDate;

/** 人员证书轻量摘要，纯核心不依赖外部 DTO */
public record PersonnelCertSummary(
        Long personnelId,
        String personnelName,
        String certName,
        LocalDate certExpiryDate
) {}
