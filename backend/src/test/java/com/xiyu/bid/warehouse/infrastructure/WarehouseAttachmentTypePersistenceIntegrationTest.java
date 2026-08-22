package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.support.AbstractMysqlIntegrationTest;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.domain.WarehouseType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("flyway-mysql")
class WarehouseAttachmentTypePersistenceIntegrationTest extends AbstractMysqlIntegrationTest {

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private WarehouseAttachmentRepository attachmentRepository;

    // 本地（非 CI）AbstractMysqlIntegrationTest 共享持久容器库 xiyu_bid_verify，
    // 数据跨 JVM 运行残留；uk_warehouse_name 唯一键要求先清历史同名行
    @org.junit.jupiter.api.BeforeEach
    void removeLeftoverWarehouse() {
        attachmentRepository.deleteAll();
        warehouseRepository.findByName("测试仓库").ifPresent(warehouseRepository::delete);
        flushAndClear();
    }

    @Test
    void 所有附件类型枚举值都能正确持久化和查询() {
        WarehouseEntity warehouse = warehouseRepository.save(WarehouseEntity.builder()
                .name("测试仓库")
                .type(WarehouseType.SELF_OPERATED)
                .region("华东")
                .province("上海")
                .address("上海市浦东新区")
                .area(new BigDecimal("1000.00"))
                .contactPerson("张三")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2029, 12, 31))
                .lessor("出租方")
                .lessee("承租方")
                .hasPropertyCert(true)
                .hasInvoice(true)
                .hasPhotos(true)
                .hasLeaseContract(true)
                .status(WarehouseStatus.IN_USE)
                .build());

        for (WarehouseAttachmentType type : WarehouseAttachmentType.values()) {
            WarehouseAttachmentEntity attachment = WarehouseAttachmentEntity.builder()
                    .warehouse(warehouse)
                    .type(type)
                    .originalFilename(type.name() + ".pdf")
                    .storedFilename("stored-" + type.name() + ".pdf")
                    .fileSize(1024L)
                    .contentType("application/pdf")
                    .uploadedBy(1L)
                    .uploadedAt(LocalDateTime.now())
                    .build();
            attachmentRepository.save(attachment);
        }

        flushAndClear();

        List<WarehouseAttachmentEntity> allAttachments = attachmentRepository.findByWarehouseId(warehouse.getId());
        assertThat(allAttachments).hasSize(WarehouseAttachmentType.values().length);

        for (WarehouseAttachmentType type : WarehouseAttachmentType.values()) {
            List<WarehouseAttachmentEntity> typedAttachments = attachmentRepository.findByWarehouseIdAndType(warehouse.getId(), type);
            assertThat(typedAttachments).hasSize(1);
            assertThat(typedAttachments.get(0).getType()).isEqualTo(type);
            assertThat(typedAttachments.get(0).getOriginalFilename()).isEqualTo(type.name() + ".pdf");
        }

        List<WarehouseAttachmentEntity> leaseContracts = attachmentRepository.findByWarehouseIdAndType(warehouse.getId(), WarehouseAttachmentType.LEASE_CONTRACT);
        assertThat(leaseContracts).hasSize(1);
        assertThat(leaseContracts.get(0).getType()).isEqualTo(WarehouseAttachmentType.LEASE_CONTRACT);
    }
}
