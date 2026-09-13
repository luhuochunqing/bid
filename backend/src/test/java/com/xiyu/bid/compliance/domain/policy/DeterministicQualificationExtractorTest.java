// Input: 招标文件原文（含 1358 真实事故金标文本）
// Output: 验证确定性资格与资质要求提取（通用法人信誉 + 专用资质证书 + 占位符/业绩排除）
// Pos: compliance/domain/policy/ - 确定性资格提取纯核心测试（FP-Java 无外部依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 确定性资质与资格要求提取器金标测试（事故 1358 专项防护）.
 */
class DeterministicQualificationExtractorTest {

    private static final String TEXT_INCIDENT_1358 = """
            3.供应商资格要求
            3.1 通用资格条件
            3.1.1 供应商须为中华人民共和国境内合法注册的独立法人或其他组织，具
            有独立承担民事责任能力，具有独立订立合同的权利。
            3.1.2 供应商财务、信誉等方面应满足下列条件：
            （1）未处于被行政主管部门责令停产、停业或进入破产程序；
            （2）未处于行政主管部门或中国雅江集团内单位相关文件确认的禁止报价
            （报价）的范围和处罚期间内；
            （3）未在国家企业信用信息公示系统中被列入严重违法失信企业名单或者
            经营异常名录；
            （4）未在“信用中国”网站和中国执行信息公开网中被列入失信被执行人名
            单。
            （5）供应商未作为其他供应商的分包人同时参与本项目；单位负责人为同
            一人或者存在控股、 管理关系的不同单位， 不得参加同一标段或者未划分标段的
            同一采购项目报价（报价）。
            3.2 专用资格条件
            3.2.1 资质要求：供应商应具有以下证书：
            ①增值电信业务经营许可证， 业务种类及覆盖范围需包括信息服务业务 （互
            联网信息服务）及在线数据处理与交易业务（经营类电子商务）；
            ②公安部信息系统安全等级保护备案证明第三级或第三级以上证书。
            3.2.2 业绩要求： 2023 年 1 月 1 日至首次报价截止日 （以合同签订时间为准） ，
            具有不少于 1 个合格业绩。 合格业绩要求为： ①每个业绩包含的标的物须与本标
            段采购范围中的至少一个中类类别相关； ②每个业绩合同应至少提供 1 份单张金
            额不低于 20 万元（含税）的增值税发票扫描件，且发票内容须涵盖本标段采购
            范围中至少一个中类类别， 同时附上相应的发票真伪查验证明材料； ③须提供合
            同协议书等相关证明材料， 至少须包含合同名称、 采购范围、 合同买卖双方盖章
            页、合同签订日期等内容。
            3.2.3 人员要求：/
            3.2.4 财务要求：/
            3.2.5 其他要求：/
            3.2.6 采购人接受联合体报价，联合体成员须为同一法人代表企业或同一集
            团关联关系公司。
            3.2.7 采购人不接受代理商报价。
            4. 采购文件的获取
            """;

    @Test
    @DisplayName("金标 1358：必须提取出增值电信业务经营许可证与公安部等保三级证书")
    void extract_incident1358_extractsTelecomAndSecurityProtectionCerts() {
        List<String> quals = DeterministicQualificationExtractor.extract(TEXT_INCIDENT_1358);

        assertThat(quals).isNotEmpty();

        // 核心专用资质断言
        assertThat(quals).anyMatch(q -> q.contains("增值电信业务经营许可证")
                && q.contains("信息服务业务")
                && q.contains("在线数据处理与交易业务"));
        assertThat(quals).anyMatch(q -> q.contains("公安部信息系统安全等级保护备案证明")
                && (q.contains("第三级") || q.contains("三级")));

        // 方案 A 核心断言：通用资格（独立法人、民事责任、信用中国、失信被执行人）严禁作为资质证书提取，杜绝资质库错配
        assertThat(quals).hasSize(2);
        assertThat(quals).noneMatch(q -> q.contains("独立法人") || q.contains("民事责任") || q.contains("信用中国")
                || q.contains("严重违法失信") || q.contains("经营异常名录"));

        // 占位符与非资质要求排除断言（不得提取 /、人员要求：/ 等脏数据）
        assertThat(quals).noneMatch(q -> q.contains("人员要求") && q.contains("/"));
        assertThat(quals).noneMatch(q -> q.contains("财务要求") && q.contains("/"));
        assertThat(quals).noneMatch(q -> q.contains("其他要求") && q.contains("/"));
        assertThat(quals).noneMatch(q -> q.equals("/") || q.equals("无") || q.isBlank());

        // 导语与章节标题排除断言（彻底排除 3.2.1 资质要求：供应商应具有以下证书 等导语）
        assertThat(quals).noneMatch(q -> q.contains("应具有以下证书") || q.contains("满足下列条件") || q.contains("专用资格条件"));
        assertThat(quals).noneMatch(q -> q.startsWith(";") || q.startsWith("；"));
    }

    @Test
    @DisplayName("金标 1358 通用资格条件提取：成功提取独立法人与信用合规，不混淆专用资质证书")
    void extractGeneralCapacities_incident1358_extractsGeneralConditionsCleanly() {
        List<String> generals = DeterministicQualificationExtractor.extractGeneralCapacities(TEXT_INCIDENT_1358);

        assertThat(generals).isNotEmpty();
        assertThat(generals).anyMatch(q -> q.contains("独立法人") && q.contains("民事责任能力"));
        assertThat(generals).anyMatch(q -> q.contains("信用中国") && q.contains("失信被执行人"));
        assertThat(generals).anyMatch(q -> q.contains("严重违法失信企业名单") || q.contains("经营异常名录"));

        // 专用资质证书严禁混入通用资格列表
        assertThat(generals).noneMatch(q -> q.contains("增值电信业务经营许可证"));
        assertThat(generals).noneMatch(q -> q.contains("等级保护备案证明"));
    }

    @Test
    @DisplayName("条款属性智能分类：精准识别专用行业资质 vs 通用主体资格条件")
    void isSpecificCertificate_and_isGeneralCapacity_classification() {
        // 专用资质证书示例
        assertThat(DeterministicQualificationExtractor.isSpecificCertificate("增值电信业务经营许可证")).isTrue();
        assertThat(DeterministicQualificationExtractor.isGeneralCapacity("增值电信业务经营许可证")).isFalse();

        assertThat(DeterministicQualificationExtractor.isSpecificCertificate("公安部信息系统安全等级保护备案证明第三级")).isTrue();
        assertThat(DeterministicQualificationExtractor.isGeneralCapacity("公安部信息系统安全等级保护备案证明第三级")).isFalse();

        assertThat(DeterministicQualificationExtractor.isSpecificCertificate("建筑工程施工总承包一级资质证书")).isTrue();
        assertThat(DeterministicQualificationExtractor.isGeneralCapacity("建筑工程施工总承包一级资质证书")).isFalse();

        assertThat(DeterministicQualificationExtractor.isSpecificCertificate("ISO9001 质量管理体系认证证书")).isTrue();
        assertThat(DeterministicQualificationExtractor.isGeneralCapacity("ISO9001 质量管理体系认证证书")).isFalse();

        // 通用主体资格条件示例
        String legalEntity = "1.1 供应商须为中华人民共和国境内合法注册的独立法人或其他组织，具有独立承担民事责任能力，具有独立订立合同的权利";
        assertThat(DeterministicQualificationExtractor.isSpecificCertificate(legalEntity)).isFalse();
        assertThat(DeterministicQualificationExtractor.isGeneralCapacity(legalEntity)).isTrue();

        String creditChina = "未在“信用中国”网站和中国执行信息公开网中被列入失信被执行人名单";
        assertThat(DeterministicQualificationExtractor.isSpecificCertificate(creditChina)).isFalse();
        assertThat(DeterministicQualificationExtractor.isGeneralCapacity(creditChina)).isTrue();

        String businessLicenseOnly = "供应商须具有独立法人资格，并提供有效营业执照复印件";
        assertThat(DeterministicQualificationExtractor.isSpecificCertificate(businessLicenseOnly)).isFalse();
        assertThat(DeterministicQualificationExtractor.isGeneralCapacity(businessLicenseOnly)).isTrue();
    }

    @Test
    @DisplayName("导语与章节标题过滤：彻底排除如 2.1 资质要求：供应商应具有以下证书 等纯引导语")
    void extract_introductoryHeadings_filteredOut() {
        String text = """
                3. 供应商资格要求
                3.1 通用资格条件
                3.1.1 供应商财务、信誉等方面应满足下列条件：
                （1）未在“信用中国”网站中被列入失信被执行人名单；
                3.2 专用资格条件
                2.1 资质要求：供应商应具有以下证书：
                ① 增值电信业务经营许可证；
                """;
        List<String> quals = DeterministicQualificationExtractor.extract(text);
        assertThat(quals).noneMatch(DeterministicQualificationExtractor::isIntroductoryHeading);
        assertThat(quals).noneMatch(q -> q.contains("应具有以下证书") || q.contains("资质要求：供应商应具有以下证书"));
        assertThat(quals).noneMatch(q -> q.contains("满足下列条件"));
        assertThat(quals).anyMatch(q -> q.contains("增值电信业务经营许可证"));
    }

    @Test
    @DisplayName("建筑工程标书：提取建筑施工总承包与安全生产许可证")
    void extract_constructionTender_extractsContractingAndSafetyPermit() {
        String tenderText = """
                第二章 投标人须知
                3.1 投标人资格要求
                1. 具备建设行政主管部门颁发的建筑工程施工总承包一级及以上资质；
                2. 具备有效的安全生产许可证；
                3. 具备 ISO9001 质量管理体系认证证书；
                4. 财务要求：/;
                4. 招标文件的获取
                """;

        List<String> quals = DeterministicQualificationExtractor.extract(tenderText);

        assertThat(quals).hasSize(3);
        assertThat(quals).anyMatch(q -> q.contains("建筑工程施工总承包一级"));
        assertThat(quals).anyMatch(q -> q.contains("安全生产许可证"));
        assertThat(quals).anyMatch(q -> q.contains("ISO9001"));
    }

    @Test
    @DisplayName("单句资质要求：无多级标题时仍能定位并提取")
    void extract_singleSentence_extractsDirectly() {
        String tenderText = "本项目资格要求：供应商必须具备有效的计算机信息系统集成二级资质及 ISO27001 认证证书。";

        List<String> quals = DeterministicQualificationExtractor.extract(tenderText);

        assertThat(quals).isNotEmpty();
        assertThat(quals).anyMatch(q -> q.contains("计算机信息系统集成二级资质") || q.contains("ISO27001"));
    }

    @Test
    @DisplayName("空文本与无资质文本：安全返回空列表，不抛异常")
    void extract_emptyOrBlank_returnsEmptyList() {
        assertThat(DeterministicQualificationExtractor.extract(null)).isEmpty();
        assertThat(DeterministicQualificationExtractor.extract("")).isEmpty();
        assertThat(DeterministicQualificationExtractor.extract("  \n\t  ")).isEmpty();
        assertThat(DeterministicQualificationExtractor.extract("本项目采购办公用纸 500 箱，请于周五前送达。")).isEmpty();
    }

    @Test
    @DisplayName("金标 1359/1377 防护：严禁将评分细则、超长文本（>100字）及应答模板误提取为硬性资质")
    void extract_incident1377_doesNotExtractScoringRulesOrTemplates() throws Exception {
        java.nio.file.Path sidecarPath = java.nio.file.Path.of("src/test/resources/scoreparse-golden/1359_sidecar.md");
        if (!java.nio.file.Files.exists(sidecarPath)) {
            sidecarPath = java.nio.file.Path.of("backend/src/test/resources/scoreparse-golden/1359_sidecar.md");
        }
        String tenderText = java.nio.file.Files.readString(sidecarPath);

        List<String> quals = DeterministicQualificationExtractor.extract(tenderText);

        // 核心防线 1：任何提取出的资质长度绝对不得超过 100 字（杜绝粘连巨型评分标准）
        assertThat(quals).allSatisfy(q -> assertThat(q.length()).as("资质名称不应超长: %s", q).isLessThanOrEqualTo(100));

        // 核心防线 2：绝对不得提取前附表履约提示与评分细则粘连段落
        assertThat(quals).noneMatch(q -> q.contains("紧急需求应急响应专项方案")
                || q.contains("商务评审细则")
                || q.contains("框架采购供货履约承诺函")
                || q.contains("是否应急响应小类"));

        // 核心防线 3：绝对不得提取第五章应答模板（有/无、填写认证名称、响应说明）
        assertThat(quals).noneMatch(q -> q.contains("填写认证名称")
                || q.contains("企业绿码国家级认证：有")
                || q.contains("绿色工厂国家级认证：有")
                || q.contains("响应说明："));
    }

    @Test
    @DisplayName("正向提取防误杀：正规资质条款包含认证机构、有效期限或佐证材料等常用词时必须正常提取")
    void extract_legitimateQualificationWithCommonWords_isNotFalseExcluded() {
        String text = """
                3.2 专用资格条件
                （1）投标人须具备由经认证认可监督管理委员会认可的认证机构颁发的ISO9001质量管理体系认证证书，且证书须在有效期限内；
                （2）投标人须具备建筑工程施工总承包一级资质，并提供加盖公章的证书扫描件作为佐证材料。
                """;
        List<String> quals = DeterministicQualificationExtractor.extract(text);
        assertThat(quals).hasSize(2);
        assertThat(quals).anyMatch(q -> q.contains("ISO9001") && q.contains("认证机构"));
        assertThat(quals).anyMatch(q -> q.contains("总承包") && q.contains("佐证材料"));
    }
}
