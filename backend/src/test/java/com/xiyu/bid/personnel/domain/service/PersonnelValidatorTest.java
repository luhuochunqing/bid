package com.xiyu.bid.personnel.domain.service;

import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.valueobject.Education;
import com.xiyu.bid.personnel.domain.valueobject.PersonnelStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PersonnelValidator 单元测试。
 * 纯核心，无框架依赖。
 *
 * 重点覆盖 Sentry issue 7591004886 的回归场景：
 * 教育经历 startDate 为 null（选填项）时不应抛 NPE。
 */
class PersonnelValidatorTest {

    private final PersonnelValidator validator = new PersonnelValidator();

    private Personnel personnelWith(Education... educations) {
        return Personnel.create(
                1L, "张三", "E001", "DEPT-1", "技术部",
                "男", LocalDate.of(2020, 1, 1), LocalDate.of(1995, 1, 1),
                "13800000000", "本科", "工程师",
                PersonnelStatus.ACTIVE, null, null,
                List.of(), List.of(educations)
        );
    }

    /**
     * 回归测试：入学时间为选填项（Education 构造器允许 startDate=null），
     * validator 不应在比较时抛出 NPE。
     * Sentry: NullPointerException at PersonnelValidator.validateEducationDates:78
     */
    @Test
    void shouldNotThrowWhenEducationStartDateIsNull() {
        var edu = new Education(
                "清华大学", null, LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机", false
        );
        var personnel = personnelWith(edu);

        var result = validator.validate(personnel);

        assertThat(result.isValid()).isTrue();
    }

    /**
     * 边界场景：startDate 和 endDate 均存在且 endDate 早于 startDate。
     * 注意：Education 构造器本身会拒绝这种输入（抛 IllegalArgumentException），
     * 所以 validator 这条路径在生产数据上实际不会触发；但 validator 自身逻辑应保持正确。
     * 这里通过反射绕过构造器不变式来直接验证 validator 行为不可行（record 不支持），
     * 因此本测试仅验证 startDate=null 与正常场景，endDate<startDate 的不变式由 EducationTest 覆盖。
     */

    @Test
    void shouldPassWhenEducationDatesAreValid() {
        var edu = new Education(
                "清华大学", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机", false
        );
        var personnel = personnelWith(edu);

        var result = validator.validate(personnel);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRequireAtLeastOneEducation() {
        var personnel = Personnel.create(
                1L, "张三", "E001", "DEPT-1", "技术部",
                "男", LocalDate.of(2020, 1, 1), LocalDate.of(1995, 1, 1),
                "13800000000", "本科", "工程师",
                PersonnelStatus.ACTIVE, null, null,
                List.of(), List.of()
        );

        var result = validator.validate(personnel);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).extracting("code").contains("EDUCATION_REQUIRED");
    }

    @Test
    void shouldWarnWhenEntryDateInFuture() {
        var futureDate = LocalDate.now().plusDays(1);
        var edu = new Education(
                "清华大学", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机", false
        );
        var personnel = Personnel.create(
                1L, "张三", "E001", "DEPT-1", "技术部",
                "男", futureDate, LocalDate.of(1995, 1, 1),
                "13800000000", "本科", "工程师",
                PersonnelStatus.ACTIVE, null, null,
                List.of(), List.of(edu)
        );

        var result = validator.validate(personnel);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).extracting("code").contains("ENTRY_DATE_FUTURE");
    }

    @Test
    void shouldPassWhenEntryDateAndBirthDateAreNull() {
        // 入职/出生日期为选填项，validator 不应报错
        var edu = new Education(
                "清华大学", null, LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机", false
        );
        var personnel = Personnel.create(
                1L, "张三", "E001", "DEPT-1", "技术部",
                "男", null, null,
                "13800000000", "本科", "工程师",
                PersonnelStatus.ACTIVE, null, null,
                List.of(), List.of(edu)
        );

        var result = validator.validate(personnel);

        assertThat(result.isValid()).isTrue();
    }
}
