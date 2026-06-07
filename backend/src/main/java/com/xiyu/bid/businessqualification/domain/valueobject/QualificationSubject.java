package com.xiyu.bid.businessqualification.domain.valueobject;

import com.xiyu.bid.businessqualification.domain.service.QualificationValidationResult;

public record QualificationSubject(
        QualificationSubjectType type,
        String name
) {

    public static QualificationSubject of(QualificationSubjectType type, String name) {
        return new QualificationSubject(type, name);
    }

    public QualificationValidationResult validate() {
        if (type == null) {
            return QualificationValidationResult.invalid("资质主体类型不能为空");
        }
        if (name == null || name.isBlank()) {
            return QualificationValidationResult.invalid("资质主体名称不能为空");
        }
        return QualificationValidationResult.success();
    }

    public QualificationSubjectType getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}
