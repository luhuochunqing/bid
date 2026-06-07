package com.xiyu.bid.biddraftagent.domain.validation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 人员证书匹配器（纯核心）。
 * 将要求文本与人员证书名称做智能匹配，三态判断。
 */
public class PersonnelCertMatcher {

    static final int EXPIRY_WARN_DAYS = 60;

    public record PersonnelMatchItem(
            String requirementText,
            QualificationMatchStatus status,
            String matchedPersonnelName,
            String matchedCertName,
            Integer remainingDays,
            String reason
    ) {}

    public record PersonnelMatchResult(List<PersonnelMatchItem> items) {}

    public PersonnelMatchResult match(
            List<String> requirements,
            List<PersonnelCertSummary> personnelCerts,
            LocalDate referenceDate) {

        List<PersonnelMatchItem> items = new ArrayList<>();
        if (requirements == null || requirements.isEmpty()) {
            return new PersonnelMatchResult(items);
        }

        for (String req : requirements) {
            PersonnelCertSummary matched = null;
            for (PersonnelCertSummary cert : personnelCerts) {
                if (SmartMatchUtils.isSmartMatch(req, cert.certName())
                        || SmartMatchUtils.isSmartMatch(req, cert.personnelName())) {
                    matched = cert;
                    break;
                }
            }

            if (matched == null) {
                items.add(new PersonnelMatchItem(
                        req, QualificationMatchStatus.UNSATISFIED,
                        null, null, null,
                        "人员库中未找到匹配的证书或人员"));
            } else {
                Integer remainingDays = computeRemainingDays(matched, referenceDate);
                if (remainingDays != null && remainingDays <= EXPIRY_WARN_DAYS) {
                    items.add(new PersonnelMatchItem(
                            req, QualificationMatchStatus.ATTENTION,
                            matched.personnelName(), matched.certName(), remainingDays,
                            "人员「" + matched.personnelName() + "」的证书「" + matched.certName()
                                    + "」" + remainingDays + "天后到期，建议人工复核"));
                } else {
                    items.add(new PersonnelMatchItem(
                            req, QualificationMatchStatus.SATISFIED,
                            matched.personnelName(), matched.certName(), remainingDays,
                            "人员库中已匹配「" + matched.personnelName() + " - " + matched.certName() + "」"));
                }
            }
        }
        return new PersonnelMatchResult(items);
    }

    private Integer computeRemainingDays(PersonnelCertSummary cert, LocalDate referenceDate) {
        if (cert.certExpiryDate() == null) return null;
        long days = ChronoUnit.DAYS.between(referenceDate, cert.certExpiryDate());
        return days < 0 ? null : (int) days;
    }
}
