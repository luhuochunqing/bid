// Input: ArchUnit rules + ScoreFact persistence contracts
// Output: Architecture guardrails for scoreparse module (spec 059 Phase 6)
// Pos: scoreparse/architecture — 评分架构门禁测试

package com.xiyu.bid.scoreparse.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.xiyu.bid.scoreparse.application.ScoreItemPersistenceService;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 评分标准解析架构门禁测试（spec 059 Phase 6）。
 * <ol>
 *   <li>scoreparse.domain 绝不依赖 Spring / HTTP / JPA 等框架技术栈（FP-Java 纯核心）</li>
 *   <li>LLM / infrastructure 层不得构造 ScoreFact，仅允许确定性抽取与纯领域组装</li>
 *   <li>持久化服务坚决拒绝无证据的自动通过项</li>
 * </ol>
 */
class ScoreParseArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.xiyu.bid.scoreparse");

    @Test
    @DisplayName("架构规则 1: scoreparse.domain 不得依赖 Spring / HTTP / JPA")
    void scoreparseDomain_mustNotDependOnSpringHttpJpa() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..scoreparse.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "javax.persistence..",
                        "jakarta.servlet..",
                        "javax.servlet..",
                        "org.apache.http..",
                        "java.net.http.."
                ).because("scoreparse.domain 必须是零框架依赖的纯函数式核心 (FP-Java)");

        rule.check(classes);
    }

    @Test
    @DisplayName("架构规则 2: LLM 与 infrastructure 层不得构造 ScoreFact")
    void llmAndInfrastructure_mustNotConstructScoreFact() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..scoreparse.infrastructure..", "..openai..")
                .or().haveSimpleNameContaining("OpenAi")
                .or().haveSimpleNameContaining("Llm")
                .should().callConstructorWhere(new DescribedPredicate<>("target is ScoreFact constructor") {
                    @Override
                    public boolean test(JavaConstructorCall call) {
                        return call.getTarget().getOwner().isEquivalentTo(ScoreFact.class);
                    }
                }).because("ScoreFact 必须由确定性抽取通道或领域模型生成，禁止 LLM 直接合成虚假事实");

        rule.check(classes);
    }

    @Test
    @DisplayName("架构规则 3: 持久化服务拒绝无证据自动通过项")
    void persistenceService_mustRejectEvidenceLessAutoApprovedItems() {
        ScoreParseFactRepository factRepo = mock(ScoreParseFactRepository.class);
        ScoreParseEvidenceRepository evRepo = mock(ScoreParseEvidenceRepository.class);
        ScoreItemPersistenceService service = new ScoreItemPersistenceService(null, null, factRepo, evRepo);

        ScoreFact evidencelessAutoApprovedFact = new ScoreFact(
                "fact-invalid",
                "tbl-1",
                List.of(), // 无单元格引用
                "无证据项",
                "1.1",
                "技术方案",
                BigDecimal.TEN,
                null, // 无 weightEvidence
                null,
                null,
                "LLM_GUESS",
                0.99,
                ScoreFact.ValidationStatus.AUTO_APPROVED
        );

        assertThatThrownBy(() -> service.persistFactsAndEvidence(101L, 202L, List.of(evidencelessAutoApprovedFact)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("持久化服务拒绝无证据自动通过项");
    }
}
