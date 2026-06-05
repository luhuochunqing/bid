package com.xiyu.bid.tender.core;

import com.xiyu.bid.tender.dto.TenderDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 标讯基本信息校验策略（纯核心）。
 * 无副作用，不依赖数据库或外部服务。
 */
public final class TenderBasicInfoValidator {

    private TenderBasicInfoValidator() { /* utility */ }

    /**
     * 校验结果。
     */
    public record ValidationResult(List<String> errors) {

        public static ValidationResult valid() {
            return new ValidationResult(List.of());
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /**
     * 校验标讯基本信息。
     * 规则：
     * - 报名截止时间必须大于当前时间
     * - 开标时间必须大于报名截止时间
     *
     * @param dto 待校验的标讯 DTO
     * @return 校验结果
     */
    public static ValidationResult validateBasicInfo(TenderDTO dto) {
        List<String> errors = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime regDeadline = dto.getRegistrationDeadline();
        LocalDateTime bidOpenTime = dto.getBidOpeningTime();

        if (regDeadline != null && !regDeadline.isAfter(now)) {
            errors.add("报名截止时间必须大于当前时间");
        }

        if (regDeadline != null && bidOpenTime != null && !bidOpenTime.isAfter(regDeadline)) {
            errors.add("开标时间必须大于报名截止时间");
        }

        return new ValidationResult(errors);
    }
}
