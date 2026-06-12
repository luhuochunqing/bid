package com.xiyu.bid.personnel.domain.service;

import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.valueobject.Education;

import java.time.LocalDate;
import java.util.List;

public class PersonnelValidator {

    public ValidationResult validate(Personnel personnel) {
        var errors = new java.util.ArrayList<ValidationError>();
        var warnings = new java.util.ArrayList<ValidationWarning>();

        validateEntryDate(personnel.entryDate())
                .ifPresent(errors::add);

        validateBirthDate(personnel.birthDate(), personnel.entryDate())
                .ifPresent(errors::add);

        validateEducationCount(personnel.educations())
                .ifPresent(errors::add);

        for (Education edu : personnel.educations()) {
            validateEducationDates(edu)
                    .ifPresent(errors::add);
        }

        return new ValidationResult(List.copyOf(errors), List.copyOf(warnings));
    }

    private java.util.Optional<ValidationError> validateEntryDate(LocalDate entryDate) {
        if (entryDate == null) {
            return java.util.Optional.empty();
        }
        if (entryDate.isAfter(LocalDate.now())) {
            return java.util.Optional.of(
                    new ValidationError(
                            "ENTRY_DATE_FUTURE",
                            "入职时间不能晚于今天"
                    )
            );
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<ValidationError> validateBirthDate(
            LocalDate birthDate, LocalDate entryDate) {
        if (birthDate == null || entryDate == null) {
            return java.util.Optional.empty();
        }

        LocalDate minEntryDate = birthDate.plusYears(16);
        if (entryDate.isBefore(minEntryDate)) {
            return java.util.Optional.of(
                    new ValidationError(
                            "BIRTH_DATE_INVALID",
                            "出生日期不合理"
                    )
            );
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<ValidationError> validateEducationCount(List<Education> educations) {
        if (educations == null || educations.isEmpty()) {
            return java.util.Optional.of(
                    new ValidationError(
                            "EDUCATION_REQUIRED",
                            "至少需要一条教育经历记录"
                    )
            );
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<ValidationError> validateEducationDates(Education education) {
        if (education.endDate().isBefore(education.startDate())) {
            return java.util.Optional.of(
                    new ValidationError(
                            "EDUCATION_DATE_INVALID",
                            String.format("毕业日期必须晚于入学日期。学校: %s", education.schoolName())
                    )
            );
        }
        return java.util.Optional.empty();
    }

    public record ValidationResult(
            List<ValidationError> errors,
            List<ValidationWarning> warnings
    ) {
        public boolean isValid() {
            return errors.isEmpty();
        }

        public static ValidationResult success() {
            return new ValidationResult(List.of(), List.of());
        }

        public static ValidationResult failure(ValidationError error) {
            return new ValidationResult(List.of(error), List.of());
        }
    }

    public record ValidationError(
            String code,
            String message
    ) {}

    public record ValidationWarning(
            String code,
            String message
    ) {}
}
