package com.xiyu.bid.businessqualification.application.command;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class QualificationListCriteria {
    String subjectType;
    String subjectName;
    String category;
    String level;
    List<String> status;
    Integer expiringWithinDays;
    LocalDate expiringFrom;
    LocalDate expiringTo;
    String issuer;
    String keyword;
}
