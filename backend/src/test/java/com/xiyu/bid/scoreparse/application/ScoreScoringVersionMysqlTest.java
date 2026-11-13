package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.xiyu.bid.scoreparse.dto.ScoreScoringCommand;
import com.xiyu.bid.scoreparse.infrastructure.openai.*;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
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
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(classes=ScoreScoringVersionMysqlTest.Config.class,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.jpa.open-in-view=false","app.score-parse.extraction-version.enabled=true"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation=Propagation.NEVER)
class ScoreScoringVersionMysqlTest extends AbstractTransactionBoundaryTest {
 @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0");
 @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
 @Configuration @EnableAutoConfiguration @EntityScan(basePackageClasses=ScoreParseTask.class)
 @EnableJpaRepositories(basePackageClasses=ScoreParseTaskRepository.class)
 @ComponentScan(basePackageClasses=ScoreScoringAppService.class,useDefaultFilters=false,includeFilters=@ComponentScan.Filter(type=FilterType.REGEX,pattern=".*\\.(ScoreScoringAppService|ScoreScoringVersionService|ScoreParseTaskStateService|ScoreParseProgressService|ScoreResultOverwriter|ScoreExtractionCodec|EstimatedScoreService|ScoreVersionActivationService|ScoreExtractionSnapshotWriter|ScoreExtractionVersionFeature)"))
 static class Config {@Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}}
 @MockBean org.springframework.data.redis.core.StringRedisTemplate redis;
 final Map<String,String> cache = new java.util.concurrent.ConcurrentHashMap<>();
 @MockBean ProjectAccessScopeService access;
 @MockBean BidFileLoadingCoordinator loader;
 @MockBean OpenAiScoreAnalyzer analyzer;
 @MockBean com.xiyu.bid.scoreparse.application.match.CertMatchService certs;
 @MockBean com.xiyu.bid.scoreparse.application.match.PersonMatchService people;
 @MockBean com.xiyu.bid.scoreparse.application.match.ProjectMatchService projects;
 @MockBean com.xiyu.bid.scoreparse.application.match.WarehouseMatchService warehouses;
 @MockBean com.xiyu.bid.scoreparse.application.match.BrandMatchService brands;
 @Autowired ScoreVersionActivationService activation;
 @Autowired ScoreExtractionSnapshotWriter writer;
 @Autowired ScoreParseTaskStateService state;
 @Autowired EstimatedScoreService estimator;
 @Autowired ScoreScoringVersionService boundary;
 @Autowired ScoreScoringAppService scoring;
 @Autowired ScoreParseTaskRepository tasks;
 @Autowired ScoreItemRepository items;
 @Autowired ScoreResultRepository results;
 @Autowired JdbcTemplate jdbc;
 @Autowired PlatformTransactionManager txm;
 long project;
 @BeforeEach void setup(){reset(access,loader,analyzer,certs,people,projects,warehouses,brands,redis);
 cache.clear();
 when(analyzer.scoringConfiguration()).thenReturn(new com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig(
         "fixture-key","http://fixture.invalid/v1","fixture-model",java.time.Duration.ofSeconds(30),
         com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentApiStyle.CHAT_COMPLETIONS));
 org.springframework.data.redis.core.ValueOperations<String,String> values=mock(org.springframework.data.redis.core.ValueOperations.class);
 when(redis.opsForValue()).thenReturn(values);
 when(values.get(anyString())).thenAnswer(inv->cache.get(inv.getArgument(0)));
 doAnswer(inv->{cache.put(inv.getArgument(0),inv.getArgument(1));return null;}).when(values).set(anyString(),anyString(),any(java.time.Duration.class));
 when(redis.delete(anyString())).thenAnswer(inv->cache.remove(inv.getArgument(0))!=null);
 jdbc.execute("CREATE TABLE IF NOT EXISTS projects(id BIGINT PRIMARY KEY)");project=System.nanoTime();jdbc.update("INSERT INTO projects(id) VALUES(?)",project);
 var doc=ProjectDocument.builder().name("bid").fileUrl("fixture://bid").build();when(loader.findBidDocuments(project)).thenReturn(List.of(doc));
 when(loader.load(eq(project),anyString())).thenReturn(new BidFileLoadingCoordinator.LoadedBidDocument("第一章 服务方案\n完整投标内容用于证书核验","bid-hash"));
 when(loader.openScoring(eq(project),anyString(),anyLong())).thenAnswer(inv->{
     var loaded=loader.load(project,inv.getArgument(1));
     var text=mock(SidecarStreamTextExtractor.class);
     var coverage=new com.xiyu.bid.scoreparse.domain.ScoringExtractionReport(1,"fixture-v2","COMPLETE",1,1,1,0,0,0,List.of(),List.of(),List.of(),1);
     when(text.extractScoringDocument(any(byte[].class),anyString(),anyLong())).thenReturn(
             new com.xiyu.bid.scoreparse.domain.ScoringDocument(loaded.text(),List.of(Map.of("pageNumber",1)),coverage));
     return new ScoringFileSession(1L,loaded.contentHash(),new LoadedBidFile.InMemory(new byte[]{1},"bid.pdf","application/pdf"),
             text,null,inv.getArgument(2),()->{});
 });
 when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget.class)))
         .thenAnswer(inv->analyzer.assessObjective(inv.getArgument(0),inv.getArgument(1),inv.getArgument(2)));
 when(analyzer.assessObjective(anyString(),any(),anyString())).thenAnswer(inv->{var out=new ScoreAssessmentOutput();out.quote="完整投标内容用于证书核验";out.actualScore=5d;out.evidence="材料";return out;});
 }
 ScoreItem item(String name){return items.saveAndFlush(ScoreItem.builder().projectId(project).parseTaskId(1L).itemIndex((int)(items.count()+1)).code(name).dim(name).detail("完整规则：提供证书5分；提供附件5分；缺少附件不得分").weight(BigDecimal.TEN).scoreType("OBJECTIVE").statusStage1("PENDING").build());}
 @Test void changedRulesDuringScoringCannotWriteOldSuccess(){var i=item("a");when(analyzer.assessObjective(anyString(),any(),anyString())).thenAnswer(inv->{var changed=items.findById(i.getId()).orElseThrow();changed.setDetail("新版本规则");items.saveAndFlush(changed);var out=new ScoreAssessmentOutput();out.quote="完整投标内容用于证书核验";out.actualScore=5d;return out;});
 scoring.triggerScoring(project);assertThat(results.findByScoreItemIdIn(List.of(i.getId()))).isEmpty();assertThat(tasks.findAll().getLast().getStatus()).isEqualTo("FAILED");}
 @Test void itemsScopeDoesNotAssessUnselectedWithoutCache(){var a=item("a");var b=item("b");scoring.triggerScoring(project,new ScoreScoringCommand("MANUAL","ITEMS",List.of(a.getId())));verify(analyzer,times(1)).assessObjective(anyString(),any(),anyString());assertThat(results.findByScoreItemIdIn(List.of(b.getId()))).singleElement().satisfies(r->{assertThat(r.getActualScore()).isNull();assertThat(r.getStatusStage2()).isEqualTo("PENDING");});}
 @Test void callerRollbackMustNotDispatch(){item("a");var tx=new TransactionTemplate(txm);tx.executeWithoutResult(s->{scoring.triggerScoring(project);verify(loader,never()).load(any(),anyString());s.setRollbackOnly();});verify(loader,never()).load(any(),anyString());assertThat(tasks.findAll().stream().filter(t->t.getProjectId().equals(project))).isEmpty();}

 @Test void oldFailureCannotWritePendingAfterRulesChange(){var i=item("a");when(loader.load(eq(project),anyString())).thenAnswer(inv->{var changed=items.findById(i.getId()).orElseThrow();changed.setDetail("新规则");items.saveAndFlush(changed);throw new IllegalStateException("fixture failure");});scoring.triggerScoring(project);assertThat(results.findByScoreItemIdIn(List.of(i.getId()))).isEmpty();}
 @Test void sameVersionNewerTaskWins(){var i=item("a");var winner=new java.util.concurrent.atomic.AtomicReference<ScoreParseTask>();when(analyzer.assessObjective(anyString(),any(),anyString())).thenAnswer(inv->{var newer=tasks.saveAndFlush(ScoreParseTask.builder().taskId(UUID.randomUUID().toString()).projectId(project).taskType("SCORING").status("COMPLETED").itemSetHash(boundary.capture(project).fingerprint()).build());winner.set(newer);results.saveAndFlush(ScoreResult.builder().scoreItemId(i.getId()).scoringTaskId(newer.getId()).actualScore(new BigDecimal("8")).statusStage2("PENDING").reuseKind("FRESH").build());var out=new ScoreAssessmentOutput();out.quote="完整投标内容用于证书核验";out.actualScore=5d;return out;});scoring.triggerScoring(project);assertThat(results.findByScoreItemIdIn(List.of(i.getId()))).singleElement().satisfies(r->{assertThat(r.getActualScore()).isEqualByComparingTo("8");assertThat(r.getScoringTaskId()).isEqualTo(winner.get().getId());});}
 @Test void unsatisfiedScopeDoesNotAssessMissingUnselected(){var i=item("a");scoring.triggerScoring(project,new ScoreScoringCommand("MANUAL","UNSATISFIED",List.of()));verify(analyzer,never()).assessObjective(anyString(),any(),anyString());assertThat(results.findByScoreItemIdIn(List.of(i.getId()))).singleElement().satisfies(r->assertThat(r.getActualScore()).isNull());}
 @Test void ruleChangeWithSameBidChaptersReassessesAndPassesEntireRule(){var i=item("a");scoring.triggerScoring(project);var changed=items.findById(i.getId()).orElseThrow();String full="规则修改：第一项提供证书5分；第二项提供所有附件5分；缺少任何附件不得分";changed.setDetail(full);items.saveAndFlush(changed);scoring.triggerScoring(project);verify(analyzer).assessObjective(eq(full),eq(BigDecimal.TEN.setScale(2)),anyString());}

 @Test void estimatorCannotResurrectItemDeletedDuringMatching(){var old=item("资质证书");when(certs.estimateSimpleScore(any(),any(),any(),any())).thenAnswer(inv->{var tx=new TransactionTemplate(txm);tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.executeWithoutResult(st->items.deleteById(old.getId()));return new com.xiyu.bid.scoreparse.application.match.CertMatchService.ScoreEstimate(new com.xiyu.bid.scoreparse.domain.CertificateScorePolicy.Assessment(null,"PENDING",false,"fixture"),"fixture");});assertThatCode(()->estimator.estimateForProject(project)).doesNotThrowAnyException();assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).isEmpty();}

 ScoreParseTask frozen(){return frozen(List.of());}
 ScoreParseTask frozen(List<String> diagnostics){var t=state.createTask(UUID.randomUUID().toString(),project,"PARSE",null,null);var fact=new com.xiyu.bid.scoreparse.domain.ScoreFact("f","t",List.of(),"提供营业执照证书得10分","a","营业执照",BigDecimal.TEN,null,null,null,"MODEL",.8,com.xiyu.bid.scoreparse.domain.ScoreFact.ValidationStatus.REVIEW_REQUIRED);var q=new com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.Qualification("f","s","IN_SCHEME",false,"SOURCE_UNVERIFIED","","评分标准",BigDecimal.TEN,"KNOWN","总分10分","t","SINGLE_TABLE",List.of("t"),com.xiyu.bid.scoreparse.domain.ChildAggregation.SUM);writer.freeze(project,t.getId(),com.xiyu.bid.scoreparse.domain.ScoreExtractionSnapshot.from(t.getTaskId(),List.of(fact),List.of(q),diagnostics));t.setStatus("COMPLETED");return tasks.saveAndFlush(t);}
 @Test void activationEstimatesOnlyAfterCommitAndNeverOnRollback(){var t=frozen();var tx=new TransactionTemplate(txm);tx.executeWithoutResult(st->{activation.tryAutoActivate(project,t.getTaskId());assertThat(items.findByProjectIdOrderByItemIndexAsc(project).getFirst().getEstBasis()).isNull();st.setRollbackOnly();});assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).isEmpty();assertThat(activation.tryAutoActivate(project,t.getTaskId())).isTrue();assertThat(items.findByProjectIdOrderByItemIndexAsc(project).getFirst().getEstBasis()).isNotBlank();verifyNoInteractions(analyzer);}

 @Test void rrReadViewAndJpaCacheDoNotPermitFastSkipWithChangedRules() throws Exception {var i=item("a");scoring.triggerScoring(project);var tx=new TransactionTemplate(txm);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);tx.executeWithoutResult(st->{assertThat(items.findById(i.getId()).orElseThrow().getDetail()).contains("附件");try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor()){pool.submit(()->{var changed=items.findById(i.getId()).orElseThrow();changed.setDetail("并发提交的新完整规则");items.saveAndFlush(changed);}).get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception ex){throw new RuntimeException(ex);}scoring.triggerScoring(project);});verify(analyzer).assessObjective(eq("并发提交的新完整规则"),any(),anyString());}
 @Test void loadedContentSkipStillChecksVersionAtCommit(){var i=item("a");scoring.triggerScoring(project);var old=results.findByScoreItemIdIn(List.of(i.getId())).getFirst();when(loader.findBidDocuments(project)).thenReturn(List.of(ProjectDocument.builder().name("bid2").fileUrl("fixture://bid2").build()));when(loader.load(eq(project),anyString())).thenAnswer(inv->{var changed=items.findById(i.getId()).orElseThrow();changed.setDetail("下载过程中切换规则");items.saveAndFlush(changed);return new BidFileLoadingCoordinator.LoadedBidDocument("第一章 服务方案\n完整投标内容用于证书核验","bid-hash");});scoring.triggerScoring(project);assertThat(results.findById(old.getId())).isPresent();assertThat(tasks.findAll().stream().filter(t->t.getProjectId().equals(project)).max(Comparator.comparing(ScoreParseTask::getId)).orElseThrow().getStatus()).isEqualTo("FAILED");assertThat(scoring.getResults(project).results()).allSatisfy(r->assertThat(r.actualScore()).isNull());assertThat(latestScoring().getBidContentHash()).isNull();verify(analyzer,times(1)).assessObjective(anyString(),any(),anyString());}
 @Test void manualSameFileReassessmentReplacesResultIdentity(){var i=item("a");scoring.triggerScoring(project);var old=results.findByScoreItemIdIn(List.of(i.getId())).getFirst();assertThat(scoring.triggerScoring(project).status()).isEqualTo("PENDING");assertThat(results.findByScoreItemIdIn(List.of(i.getId()))).singleElement().satisfies(r->assertThat(r.getId()).isNotEqualTo(old.getId()));verify(analyzer,times(2)).assessObjective(anyString(),any(),anyString());}
 @Test void manualActivationRetainsCommittedVersionWhenEstimatorFails(){var t=frozen();doThrow(new IllegalStateException("knowledge unavailable")).when(certs).match(any());activation.activate(project,t.getTaskId(),new com.xiyu.bid.scoreparse.dto.ActivateScoreExtractionCommand("s","empty:v1","确认",null),"reviewer");assertThat(activation.currentToken(project).versionId()).isNotNull();assertThat(items.findByProjectIdOrderByItemIndexAsc(project).getFirst().getEstBasis()).isNotBlank();verifyNoInteractions(analyzer);}

 @Test void explicitVersionSwitchWhileAssessingRejectsOldSuccess(){var initial=frozen();activation.tryAutoActivate(project,initial.getTaskId());var old=items.findByProjectIdOrderByItemIndexAsc(project).getFirst();when(analyzer.assessObjective(anyString(),any(),anyString())).thenAnswer(inv->{var next=frozen();activation.activate(project,next.getTaskId(),new com.xiyu.bid.scoreparse.dto.ActivateScoreExtractionCommand("s",activation.currentToken(project).fingerprint(),"切换",null),"reviewer");var out=new ScoreAssessmentOutput();out.quote="完整投标内容用于证书核验";out.actualScore=5d;return out;});scoring.triggerScoring(project);assertThat(items.findById(old.getId())).isEmpty();assertThat(results.findAll().stream().filter(r->r.getScoreItemId().equals(old.getId()))).isEmpty();assertThat(scoring.getResults(project).results()).allSatisfy(r->assertThat(r.actualScore()).isNull());}
 @Test void explicitVersionSwitchDuringLoadRejectsOldFailure(){var initial=frozen();activation.tryAutoActivate(project,initial.getTaskId());var old=items.findByProjectIdOrderByItemIndexAsc(project).getFirst();when(loader.load(eq(project),anyString())).thenAnswer(inv->{var next=frozen();activation.activate(project,next.getTaskId(),new com.xiyu.bid.scoreparse.dto.ActivateScoreExtractionCommand("s",activation.currentToken(project).fingerprint(),"切换",null),"reviewer");throw new IllegalStateException("fixture");});scoring.triggerScoring(project);assertThat(items.findById(old.getId())).isEmpty();assertThat(results.findAll().stream().filter(r->r.getScoreItemId().equals(old.getId()))).isEmpty();}
 @Test void incompleteReparseAndActivationRollbackPreserveActualResultRows(){var initial=frozen();activation.tryAutoActivate(project,initial.getTaskId());var active=items.findByProjectIdOrderByItemIndexAsc(project).getFirst();var scored=tasks.saveAndFlush(ScoreParseTask.builder().taskId(UUID.randomUUID().toString()).projectId(project).taskType("SCORING").status("COMPLETED").itemSetHash(boundary.capture(project).fingerprint()).build());var result=results.saveAndFlush(ScoreResult.builder().scoreItemId(active.getId()).scoringTaskId(scored.getId()).actualScore(new BigDecimal("86")).statusStage2("OK").build());var partial=frozen(List.of("SOURCE_UNAVAILABLE"));assertThat(activation.tryAutoActivate(project,partial.getTaskId())).isFalse();assertSavedResult(result,scored);var before=activation.currentToken(project);var next=frozen();jdbc.execute("ALTER TABLE score_item ADD CONSTRAINT task3_reject_activation CHECK (active_version_id <= "+before.versionId()+")");try{assertThatThrownBy(()->activation.activate(project,next.getTaskId(),new com.xiyu.bid.scoreparse.dto.ActivateScoreExtractionCommand("s",before.fingerprint(),"切换",null),"r")).isInstanceOf(RuntimeException.class);}finally{jdbc.execute("ALTER TABLE score_item DROP CHECK task3_reject_activation");}assertThat(activation.currentToken(project)).isEqualTo(before);assertSavedResult(result,scored);activation.activate(project,next.getTaskId(),new com.xiyu.bid.scoreparse.dto.ActivateScoreExtractionCommand("s",before.fingerprint(),"切换",null),"r");assertThat(results.findById(result.getId())).isEmpty();}
 void assertSavedResult(ScoreResult expected,ScoreParseTask owner){assertThat(results.findById(expected.getId())).isPresent().get().satisfies(r->{assertThat(r.getActualScore()).isEqualByComparingTo("86");assertThat(r.getScoringTaskId()).isEqualTo(owner.getId());assertThat(r.getScoreItemId()).isEqualTo(expected.getScoreItemId());});}
 @Test void outerRepeatableReadCannotHideRuleChangeFromFinalWriterOrReader() throws Exception {var i=item("a");scoring.triggerScoring(project);var token=boundary.capture(project);var oldResult=results.findByScoreItemIdIn(List.of(i.getId())).getFirst();var pending=tasks.saveAndFlush(ScoreParseTask.builder().taskId(UUID.randomUUID().toString()).projectId(project).taskType("SCORING").status("PENDING").itemSetHash(token.fingerprint()).build());var tx=new TransactionTemplate(txm);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);tx.executeWithoutResult(st->{items.findById(i.getId()).orElseThrow();try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor()){pool.submit(()->{var changed=items.findById(i.getId()).orElseThrow();changed.setDetail("已提交新规则");items.saveAndFlush(changed);}).get(10,java.util.concurrent.TimeUnit.SECONDS);}catch(Exception ex){throw new RuntimeException(ex);}assertThat(boundary.finish(pending,List.of(ScoreResult.builder().scoreItemId(i.getId()).scoringTaskId(pending.getId()).actualScore(BigDecimal.ZERO).statusStage2("DANGER").build()),null)).isFalse();assertThat(scoring.getResults(project).results()).singleElement().satisfies(r->{assertThat(r.detail()).isEqualTo("已提交新规则");assertThat(r.actualScore()).isNull();});});assertThat(results.findById(oldResult.getId())).isPresent();}

 @Test void partialScopePlaceholderCannotMakeLaterAllScopeSkip(){var a=item("a");var b=item("b");scoring.triggerScoring(project,new ScoreScoringCommand("MANUAL","ITEMS",List.of(a.getId())));scoring.triggerScoring(project);verify(analyzer,times(3)).assessObjective(anyString(),any(),anyString());assertThat(scoring.getResults(project).results()).allSatisfy(r->assertThat(r.actualScore()).isNotNull());}
 @Test void currentFailurePendingReasonIsVisible(){item("a");when(loader.load(eq(project),anyString())).thenThrow(new IllegalStateException("fixture"));scoring.triggerScoring(project);assertThat(scoring.getResults(project).results()).singleElement().satisfies(r->{assertThat(r.status()).isEqualTo("PENDING");assertThat(r.missedReason()).contains("当前规则尚未评分");});}

 @Test void originalMultilineMaterialsReachActualAssessor(){var t=state.createTask(UUID.randomUUID().toString(),project,"PARSE",null,null);var fact=TextPrimaryScoreFactMapperTest.mappedCompleteRule();var q=new com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy.Qualification(fact.factId(),"s","IN_SCHEME",false,"SOURCE_UNVERIFIED","","评分标准",BigDecimal.TEN,"KNOWN","总分10分","t","SINGLE_TABLE",List.of("t"),com.xiyu.bid.scoreparse.domain.ChildAggregation.SUM);writer.freeze(project,t.getId(),com.xiyu.bid.scoreparse.domain.ScoreExtractionSnapshot.from(t.getTaskId(),List.of(fact),List.of(q),List.of()));t.setStatus("COMPLETED");tasks.saveAndFlush(t);activation.tryAutoActivate(project,t.getTaskId());scoring.triggerScoring(project);verify(analyzer).assessObjective(eq(TextPrimaryScoreFactMapperTest.COMPLETE_RULE),any(),anyString());}
 @Test void resultInsertFailureRollsBackDeleteAndCompletedStatus(){var i=item("a");scoring.triggerScoring(project);var old=results.findByScoreItemIdIn(List.of(i.getId())).getFirst();var task=tasks.saveAndFlush(ScoreParseTask.builder().taskId(UUID.randomUUID().toString()).projectId(project).taskType("SCORING").status("PENDING").itemSetHash(boundary.capture(project).fingerprint()).build());jdbc.execute("ALTER TABLE score_result ADD CONSTRAINT task3_reject_result CHECK (actual_score <> 7)");try{assertThatThrownBy(()->boundary.finish(task,List.of(ScoreResult.builder().scoreItemId(i.getId()).scoringTaskId(task.getId()).actualScore(new BigDecimal("7")).statusStage2("OK").build()),null)).isInstanceOf(RuntimeException.class);}finally{jdbc.execute("ALTER TABLE score_result DROP CHECK task3_reject_result");}assertThat(results.findById(old.getId())).isPresent().get().satisfies(r->assertThat(r.getActualScore()).isEqualByComparingTo("5"));assertThat(tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");}
 @Test void postCommitDispatchRejectionDoesNotReportCommittedTransactionAsFailed(){var called=new java.util.concurrent.atomic.AtomicInteger();var tx=new TransactionTemplate(txm);assertThatCode(()->tx.executeWithoutResult(st->{state.createTask(UUID.randomUUID().toString(),project,"PARSE",null,null);ScoreAfterCommit.dispatch(()->{called.incrementAndGet();throw new java.util.concurrent.RejectedExecutionException("fixture");});assertThat(called).hasValue(0);})).doesNotThrowAnyException();assertThat(called).hasValue(1);assertThat(tasks.findAll().stream().filter(t->t.getProjectId().equals(project))).hasSize(1);}

 @Test void scoringCreationWaitsForActivationProjectLock() throws Exception {try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor()){var entered=new java.util.concurrent.CountDownLatch(1);var future=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<ScoreParseTask>>();new TransactionTemplate(txm).executeWithoutResult(st->{tasks.lockProjectForTaskChange(project);future.set(pool.submit(()->{entered.countDown();return state.createTask(UUID.randomUUID().toString(),project,"SCORING",null,null);}));try{assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->future.get().get(200,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);}catch(InterruptedException ex){throw new RuntimeException(ex);}});assertThat(future.get().get(5,java.util.concurrent.TimeUnit.SECONDS).getId()).isNotNull();}}

 @Test void unsatisfiedUsesOldDangerToSelectNewRuleWithoutReusingOldScore() {
     var selected=item("a");
     when(analyzer.assessObjective(anyString(),any(),anyString())).thenAnswer(inv->{var out=new ScoreAssessmentOutput();out.quote="完整投标内容用于证书核验";out.actualScore=0d;return out;});
     scoring.triggerScoring(project);
     assertThat(results.findByScoreItemIdIn(List.of(selected.getId()))).singleElement().satisfies(r->assertThat(r.getStatusStage2()).isEqualTo("DANGER"));
     var changed=items.findById(selected.getId()).orElseThrow();
     changed.setDetail("新规则：完整合同及验收证明缺一不得分");items.saveAndFlush(changed);
     var unselected=item("b");
     clearInvocations(analyzer);
     when(analyzer.assessObjective(anyString(),any(),anyString())).thenAnswer(inv->{var out=new ScoreAssessmentOutput();out.quote="完整投标内容用于证书核验";out.actualScore=5d;return out;});
     scoring.triggerScoring(project,new ScoreScoringCommand("MANUAL","UNSATISFIED",List.of()));
     verify(analyzer,times(1)).assessObjective(eq(changed.getDetail()),any(),anyString());

     assertThat(results.findByScoreItemIdIn(List.of(selected.getId()))).singleElement().satisfies(r->assertThat(r.getActualScore()).isEqualByComparingTo("5"));
     assertThat(results.findByScoreItemIdIn(List.of(unselected.getId()))).singleElement().satisfies(r->assertThat(r.getActualScore()).isNull());
 }
 @Test void analyzerFailureClearsCachedProcessingAndStatusReadsRealFailed() {
     item("a");
     when(analyzer.assessObjective(anyString(),any(),anyString())).thenAnswer(inv->{
         assertThat(scoring.getStatus(project).status()).isEqualTo("PROCESSING");
         assertThat(cache).isNotEmpty();throw new IllegalStateException("fixture failure");
     });
     scoring.triggerScoring(project);
     assertThat(tasks.findAll().stream().filter(t->t.getProjectId().equals(project))).singleElement().satisfies(t->assertThat(t.getStatus()).isEqualTo("FAILED"));
     assertThat(scoring.getStatus(project).status()).isEqualTo("FAILED");
     assertThat(cache).isEmpty();
 }
 @Test void resultFinishFailureClearsCachedProcessing() {
     var item=item("a");
     jdbc.execute("ALTER TABLE score_result ADD CONSTRAINT fix1_reject_result CHECK (score_item_id <> "+item.getId()+" OR actual_score <> 5)");
     try {scoring.triggerScoring(project);} finally {jdbc.execute("ALTER TABLE score_result DROP CHECK fix1_reject_result");}
     assertThat(scoring.getStatus(project).status()).isEqualTo("FAILED");
     assertThat(cache).isEmpty();
 }
 @Test void failureFinishItselfThrowingStillClearsCachedProcessing() {
     item("a");
     when(analyzer.assessObjective(anyString(),any(),anyString())).thenThrow(new IllegalStateException("fixture"));
     jdbc.execute("ALTER TABLE score_parse_task ADD CONSTRAINT fix1_reject_failed CHECK (project_id <> "+project+" OR status <> 'FAILED')");
     try {scoring.triggerScoring(project);} finally {jdbc.execute("ALTER TABLE score_parse_task DROP CHECK fix1_reject_failed");}
     assertThat(cache).isEmpty();
     assertThat(tasks.findAll().stream().filter(t->t.getProjectId().equals(project))).singleElement().satisfies(t->assertThat(t.getStatus()).isEqualTo("PROCESSING"));
 }
 @Test void failedRunSuccessfulItemSurvivesRetryOfOnlyFailedItem() {
     var a=item("a");var b=item("b");
     when(analyzer.assessObjective(eq(b.getDetail()),any(),anyString())).thenAnswer(inv->{
         // Both fixtures have equal rules; use distinct rules below before triggering.
         var output=new ScoreAssessmentOutput();output.actualScore=5d;output.quote="完整投标内容用于证书核验";return output;
     });
     b.setDetail("B规则");items.saveAndFlush(b);
     when(analyzer.assessObjective(eq("B规则"),any(),anyString())).thenThrow(new IllegalStateException("fixture model timeout"));
     scoring.triggerScoring(project);
     var failed=latestScoring();
     assertThat(failed.getStatus()).isEqualTo("FAILED");
     assertThat(scoring.getResults(project).objectiveScored()).isZero();
     assertThat(scoring.getResults(project).objectivePending()).isEqualTo(1);
     assertThat(scoring.getResults(project).failed()).isEqualTo(1);
     when(analyzer.assessObjective(eq("B规则"),any(),anyString())).thenAnswer(inv->{var out=new ScoreAssessmentOutput();out.actualScore=5d;out.quote="完整投标内容用于证书核验";return out;});
     clearInvocations(analyzer);
     scoring.triggerScoring(project,new ScoreScoringCommand("MANUAL","ITEMS",List.of(b.getId())));
     assertThat(latestScoring().getStatus()).isEqualTo("COMPLETED");
     var stored=results.findByScoreItemIdIn(List.of(a.getId(),b.getId()));
     assertThat(stored).allSatisfy(r->{assertThat(r.getActualScore()).isEqualByComparingTo("5");assertThat(r.getScoringTaskId()).isEqualTo(latestScoring().getId());});
     var origins=new ScoringRunReportCodec().scoring(latestScoring().getRunSummary()).path("items");
     assertThat(java.util.stream.StreamSupport.stream(origins.spliterator(),false).filter(n->n.path("scoreItemId").asLong()==a.getId()).findFirst().orElseThrow().path("originTaskId").asLong()).isEqualTo(failed.getId());
     verify(analyzer,never()).assessObjective(eq(a.getDetail()),any(),anyString());
 }
 @Test void changedOriginalPartialRefusesWithoutDeletingStoredRowsAndReadSideExcludesOldScores() {
     var a=item("a");scoring.triggerScoring(project);
     var before=results.findByScoreItemIdIn(List.of(a.getId())).getFirst();
     when(loader.load(eq(project),anyString())).thenReturn(new BidFileLoadingCoordinator.LoadedBidDocument("新原件完整投标内容用于证书核验","different-hash"));
     clearInvocations(analyzer);
     scoring.triggerScoring(project,new ScoreScoringCommand("MANUAL","ITEMS",List.of(a.getId())));
     assertThat(latestScoring().getErrorMessage()).isEqualTo("SCORING_REQUIRES_ALL");
     assertThat(results.findById(before.getId())).isPresent().get().satisfies(r->assertThat(r.getActualScore()).isEqualByComparingTo("5"));
     var read=scoring.getResults(project);
     assertThat(read.summary().totalActualScore()).isEqualByComparingTo("0");
     assertThat(read.results()).singleElement().satisfies(r->{assertThat(r.actualScore()).isNull();assertThat(r.reuseKind()).isEqualTo("SOURCE_EXPIRED");});
     verify(analyzer,never()).assessObjective(anyString(),any(),anyString());
 }
 @Test void returningToOriginalAfterRefusedPartialPreservesUnselectedScore() {
     var selected=item("a");var unselected=item("b");
     unselected.setDetail("未选项规则：提供证书5分；提供附件5分；缺少附件不得分");items.saveAndFlush(unselected);
     scoring.triggerScoring(project);
     var original=latestScoring();
     var oldUnselected=results.findByScoreItemIdIn(List.of(unselected.getId())).getFirst();
     assertThat(oldUnselected.getActualScore()).isEqualByComparingTo("5");
     when(loader.load(eq(project),anyString())).thenReturn(
             new BidFileLoadingCoordinator.LoadedBidDocument("新原件完整投标内容用于证书核验","original-B"));
     scoring.triggerScoring(project,new ScoreScoringCommand("MANUAL","ITEMS",List.of(selected.getId())));
     assertThat(latestScoring().getErrorMessage()).isEqualTo("SCORING_REQUIRES_ALL");
     assertThat(results.findById(oldUnselected.getId())).isPresent().get()
             .satisfies(r->assertThat(r.getActualScore()).isEqualByComparingTo("5"));
     assertThat(scoring.getResults(project).summary().totalActualScore()).isEqualByComparingTo("0");
     when(loader.load(eq(project),anyString())).thenReturn(
             new BidFileLoadingCoordinator.LoadedBidDocument("第一章 服务方案\n完整投标内容用于证书核验","bid-hash"));
     clearInvocations(analyzer);
     scoring.triggerScoring(project,new ScoreScoringCommand("MANUAL","ITEMS",List.of(selected.getId())));
     var retry=latestScoring();
     assertThat(retry.getStatus()).isEqualTo("COMPLETED");
     assertThat(results.findByScoreItemIdIn(List.of(unselected.getId()))).singleElement().satisfies(r->{
         assertThat(r.getActualScore()).isEqualByComparingTo("5");
         assertThat(r.getScoringTaskId()).isEqualTo(retry.getId());
     });
     assertThat(scoring.getResults(project).summary().totalActualScore()).isEqualByComparingTo("0");
     var origins=new ScoringRunReportCodec().scoring(retry.getRunSummary()).path("items");
     assertThat(java.util.stream.StreamSupport.stream(origins.spliterator(),false)
             .filter(n->n.path("scoreItemId").asLong()==unselected.getId()).findFirst().orElseThrow()
             .path("originTaskId").asLong()).isEqualTo(original.getId());
     verify(analyzer,times(1)).assessObjective(eq(selected.getDetail()),any(),anyString());
     verify(analyzer,never()).assessObjective(eq(unselected.getDetail()),any(),anyString());
 }
 @Test void twoNormalNeedsReviewRunsDoNotOpenSharedTechnicalCircuit() {
     item("a");when(analyzer.assessObjective(anyString(),any(),anyString())).thenReturn(new ScoreAssessmentOutput());
     scoring.triggerScoring(project,new ScoreScoringCommand("AUTO","ALL",List.of()));
     scoring.triggerScoring(project,new ScoreScoringCommand("AUTO","ALL",List.of()));
     assertThat(latestScoring().getStatus()).isEqualTo("COMPLETED");
     assertThat(scoring.getResults(project).qualityStatus()).isEqualTo("NEEDS_REVIEW");
     assertThat(new ScoreParseAutoPolicy(tasks,items).circuitOpen(project)).isFalse();
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
 void changedOriginalTimeoutKeepsRowsButNeverPublishesOldOrLateScore(boolean scanner) {
     var a=item("a");scoring.triggerScoring(project);
     var old=results.findByScoreItemIdIn(List.of(a.getId())).getFirst();
     when(loader.load(eq(project),anyString())).thenReturn(new BidFileLoadingCoordinator.LoadedBidDocument("完整投标内容用于证书核验","original-B"));
     when(analyzer.assessObjective(anyString(),any(),anyString())).thenAnswer(inv->{
         var task=latestScoring();
         assertThat(task.getBidContentHash()).isEqualTo("original-B");
         jdbc.update("UPDATE score_parse_task SET created_at=? WHERE id=?",
                 java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusSeconds(1801)),task.getId());
         if(scanner)jdbc.update("UPDATE score_parse_task SET status='FAILED',timeout_marked=true,error_message='SCORING_DEADLINE_EXCEEDED',completed_at=CURRENT_TIMESTAMP WHERE id=?",task.getId());
         var out=new ScoreAssessmentOutput();out.actualScore=9d;out.quote="完整投标内容用于证书核验";return out;
     });
     scoring.triggerScoring(project,new ScoreScoringCommand("MANUAL","ALL",List.of()));
     assertThat(latestScoring().getStatus()).isEqualTo("FAILED");
     assertThat(latestScoring().getTimeoutMarked()).isTrue();
     assertThat(results.findById(old.getId())).isPresent().get().satisfies(r->assertThat(r.getActualScore()).isEqualByComparingTo("5"));
     var read=scoring.getResults(project);
     assertThat(read.summary().totalActualScore()).isEqualByComparingTo("0");
     assertThat(read.results()).singleElement().satisfies(r->{assertThat(r.actualScore()).isNull();assertThat(r.reuseKind()).isEqualTo("SOURCE_EXPIRED");});
 }
 @Test void oversizedRequiredReportKeepsPriorRowsAndPersistsFourKibFailure() {
     var i=item("a");scoring.triggerScoring(project);var old=results.findByScoreItemIdIn(List.of(i.getId())).getFirst();
     var pending=state.createTask(UUID.randomUUID().toString(),project,"SCORING",null,null);
     pending.setItemSetHash(boundary.capture(project).fingerprint());pending=tasks.saveAndFlush(pending);
     pending.setRunSummary("{\"scoring\":{\"input\":{\"fileHash\":\""+"x".repeat(70000)+"\"}}}");
     assertThat(boundary.finish(pending,List.of(ScoreResult.builder().scoreItemId(i.getId()).scoringTaskId(pending.getId()).actualScore(BigDecimal.ONE).build()),null)).isTrue();
     assertThat(results.findById(old.getId())).isPresent();
     var stored=tasks.findById(pending.getId()).orElseThrow();
     assertThat(stored.getStatus()).isEqualTo("FAILED");assertThat(stored.getErrorMessage()).isEqualTo("REPORT_TOO_LARGE");
     assertThat(stored.getRunSummary().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(4096);
 }
 @Test void expiredPendingTaskCannotAcquireAnotherThirtyMinutesAtStart() {
     item("a");var pending=state.createTask(UUID.randomUUID().toString(),project,"SCORING",null,null);
     pending.setItemSetHash(boundary.capture(project).fingerprint());tasks.saveAndFlush(pending);
     jdbc.update("UPDATE score_parse_task SET created_at = ? WHERE id = ?",java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusSeconds(1801)),pending.getId());
     assertThat(boundary.start(pending.getTaskId())).isNull();
     assertThat(tasks.findById(pending.getId()).orElseThrow().getTimeoutMarked()).isTrue();
     verify(loader,never()).openScoring(any(),anyString(),anyLong());
 }
 @Test void boundedChineseReportRoundTripsThroughRealMysqlText() {
     item("a");var pending=state.createTask(UUID.randomUUID().toString(),project,"SCORING",null,null);
     pending.setItemSetHash(boundary.capture(project).fingerprint());pending=tasks.saveAndFlush(pending);
     var report=Map.of("request",Map.of("source","MANUAL","scope","ALL","itemIds",List.of()),
             "input",Map.of("sourceDocumentId",1,"fileHash","fixture-hash","ruleFingerprint",pending.getItemSetHash()),
             "items",java.util.stream.LongStream.range(0,500).mapToObj(i->Map.of("scoreItemId",i,"outcome","SCORED","originTaskId",1,
                     "description","中文说明".repeat(300))).toList());
     var encoded=new ScoringRunReportCodec().encode(report);assertThat(encoded.tooLarge()).isFalse();pending.setRunSummary(encoded.json());
     assertThat(boundary.finish(pending,null,null)).isTrue();
     var stored=tasks.findById(pending.getId()).orElseThrow().getRunSummary();
     assertThat(stored.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(61440);
     assertThat(new ScoringRunReportCodec().scoring(stored).path("items").size()).isEqualTo(500);
 }
 private ScoreParseTask latestScoring(){return tasks.findAll().stream().filter(t->t.getProjectId().equals(project)&&"SCORING".equals(t.getTaskType())).max(Comparator.comparing(ScoreParseTask::getId)).orElseThrow();}

}

/** Embedded, no-listener coverage of the same transaction boundary; MySQL coverage remains above. */
@org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest(showSql=false, properties={
        "spring.datasource.url=jdbc:h2:mem:scoring-input-identity;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"})
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(
        replace=org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=ScoreScoringInputIdentityTest.Config.class)
@Transactional(propagation=Propagation.NEVER)
class ScoreScoringInputIdentityTest {
    @Configuration @EntityScan(basePackageClasses=ScoreParseTask.class)
    @EnableJpaRepositories(basePackageClasses=ScoreParseTaskRepository.class)
    @Import({ScoreScoringVersionService.class,ScoreExtractionCodec.class})
    static class Config { @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();} }
    @Autowired ScoreScoringVersionService boundary;
    @Autowired ScoreParseTaskRepository tasks;
    @Autowired ScoreItemRepository items;
    @Autowired ScoreResultRepository results;
    @Autowired JdbcTemplate jdbc;
    final ScoringRunReportCodec reports=new ScoringRunReportCodec();
    final com.xiyu.bid.scoreparse.domain.ScoringExtractionReport coverage=
            new com.xiyu.bid.scoreparse.domain.ScoringExtractionReport(1,"fixture","COMPLETE",1,1,1,0,0,0,List.of(),List.of(),List.of(),0);
    long project;
    ScoreItem item;
    ScoreResult oldRow;
    ScoreParseTask pending;
    BidFileLoadingCoordinator loader;
    OpenAiScoreAnalyzer analyzer;
    SidecarStreamTextExtractor texts;

    @BeforeEach void setup() {
        loader=mock(BidFileLoadingCoordinator.class);analyzer=mock(OpenAiScoreAnalyzer.class);
        texts=mock(SidecarStreamTextExtractor.class);
        jdbc.execute("CREATE TABLE IF NOT EXISTS projects(id BIGINT PRIMARY KEY)");
        project=System.nanoTime();jdbc.update("INSERT INTO projects(id) VALUES(?)",project);
        item=items.saveAndFlush(ScoreItem.builder().projectId(project).parseTaskId(1L).itemIndex(1).code("a").dim("证书")
                .detail("提供完整证书得5分").weight(BigDecimal.TEN).scoreType("OBJECTIVE").statusStage1("PENDING").build());
        var owner=newTask();owner.setStatus("COMPLETED");owner.setBidContentHash("original-A");
        oldRow=ScoreResult.builder().scoreItemId(item.getId()).scoringTaskId(owner.getId())
                .actualScore(new BigDecimal("5")).statusStage2("OK").reuseKind("FRESH").quote("完整证书").build();
        owner.setRunSummary(reports.bindPolicy(owner.getRunSummary(),"policy"));
        owner.setRunSummary(reports.complete(owner,1L,coverage,List.of(oldRow),Map.of(),null,0,0).json());
        tasks.saveAndFlush(owner);oldRow=results.saveAndFlush(oldRow);
        assertThat(boundary.read(project).summary().totalActualScore()).isEqualByComparingTo("5");
        pending=newTask();
    }
    ScoreParseTask newTask() {
        return tasks.saveAndFlush(ScoreParseTask.builder().taskId(UUID.randomUUID().toString()).projectId(project)
                .taskType("SCORING").status("PROCESSING").stage("ALL").triggerSource("MANUAL")
                .pipelineVersion("SCORING_VERSION_V1").itemSetHash(boundary.capture(project).fingerprint())
                .runSummary(reports.request(new ScoreScoringCommand("MANUAL","ALL",List.of()))).build());
    }
    void expire(boolean scanner) {
        jdbc.update("UPDATE score_parse_task SET created_at=? WHERE id=?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusSeconds(1801)),pending.getId());
        if(scanner) jdbc.update("UPDATE score_parse_task SET status='FAILED',timeout_marked=true,error_message='SCORING_DEADLINE_EXCEEDED',completed_at=CURRENT_TIMESTAMP WHERE id=?",pending.getId());
    }
    void execute() {
        new ScoreScoringExecution(loader,analyzer,new com.xiyu.bid.scoreparse.domain.ImageBudget(0,0),boundary,(value,text)->{})
                .execute(pending,boundary.capture(project));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"original-B,false,false", "original-B,true,false", "original-B,true,true", "original-A,false,false"})
    void timeoutRetainsOriginalRowsButExcludesOnlyChangedOriginal(String hash,boolean scanner,boolean beforeRecord) throws Exception {
        when(loader.openScoring(eq(project),eq(pending.getTaskId()),anyLong())).thenAnswer(inv->{
            if(beforeRecord)expire(true);
            return new ScoringFileSession(2L,hash,new LoadedBidFile.InMemory(new byte[]{1},"bid.pdf","application/pdf"),
                    texts,null,inv.getArgument(2),()->{});
        });
        // Only provider/Sidecar IO is replaced. Execution, persistence, reads and summary are real.
        lenient().when(analyzer.scoringConfiguration()).thenReturn(new com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig(
                "fixture","http://fixture.invalid/v1","fixture",java.time.Duration.ofSeconds(1),
                com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentApiStyle.CHAT_COMPLETIONS));
        lenient().when(texts.extractScoringDocument(any(byte[].class),anyString(),anyLong())).thenAnswer(inv->{
            // This happens after the hash is known, before finish can persist any scoring report.
            expire(scanner);throw new IllegalStateException("SCORING_DEADLINE_EXCEEDED");
        });
        execute();
        var stored=tasks.findById(pending.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("FAILED");assertThat(stored.getTimeoutMarked()).isTrue();
        assertThat(stored.getBidContentHash()).isEqualTo(hash);
        assertThat(reports.scoring(stored.getRunSummary()).path("input").path("fileHash").asText()).isEqualTo(hash);
        assertThat(reports.scoring(stored.getRunSummary()).path("input").path("sourceDocumentId").asLong()).isEqualTo(2L);
        // Even a late successful model response must not overwrite the retained original result.
        assertThat(boundary.finish(pending,List.of(ScoreResult.builder().scoreItemId(item.getId()).scoringTaskId(pending.getId())
                .actualScore(new BigDecimal("9")).statusStage2("OK").build()),null)).isFalse();
        assertThat(results.findById(oldRow.getId())).isPresent().get().satisfies(r->{
            assertThat(r.getActualScore()).isEqualByComparingTo("5");assertThat(r.getScoringTaskId()).isEqualTo(oldRow.getScoringTaskId());
        });
        var read=boundary.read(project);
        if("original-B".equals(hash)) {
            assertThat(read.summary().totalActualScore()).isEqualByComparingTo("0");
            assertThat(read.objectiveScored()).isZero();
            assertThat(read.results()).singleElement().satisfies(r->{assertThat(r.actualScore()).isNull();assertThat(r.reuseKind()).isEqualTo("SOURCE_EXPIRED");});
        } else assertThat(read.summary().totalActualScore()).isEqualByComparingTo("5");
        if(beforeRecord)verifyNoInteractions(analyzer,texts);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void supersededInputCannotInvalidateCurrentScores(boolean newerTask) {
        when(loader.openScoring(eq(project),eq(pending.getTaskId()),anyLong())).thenAnswer(inv->{
            if(newerTask)newTask();
            else {item.setDetail("已生效的新评分规则");items.saveAndFlush(item);}
            return new ScoringFileSession(2L,"obsolete-B",new LoadedBidFile.InMemory(new byte[]{1},"bid.pdf","application/pdf"),
                    texts,null,inv.getArgument(2),()->{});
        });
        execute();
        var stored=tasks.findById(pending.getId()).orElseThrow();
        assertThat(stored.getBidContentHash()).isNull();
        assertThat(reports.scoring(stored.getRunSummary()).path("input").hasNonNull("fileHash")).isFalse();
        assertThat(results.findById(oldRow.getId())).isPresent().get().satisfies(r->assertThat(r.getActualScore()).isEqualByComparingTo("5"));
        verifyNoInteractions(analyzer,texts);
    }
}
