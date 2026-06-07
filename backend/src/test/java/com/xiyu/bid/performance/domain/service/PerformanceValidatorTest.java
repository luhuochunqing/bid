package com.xiyu.bid.performance.domain.service;

import com.xiyu.bid.performance.domain.model.PerformanceRecord;
import com.xiyu.bid.performance.domain.valueobject.CustomerLevel;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.domain.valueobject.DockingMethod;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceValidatorTest {

    private PerformanceRecord createValidRecord() {
        return new PerformanceRecord(
                1L,
                "测试合同",
                "签约公司",
                "集团公司",
                CustomerType.PRIVATE_ENTERPRISE,
                "行业",
                ProjectType.OFFICE,
                DockingMethod.API,
                CustomerLevel.GROUP,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2027, 12, 31),
                "联系人",
                "13800000000",
                "属地",
                "详细地址",
                "西域项目负责人",
                "http://mall.com",
                true,
                "备注",
                List.of(
                        new PerformanceRecord.AttachmentEntry(1L, "合同协议", "http://oss.com/contract.pdf", "CONTRACT_AGREEMENT"),
                        new PerformanceRecord.AttachmentEntry(2L, "中标通知书", "http://oss.com/notice.pdf", "BID_NOTICE")
                ),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    void testValidRecord() {
        Optional<String> result = PerformanceValidator.validate(createValidRecord());
        assertTrue(result.isEmpty());
    }

    @Test
    void testInvalidContractName() {
        PerformanceRecord record = new PerformanceRecord(
                1L, "", "签约公司", "集团公司", CustomerType.PRIVATE_ENTERPRISE, "行业", ProjectType.OFFICE, DockingMethod.API, CustomerLevel.GROUP,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, "联系人", "13800000000", "属地", "地址", "负责人", "", false, "",
                List.of(new PerformanceRecord.AttachmentEntry(1L, "合同协议", "http://oss.com/c.pdf", "CONTRACT_AGREEMENT")), LocalDateTime.now(), LocalDateTime.now()
        );
        Optional<String> result = PerformanceValidator.validate(record);
        assertTrue(result.isPresent());
        assertEquals("请输入合同名称", result.get());
    }

    @Test
    void testInvalidDateRange() {
        PerformanceRecord record = new PerformanceRecord(
                1L, "合同", "签约公司", "集团公司", CustomerType.PRIVATE_ENTERPRISE, "行业", ProjectType.OFFICE, DockingMethod.API, CustomerLevel.GROUP,
                LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), null, "联系人", "13800000000", "属地", "地址", "负责人", "", false, "",
                List.of(new PerformanceRecord.AttachmentEntry(1L, "合同协议", "http://oss.com/c.pdf", "CONTRACT_AGREEMENT")), LocalDateTime.now(), LocalDateTime.now()
        );
        Optional<String> result = PerformanceValidator.validate(record);
        assertTrue(result.isPresent());
        assertEquals("截止日期必须晚于签约日期", result.get());
    }

    @Test
    void testInvalidTotalExpiryDate() {
        PerformanceRecord record = new PerformanceRecord(
                1L, "合同", "签约公司", "集团公司", CustomerType.PRIVATE_ENTERPRISE, "行业", ProjectType.OFFICE, DockingMethod.API, CustomerLevel.GROUP,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), LocalDate.of(2026, 6, 1), "联系人", "13800000000", "属地", "地址", "负责人", "", false, "",
                List.of(new PerformanceRecord.AttachmentEntry(1L, "合同协议", "http://oss.com/c.pdf", "CONTRACT_AGREEMENT")), LocalDateTime.now(), LocalDateTime.now()
        );
        Optional<String> result = PerformanceValidator.validate(record);
        assertTrue(result.isPresent());
        assertEquals("总截止日期需晚于截止日期", result.get());
    }

    @Test
    void testCentralSoeMissingAttachments() {
        // 央企类型，但是没有 SOE_DIRECTORY 只有关系证明
        PerformanceRecord record1 = new PerformanceRecord(
                1L, "合同", "签约公司", "集团公司", CustomerType.CENTRAL_SOE, "行业", ProjectType.OFFICE, DockingMethod.API, CustomerLevel.GROUP,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, "联系人", "13800000000", "属地", "地址", "负责人", "", false, "",
                List.of(
                        new PerformanceRecord.AttachmentEntry(1L, "合同协议", "http://oss.com/c.pdf", "CONTRACT_AGREEMENT"),
                        new PerformanceRecord.AttachmentEntry(2L, "关系证明", "http://oss.com/rel.pdf", "RELATIONSHIP_PROOF")
                ), LocalDateTime.now(), LocalDateTime.now()
        );
        Optional<String> result1 = PerformanceValidator.validate(record1);
        assertTrue(result1.isPresent());
        assertEquals("央企客户必须上传央企名录截图", result1.get());

        // 央企类型，有名录，没有关系证明
        PerformanceRecord record2 = new PerformanceRecord(
                1L, "合同", "签约公司", "集团公司", CustomerType.CENTRAL_SOE, "行业", ProjectType.OFFICE, DockingMethod.API, CustomerLevel.GROUP,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, "联系人", "13800000000", "属地", "地址", "负责人", "", false, "",
                List.of(
                        new PerformanceRecord.AttachmentEntry(1L, "合同协议", "http://oss.com/c.pdf", "CONTRACT_AGREEMENT"),
                        new PerformanceRecord.AttachmentEntry(2L, "名录", "http://oss.com/soe.pdf", "SOE_DIRECTORY")
                ), LocalDateTime.now(), LocalDateTime.now()
        );
        Optional<String> result2 = PerformanceValidator.validate(record2);
        assertTrue(result2.isPresent());
        assertEquals("央企客户必须上传关系证明", result2.get());
    }

    @Test
    void testMissingBidNotice() {
        PerformanceRecord record = new PerformanceRecord(
                1L, "合同", "签约公司", "集团公司", CustomerType.PRIVATE_ENTERPRISE, "行业", ProjectType.OFFICE, DockingMethod.API, CustomerLevel.GROUP,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, "联系人", "13800000000", "属地", "地址", "负责人", "", true, "",
                List.of(new PerformanceRecord.AttachmentEntry(1L, "合同协议", "http://oss.com/c.pdf", "CONTRACT_AGREEMENT")), LocalDateTime.now(), LocalDateTime.now()
        );
        Optional<String> result = PerformanceValidator.validate(record);
        assertTrue(result.isPresent());
        assertEquals("当中标通知书为是的时候，必传", result.get());
    }

    @Test
    void testInvalidContactInfo() {
        PerformanceRecord record = new PerformanceRecord(
                1L, "合同", "签约公司", "集团公司", CustomerType.PRIVATE_ENTERPRISE, "行业", ProjectType.OFFICE, DockingMethod.API, CustomerLevel.GROUP,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, "联系人", "invalid-phone", "属地", "地址", "负责人", "", false, "",
                List.of(new PerformanceRecord.AttachmentEntry(1L, "合同协议", "http://oss.com/c.pdf", "CONTRACT_AGREEMENT")), LocalDateTime.now(), LocalDateTime.now()
        );
        Optional<String> result = PerformanceValidator.validate(record);
        assertTrue(result.isPresent());
        assertEquals("请输入有效的联系方式", result.get());
    }
}
