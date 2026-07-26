// Input: e2e Spring profile, FormDefinitionRegistryRepository
// Output: pre-seeded form_definition_registry rows for E2E tests
// Pos: Bootstrap/E2E 表单定义种子数据初始化
// 维护声明: 仅维护 e2e profile 的表单定义种子；生产种子数据走 Flyway V140 迁移脚本.
//          修改字段定义时请同步更新 V140__dynamic_form_engine.sql 和本 seeder.
package com.xiyu.bid.bootstrap;

import com.xiyu.bid.formengine.infrastructure.persistence.FormDefinitionRegistryRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormDefinitionRegistryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * E2E 环境表单定义种子数据初始化器.
 *
 * <p>E2E 测试栈（application-e2e.yml + H2 内存数据库）启动时通过
 * {@code --spring.flyway.enabled=false} 禁用了 Flyway，因此 V140 迁移脚本里
 * 预置的 {@code form_definition_registry} 种子数据不会被加载，导致
 * {@code /api/form-definitions/{scope}/active} 接口返回 404，
 * form-engine-scope-router / form-engine-adaptive-flow 测试全部失败。</p>
 *
 * <p>本 seeder 在 e2e profile 下运行，幂等地预置核心 scope 的表单定义数据，
 * 与 V140 迁移脚本保持一致（额外补充测试需要的 {@code knowledge.qual} scope）。</p>
 *
 * <p>注：dev/prod 环境走 Flyway V140 迁移脚本，不需要本 seeder。</p>
 */
@Component
@Profile("e2e")
@RequiredArgsConstructor
@Slf4j
@Order(20)
public class FormDefinitionE2eSeeder implements ApplicationRunner {

    private final FormDefinitionRegistryRepository definitionRepository;

    @Override
    public void run(ApplicationArguments args) {
        int inserted = 0;
        int skipped = 0;
        for (FormDefinitionSeed seed : SEEDS) {
            if (definitionRepository.existsByScope(seed.scope())) {
                skipped++;
                continue;
            }
            FormDefinitionRegistryEntity entity = new FormDefinitionRegistryEntity();
            entity.setScope(seed.scope());
            entity.setScopeLabel(seed.scopeLabel());
            entity.setVersion(1);
            entity.setSchemaJson(seed.schemaJson());
            entity.setEnabled(Boolean.TRUE);
            entity.setOrgId(null);
            entity.setCreatedBy("system");
            LocalDateTime now = LocalDateTime.now();
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            definitionRepository.save(entity);
            inserted++;
        }
        log.info("FormDefinitionE2eSeeder: inserted={}, skipped={} (total seeds={})",
                inserted, skipped, SEEDS.size());
    }

    /**
     * 核心 scope 的表单定义种子数据.
     * <p>与 V140__dynamic_form_engine.sql 保持一致，额外补充 {@code knowledge.qual}.</p>
     */
    private static final List<FormDefinitionSeed> SEEDS = List.of(
            new FormDefinitionSeed(
                    "tender.entry",
                    "标讯手工录入",
                    "{\"fields\":[{\"key\":\"title\",\"label\":\"标讯标题\",\"type\":\"TEXT\",\"required\":true,\"placeholder\":\"请输入标讯标题\",\"maxLength\":200},{\"key\":\"source\",\"label\":\"信息来源\",\"type\":\"SELECT\",\"required\":false,\"options\":[{\"label\":\"招标公告\",\"value\":\"bidding\"},{\"label\":\"比选公告\",\"value\":\"selection\"},{\"label\":\"竞争性谈判\",\"value\":\"negotiation\"},{\"label\":\"单一来源\",\"value\":\"single_source\"},{\"label\":\"其他\",\"value\":\"other\"}]},{\"key\":\"budget\",\"label\":\"预算金额\",\"type\":\"CURRENCY\",\"required\":false,\"validation\":{\"min\":0,\"precision\":2}},{\"key\":\"region\",\"label\":\"项目地区\",\"type\":\"ADDRESS\",\"required\":false},{\"key\":\"publishDate\",\"label\":\"发布日期\",\"type\":\"DATE\",\"required\":false},{\"key\":\"deadline\",\"label\":\"截止日期\",\"type\":\"DATE\",\"required\":true},{\"key\":\"contactName\",\"label\":\"联系人\",\"type\":\"TEXT\",\"required\":false},{\"key\":\"contactPhone\",\"label\":\"联系电话\",\"type\":\"PHONE\",\"required\":false},{\"key\":\"description\",\"label\":\"标讯描述\",\"type\":\"TEXTAREA\",\"required\":false,\"rows\":4}]}"),
            new FormDefinitionSeed(
                    "project.basic",
                    "项目基本信息",
                    "{\"fields\":[{\"key\":\"name\",\"label\":\"项目名称\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"managerId\",\"label\":\"项目经理\",\"type\":\"PERSON\",\"required\":true},{\"key\":\"teamMembers\",\"label\":\"团队成员\",\"type\":\"PERSON\",\"required\":false},{\"key\":\"startDate\",\"label\":\"开始日期\",\"type\":\"DATE\",\"required\":false},{\"key\":\"endDate\",\"label\":\"结束日期\",\"type\":\"DATE\",\"required\":false},{\"key\":\"budget\",\"label\":\"项目预算\",\"type\":\"CURRENCY\",\"required\":false},{\"key\":\"industry\",\"label\":\"所属行业\",\"type\":\"SELECT\",\"required\":false,\"options\":[{\"label\":\"政府\",\"value\":\"government\"},{\"label\":\"央企\",\"value\":\"soe\"},{\"label\":\"民营\",\"value\":\"private\"}]},{\"key\":\"description\",\"label\":\"项目描述\",\"type\":\"TEXTAREA\",\"required\":false,\"rows\":4}]}"),
            new FormDefinitionSeed(
                    "resource.expense",
                    "费用申请",
                    "{\"fields\":[{\"key\":\"projectId\",\"label\":\"关联项目\",\"type\":\"PROJECT\",\"required\":true},{\"key\":\"category\",\"label\":\"费用类别\",\"type\":\"SELECT\",\"required\":true,\"options\":[{\"label\":\"差旅费\",\"value\":\"TRANSPORTATION\"},{\"label\":\"材料费\",\"value\":\"MATERIAL\"},{\"label\":\"人工费\",\"value\":\"LABOR\"},{\"label\":\"设备费\",\"value\":\"EQUIPMENT\"},{\"label\":\"分包费\",\"value\":\"SUBCONTRACTING\"},{\"label\":\"管理费\",\"value\":\"OVERHEAD\"},{\"label\":\"其他\",\"value\":\"OTHER\"}]},{\"key\":\"amount\",\"label\":\"金额\",\"type\":\"CURRENCY\",\"required\":true,\"validation\":{\"min\":0.01}},{\"key\":\"date\",\"label\":\"费用日期\",\"type\":\"DATE\",\"required\":true},{\"key\":\"expenseType\",\"label\":\"费用类型\",\"type\":\"TEXT\",\"required\":false},{\"key\":\"description\",\"label\":\"费用说明\",\"type\":\"TEXTAREA\",\"required\":false,\"rows\":3}]}"),
            new FormDefinitionSeed(
                    "knowledge.case",
                    "案例建档",
                    "{\"fields\":[{\"key\":\"title\",\"label\":\"案例标题\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"industry\",\"label\":\"所属行业\",\"type\":\"SELECT\",\"required\":false,\"options\":[{\"label\":\"政府\",\"value\":\"government\"},{\"label\":\"央企\",\"value\":\"soe\"},{\"label\":\"民营\",\"value\":\"private\"}]},{\"key\":\"amount\",\"label\":\"合同金额\",\"type\":\"CURRENCY\",\"required\":false},{\"key\":\"projectDate\",\"label\":\"完成日期\",\"type\":\"DATE\",\"required\":false},{\"key\":\"description\",\"label\":\"案例描述\",\"type\":\"TEXTAREA\",\"required\":false,\"rows\":4},{\"key\":\"tags\",\"label\":\"标签\",\"type\":\"TEXT\",\"required\":false,\"placeholder\":\"多个标签用逗号分隔\"}]}"),
            new FormDefinitionSeed(
                    "knowledge.qual",
                    "资质建档",
                    "{\"fields\":[{\"key\":\"name\",\"label\":\"资质名称\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"level\",\"label\":\"资质等级\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"agency\",\"label\":\"代理机构\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"agencyContact\",\"label\":\"代理机构联系人\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"certScope\",\"label\":\"认证范围\",\"type\":\"TEXTAREA\",\"required\":true,\"rows\":3},{\"key\":\"certificateNo\",\"label\":\"证书编号\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"issueDate\",\"label\":\"发证日期\",\"type\":\"DATE\",\"required\":true},{\"key\":\"expiryDate\",\"label\":\"有效期至\",\"type\":\"DATE\",\"required\":true}]}")
            );

    private record FormDefinitionSeed(String scope, String scopeLabel, String schemaJson) {
    }
}
