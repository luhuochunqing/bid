package com.xiyu.bid.tender.controller;

import com.xiyu.bid.tender.dto.TenderRequest;
import com.xiyu.bid.tender.service.TenderSearchCriteria;
import com.xiyu.bid.util.InputSanitizer;
import org.springframework.stereotype.Component;

import java.util.List;

/** 仅维护协议适配与参数校验；业务规则下沉到 service. */
@Component
public class TenderRequestSanitizer {

    public void sanitize(TenderRequest request) {
        if (request == null) return;
        sanitizeString(request::setTitle, request.getTitle(), 500);
        sanitizeString(request::setSource, request.getSource(), 200);
        sanitizeString(request::setRegion, request.getRegion(), 100);
        sanitizeString(request::setIndustry, request.getIndustry(), 100);
        sanitizeString(request::setTenderAgency, request.getTenderAgency(), 255);
        sanitizeString(request::setPurchaserName, request.getPurchaserName(), 255);
        sanitizeString(request::setPurchaserHash, request.getPurchaserHash(), 64);
        sanitizeString(request::setContactName, request.getContactName(), 100);
        sanitizeString(request::setContactPhone, request.getContactPhone(), 50);
        sanitizeString(request::setSourceDocumentName, request.getSourceDocumentName(), 255);
        sanitizeString(request::setSourceDocumentFileType, request.getSourceDocumentFileType(), 100);
        sanitizeString(request::setSourceDocumentFileUrl, request.getSourceDocumentFileUrl(), 1000);
        sanitizeString(request::setCustomerType, request.getCustomerType(), 100);
        sanitizeString(request::setPriority, request.getPriority(), 10);
        sanitizeString(request::setDescription, request.getDescription(), 5000);
        if (request.getTags() != null) {
            request.setTags(request.getTags().stream()
                    .map(tag -> sanitize(tag, 100))
                    .filter(tag -> !tag.isBlank())
                    .toList());
        }
    }

    public void sanitizeCriteria(TenderSearchCriteria criteria) {
        if (criteria == null) return;
        sanitizeString(criteria::setKeyword, criteria.getKeyword(), 200);
        sanitizeListString(criteria::setSource, criteria.getSource(), 200);
        sanitizeString(criteria::setRegion, criteria.getRegion(), 100);
        sanitizeString(criteria::setIndustry, criteria.getIndustry(), 100);
        sanitizeString(criteria::setPurchaserName, criteria.getPurchaserName(), 255);
        sanitizeString(criteria::setPurchaserHash, criteria.getPurchaserHash(), 64);
        sanitizeListString(criteria::setCustomerType, criteria.getCustomerType(), 100);
        sanitizeListString(criteria::setPriority, criteria.getPriority(), 10);
    }

    @FunctionalInterface
    interface StringSetter {
        void set(String value);
    }

    @FunctionalInterface
    interface ListStringSetter {
        void set(List<String> value);
    }

    private void sanitizeString(StringSetter setter, String value, int maxLength) {
        if (value == null) return;
        setter.set(InputSanitizer.sanitizeString(value, maxLength));
    }

    private void sanitizeListString(ListStringSetter setter, List<String> values, int maxLength) {
        if (values == null || values.isEmpty()) return;
        setter.set(values.stream()
                .map(v -> v == null ? "" : InputSanitizer.sanitizeString(v, maxLength))
                .filter(v -> !v.isBlank())
                .toList());
    }

    private String sanitize(String value, int maxLength) {
        return value == null ? null : InputSanitizer.sanitizeString(value, maxLength);
    }
}
