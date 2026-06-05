package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.application.view.ExpiringQualificationAlertView;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScanExpiringQualificationsAppService {

    private final BusinessQualificationRepository qualificationRepository;

    @Transactional(readOnly = true)
    public List<ExpiringQualificationAlertView> scan(int thresholdDays) {
        return qualificationRepository.findExpiringWithinDays(thresholdDays).stream()
                .filter(this::shouldAlert)
                .map(this::toAlertView)
                .toList();
    }

    private boolean shouldAlert(BusinessQualification qualification) {
        return qualification.remainingDays() >= 0 && qualification.reminderPolicy().isEnabled();
    }

    private ExpiringQualificationAlertView toAlertView(BusinessQualification qualification) {
        return ExpiringQualificationAlertView.builder()
                .qualificationId(qualification.id())
                .qualificationName(qualification.name())
                .expiryDate(qualification.validityPeriod().getExpiryDate())
                .remainingDays(qualification.remainingDays())
                .relatedId(String.format("Qualification:%s:%s", qualification.id(), qualification.validityPeriod().getExpiryDate()))
                .message(String.format("资质 %s 将在 %d 天后到期", qualification.name(), qualification.remainingDays()))
                .build();
    }
}
