package com.xiyu.bid.personnel.application.mapper;

import com.xiyu.bid.personnel.application.dto.CertificateDTO;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;
import com.xiyu.bid.personnel.domain.valueobject.CertificateType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PersonnelMapper 单元测试（CO-467）
 * 验证 toCertDTO 透传 remark 字段，修复证书与职称 tab 备注字段丢失。
 */
class PersonnelMapperTest {

    private final PersonnelMapper mapper = new PersonnelMapper();

    @Test
    void toCertDTO_shouldCarryRemarkField() {
        Certificate cert = new Certificate(
                1L, "一级建造师", "CERT-001", CertificateType.CONSTRUCTOR,
                LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1),
                "https://example.com/cert.pdf",
                "高级", true, "长期有效备注"
        );

        CertificateDTO dto = mapper.toCertDTO(cert);

        assertThat(dto.remark()).isEqualTo("长期有效备注");
        assertThat(dto.title()).isEqualTo("高级");
        assertThat(dto.isPermanent()).isTrue();
    }

    @Test
    void toCertDTO_shouldHandleNullRemark() {
        Certificate cert = new Certificate(
                null, "PMP", "CERT-002", CertificateType.PMP,
                null, null, null,
                null, false, null
        );

        CertificateDTO dto = mapper.toCertDTO(cert);

        assertThat(dto.remark()).isNull();
    }
}
