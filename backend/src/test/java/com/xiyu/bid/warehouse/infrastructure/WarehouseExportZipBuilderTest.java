package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 WarehouseExportZipBuilder 的 typeLabel 与 countByType
 * 正确处理 LEASE_CONTRACT（CO-493 P0 Bug #2 回归测试）。
 */
class WarehouseExportZipBuilderTest {

    @Test
    @DisplayName("typeLabel 返回「租赁合同」而非 null")
    void typeLabelReturnsLeaseContract() throws Exception {
        String label = invokeTypeLabel(WarehouseAttachmentType.LEASE_CONTRACT);
        assertThat(label).isEqualTo("租赁合同");
    }

    @Test
    @DisplayName("typeLabel 仍然返回原有三种类型的标签")
    void typeLabelStillReturnsOriginalTypes() throws Exception {
        assertThat(invokeTypeLabel(WarehouseAttachmentType.PROPERTY_CERTIFICATE)).isEqualTo("产权证");
        assertThat(invokeTypeLabel(WarehouseAttachmentType.INVOICE)).isEqualTo("发票");
        assertThat(invokeTypeLabel(WarehouseAttachmentType.PHOTOS)).isEqualTo("内外照片");
    }

    @Test
    @DisplayName("countByType 累加 leaseContractCount")
    void countByTypeIncrementsLeaseContractCount() throws Exception {
        WarehouseExportZipBuilder.ZipStats stats = new WarehouseExportZipBuilder.ZipStats();
        invokeCountByType(stats, WarehouseAttachmentType.LEASE_CONTRACT);
        invokeCountByType(stats, WarehouseAttachmentType.LEASE_CONTRACT);
        assertThat(stats.leaseContractCount).isEqualTo(2);
    }

    private static String invokeTypeLabel(WarehouseAttachmentType type) throws Exception {
        Method m = WarehouseExportZipBuilder.class.getDeclaredMethod("typeLabel", WarehouseAttachmentType.class);
        m.setAccessible(true);
        return (String) m.invoke(null, type);
    }

    private static void invokeCountByType(WarehouseExportZipBuilder.ZipStats stats, WarehouseAttachmentType type) throws Exception {
        Method m = WarehouseExportZipBuilder.class.getDeclaredMethod("countByType",
                WarehouseExportZipBuilder.ZipStats.class, WarehouseAttachmentType.class);
        m.setAccessible(true);
        m.invoke(new WarehouseExportZipBuilder(), stats, type);
    }
}
