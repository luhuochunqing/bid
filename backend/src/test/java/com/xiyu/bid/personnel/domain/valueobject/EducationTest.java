package com.xiyu.bid.personnel.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Education 值对象单元测试（蓝图 4.3 "新增证书" Tab 2 教育经历不变式）
 * 纯核心，无副作用。
 */
class EducationTest {

    @Test
    void shouldCreateValidEducation() {
        var edu = new Education(
                "清华大学", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机科学与技术"
        );
        assertThat(edu.schoolName()).isEqualTo("清华大学");
        assertThat(edu.startDate()).isEqualTo(LocalDate.of(2015, 9, 1));
        assertThat(edu.endDate()).isEqualTo(LocalDate.of(2019, 6, 30));
        assertThat(edu.highestEducation()).isEqualTo("本科");
        assertThat(edu.studyForm()).isEqualTo("全日制");
        assertThat(edu.major()).isEqualTo("计算机科学与技术");
    }

    @Test
    void shouldRejectBlankSchoolName() {
        assertThatThrownBy(() -> new Education(
                "  ", LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("学校名称不能为空");
    }

    @Test
    void shouldRejectNullSchoolName() {
        assertThatThrownBy(() -> new Education(
                null, LocalDate.of(2015, 9, 1), LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("学校名称不能为空");
    }

    @Test
    void shouldRejectNullDates() {
        assertThatThrownBy(() -> new Education(
                "清华大学", null, LocalDate.of(2019, 6, 30),
                "本科", "全日制", "计算机"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("入学时间和毕业时间不能为空");

        assertThatThrownBy(() -> new Education(
                "清华大学", LocalDate.of(2015, 9, 1), null,
                "本科", "全日制", "计算机"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("入学时间和毕业时间不能为空");
    }

    @Test
    void shouldRejectEndDateBeforeStartDate() {
        assertThatThrownBy(() -> new Education(
                "清华大学", LocalDate.of(2019, 6, 30), LocalDate.of(2015, 9, 1),
                "本科", "全日制", "计算机"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("毕业时间不能早于入学时间");
    }

    @Test
    void shouldAllowSameStartAndEndDate() {
        // 同月入学毕业（边界场景）
        var edu = new Education(
                "短训班", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31),
                "高中", "其他", null
        );
        assertThat(edu.schoolName()).isEqualTo("短训班");
    }

    @Test
    void shouldAllowNullMajor() {
        var edu = new Education(
                "北京大学", LocalDate.of(2016, 9, 1), LocalDate.of(2020, 6, 30),
                "硕士", "非全日制", null
        );
        assertThat(edu.major()).isNull();
    }
}
