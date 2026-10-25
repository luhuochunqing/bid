package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicCaCertificateExtractorTest {

    @Test
    void extract_publicationDefinitionsAndMixedClauses_doNotOverrideBidPlatform() {
        for (String text : java.util.List.of(
                "公告发布于：中国招标投标公共服务平台（以下简称公告平台）。本项目通过：招商局集团电子招标采购交易网进行电子投标，使用CA数字证书。",
                "本项目采用招商局集团电子招标采购交易网进行电子投标，并在中国政府采购网发布公告。",
                "本项目采用招商局集团电子招标采购交易网进行电子投标并在中国政府采购网发布公告。")) {
            assertThat(DeterministicCaCertificateExtractor.extract(text).platformName())
                    .isEqualTo("招商局集团电子招标采购交易网");
        }
    }

    @Test
    void extract_definedNameAfterTimeClause_keepsOnlyPlatformName() {
        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(
                "依托“招商局集团电子招标采购交易网（或称交易平台）”使用CA数字证书。"
                        + "按投标人须知前附表规定的时间在招商局集团电子招标采购交易网（以下简称交易平台）递交。");
        assertThat(req.platformName()).isEqualTo("招商局集团电子招标采购交易网");
    }

    @Test
    void extract_productionDefinitionOfTradingWebsite_matchesLedgerAlias() {
        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(
                "以“招商局数字证书”为安全保障工具，依托“招商局集团电子招标采购交易网（或称交易平台）"
                        + "（网址：https://dzzb.ciesco.com.cn/）”实现网上投标、网上开标。"
                        + "招标文件购买时间详见招商局集团招标采购交易平台发布的招标公告。采用CA数字证书签名。");
        assertThat(req.platformName()).isEqualTo("招商局集团电子招标采购交易网");
        CaCertificateEvidence ca = new CaCertificateEvidence(65L, "ENTITY_CA", "公章", "CFCA",
                java.time.LocalDate.of(2027, 9, 8), "招商局集团电子招采平台", "IN_STOCK", "ACTIVE", "保管人", "持有人");
        assertThat(ca.matchesPlatform(req.platformName())).isTrue();
    }

    @Test
    void extract_operationalContextWinsOverPublicationWebsite() {
        for (String verb : java.util.List.of("采用", "指定", "通过")) {
            CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(
                    "公告同时发布于中国招标投标公共服务平台。本项目" + verb
                            + "招商局集团电子招标采购交易平台进行电子投标，须使用CA数字证书。");
            assertThat(req.platformName()).isEqualTo("招商局集团电子招标采购交易平台");
        }
    }

    @Test
    void extract_genericCaActions_haveNoConcretePlatform() {
        for (String text : java.util.List.of("本项目采用CA数字证书进行电子投标", "本项目须办理CA数字证书进行电子投标")) {
            assertThat(DeterministicCaCertificateExtractor.extract(text).platformName()).isNull();
        }
    }

    @Test
    void fuseAll_regionalCaFallback_preservesSourcePage() {
        DocumentContent doc = DocumentContent.fromText("tender.pdf", "hash", "本项目须使用河北CA数字证书进行电子投标。");
        CaCertificateRequirement req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), doc)
                .caCertificateRequirement();
        assertThat(CaCertificateCheckPolicy.check(DocumentContent.empty(), req, java.util.List.of()).evidences())
                .anySatisfy(e -> {
                    assertThat(e.pageNumber()).isEqualTo(1);
                    assertThat(e.quote()).contains("本项目须使用河北CA数字证书");
                });
    }

    @Test
    void extract_genericDefinitionBeforeActualPlatform_resolvesFullNameWithPageEvidence() {
        DocumentContent doc = new DocumentContent("tender.pdf", "hash", "", java.util.List.of(
                new DocumentContent.PageContent(2, "电子交易平台（或称交易平台）指本项目交易系统。", java.util.List.of()),
                new DocumentContent.PageContent(8, "本项目通过招商局集团电子招标采购交易平台（以下简称交易平台）进行电子投标，须使用CA数字证书。", java.util.List.of())));
        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(doc);
        assertThat(req.platformName()).isEqualTo("招商局集团电子招标采购交易平台");
        assertThat(CaCertificateCheckPolicy.check(DocumentContent.empty(), req, java.util.List.of()).evidences())
                .anySatisfy(e -> {
                    assertThat(e.pageNumber()).isEqualTo(8);
                    assertThat(e.quote()).contains("招商局集团电子招标采购交易平台（以下简称交易平台）");
                });
    }

    @Test
    void extract_genericPlatformOnly_requiresManualPlatformConfirmation() {
        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(
                "电子交易平台（或称交易平台）须使用CA数字证书进行电子投标。");
        assertThat(req.required()).isTrue();
        assertThat(req.platformName()).isNull();
    }

    @Test
    void extract_conflictingConcretePlatforms_doesNotChooseFirst() {
        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(
                "本项目通过浙江省公共资源交易平台进行电子投标。通过江苏省公共资源交易平台进行电子投标，使用CA数字证书。");
        assertThat(req.platformName()).isNull();
    }

    @Test
    void fuse_unresolvedDeterministicPlatform_doesNotRestoreLlmGuess() {
        CaCertificateRequirement deterministic = DeterministicCaCertificateExtractor.extract(
                "电子交易平台（或称交易平台）使用CA数字证书。");
        CaCertificateRequirement llm = CaCertificateRequirement.requiredStandard("或称交易平台", "模型提取");
        assertThat(llm.fuseWithDeterministic(deterministic).platformName()).isNull();
    }

    @Test
    @DisplayName("项目1117：纸质模板措辞不得覆盖明确电子投标与数字证书要求")
    void extract_paperTemplateAndExplicitElectronicEvidence_requiresCa() {
        String text = "纸质标书格式模板仅供参考。投标人须通过电子招投标交易平台上传加密投标文件，并使用CA数字证书签章。";

        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(text);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
    }

    @Test
    @DisplayName("历史无需CA模板不得覆盖当前明确CA数字证书要求")
    void extract_noCaTemplateAndExplicitCaRequirement_requiresCa() {
        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(
                "旧版纸质模板注明无需CA。当前项目采用电子投标，须使用CA数字证书加密并电子签章。");

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
    }

    @Test
    @DisplayName("网上开标与远程解密应覆盖纸质标模板字样")
    void extract_onlineOpeningAndRemoteDecryption_overridePaperTemplate() {
        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(
                "纸质标模板仅供格式参考。本项目网上开标，投标人须远程解密投标文件。");

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
    }

    @Test
    @DisplayName("提取器：识别纸质标免除信号时，输出 required=false")
    void testExtract_WhenPaperBid_ReturnsNotRequired() {
        String text = "本项目为纸质投标文件递交，投标人需携带纸质标书至开标现场，无需CA。";
        DocumentContent doc = DocumentContent.fromText("tender.pdf", "hash1", text);

        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(doc);

        assertThat(req).isNotNull();
        assertThat(req.required()).isFalse();
        assertThat(req.description()).contains("纸质标");
    }

    @Test
    @DisplayName("提取器：提取平台名称与 CA 证书要求")
    void testExtract_WhenElectronicPlatform_ExtractsPlatformAndFlags() {
        String text = """
                前附表：
                本项目采用全流程电子招投标，通过浙江省公共资源交易平台进行。
                投标人须办理 CA 数字证书，提供 CA 证书复印件及有效性证明，并加盖电子签章。
                """;
        DocumentContent doc = DocumentContent.fromText("tender.pdf", "hash2", text);

        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(doc);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.platformName()).contains("浙江省公共资源交易平台");
        assertThat(req.requireCertificateCopy()).isTrue();
        assertThat(req.requireElectronicSeal()).isTrue();
    }

    @Test
    @DisplayName("提取器：未提及任何 CA 或电子化内容时返回 null")
    void testExtract_WhenNoCaMentioned_ReturnsNull() {
        String text = "本项目采购一批普通办公用品，交付期限为 30 天。";
        DocumentContent doc = DocumentContent.fromText("tender.pdf", "hash3", text);

        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(doc);

        assertThat(req).isNull();
    }

    @Test
    @DisplayName("契约融合：确定性规则硬事实优先于大模型生成内容")
    void testRequirement_FuseWithDeterministic() {
        CaCertificateRequirement llm = new CaCertificateRequirement(true, null, false, false, "LLM提取描述");
        CaCertificateRequirement deterministic = new CaCertificateRequirement(true, "政采云", true, true, "确定性描述");

        CaCertificateRequirement fused = llm.fuseWithDeterministic(deterministic);

        assertThat(fused.platformName()).isEqualTo("政采云");
        assertThat(fused.requireCertificateCopy()).isTrue();
        assertThat(fused.requireElectronicSeal()).isTrue();
        assertThat(fused.description()).isEqualTo("确定性描述");
    }

    @Test
    @DisplayName("提取器：识别各省专用 CA 锁（如河北 CA、企业河北 CA）并提取为平台/CA名称")
    void testExtract_WhenHebeiCaSpecified_ExtractsHebeiCa() {
        String text = "编制投标文件需使用企业河北 CA，未办理河北 CA 的投标单位，需进行企业河北 CA 注册。CA 注册有一定周期，请及时办理以免影响本次项目。在线办理地址为：";
        DocumentContent doc = DocumentContent.fromText("tender.pdf", "hash_hebei", text);

        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(doc);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.platformName()).contains("河北");
        assertThat(req.platformName()).contains("CA");
    }
}
