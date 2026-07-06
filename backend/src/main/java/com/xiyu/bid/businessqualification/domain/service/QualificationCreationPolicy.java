package com.xiyu.bid.businessqualification.domain.service;

import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;

/**
 * 资质证书创建校验策略（纯核心）。
 * 不依赖 Spring、不读写数据库、不修改入参。
 */
public class QualificationCreationPolicy {

    public QualificationValidationResult validateForCreate(BusinessQualification qualification) {
        return validateCore(qualification);
    }

    /**
     * CO-525 fix: 更新路径必须与创建路径保持同等业务约束，防止通过更新 API 清空必填字段。
     */
    public QualificationValidationResult validateForUpdate(BusinessQualification qualification) {
        return validateCore(qualification);
    }

    private QualificationValidationResult validateCore(BusinessQualification qualification) {
        if (isBlank(qualification.level())) {
            return QualificationValidationResult.invalid("等级不能为空");
        }
        if (isBlank(qualification.agency())) {
            return QualificationValidationResult.invalid("代理机构不能为空");
        }
        if (isBlank(qualification.agencyContact())) {
            return QualificationValidationResult.invalid("代理机构联系人不能为空");
        }
        if (isBlank(qualification.certScope())) {
            return QualificationValidationResult.invalid("认证范围不能为空");
        }
        if (isBlank(qualification.certificateNo())) {
            return QualificationValidationResult.invalid("证书编号不能为空");
        }
        ValidityPeriod period = qualification.validityPeriod();
        if (period == null) {
            return QualificationValidationResult.invalid("有效期不能为空");
        }
        if (period.getIssueDate() != null && period.getExpiryDate() != null
                && period.getIssueDate().isAfter(period.getExpiryDate())) {
            return QualificationValidationResult.invalid("证书发证日期不可晚于到期日期");
        }
        return QualificationValidationResult.success();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
