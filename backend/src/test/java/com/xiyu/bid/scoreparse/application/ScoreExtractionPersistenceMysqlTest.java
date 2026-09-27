package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.WeightNormalizationPolicy;
import com.xiyu.bid.scoreparse.domain.KnowledgeCategoryPolicy;
import com.xiyu.bid.scoreparse.domain.AutoFailCircuit;
import com.xiyu.bid.scoreparse.domain.ScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreAssessmentGuard;
import com.xiyu.bid.scoreparse.domain.ScoreItemChapterMatch;
import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreTableSectionPolicy;
import com.xiyu.bid.scoreparse.domain.StructuredTableCandidateExtractor;
import com.xiyu.bid.scoreparse.domain.SourceSpan;
import com.xiyu.bid.scoreparse.domain.ScoreParseConstants;
import com.xiyu.bid.scoreparse.domain.ScoreStatusPolicy;
import com.xiyu.bid.scoreparse.domain.ModelSourceClaim;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.ScoreVersionFingerprint;
import com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy;
import com.xiyu.bid.scoreparse.domain.MatchTierPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreExtractionSnapshot;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.BidChapterDirtySet;
import com.xiyu.bid.scoreparse.domain.ScoreParsePipeline;
import com.xiyu.bid.scoreparse.domain.ScoreVocabularyRegistry;
import com.xiyu.bid.scoreparse.domain.ScoreItemTitleExtractor;
import com.xiyu.bid.scoreparse.domain.ScoreTableRecallLane;
import com.xiyu.bid.scoreparse.domain.EvidenceText;
import com.xiyu.bid.scoreparse.domain.ScoreRangeGuard;
import com.xiyu.bid.scoreparse.domain.ScoreTableBlockLocator;
import com.xiyu.bid.scoreparse.domain.ScoreUnitPolicy;
import com.xiyu.bid.scoreparse.domain.AutoParseGate;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.TableSplitResult;
import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import com.xiyu.bid.scoreparse.domain.AiProviderDiagnostics;
import com.xiyu.bid.scoreparse.domain.ScoreItemOrderPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreCodeSanitizer;
import com.xiyu.bid.scoreparse.domain.TableCompleteness;
import com.xiyu.bid.scoreparse.domain.SourceAnchorCatalog;
import com.xiyu.bid.scoreparse.domain.ScoreCompletenessPolicy;
import com.xiyu.bid.scoreparse.domain.DocumentImage;
import com.xiyu.bid.scoreparse.domain.ScoreTableSummaryPolicy;
import com.xiyu.bid.scoreparse.domain.DeterministicScoreFactExtractor;
import com.xiyu.bid.scoreparse.domain.MultimodalFactParentCodeNormalizer;
import com.xiyu.bid.scoreparse.domain.UnknownDeclaredTotalReviewPolicy;
import com.xiyu.bid.scoreparse.domain.ItemCountCheck;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction;
import com.xiyu.bid.scoreparse.domain.OriginalRuleUnitText;
import com.xiyu.bid.scoreparse.domain.ExtractedScoreItem;
import com.xiyu.bid.scoreparse.domain.ScoreSourceMetadata;
import com.xiyu.bid.scoreparse.domain.SummaryAggregator;
import com.xiyu.bid.scoreparse.domain.ScoreTableSplitPolicy;
import com.xiyu.bid.scoreparse.domain.PartialScorePolicy;
import com.xiyu.bid.scoreparse.domain.SourceRecheckPlan;
import com.xiyu.bid.scoreparse.domain.ScoreMergeResultGuard;
import com.xiyu.bid.scoreparse.domain.ScoreTypeClassificationPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreTableRowParser;
import com.xiyu.bid.scoreparse.domain.ScoreOriginalTextAligner;
import com.xiyu.bid.scoreparse.domain.ImageBudget;
import com.xiyu.bid.scoreparse.domain.WeightOverloadTrimPolicy;
import com.xiyu.bid.scoreparse.domain.GhostCandidateFilter;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.ScoreItemMergePolicy;
import com.xiyu.bid.scoreparse.domain.SourceSpanResolver;
import com.xiyu.bid.scoreparse.domain.ScoreParseReviewSnapshot;
import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.BidScoreSkipPolicy;
import com.xiyu.bid.scoreparse.domain.ScoreRule;
import com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration;
import com.xiyu.bid.scoreparse.domain.ProjectEvidenceRequirementPolicy;
import com.xiyu.bid.scoreparse.domain.LogicalRuleSourceUnit;
import com.xiyu.bid.scoreparse.domain.DeductionItemPolicy;
import com.xiyu.bid.scoreparse.domain.ImageAssignmentPlan;
import com.xiyu.bid.scoreparse.dto.ActivateScoreExtractionCommand;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.entity.ScoreActiveVersion;
import com.xiyu.bid.scoreparse.entity.ScoreExtractionRecord;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreParseEvidence;
import com.xiyu.bid.scoreparse.entity.ScoreParseReview;
import com.xiyu.bid.scoreparse.entity.ScoreParseFact;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreActiveVersionRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import com.xiyu.bid.scoreparse.repository.ScoreExtractionRecordRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseReviewRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.test.AbstractTransactionBoundaryTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(classes=ScoreExtractionPersistenceMysqlTest.Config.class,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","app.score-parse.extraction-version.enabled=true","spring.jpa.open-in-view=false"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation=Propagation.NEVER)
class ScoreExtractionPersistenceMysqlTest extends AbstractTransactionBoundaryTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("extraction_app");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Configuration @EnableAutoConfiguration @EntityScan(basePackageClasses=ScoreParseTask.class)
    @EnableJpaRepositories(basePackageClasses=ScoreParseTaskRepository.class)
    @Import({ScoreExtractionSnapshotWriter.class,ScoreVersionActivationService.class,ScoreExtractionCodec.class,ScoreExtractionVersionFeature.class,
            ScoreExtractionAppService.class,ScoreParseTaskStateService.class,ScoreParseProgressService.class,VersionedScoreParseCompletion.class,ScoreExtractionTaskFinalizer.class,ScoreItemPersistenceService.class})
    static class Config { @Bean ObjectMapper objectMapper(){return new ObjectMapper().findAndRegisterModules();} }
    @MockBean ProjectAccessScopeService access;
    @Autowired ScoreParseTaskRepository tasks;
    @Autowired ScoreParseFactRepository facts;
    @Autowired ScoreItemRepository items;
    @Autowired ScoreResultRepository results;
    @Autowired ScoreParseProgressService progress;
    @Autowired ScoreParseEvidenceRepository evidence;
    @Autowired ScoreExtractionCodec codec;
    @Autowired ObjectMapper mapper;
    @Autowired ScoreActiveVersionRepository versions;
    @Autowired ScoreExtractionRecordRepository snapshots;
    @Autowired ScoreExtractionSnapshotWriter writer;
    @Autowired ScoreVersionActivationService activation;
    @Autowired ScoreExtractionAppService reads;
    @Autowired ScoreParseTaskStateService state;
    @Autowired VersionedScoreParseCompletion completion;
    @Autowired ScoreItemPersistenceService legacy;
    @Autowired JdbcTemplate jdbc;
    long project;
    @BeforeEach void setup(){reset(access);jdbc.execute("CREATE TABLE IF NOT EXISTS projects(id BIGINT PRIMARY KEY)");project=System.nanoTime();jdbc.update("INSERT INTO projects(id) VALUES(?)",project);}

    @Test void realCompletionFirstParseKeepsAllFactsAndActivatesUnverifiedComputableUnits() {
        var t=task();var snapshot=snapshot(t,2,true);
        completion.structured(t,StructuredTableResult.evidencePipeline(snapshot.items().stream().map(ExtractedScoreItem::fact).toList(),List.of(),null,List.of(),List.of(),snapshot.items().stream().map(ExtractedScoreItem::qualification).toList(),null));
        assertThat(tasks.findById(t.getId()).orElseThrow().getStatus()).isEqualTo("COMPLETED");
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).hasSize(2).allSatisfy(i->{assertThat(i.getWeight()).isNotNull();assertThat(i.getValidationStatus()).isEqualTo("REVIEW_REQUIRED");assertThat(i.getSourceFactId()).isNotBlank();});
        assertThat(facts.findByParseTaskId(t.getId())).hasSize(3).anySatisfy(f->{assertThat(f.getWeight()).isNull();assertThat(f.getItemPayload()).contains("rawMaximum");});
        assertThat(reads.getLatest(project).adoption().status()).isEqualTo("PARTIAL");
    }
    @Test void incompleteUnknownAndManualChangesProtectActiveProjection() {
        var first=frozen(2,false);assertThat(activation.tryAutoActivate(project,first.getTaskId())).isTrue();
        String original=activation.currentToken(project).fingerprint();
        var partial=frozen(1,true);assertThat(activation.tryAutoActivate(project,partial.getTaskId())).isFalse();
        assertThat(activation.currentToken(project).fingerprint()).isEqualTo(original);
        var edited=items.findByProjectIdOrderByItemIndexAsc(project).get(0);edited.setDetail("人工修改后的规则");items.saveAndFlush(edited);
        var full=frozen(2,false);assertThat(activation.currentToken(project).hasManualEdits()).isTrue();
        assertThat(activation.tryAutoActivate(project,full.getTaskId())).isFalse();
    }
    @Test void completeReparseAutoReplacesButOldAsynchronousTaskCannotPublish() {
        var first=frozen(2,false);assertThat(activation.tryAutoActivate(project,first.getTaskId())).isTrue();
        var full=frozen(2,false);assertThat(activation.tryAutoActivate(project,full.getTaskId())).isTrue();
        var old=frozen(2,false);var newer=task();
        assertThat(activation.tryAutoActivate(project,old.getTaskId())).isFalse();
        assertThat(reads.getLatest(project).taskId()).isEqualTo(newer.getTaskId());
    }
    @Test void historicalManualPartialReplacementPreservesQualityAndUnselectedSnapshot() {
        var old=frozen(2,true);var newer=task();
        var all=reads.getByTaskId(project,old.getTaskId()).items();
        var id=all.get(0).itemId();
        activation.activate(project,old.getTaskId(),new ActivateScoreExtractionCommand("s","empty:v1","确认",List.of(id)),"reviewer");
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).hasSize(1);
        assertThat(reads.getByTaskId(project,old.getTaskId()).items()).hasSize(3);
        assertThat(reads.getByTaskId(project,old.getTaskId()).validationStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(reads.getLatest(project).taskId()).isEqualTo(newer.getTaskId());
        assertThatThrownBy(()->activation.activate(project,old.getTaskId(),new ActivateScoreExtractionCommand("s",activation.currentToken(project).fingerprint(),"确认",List.of(all.get(2).itemId())),"r"))
                .isInstanceOfSatisfying(ScoreExtractionException.class,e->assertThat(e.status()).isEqualTo(400));
    }
    @Test void twoConcurrentCasActivationsHaveOneWinner() throws Exception {
        var t=frozen(2,false);var command=new ActivateScoreExtractionCommand("s","empty:v1","确认",null);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> operation=()->{start.await();try{activation.activate(project,t.getTaskId(),command,"r");return 200;}catch(ScoreExtractionException e){return e.status();}};
            var a=pool.submit(operation);var b=pool.submit(operation);start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).hasSize(2);
        assertThat(versions.findAll().stream().filter(v->v.getProjectId().equals(project))).hasSize(1);
    }
    @Test void insertionFailureRollsBackVersionDeleteAndNewItems() {
        var first=frozen(2,false);activation.tryAutoActivate(project,first.getTaskId());String before=activation.currentToken(project).fingerprint();
        var second=frozen(2,false);
        jdbc.execute("ALTER TABLE score_item ADD CONSTRAINT task2_reject_projection CHECK (active_version_id <= "+activation.currentToken(project).versionId()+")");
        try {assertThatThrownBy(()->activation.activate(project,second.getTaskId(),new ActivateScoreExtractionCommand("s",before,"确认",null),"r")).isInstanceOf(RuntimeException.class);}
        finally {jdbc.execute("ALTER TABLE score_item DROP CHECK task2_reject_projection");}
        assertThat(activation.currentToken(project).fingerprint()).isEqualTo(before);
        assertThat(versions.findAll().stream().filter(v->v.getProjectId().equals(project))).hasSize(1);
    }
    @Test void crossProjectPermissionsFeatureOffAndLegacyWritesDoNotCorruptVersions() {
        var t=frozen(2,false);activation.tryAutoActivate(project,t.getTaskId());
        assertThatThrownBy(()->reads.getByTaskId(project+1,t.getTaskId())).isInstanceOfSatisfying(ScoreExtractionException.class,e->assertThat(e.status()).isEqualTo(404));
        doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(access).assertCurrentUserCanAccessProject(project);
        assertThatThrownBy(()->reads.getByTaskId(project,t.getTaskId())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        reset(access);
        assertThatThrownBy(()->legacy.persistItems(project,t.getId(),List.of())).isInstanceOfSatisfying(ScoreExtractionException.class,e->assertThat(e.status()).isEqualTo(409));
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).hasSize(2);
    }
    @Test void legacyReadDoesNotInventPayloadAndFailurePreservesStagedCandidates() {
        var t=tasks.saveAndFlush(ScoreParseTask.builder().taskId(UUID.randomUUID().toString()).projectId(project).taskType("PARSE").status("FAILED").progress(0).timeoutMarked(false).build());
        facts.saveAndFlush(ScoreParseFact.builder().projectId(project).parseTaskId(t.getId()).factId("old").sourceText("原文").dimension("旧项").weight(BigDecimal.ZERO).extractionMethod("OLD").build());
        assertThat(reads.getByTaskId(project,t.getTaskId()).payloadVersion()).isZero();
        assertThat(facts.findByParseTaskId(t.getId()).get(0).getPayloadVersion()).isNull();
        var staged=task();writer.stage(project,staged.getId(),snapshot(staged,2,true));writer.freezeFailure(staged.getTaskId(),"FAILURE");
        assertThat(reads.getByTaskId(project,staged.getTaskId()).items()).hasSize(3);
        assertThat(reads.getByTaskId(project,staged.getTaskId()).sourceDiagnostics()).contains("FAILURE");
    }
    @Test void invalidRequestsFailWithTypedStatusesAndDoNotClearProjection() {
        var t=frozen(2,true);
        for(var command:List.of(new ActivateScoreExtractionCommand("s","empty:v1","确认",List.of()),new ActivateScoreExtractionCommand("bad","empty:v1","确认",null),new ActivateScoreExtractionCommand("s",null,"确认",null)))
            assertThatThrownBy(()->activation.activate(project,t.getTaskId(),command,"r")).isInstanceOfSatisfying(ScoreExtractionException.class,e->assertThat(e.status()).isEqualTo(400));
        assertThatThrownBy(()->activation.activate(project,t.getTaskId(),new ActivateScoreExtractionCommand("s","stale","确认",null),"r"))
            .isInstanceOfSatisfying(ScoreExtractionException.class,e->assertThat(e.status()).isEqualTo(409));
        var running=task();
        assertThatThrownBy(()->activation.activate(project,running.getTaskId(),new ActivateScoreExtractionCommand("s","empty:v1","确认",null),"r"))
            .isInstanceOfSatisfying(ScoreExtractionException.class,e->assertThat(e.status()).isEqualTo(409));
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).isEmpty();
    }
    @Test void actualParseWorkerUsesCapturedModeAndDoesNotPublishLegacyBeforeCas() {
        var resolver=mock(InitiationTenderTextResolver.class);
        String source="第三章 评审办法\n本项目总分为20分\n1 服务方案，满分10分。\n2 产品质量，满分10分。";
        when(resolver.resolveExecutionSource(project)).thenReturn(TenderExecutionSource.found(new TenderTextSource("source.txt",null,source),null));
        org.springframework.beans.factory.ObjectProvider<SidecarDocumentArtifactExtractor> provider=mock(org.springframework.beans.factory.ObjectProvider.class);
        var worker=new ScoreParseAppService(tasks,items,results,null,state,progress,null,access,null,null,legacy,resolver,null,provider,null,null,null);
        var model=new MultimodalScoreFactExtractor() {
            public boolean supportsTextOnly(){return true;}
            public ExtractionResult extract(com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact a,List<DocumentImage> images){throw new AssertionError("text path expected");}
            public ExtractionResult extractWithText(com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact a,String text) {
                return new ExtractionResult(BigDecimal.valueOf(20),List.of(
                    new MultimodalScoreFact("第三章 评审办法","1","","服务方案",BigDecimal.TEN,"服务方案，满分10分。","SUBJECTIVE",null,"",List.of("text:line[2]"),"服务方案，满分10分。",.7,ChildAggregation.SUM),
                    new MultimodalScoreFact("第三章 评审办法","2","","产品质量",BigDecimal.TEN,"产品质量，满分10分。","SUBJECTIVE",null,"",List.of("text:line[3]"),"产品质量，满分10分。",.7,ChildAggregation.SUM)),"fixed-extraction-port",0);
            }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(worker,"versionedCompletion",completion);
        org.springframework.test.util.ReflectionTestUtils.setField(worker,"multimodalScoreFactExtractor",model);
        var first=task();worker.executeParseAsync(first.getTaskId());
        assertThat(tasks.findById(first.getId()).orElseThrow().getStatus()).isEqualTo("COMPLETED");
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).hasSize(2);
        var edited=items.findByProjectIdOrderByItemIndexAsc(project).get(0);edited.setDetail("人工确认的规则");items.saveAndFlush(edited);
        String before=activation.currentToken(project).fingerprint();
        var reparse=task();worker.executeParseAsync(reparse.getTaskId());
        assertThat(reads.getByTaskId(project,reparse.getTaskId()).items()).hasSize(2);
        assertThat(activation.currentToken(project).fingerprint()).isEqualTo(before);
    }
    @Test void flagOffKeepsLegacyProjectsAndRejectsDowngradeOfExistingVersion() {
        var off=new ScoreExtractionVersionFeature(false);
        var disabledWriter=new ScoreExtractionSnapshotWriter(snapshots,facts,evidence,tasks,codec,activation,off,mapper);
        var old=tasks.saveAndFlush(ScoreParseTask.builder().taskId(UUID.randomUUID().toString()).projectId(project).taskType("PARSE").status("COMPLETED").progress(100).timeoutMarked(false).build());
        disabledWriter.begin(old);assertThat(snapshots.existsById(old.getId())).isFalse();
        legacy.persistItems(project,old.getId(),List.of(new ScoreCandidate("1","旧项","原规则",BigDecimal.TEN,"SUBJECTIVE",null,"原文",null,null)));
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).hasSize(1);
        var newer=frozen(2,false);
        activation.activate(project,newer.getTaskId(),new ActivateScoreExtractionCommand("s",activation.currentToken(project).fingerprint(),"切换",null),"r");
        var disabledActivation=new ScoreVersionActivationService(tasks,snapshots,versions,items,results,codec,off,access);
        assertThatThrownBy(()->disabledActivation.activate(project,newer.getTaskId(),new ActivateScoreExtractionCommand("s",activation.currentToken(project).fingerprint(),"确认",null),"r"))
            .isInstanceOfSatisfying(ScoreExtractionException.class,e->assertThat(e.status()).isEqualTo(409));
        assertThatThrownBy(()->legacy.persistItems(project,old.getId(),List.of())).isInstanceOf(ScoreExtractionException.class);
        var disabledReads=new ScoreExtractionAppService(tasks,facts,snapshots,versions,items,activation,codec,off,access);
        assertThat(disabledReads.getByTaskId(project,newer.getTaskId()).active().versionId()).isNotNull();
    }
    @Test void privateFortyCandidateSourceFreezesActivatesAndReadsThirteenAt100() throws Exception {
        var task=task();var replay=PrivateExtractionSnapshotReplayTest.replay(task.getTaskId());
        completion.structured(task,StructuredTableResult.evidencePipeline(replay.items().stream().map(ExtractedScoreItem::fact).toList(),List.of(),null,List.of(),List.of(),replay.items().stream().map(ExtractedScoreItem::qualification).toList(),null));
        assertThat(facts.findByParseTaskId(task.getId())).hasSize(40);
        var active=items.findByProjectIdOrderByItemIndexAsc(project);
        assertThat(active).hasSize(13);
        assertThat(active.stream().map(ScoreItem::getWeight).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("100");
        var view=reads.getByTaskId(project,task.getTaskId());assertThat(view.items()).hasSize(40);
        assertThat(active.stream().filter(i -> i.getWeight().compareTo(new BigDecimal("12"))==0).findFirst().orElseThrow().getDetail()).startsWith("第二部分").doesNotContain("清单报价");
        assertThat(active.stream().filter(i -> i.getWeight().compareTo(new BigDecimal("28"))==0).findFirst().orElseThrow().getDetail()).contains("覆盖率").contains("扣5分").doesNotContain("第二部分");
        assertThat(view.active().hasManualEdits()).isFalse();
        writer.freeze(project,task.getId(),new ScoreExtractionCodec(mapper).decode(snapshots.findById(task.getId()).orElseThrow().getPayload()));
        assertThat(view.schemes()).anySatisfy(s->{assertThat(s.extractedCount()).isEqualTo(13);assertThat(s.computableCount()).isEqualTo(13);assertThat(s.activeCount()).isEqualTo(13);assertThat(s.declaredTotal()).isEqualByComparingTo("100");});
        assertThat(active.stream().filter(i->i.getCode().contains("物流仓储")).findFirst().orElseThrow().getValidationStatus()).isEqualTo("REVIEW_REQUIRED");
    }
    @Test void timedOutWorkerRetainsSnapshotButCannotResurrectAndAutoPublish() {
        var detached=task();var replay=snapshot(detached,2,false);
        var timedOut=tasks.findById(detached.getId()).orElseThrow();timedOut.setStatus("FAILED");timedOut.setTimeoutMarked(true);tasks.saveAndFlush(timedOut);
        completion.structured(detached,StructuredTableResult.evidencePipeline(replay.items().stream().map(ExtractedScoreItem::fact).toList(),List.of(),null,List.of(),List.of(),replay.items().stream().map(ExtractedScoreItem::qualification).toList(),null));
        assertThat(tasks.findById(detached.getId()).orElseThrow().getStatus()).isEqualTo("FAILED");
        assertThat(tasks.findById(detached.getId()).orElseThrow().getTimeoutMarked()).isTrue();
        assertThat(reads.getByTaskId(project,detached.getTaskId()).items()).hasSize(2);
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).isEmpty();
    }
    @Test void activeRulesKeepOriginalMaterialsAndThresholdsInsteadOfModelSummary() {
        var t=task();String original=TextPrimaryScoreFactMapperTest.COMPLETE_RULE;
        var f=TextPrimaryScoreFactMapperTest.mappedCompleteRule();
        var q=new OriginalScoreEvidencePolicy.Qualification(f.factId(),"s","IN_SCHEME",false,"SOURCE_UNVERIFIED","");
        var snapshot=ScoreExtractionSnapshot.from(t.getTaskId(),List.of(f),List.of(q),List.of());
        writer.freeze(project,t.getId(),snapshot);state.markCompleted(t.getTaskId());activation.tryAutoActivate(project,t.getTaskId());
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project).get(0).getDetail()).isEqualTo(original);
        assertThat(reads.getByTaskId(project,t.getTaskId()).items().get(0).fact().sourceMetadata().modelRuleText()).isEqualTo("按项目经验评分");
    }
    @Test void structuredFallbackPersistsAllCandidatesAndOnlySelectedSetScores() throws Exception {
        var t=task();var result=MultimodalStructuredExtractionHandlerTest.fallbackWithOriginalModel(true);
        completion.structured(t,result);
        var view=reads.getByTaskId(project,t.getTaskId());
        assertThat(view.items()).hasSize(5);
        assertThat(facts.findByParseTaskId(t.getId())).hasSize(5);
        var model=view.items().stream().filter(i -> i.name().equals("审查原始未核验候选")).findFirst().orElseThrow();
        assertThat(model.diagnostics()).contains("ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED");
        assertThat(model.fact().sourceMetadata().modelRuleText()).isEqualTo("模型原始规则最高6分");
        assertThat(model.computable()).isFalse();
        assertThat(view.items().stream().filter(ExtractedScoreItem::computable)).allSatisfy(i -> assertThat(i.fact().sourceMetadata().sourceSpans()).isNotEmpty());
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project).stream().map(ScoreItem::getWeight).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("100");
        assertThat(view.active().hasManualEdits()).isFalse();
    }
    @Test void subCentWeightRemainsExactInSnapshotWithoutDatabaseRounding() {
        var t=task();var f=new ScoreFact("precise","t",List.of(),"满分0.125分","p","精确分值",new BigDecimal("0.125"),null,null,null,"MODEL",.8,ScoreFact.ValidationStatus.REVIEW_REQUIRED);
        var q=new OriginalScoreEvidencePolicy.Qualification(f.factId(),"s","IN_SCHEME",false,"SOURCE_UNVERIFIED","");
        completion.structured(t,StructuredTableResult.evidencePipeline(List.of(f),List.of(),null,List.of(),List.of(),List.of(q),null));
        var item=reads.getByTaskId(project,t.getTaskId()).items().getFirst();
        assertThat(facts.findByParseTaskId(t.getId()).getFirst().getWeight()).isNull();
        assertThat(item.rawMaximum()).isEqualByComparingTo("0.125");
        assertThat(item.diagnostics()).contains("WEIGHT_PRECISION_UNSUPPORTED");
        assertThatThrownBy(()->legacy.persistItems(project,t.getId(),List.of())).isInstanceOfSatisfying(ScoreExtractionException.class,e -> assertThat(e.status()).isEqualTo(409));
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).isEmpty();
    }
    @Test void outOfRangeCandidateStillFreezesWithoutNumericColumnOverflow() {
        var t=task();var f=new ScoreFact("huge","t",List.of(),"满分10000分","p","超范围分值",new BigDecimal("10000"),null,null,null,"MODEL",.8,ScoreFact.ValidationStatus.REVIEW_REQUIRED);
        var q=new OriginalScoreEvidencePolicy.Qualification(f.factId(),"s","IN_SCHEME",false,"SOURCE_UNVERIFIED","");
        completion.structured(t,StructuredTableResult.evidencePipeline(List.of(f),List.of(),null,List.of(),List.of(),List.of(q),null));
        assertThat(facts.findByParseTaskId(t.getId()).getFirst().getWeight()).isNull();
        assertThat(reads.getByTaskId(project,t.getTaskId()).items().getFirst().rawMaximum()).isEqualByComparingTo("10000");
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).isEmpty();
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Test void legacyWriterUsesCurrentReadAfterRepeatableReadSnapshotAndConcurrentActivation() throws Exception {
        var t=frozen(2,false);
        var executor=Executors.newSingleThreadExecutor();
        try {
            var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
            tx.execute(status -> {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_active_version WHERE project_id=?",Integer.class,project)).isZero();
                try { executor.submit(()->activation.tryAutoActivate(project,t.getTaskId())).get(15,TimeUnit.SECONDS); }
                catch(Exception e){throw new IllegalStateException(e);}
                // This ordinary read still sees the old RR snapshot, proving the race precondition.
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM score_active_version WHERE project_id=?",Integer.class,project)).isZero();
                assertThatThrownBy(()->legacy.persistItems(project,null,List.of())).isInstanceOfSatisfying(ScoreExtractionException.class,e -> assertThat(e.status()).isEqualTo(409));
                status.setRollbackOnly();return null;
            });
            assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).hasSize(2);
        } finally {executor.shutdownNow();}
    }
    @Test void unresolvedParentReparseKeepsOldVersionAndResults() {
        var initial=frozen(1,false);activation.tryAutoActivate(project,initial.getTaskId());
        var oldItem=items.findByProjectIdOrderByItemIndexAsc(project).getFirst();
        var savedResult=results.saveAndFlush(ScoreResult.builder().scoreItemId(oldItem.getId()).scoringTaskId(initial.getId()).actualScore(BigDecimal.ONE).statusStage2("OK").build());
        var before=activation.currentToken(project);var next=task();
        var old=snapshot(next,1,false).items().getFirst().fact();
        var parent=new ScoreFact("p","t",List.of(),"父项5分","2","父项",new BigDecimal("5"),null,null,null,"MODEL",.8,ScoreFact.ValidationStatus.REVIEW_REQUIRED,ChildAggregation.UNKNOWN);
        var child=new ScoreFact("c","t",List.of(),"子项5分","2.1","子项",new BigDecimal("5"),null,null,"p","MODEL",.8,ScoreFact.ValidationStatus.REVIEW_REQUIRED);
        var fs=List.of(old,parent,child);
        var qs=fs.stream().map(f -> new OriginalScoreEvidencePolicy.Qualification(f.factId(),"s","IN_SCHEME",false,"SOURCE_UNVERIFIED","","评分标准",BigDecimal.TEN,"KNOWN","总分10分","t","SINGLE_TABLE",List.of("t"),ChildAggregation.SUM)).toList();
        completion.structured(next,StructuredTableResult.evidencePipeline(fs,List.of(),null,List.of(),List.of(),qs,null));
        assertThat(activation.currentToken(project)).isEqualTo(before);
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project).getFirst().getId()).isEqualTo(oldItem.getId());
        assertThat(results.findById(savedResult.getId())).isPresent().get().extracting(ScoreResult::getActualScore).isEqualTo(new BigDecimal("1.00"));
        assertThat(reads.getByTaskId(project,next.getTaskId()).items()).hasSize(3);
    }
    @Test void unresolvedRuleRangeCanComputeInitiallyButCannotReplaceExistingFullRule() {
        var ambiguous=TextPrimaryScoreFactMapperTest.ambiguousSharedRule();
        var initial=task();
        var full=new ScoreFact(ambiguous.factId(),"t",List.of(),"完整原始规则：须提供合同首页及签章页。",ambiguous.code(),ambiguous.dimension(),ambiguous.weight(),null,null,null,"MODEL",.8,ScoreFact.ValidationStatus.REVIEW_REQUIRED);
        var qs=List.of(new OriginalScoreEvidencePolicy.Qualification(full.factId(),"s","IN_SCHEME",false,"SOURCE_UNVERIFIED","","评分标准",full.weight(),"KNOWN","总分28分","t","SINGLE_TABLE",List.of("t"),ChildAggregation.SUM));
        completion.structured(initial,StructuredTableResult.evidencePipeline(List.of(full),List.of(),null,List.of(),List.of(),qs,null));
        var before=activation.currentToken(project);var next=task();
        var candidate=ScoreExtractionSnapshot.from(next.getTaskId(),List.of(ambiguous),qs,List.of());
        assertThat(candidate.items().getFirst().computable()).isTrue();
        assertThat(candidate.schemes().getFirst().extractedTotalComplete()).isFalse();
        completion.structured(next,StructuredTableResult.evidencePipeline(List.of(ambiguous),List.of(),null,List.of(),List.of(),qs,null));
        assertThat(activation.currentToken(project)).isEqualTo(before);
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project).getFirst().getDetail()).isEqualTo("完整原始规则：须提供合同首页及签章页。");
    }
    @Test void actualDisconnectedDocxCannotAutoReplaceCompleteRuleOrPriorScore() throws Exception {
        var first = task();
        var complete = FullRuleSourceReplayTest.mapped("adjacent", "name-score");
        completion.structured(first, fullRuleResult(complete));
        var oldItem = items.findByProjectIdOrderByItemIndexAsc(project).getFirst();
        var saved = results.saveAndFlush(ScoreResult.builder().scoreItemId(oldItem.getId()).scoringTaskId(first.getId())
                .actualScore(BigDecimal.ONE).statusStage2("OK").build());
        var before = activation.currentToken(project);
        var next = task();
        var unresolved = FullRuleSourceReplayTest.mapped("paragraph", "exact");
        completion.structured(next, fullRuleResult(unresolved));
        assertThat(activation.currentToken(project)).isEqualTo(before);
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project).getFirst().getId()).isEqualTo(oldItem.getId());
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project).getFirst().getDetail()).isEqualTo(oldItem.getDetail());
        assertThat(results.findById(saved.getId())).isPresent().get().extracting(ScoreResult::getActualScore).isEqualTo(new BigDecimal("1.00"));
        var candidate = reads.getByTaskId(project, next.getTaskId()).items().getFirst();
        assertThat(candidate.ruleText()).isEqualTo(FullRuleSourceReplayTest.RULE);
        assertThat(candidate.diagnostics()).contains("FULL_RULE_UNIT_UNRESOLVED");
        assertThat(candidate.computable()).isTrue();
        activation.activate(project, next.getTaskId(), new ActivateScoreExtractionCommand("s", before.fingerprint(), "确认完整模型候选", null), "r");
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project).getFirst().getDetail()).isEqualTo(FullRuleSourceReplayTest.RULE);
    }
    @Test void actualDocxFirstActivationAndScorerRetainEveryRuleClause() throws Exception {
        for (String variant : List.of("adjacent", "paragraph")) {
            var t = task();
            var mapped = FullRuleSourceReplayTest.mapped(variant, "exact");
            var snapshot = FullRuleSourceReplayTest.snapshot(mapped);
            writer.freeze(project, t.getId(), snapshot); state.markCompleted(t.getTaskId());
            if (variant.equals("adjacent")) assertThat(activation.tryAutoActivate(project, t.getTaskId())).isTrue();
            else activation.activate(project, t.getTaskId(), new ActivateScoreExtractionCommand("s", activation.currentToken(project).fingerprint(), "确认", null), "r");
            var active = items.findByProjectIdOrderByItemIndexAsc(project);
            assertThat(active.getFirst().getDetail().replace("\n", "")).isEqualTo(FullRuleSourceReplayTest.RULE);
            var plan = new ScoreScoringItemPicker().plan(active, Map.of(), "投标方案", null, "ALL", List.of());
            assertThat(plan.toAssess().getFirst().getDetail()).isEqualTo(active.getFirst().getDetail());
            assertThat(reads.getByTaskId(project, t.getTaskId()).items().getFirst().fact().sourceMetadata().modelQuotedText()).isEqualTo("方案编制合理，");
        }
    }
    @Test void legacyPublicMappingNeverAdoptsTruncatedSourceRule() throws Exception {
        var mapped = FullRuleSourceReplayTest.mapped("adjacent", "exact");
        var candidate = mapped.withValidationStatus(ScoreFact.ValidationStatus.AUTO_APPROVED).toCandidate().orElseThrow();
        var t = tasks.saveAndFlush(ScoreParseTask.builder().taskId(UUID.randomUUID().toString()).projectId(project)
                .taskType("PARSE").status("COMPLETED").progress(100).timeoutMarked(false).build());
        legacy.persistItems(project, t.getId(), List.of(candidate));
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project).getFirst().getDetail().replace("\n", "")).isEqualTo(FullRuleSourceReplayTest.RULE);
    }
    @Test void actualUnresolvedDocxFirstActivationKeepsFullModelWithWarning() throws Exception {
        var t = task();
        var fact = FullRuleSourceReplayTest.mapped("paragraph", "exact");
        completion.structured(t, fullRuleResult(fact));
        assertThat(items.findByProjectIdOrderByItemIndexAsc(project).getFirst().getDetail()).isEqualTo(FullRuleSourceReplayTest.RULE);
        var view = reads.getByTaskId(project, t.getTaskId()).items().getFirst();
        assertThat(view.fieldSources().get("ruleText")).isEqualTo("MODEL_CLAIM");
        assertThat(view.diagnostics()).contains("FULL_RULE_UNIT_UNRESOLVED");
        assertThat(view.computable()).isTrue();
        assertThat(fact.toCandidate()).isEmpty();
    }
    private StructuredTableResult fullRuleResult(ScoreFact fact) {
        return StructuredTableResult.evidencePipeline(List.of(fact), List.of(), null, List.of(), List.of(), FullRuleSourceReplayTest.knownTotal(fact), null);
    }
    private ScoreParseTask task(){return state.createTask(UUID.randomUUID().toString(),project,"PARSE",null,null,"MANUAL");}
    private ScoreParseTask frozen(int count,boolean unknown){var t=task();writer.freeze(project,t.getId(),snapshot(t,count,unknown));t.setStatus("COMPLETED");t.setDecisionStatus("REVIEW_REQUIRED");return tasks.saveAndFlush(t);}
    private ScoreExtractionSnapshot snapshot(ScoreParseTask t,int count,boolean unknown) {
        List<ScoreFact> fs=new ArrayList<>();List<OriginalScoreEvidencePolicy.Qualification> qs=new ArrayList<>();
        for(int n=0;n<count+(unknown?1:0);n++) {
            var weight=n<count?BigDecimal.TEN:null;String id="f"+n;
            fs.add(new ScoreFact(id,"t",List.of(),"规则原文","same",id,weight,null,null,null,"MODEL",.6,ScoreFact.ValidationStatus.REVIEW_REQUIRED));
            qs.add(new OriginalScoreEvidencePolicy.Qualification(id,"s","IN_SCHEME",false,"SOURCE_UNVERIFIED","","评分标准",BigDecimal.valueOf(count*10L),"KNOWN","声明","loc","MULTI_TABLE",List.of("t"),ChildAggregation.SUM));
        }
        return ScoreExtractionSnapshot.from(t.getTaskId(),fs,qs,List.of());
    }
}
