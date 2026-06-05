package com.xiyu.bid.brandauth.domain.valueobject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public enum AuthorizationStatus {
    ACTIVE,         // 有效
    EXPIRING_SOON,   // 即将到期
    EXPIRED,         // 已过期
    ARCHIVED         // 已下架
}
