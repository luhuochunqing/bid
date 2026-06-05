package com.xiyu.bid.businessqualification.application.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiringQualificationAlertView {
    private Long qualificationId;
    private String qualificationName;
    private LocalDate expiryDate;
    private long remainingDays;
    private String relatedId;
    private String message;
}
