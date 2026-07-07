package com.xiyu.bid.resources.core;

import com.xiyu.bid.resources.entity.CaCertificateEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CO-515: CaFieldDiffCalculator 单元测试（纯核心，不依赖框架）。
 */
class CaFieldDiffCalculatorTest {

    @Test
    void diff_bothNull_returnsEmptyList() {
        List<String> changes = CaFieldDiffCalculator.diff(null, null);
        assertTrue(changes.isEmpty());
    }

    @Test
    void diff_afterNull_returnsEmptyList() {
        List<String> changes = CaFieldDiffCalculator.diff(null, CaCertificateEntity.builder().build());
        assertTrue(changes.isEmpty());
    }

    @Test
    void diff_noChanges_returnsEmptyList() {
        CaCertificateEntity before = CaCertificateEntity.builder()
                .caType("ENTITY_CA")
                .sealType("OFFICIAL_SEAL")
                .issuer("某机构")
                .build();
        CaCertificateEntity after = CaCertificateEntity.builder()
                .caType("ENTITY_CA")
                .sealType("OFFICIAL_SEAL")
                .issuer("某机构")
                .build();
        List<String> changes = CaFieldDiffCalculator.diff(before, after);
        assertTrue(changes.isEmpty());
    }

    @Test
    void diff_singleFieldChange_returnsOneChange() {
        CaCertificateEntity before = CaCertificateEntity.builder()
                .caType("ENTITY_CA")
                .build();
        CaCertificateEntity after = CaCertificateEntity.builder()
                .caType("ELECTRONIC_CA")
                .build();
        List<String> changes = CaFieldDiffCalculator.diff(before, after);
        assertEquals(1, changes.size());
        assertEquals("CA类型：ENTITY_CA -> ELECTRONIC_CA", changes.get(0));
    }

    @Test
    void diff_multipleFieldChanges_returnsAllChanges() {
        CaCertificateEntity before = CaCertificateEntity.builder()
                .caType("ENTITY_CA")
                .holderName("张三")
                .custodianName("张三")
                .build();
        CaCertificateEntity after = CaCertificateEntity.builder()
                .caType("ELECTRONIC_CA")
                .holderName("李四")
                .custodianName("李四")
                .build();
        List<String> changes = CaFieldDiffCalculator.diff(before, after);
        assertEquals(3, changes.size());
        assertTrue(changes.contains("CA类型：ENTITY_CA -> ELECTRONIC_CA"));
        assertTrue(changes.contains("持有人：张三 -> 李四"));
        assertTrue(changes.contains("保管员姓名：张三 -> 李四"));
    }

    @Test
    void diff_passwordChange_shows已更新() {
        CaCertificateEntity before = CaCertificateEntity.builder()
                .caPassword("old_encrypted_password")
                .build();
        CaCertificateEntity after = CaCertificateEntity.builder()
                .caPassword("new_encrypted_password")
                .build();
        List<String> changes = CaFieldDiffCalculator.diff(before, after);
        assertEquals(1, changes.size());
        assertEquals("CA密码：已更新", changes.get(0));
    }

    @Test
    void diff_nullToValue_showsDashAsOldValue() {
        CaCertificateEntity before = CaCertificateEntity.builder()
                .issuer(null)
                .build();
        CaCertificateEntity after = CaCertificateEntity.builder()
                .issuer("某机构")
                .build();
        List<String> changes = CaFieldDiffCalculator.diff(before, after);
        assertEquals(1, changes.size());
        assertEquals("颁发机构：- -> 某机构", changes.get(0));
    }

    @Test
    void diff_valueToNull_showsDashAsNewValue() {
        CaCertificateEntity before = CaCertificateEntity.builder()
                .issuer("某机构")
                .build();
        CaCertificateEntity after = CaCertificateEntity.builder()
                .issuer(null)
                .build();
        List<String> changes = CaFieldDiffCalculator.diff(before, after);
        assertEquals(1, changes.size());
        assertEquals("颁发机构：某机构 -> -", changes.get(0));
    }

    @Test
    void diff_dateChange_formattedAsYYYYMMDD() {
        CaCertificateEntity before = CaCertificateEntity.builder()
                .expiryDate(LocalDate.of(2026, 7, 6))
                .build();
        CaCertificateEntity after = CaCertificateEntity.builder()
                .expiryDate(LocalDate.of(2027, 1, 15))
                .build();
        List<String> changes = CaFieldDiffCalculator.diff(before, after);
        assertEquals(1, changes.size());
        assertEquals("有效期至：2026-07-06 -> 2027-01-15", changes.get(0));
    }

    @Test
    void formatSummary_joinsWithSemicolon() {
        List<String> changes = List.of(
                "CA类型：ENTITY_CA -> ELECTRONIC_CA",
                "持有人：张三 -> 李四"
        );
        String summary = CaFieldDiffCalculator.formatSummary(changes);
        assertEquals("CA类型：ENTITY_CA -> ELECTRONIC_CA；持有人：张三 -> 李四", summary);
    }

    @Test
    void formatSummary_emptyList_returnsEmptyString() {
        assertEquals("", CaFieldDiffCalculator.formatSummary(List.of()));
        assertEquals("", CaFieldDiffCalculator.formatSummary(null));
    }

    @Test
    void diff_emptyStringTreatedAsDash() {
        CaCertificateEntity before = CaCertificateEntity.builder()
                .issuer("")
                .build();
        CaCertificateEntity after = CaCertificateEntity.builder()
                .issuer("某机构")
                .build();
        List<String> changes = CaFieldDiffCalculator.diff(before, after);
        assertEquals(1, changes.size());
        assertEquals("颁发机构：- -> 某机构", changes.get(0));
    }

    @Test
    void diff_longIdValue_handledAsString() {
        CaCertificateEntity before = CaCertificateEntity.builder()
                .custodianId(1001L)
                .build();
        CaCertificateEntity after = CaCertificateEntity.builder()
                .custodianId(2002L)
                .build();
        List<String> changes = CaFieldDiffCalculator.diff(before, after);
        assertEquals(1, changes.size());
        assertEquals("保管员：1001 -> 2002", changes.get(0));
    }
}
