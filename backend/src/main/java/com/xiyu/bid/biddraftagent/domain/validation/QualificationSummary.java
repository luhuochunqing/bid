package com.xiyu.bid.biddraftagent.domain.validation;

import java.time.LocalDate;

/** 资质库轻量摘要，纯核心不依赖外部 DTO */
public record QualificationSummary(Long id, String name, LocalDate expiryDate) {}
