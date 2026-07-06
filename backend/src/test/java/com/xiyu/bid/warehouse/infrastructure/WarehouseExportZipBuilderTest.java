package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.domain.WarehouseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    @Test
    @DisplayName("buildZip 将租赁合同附件打包进 zip 的 attachments 目录")
    void buildZip_IncludesLeaseContractAttachment() throws Exception {
        Path attachmentRoot = Files.createTempDirectory("wh-zip-test-attachments-");
        Path warehouseDir = attachmentRoot.resolve("1");
        Files.createDirectories(warehouseDir);
        Path sourceFile = warehouseDir.resolve("stored_lease.pdf");
        Files.writeString(sourceFile, "lease contract content");

        WarehouseEntity warehouse = WarehouseEntity.builder()
                .id(1L)
                .name("测试仓")
                .type(WarehouseType.SELF_OPERATED)
                .region("华东")
                .province("上海")
                .address("测试地址")
                .area(new java.math.BigDecimal("100"))
                .contactPerson("张三")
                .lessor("甲方")
                .lessee("乙方")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2027, 1, 1))
                .hasPropertyCert(false)
                .hasInvoice(false)
                .hasPhotos(false)
                .hasLeaseContract(true)
                .status(WarehouseStatus.IN_USE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .build();

        WarehouseAttachmentEntity leaseAttachment = WarehouseAttachmentEntity.builder()
                .id(1L)
                .warehouse(warehouse)
                .type(WarehouseAttachmentType.LEASE_CONTRACT)
                .originalFilename("租赁合同.pdf")
                .storedFilename("stored_lease.pdf")
                .fileSize(22L)
                .contentType("application/pdf")
                .uploadedBy(1L)
                .uploadedAt(LocalDateTime.now())
                .build();

        WarehouseExportZipBuilder builder = new WarehouseExportZipBuilder();
        Field attachmentRootField = WarehouseExportZipBuilder.class.getDeclaredField("attachmentRoot");
        attachmentRootField.setAccessible(true);
        attachmentRootField.set(builder, attachmentRoot.toString());

        byte[] xlsxBytes = new byte[]{0x01, 0x02, 0x03};
        WarehouseExportZipBuilder.ZipBuildResult result = builder.buildZip(
                xlsxBytes, List.of(warehouse), Map.of(1L, List.of(leaseAttachment)));

        assertThat(result.zipFile()).exists();
        assertThat(result.totalBytes()).isGreaterThan(0);
        assertThat(result.stats().leaseContractCount).isEqualTo(1);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(result.zipFile()))) {
            List<String> entryNames = new java.util.ArrayList<>();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }
            assertThat(entryNames).contains("仓库信息台账.xlsx", "attachments/WH_测试仓_租赁合同.pdf");
        } finally {
            Files.walk(attachmentRoot)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) { }
                    });
            Files.deleteIfExists(result.zipFile());
            Files.deleteIfExists(result.zipFile().getParent());
        }
    }
}
