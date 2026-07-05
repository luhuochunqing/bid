package com.xiyu.bid.warehouse.service;

import com.xiyu.bid.warehouse.domain.WarehouseFilterCriteria;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.domain.WarehouseType;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WarehouseFilterSpec 集成测试。
 *
 * 用 H2 内存库 + TestEntityManager，persist 几个真实 WarehouseEntity，
 * 调用 WarehouseFilterSpec.toSpec(criteria) 并通过 WarehouseRepository.findAll(spec)
 * 验证 spec 行为正确。
 *
 * 重点覆盖：regions IN 查询（修复前 spec 漏了 regions 处理，导致 regions 筛选永远不生效）。
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(WarehouseFilterSpec.class)
class WarehouseFilterSpecTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private com.xiyu.bid.warehouse.infrastructure.WarehouseRepository warehouseRepository;

    @Autowired
    private WarehouseFilterSpec warehouseFilterSpec;

    @Test
    void filterByRegions_returnsOnlyMatchingWarehouses() {
        // given: 3 个仓库，分别位于华北 / 华东 / 华南
        persistWarehouse("北京仓", "华北");
        persistWarehouse("上海仓", "华东");
        persistWarehouse("深圳仓", "华南");
        entityManager.flush();
        entityManager.clear();

        // when: 筛选 regions=[华北, 华南]
        WarehouseFilterCriteria criteria = new WarehouseFilterCriteria(
                null, List.of(), List.of(), List.of("华北", "华南"), List.of(),
                null, null, null, null, null, null, null
        );
        List<WarehouseEntity> result = warehouseRepository.findAll(warehouseFilterSpec.toSpec(criteria));

        // then: 只返回华北和华南两个
        assertThat(result)
                .extracting(WarehouseEntity::getName)
                .containsExactlyInAnyOrder("北京仓", "深圳仓");
    }

    @Test
    void filterByRegions_emptyRegions_returnsAll() {
        // given
        persistWarehouse("北京仓", "华北");
        persistWarehouse("上海仓", "华东");
        entityManager.flush();
        entityManager.clear();

        // when: regions 为空（不筛选）
        WarehouseFilterCriteria criteria = WarehouseFilterCriteria.empty();
        List<WarehouseEntity> result = warehouseRepository.findAll(warehouseFilterSpec.toSpec(criteria));

        // then: 返回全部
        assertThat(result).hasSize(2);
    }

    @Test
    void filterByTypes_returnsOnlyMatchingWarehouses() {
        // given
        persistWarehouseWithType("自营仓", WarehouseType.SELF_OPERATED);
        persistWarehouseWithType("云仓", WarehouseType.CLOUD);
        entityManager.flush();
        entityManager.clear();

        // when: 筛选 types=[CLOUD]
        WarehouseFilterCriteria criteria = new WarehouseFilterCriteria(
                null, List.of(WarehouseType.CLOUD), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null
        );
        List<WarehouseEntity> result = warehouseRepository.findAll(warehouseFilterSpec.toSpec(criteria));

        // then
        assertThat(result)
                .extracting(WarehouseEntity::getName)
                .containsExactly("云仓");
    }

    @Test
    void filterByStatuses_returnsOnlyMatchingWarehouses() {
        // given
        persistWarehouseWithStatus("使用中", WarehouseStatus.IN_USE);
        persistWarehouseWithStatus("已关仓", WarehouseStatus.CLOSED);
        entityManager.flush();
        entityManager.clear();

        // when: 筛选 statuses=[CLOSED]
        WarehouseFilterCriteria criteria = new WarehouseFilterCriteria(
                null, List.of(), List.of(WarehouseStatus.CLOSED), List.of(), List.of(),
                null, null, null, null, null, null, null
        );
        List<WarehouseEntity> result = warehouseRepository.findAll(warehouseFilterSpec.toSpec(criteria));

        // then
        assertThat(result)
                .extracting(WarehouseEntity::getName)
                .containsExactly("已关仓");
    }

    private void persistWarehouse(String name, String region) {
        WarehouseEntity e = baseBuilder(name).region(region).build();
        entityManager.persist(e);
    }

    private void persistWarehouseWithType(String name, WarehouseType type) {
        WarehouseEntity e = baseBuilder(name).type(type).build();
        entityManager.persist(e);
    }

    private void persistWarehouseWithStatus(String name, WarehouseStatus status) {
        WarehouseEntity e = baseBuilder(name).status(status).build();
        entityManager.persist(e);
    }

    private WarehouseEntity.WarehouseEntityBuilder baseBuilder(String name) {
        return WarehouseEntity.builder()
                .name(name)
                .type(WarehouseType.SELF_OPERATED)
                .region("华北")
                .province("北京市")
                .address("测试地址")
                .area(new BigDecimal("100.00"))
                .contactPerson("测试联系人")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2027, 1, 1))
                .lessor("测试出租方")
                .lessee("西域")
                .hasPropertyCert(false)
                .hasInvoice(false)
                .hasPhotos(false)
                .hasLeaseContract(false)
                .status(WarehouseStatus.IN_USE);
    }
}
