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
 * 与 Flyway 迁移保持一致（额外补充测试需要的 {@code knowledge.qual} scope）。
 * 注：{@code knowledge.case} 和 {@code resource.expense} 已随 V1182 作为废弃定义清理
 *（前者后端 handleCase 永远返回 failure、无前端提交入口；后者前端侧边栏菜单无入口、
 * 工作台快捷入口被 dynamicLayout=null 门控永远不渲染、费用页面走独立 REST），
 * 故本 seeder 不再预置这两个 scope。</p>
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
                    "{ \"fields\": [ {\"key\": \"title\", \"label\": \"项目名称\", \"type\": \"TEXT\", \"required\": true, \"placeholder\": \"请输入项目名称\", \"maxLength\": 200}, {\"key\": \"purchaser\", \"label\": \"招标主体\", \"type\": \"TEXT\", \"required\": true, \"placeholder\": \"请输入招标主体\", \"maxLength\": 200}, {\"key\": \"region\", \"label\": \"总部所在地\", \"type\": \"SELECT\", \"required\": true, \"options\": [ {\"label\": \"北京\", \"value\": \"北京\"}, {\"label\": \"天津\", \"value\": \"天津\"}, {\"label\": \"河北\", \"value\": \"河北\"}, {\"label\": \"山西\", \"value\": \"山西\"}, {\"label\": \"内蒙古\", \"value\": \"内蒙古\"}, {\"label\": \"辽宁\", \"value\": \"辽宁\"}, {\"label\": \"吉林\", \"value\": \"吉林\"}, {\"label\": \"黑龙江\", \"value\": \"黑龙江\"}, {\"label\": \"上海\", \"value\": \"上海\"}, {\"label\": \"江苏\", \"value\": \"江苏\"}, {\"label\": \"浙江\", \"value\": \"浙江\"}, {\"label\": \"安徽\", \"value\": \"安徽\"}, {\"label\": \"福建\", \"value\": \"福建\"}, {\"label\": \"江西\", \"value\": \"江西\"}, {\"label\": \"山东\", \"value\": \"山东\"}, {\"label\": \"河南\", \"value\": \"河南\"}, {\"label\": \"湖北\", \"value\": \"湖北\"}, {\"label\": \"湖南\", \"value\": \"湖南\"}, {\"label\": \"广东\", \"value\": \"广东\"}, {\"label\": \"广西\", \"value\": \"广西\"}, {\"label\": \"海南\", \"value\": \"海南\"}, {\"label\": \"重庆\", \"value\": \"重庆\"}, {\"label\": \"四川\", \"value\": \"四川\"}, {\"label\": \"贵州\", \"value\": \"贵州\"}, {\"label\": \"云南\", \"value\": \"云南\"}, {\"label\": \"西藏\", \"value\": \"西藏\"}, {\"label\": \"陕西\", \"value\": \"陕西\"}, {\"label\": \"甘肃\", \"value\": \"甘肃\"}, {\"label\": \"青海\", \"value\": \"青海\"}, {\"label\": \"宁夏\", \"value\": \"宁夏\"}, {\"label\": \"新疆\", \"value\": \"新疆\"}, {\"label\": \"台湾\", \"value\": \"台湾\"}, {\"label\": \"香港\", \"value\": \"香港\"}, {\"label\": \"澳门\", \"value\": \"澳门\"} ]}, {\"key\": \"deadline\", \"label\": \"报名截止时间\", \"type\": \"DATETIME\", \"required\": true}, {\"key\": \"bidOpeningTime\", \"label\": \"开标时间\", \"type\": \"DATETIME\", \"required\": true}, {\"key\": \"customerType\", \"label\": \"客户类型\", \"type\": \"SELECT\", \"required\": true, \"options\": [ {\"label\": \"政府机关/事业单位/高校\", \"value\": \"政府机关/事业单位/高校\"}, {\"label\": \"央企\", \"value\": \"央企\"}, {\"label\": \"地方国企\", \"value\": \"地方国企\"}, {\"label\": \"民企\", \"value\": \"民企\"}, {\"label\": \"港澳台及外企\", \"value\": \"港澳台及外企\"} ]}, {\"key\": \"priority\", \"label\": \"优先级\", \"type\": \"SELECT\", \"required\": true, \"options\": [ {\"label\": \"S 级 · 战略级高价值客户\", \"value\": \"S\"}, {\"label\": \"A 级 · 高价值重点客户\", \"value\": \"A\"}, {\"label\": \"B 级 · 重要潜力客户\", \"value\": \"B\"}, {\"label\": \"C 级 · 潜力客户\", \"value\": \"C\"} ]}, {\"key\": \"projectType\", \"label\": \"项目类型\", \"type\": \"SELECT\", \"required\": true, \"options\": [ {\"label\": \"工业品\", \"value\": \"工业品\"}, {\"label\": \"办公\", \"value\": \"办公\"}, {\"label\": \"综合\", \"value\": \"综合\"}, {\"label\": \"集采\", \"value\": \"集采\"}, {\"label\": \"其他\", \"value\": \"其他\"} ]}, {\"key\": \"contact\", \"label\": \"联系人1\", \"type\": \"TEXT\", \"required\": false, \"placeholder\": \"联系人姓名\"}, {\"key\": \"phone\", \"label\": \"联系人1手机号\", \"type\": \"TEXT\", \"required\": false, \"placeholder\": \"手机号\"}, {\"key\": \"landline\", \"label\": \"联系人1座机\", \"type\": \"TEXT\", \"required\": false, \"placeholder\": \"座机（如 010-12345678）\"}, {\"key\": \"mail\", \"label\": \"联系人1邮箱\", \"type\": \"TEXT\", \"required\": false, \"placeholder\": \"邮箱\"}, {\"key\": \"contact2\", \"label\": \"联系人2\", \"type\": \"TEXT\", \"required\": false, \"placeholder\": \"联系人姓名（选填）\"}, {\"key\": \"phone2\", \"label\": \"联系人2手机号\", \"type\": \"TEXT\", \"required\": false, \"placeholder\": \"手机号（选填）\"}, {\"key\": \"landline2\", \"label\": \"联系人2座机\", \"type\": \"TEXT\", \"required\": false, \"placeholder\": \"座机（选填）\"}, {\"key\": \"mail2\", \"label\": \"联系人2邮箱\", \"type\": \"TEXT\", \"required\": false, \"placeholder\": \"邮箱（选填）\"}, {\"key\": \"description\", \"label\": \"标讯描述\", \"type\": \"TEXTAREA\", \"required\": false, \"rows\": 3, \"maxLength\": 5000}, {\"key\": \"tenderInfo\", \"label\": \"标讯信息\", \"type\": \"TEXTAREA\", \"required\": false, \"rows\": 3, \"maxLength\": 20000} ] }"),
            new FormDefinitionSeed(
                    "project.basic",
                    "项目基本信息",
                    "{\"fields\":[{\"key\":\"name\",\"label\":\"项目名称\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"managerId\",\"label\":\"项目经理\",\"type\":\"PERSON\",\"required\":true},{\"key\":\"teamMembers\",\"label\":\"团队成员\",\"type\":\"PERSON\",\"required\":false},{\"key\":\"startDate\",\"label\":\"开始日期\",\"type\":\"DATE\",\"required\":false},{\"key\":\"endDate\",\"label\":\"结束日期\",\"type\":\"DATE\",\"required\":false},{\"key\":\"budget\",\"label\":\"项目预算\",\"type\":\"CURRENCY\",\"required\":false},{\"key\":\"industry\",\"label\":\"所属行业\",\"type\":\"SELECT\",\"required\":false,\"options\":[{\"label\":\"政府\",\"value\":\"government\"},{\"label\":\"央企\",\"value\":\"soe\"},{\"label\":\"民营\",\"value\":\"private\"}]},{\"key\":\"description\",\"label\":\"项目描述\",\"type\":\"TEXTAREA\",\"required\":false,\"rows\":4}]}"),
            new FormDefinitionSeed(
                    "knowledge.qual",
                    "资质建档",
                    "{\"fields\":[{\"key\":\"name\",\"label\":\"资质名称\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"level\",\"label\":\"资质等级\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"agency\",\"label\":\"代理机构\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"agencyContact\",\"label\":\"代理机构联系人\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"certScope\",\"label\":\"认证范围\",\"type\":\"TEXTAREA\",\"required\":true,\"rows\":3},{\"key\":\"certificateNo\",\"label\":\"证书编号\",\"type\":\"TEXT\",\"required\":true},{\"key\":\"issueDate\",\"label\":\"发证日期\",\"type\":\"DATE\",\"required\":true},{\"key\":\"expiryDate\",\"label\":\"有效期至\",\"type\":\"DATE\",\"required\":true}]}")
            );

    private record FormDefinitionSeed(String scope, String scopeLabel, String schemaJson) {
    }
}
