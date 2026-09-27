package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.application.match.*;
import com.xiyu.bid.scoreparse.domain.BidScoreSkipPolicy;
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
import com.xiyu.bid.scoreparse.infrastructure.openai.*;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.test.AbstractTransactionBoundaryTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(classes=ScoreScoringVersionMysqlTest.Config.class,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.jpa.open-in-view=false","app.score-parse.extraction-version.enabled=false","spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=1"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.context.annotation.Import({ScoreParseReviewAppService.class,ScoreItemPersistenceService.class})
@Transactional(propagation=Propagation.NEVER)
class LegacyScoringVersionMysqlTest extends AbstractTransactionBoundaryTest {
 @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0");
 @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
 @MockBean ProjectAccessScopeService access;
 @MockBean BidFileLoadingCoordinator loader;
 @MockBean OpenAiScoreAnalyzer analyzer;
 @MockBean CertMatchService certs;
 @MockBean PersonMatchService people;
 @MockBean ProjectMatchService projects;
 @MockBean WarehouseMatchService warehouses;
 @MockBean BrandMatchService brands;
 @Autowired ScoreParseReviewAppService review;
 @Autowired ScoreParseFactRepository facts;
 @Autowired ScoreParseEvidenceRepository evidence;
 @Autowired org.springframework.transaction.PlatformTransactionManager txm;
 @Autowired ScoreScoringAppService scoring;
 @Autowired ScoreScoringVersionService boundary;
 @Autowired ScoreExtractionVersionFeature feature;
 @Autowired ScoreParseTaskRepository tasks;
 @Autowired ScoreItemRepository items;
 @Autowired ScoreResultRepository results;
 @Autowired JdbcTemplate jdbc;
 long project;
 @BeforeEach void setup(){reset(certs,analyzer,loader);jdbc.execute("CREATE TABLE IF NOT EXISTS projects(id BIGINT PRIMARY KEY)");project=System.nanoTime();jdbc.update("INSERT INTO projects(id) VALUES(?)",project);}
 ScoreItem legacy(){var i=items.saveAndFlush(ScoreItem.builder().projectId(project).parseTaskId(1L).itemIndex(1).code("a").dim("资格").detail("完整规则").weight(new BigDecimal("10.00")).scoreType("OBJECTIVE").statusStage1("PENDING").build());String oldHash=BidScoreSkipPolicy.hashItems(List.of(BidScoreSkipPolicy.itemFingerprint(i.getId(),i.getWeight(),i.getDetail())));var t=tasks.saveAndFlush(ScoreParseTask.builder().taskId(UUID.randomUUID().toString()).projectId(project).taskType("SCORING").status("COMPLETED").itemSetHash(oldHash).bidContentHash("bid-hash").fileUrl("fixture://bid").build());results.saveAndFlush(ScoreResult.builder().scoreItemId(i.getId()).scoringTaskId(t.getId()).actualScore(new BigDecimal("8.6")).statusStage2("PENDING").evidence("历史材料").reuseKind("FRESH").build());return i;}
 @Test void featureOffKeepsLegacyVisibleWithoutCallingItCurrentOrReusable(){assertThat(feature.enabled()).isFalse();var i=legacy();var response=scoring.getResults(project);assertThat(response.results()).singleElement().satisfies(r->{assertThat(r.actualScore()).isEqualByComparingTo("8.6");assertThat(r.evidence()).isEqualTo("历史材料");assertThat(r.reuseKind()).isEqualTo("LEGACY_UNVERIFIED");assertThat(r.missedReason()).contains("历史评分");});assertThat(response.summary().totalActualScore()).isZero();assertThat(boundary.capture(project).results()).isEmpty();when(loader.findBidDocuments(project)).thenReturn(List.of(ProjectDocument.builder().name("bid").fileUrl("fixture://bid").build()));when(loader.load(eq(project),anyString())).thenReturn(new BidFileLoadingCoordinator.LoadedBidDocument("投标内容","bid-hash"));when(analyzer.assessObjective(anyString(),any(),anyString())).thenAnswer(inv->{var o=new ScoreAssessmentOutput();o.actualScore=5d;return o;});scoring.triggerScoring(project);verify(analyzer).assessObjective(anyString(),any(),anyString());assertThat(scoring.getResults(project).results()).singleElement().satisfies(r->assertThat(r.reuseKind()).isEqualTo("FRESH"));}
 @Test void featureOffDoesNotRelaxVersionedResultGuard(){var i=legacy();i.setActiveVersionId(987L);items.saveAndFlush(i);assertThat(scoring.getResults(project).results()).singleElement().satisfies(r->{assertThat(r.actualScore()).isNull();assertThat(r.status()).isEqualTo("PENDING");});}

 com.xiyu.bid.scoreparse.dto.ScoreParseReviewCommand adoption() {
     var task=tasks.saveAndFlush(ScoreParseTask.builder().taskId(UUID.randomUUID().toString()).projectId(project).taskType("PARSE").status("COMPLETED").decisionStatus("REVIEW_REQUIRED").build());
     facts.saveAndFlush(ScoreParseFact.builder().projectId(project).parseTaskId(task.getId()).factId("f").code("a").dimension("营业执照证书").sourceText("提供营业执照证书得10分").weight(BigDecimal.TEN).extractionMethod("MODEL").build());
     evidence.saveAndFlush(ScoreParseEvidence.builder().parseTaskId(task.getId()).factId("f").kind("SCHEME_QUALIFICATION").explanation("{\"schemeId\":\"s\",\"scopeStatus\":\"IN_SCHEME\",\"candidateEligible\":true,\"declaredTotal\":10,\"totalEvidenceStatus\":\"VERIFIED\",\"tableIds\":[\"t\"]}").build());
     return new com.xiyu.bid.scoreparse.dto.ScoreParseReviewCommand(List.of(new com.xiyu.bid.scoreparse.dto.ScoreParseReviewCommand.Action("f","ADOPT_CANDIDATES",null,"确认")),"确认",task.getId());
 }
 @Test void legacyReviewEstimatesAfterActualCallerCommit() {
     var command=adoption();
     when(certs.match(any())).thenReturn(com.xiyu.bid.scoreparse.dto.KnowledgeMatchResult.empty("fixture"));
     new org.springframework.transaction.support.TransactionTemplate(txm).executeWithoutResult(st->{
         review.submitReview(project,command,"reviewer");
         verifyNoInteractions(certs);
         assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).singleElement().satisfies(i->assertThat(i.getEstBasis()).isNull());
     });
     verify(certs,times(1)).match(any());
     assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).singleElement().satisfies(i->assertThat(i.getEstBasis()).isNotBlank());
 }
 @Test void legacyReviewRollbackDoesNotRunEstimation() {
     var command=adoption();
     new org.springframework.transaction.support.TransactionTemplate(txm).executeWithoutResult(st->{
         review.submitReview(project,command,"reviewer");
         st.setRollbackOnly();
     });
     verifyNoInteractions(certs);
     assertThat(items.findByProjectIdOrderByItemIndexAsc(project)).isEmpty();
     assertThat(tasks.findById(command.parseTaskId()).orElseThrow().getDecisionStatus()).isEqualTo("REVIEW_REQUIRED");
 }
}
