package com.xiyu.bid.businessqualification.application.command;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QualificationListCriteria {
    String subjectType;
    String subjectName;
    String category;
    String status;
    String borrowStatus;
    Integer expiringWithinDays;
    String keyword;
}
