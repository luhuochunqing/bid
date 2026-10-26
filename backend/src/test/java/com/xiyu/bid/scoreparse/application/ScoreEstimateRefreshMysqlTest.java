package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.businessqualification.domain.valueobject.*;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.scoreparse.application.match.*;
import com.xiyu.bid.scoreparse.entity.*;
import com.xiyu.bid.scoreparse.repository.*;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.test.AbstractTransactionBoundaryTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.*;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL/JPA estimate transactions; permission denial uses the real project guard.
 * Ordinary-user scope is covered through standalone MVC; authentication filters remain a separate gate. */
@SpringBootTest(classes=ScoreEstimateRefreshMysqlTest.Config.class,properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","spring.jpa.open-in-view=false"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation=Propagation.NEVER)
class ScoreEstimateRefreshMysqlTest extends AbstractTransactionBoundaryTest {
 static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0");
 @DynamicPropertySource static void db(DynamicPropertyRegistry r){
  String url=System.getProperty("certificate.test.mysql.url");
  if(url==null){MYSQL.start();r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
  else {if(!url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/cert_score_test_[a-zA-Z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Only isolated local cert_score_test_* schemas are allowed");
   r.add("spring.datasource.url",()->url);r.add("spring.datasource.username",()->"root");r.add("spring.datasource.password",()->"");}
 }
 @AfterAll static void stop(){if(MYSQL.isRunning())MYSQL.stop();}
 @Configuration @EnableAutoConfiguration
 @EntityScan(basePackageClasses={ScoreItem.class,BusinessQualificationEntity.class})
 @EnableJpaRepositories(basePackageClasses={ScoreItemRepository.class,BusinessQualificationJpaRepository.class})
 @Import({CertMatchService.class,EstimatedScoreService.class,ScoreScoringVersionService.class,ScoreExtractionCodec.class,ScoreEstimateFreshness.class,ScoreEstimateRefreshService.class})
 static class Config {
  @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}
  @Bean ProjectAccessScopeService access(){return new ProjectAccessScopeService(
   mock(com.xiyu.bid.repository.UserRepository.class),mock(com.xiyu.bid.repository.ProjectRepository.class),
   mock(com.xiyu.bid.admin.service.DataScopeConfigService.class),mock(com.xiyu.bid.admin.service.ProjectGroupService.class),
   mock(com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository.class),mock(com.xiyu.bid.matrixcollaboration.repository.CrmCustomerPermissionRepository.class),
   mock(com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository.class),mock(com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository.class),
   mock(com.xiyu.bid.repository.TaskRepository.class),mock(com.xiyu.bid.project.repository.BidDocumentReviewRepository.class),mock(com.xiyu.bid.security.EffectiveRoleResolver.class));}
 }
 @MockBean PersonMatchService people;
 @MockBean ProjectMatchService projects;
 @MockBean WarehouseMatchService warehouses;
 @MockBean BrandMatchService brands;
 @Autowired ScoreItemRepository items;
 @Autowired BusinessQualificationJpaRepository certificates;
 @Autowired ScoreScoringVersionService versions;
 @Autowired ScoreEstimateRefreshService refresh;
 @Autowired ScoreEstimateFreshness freshness;
 @Autowired ProjectAccessScopeService access;
 @Autowired JdbcTemplate jdbc;
 long project;
 static final String RULE="具有ISO9001质量管理体系认证、ISO14001环境管理体系认证、ISO45001职业健康安全管理体系认证的得10分。三项齐全得满分，缺少一项，不得分";
 @BeforeEach void setup(){
  SecurityContextHolder.clearContext();certificates.deleteAll();
  jdbc.execute("CREATE TABLE IF NOT EXISTS projects(id BIGINT PRIMARY KEY)");
  project=System.nanoTime();jdbc.update("INSERT INTO projects(id) VALUES(?)",project);
 }
 @AfterEach void clearSecurity(){SecurityContextHolder.clearContext();}
 ScoreItem item(){return items.saveAndFlush(ScoreItem.builder().projectId(project).parseTaskId(1L).itemIndex(1).code("A1").dim("管理体系").detail(RULE).weight(BigDecimal.TEN).scoreType("OBJECTIVE").statusStage1("OK").estScore(BigDecimal.TEN).build());}
 void allCertificates(){for(String name:List.of("ISO9001","ISO14001","ISO45001"))certificates.saveAndFlush(BusinessQualificationEntity.builder().name(name).subjectName("isolated fixture").subjectType(QualificationSubjectType.values()[0]).category(QualificationCategory.values()[0]).status(QualificationStatus.IN_STOCK).expiryDate(LocalDate.now().plusYears(1)).build());}
 void authorizeExternalApi(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("integration",null,List.of(new SimpleGrantedAuthority("ROLE_EXTERNAL_API"))));}
 ScoreItem read(Long id){return items.findById(id).orElseThrow();}
 @Test void explicitDimensionsWithUnknownSyntaxCannotKeepHistoricalFullScore(){
  var ids=new java.util.ArrayList<Long>();
  for(String dim:List.of("企业荣誉","管理体系","企业资质")) {
   for(String detail:List.of("投标人具有中国品牌信誉评价认证五星级证书得5分","须具备相关认证证书","任意未知条件","")) {
    var old=item();old.setDim(dim);old.setDetail(detail);items.saveAndFlush(old);ids.add(old.getId());
    assertThat(freshness.status(old,LocalDate.now())).as(dim+":"+detail).isEqualTo("STALE");
   }
  }
  allCertificates();authorizeExternalApi();refresh.refresh(project);
  for(Long id:ids){var pending=read(id);assertThat(pending.getEstScore()).isNull();
   assertThat(pending.getStatusStage1()).isEqualTo("PENDING");assertThat(pending.getKbHit()).isFalse();
   assertThat(freshness.status(pending,LocalDate.now())).isEqualTo("FRESH");}
 }
 @Test void unknownProofConditionsRefreshHistoricalFullScoreToPending(){
  var old=item();old.setDim("企业荣誉");old.setDetail("具有中国品牌信誉评价认证五星级证书；2、证明文件：投标人须提供认证证书扫描件，原件备查并须另具CMMI证书");
  items.saveAndFlush(old);assertThat(freshness.status(old,LocalDate.now())).isEqualTo("STALE");
  authorizeExternalApi();refresh.refresh(project);var pending=read(old.getId());
  assertThat(pending.getEstScore()).isNull();assertThat(pending.getStatusStage1()).isEqualTo("PENDING");
  assertThat(freshness.status(pending,LocalDate.now())).isEqualTo("FRESH");
 }
 @Test void legacyRefreshRetirementAndDateAreRealPersistedTransitions(){
  var old=item();allCertificates();assertThat(freshness.status(old,LocalDate.now())).isEqualTo("STALE");
  authorizeExternalApi();refresh.refresh(project);var fresh=read(old.getId());
  assertThat(fresh.getEstScore()).isEqualByComparingTo("10");assertThat(fresh.getEstimatedAt()).isNotNull();
  assertThat(freshness.status(fresh,LocalDate.now())).isEqualTo("FRESH");
  assertThat(freshness.status(fresh,LocalDate.now().plusDays(1))).isEqualTo("STALE");
  var cert=certificates.findAll().getFirst();cert.setRetired(true);certificates.saveAndFlush(cert);
  assertThat(freshness.status(read(old.getId()),LocalDate.now())).isEqualTo("STALE");
  refresh.refresh(project);var missing=read(old.getId());assertThat(missing.getEstScore()).isZero();
  assertThat(missing.getStatusStage1()).isEqualTo("DANGER");assertThat(freshness.status(missing,LocalDate.now())).isEqualTo("FRESH");
 }
 @Test void refreshPreservesCandidateRowsAndStageTwoBytes(){
  var old=item();allCertificates();
  jdbc.update("INSERT INTO score_result(score_item_id,scoring_task_id,actual_score,status_stage2,evidence,created_at,updated_at) VALUES(?,1,7,'PENDING','immutable stage2',NOW(),NOW())",old.getId());
  jdbc.update("INSERT INTO score_extraction_snapshot(parse_task_id,project_id,base_fingerprint,payload) VALUES(?,?,?,?)",project,project,"unadopted","{\"candidate\":\"pending review\"}");
  var stage2=jdbc.queryForList("SELECT * FROM score_result WHERE score_item_id=?",old.getId());
  var candidates=jdbc.queryForList("SELECT * FROM score_extraction_snapshot WHERE project_id=?",project);
  authorizeExternalApi();refresh.refresh(project);
  assertThat(jdbc.queryForList("SELECT * FROM score_result WHERE score_item_id=?",old.getId())).isEqualTo(stage2);
  assertThat(jdbc.queryForList("SELECT * FROM score_extraction_snapshot WHERE project_id=?",project)).isEqualTo(candidates);
  assertThat(read(old.getId()).getDetail()).isEqualTo(RULE);assertThat(read(old.getId()).getWeight()).isEqualByComparingTo("10");
 }
 @Test void ruleChangeRejectsOldCalculatedWrite(){
  var old=item();allCertificates();var captured=versions.capture(project);
  var changed=read(old.getId());changed.setDetail(RULE+"新规则补充");items.saveAndFlush(changed);
  old.setEstScore(BigDecimal.ZERO);old.setEstBasis("must never land");
  assertThat(versions.saveEstimates(project,captured.fingerprint(),List.of(old))).isFalse();
  assertThat(read(old.getId()).getEstBasis()).isNull();assertThat(read(old.getId()).getDetail()).endsWith("新规则补充");
 }
 @Test void certificateChangeRejectsOldCalculatedWrite(){
  var old=item();allCertificates();authorizeExternalApi();refresh.refresh(project);
  var captured=versions.capture(project);var calculated=read(old.getId());
  var cert=certificates.findAll().getFirst();cert.setRetired(true);certificates.saveAndFlush(cert);
  assertThat(versions.saveEstimates(project,captured.fingerprint(),List.of(calculated))).isFalse();
  assertThat(freshness.status(read(old.getId()),LocalDate.now())).isEqualTo("STALE");
 }
 @Test void oneFailedItemDoesNotBlockOtherFreshRows() {
  var failed=item();var good=items.saveAndFlush(ScoreItem.builder().projectId(project).parseTaskId(1L).itemIndex(2).code("A2").dim("管理体系").detail(RULE).weight(BigDecimal.TEN).scoreType("OBJECTIVE").statusStage1("PENDING").build());
  allCertificates();authorizeExternalApi();refresh.refresh(project);var captured=versions.capture(project);
  failed=read(failed.getId());good=read(good.getId());
  failed.setEstScore(null);failed.setStatusStage1("PENDING");failed.setKbHit(false);
  failed.setEstimateInputFingerprint(null);failed.setEstimatedAt(null);failed.setEstBasis("预计得分计算失败，待人工确认");
  CertificateScoreEstimateSupport.calculate(good,new CertMatchService(certificates),LocalDate.now());
  var computedAt=good.getEstimatedAt();
  assertThat(versions.saveEstimates(project,captured.fingerprint(),List.of(failed,good))).isTrue();
  assertThat(read(failed.getId()).getEstScore()).isNull();
  assertThat(read(good.getId()).getEstimatedAt()).isEqualTo(computedAt.withNano(computedAt.getNano()/1000*1000));
 }
 /** Standalone MVC proves controller/service/real guard/403 mapping, not authentication filters. */
 @Test void ordinaryMemberRefreshesAuthorizedProjectButCrossProjectHttp403NeverWrites() throws Exception {
  var own=item();allCertificates();long deniedProject=project+1;
  jdbc.update("INSERT INTO projects(id) VALUES(?)",deniedProject);
  var denied=items.saveAndFlush(ScoreItem.builder().projectId(deniedProject).parseTaskId(1L).itemIndex(1)
   .code("DENIED").dim("管理体系").detail(RULE).weight(BigDecimal.TEN).scoreType("OBJECTIVE")
   .statusStage1("OK").estScore(BigDecimal.TEN).build());
  var guard=org.springframework.test.util.AopTestUtils.<ProjectAccessScopeService>getUltimateTargetObject(access);
  var users=(com.xiyu.bid.repository.UserRepository)org.springframework.test.util.ReflectionTestUtils.getField(guard,"userRepository");
  var scopes=(com.xiyu.bid.admin.service.DataScopeConfigService)org.springframework.test.util.ReflectionTestUtils.getField(guard,"dataScopeConfigService");
  var roles=(com.xiyu.bid.security.EffectiveRoleResolver)org.springframework.test.util.ReflectionTestUtils.getField(guard,"effectiveRoleResolver");
  var member=com.xiyu.bid.entity.User.builder().id(42L).username("member").build();
  when(users.findByUsername("member")).thenReturn(java.util.Optional.of(member));
  when(roles.resolveRoleCode(member)).thenReturn("bid-Specialist");
  when(scopes.getAccessProfile(member)).thenReturn(com.xiyu.bid.admin.service.DataScopeAccessProfile.builder().explicitProjectIds(List.of(project)).build());
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("member",null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  var parser=mock(ScoreParseAppService.class);
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
   new com.xiyu.bid.scoreparse.controller.ScoreEstimateController(refresh,parser))
   .setControllerAdvice(new com.xiyu.bid.exception.GlobalExceptionHandler()).build();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/projects/"+project+"/score-parse/estimates/refresh")
   .accept(org.springframework.http.MediaType.APPLICATION_JSON)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  assertThat(freshness.status(read(own.getId()),LocalDate.now())).isEqualTo("FRESH");
  assertThat(read(own.getId()).getEstScore()).isEqualByComparingTo("10");verify(parser).getItems(project);
  var deniedBefore=jdbc.queryForMap("SELECT * FROM score_item WHERE id=?",denied.getId());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/projects/"+deniedProject+"/score-parse/estimates/refresh")
   .accept(org.springframework.http.MediaType.APPLICATION_JSON)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
  assertThat(jdbc.queryForMap("SELECT * FROM score_item WHERE id=?",denied.getId())).isEqualTo(deniedBefore);
  verify(parser,never()).getItems(deniedProject);
 }
 @Test void changedPersistedEstimateCannotBorrowFreshInputStamp() {
  var old=item();allCertificates();authorizeExternalApi();refresh.refresh(project);
  var overwritten=read(old.getId());overwritten.setEstScore(new BigDecimal("9"));
  overwritten.setEstBasis("legacy writer changed score without updating calculation identity");
  items.saveAndFlush(overwritten);
  assertThat(freshness.status(read(old.getId()),LocalDate.now())).isEqualTo("STALE");
 }
 @Test void unauthenticatedGuardRejectsBeforeAnyEstimateWrite(){
  var old=item();allCertificates();
  assertThatThrownBy(()->refresh.refresh(project)).isInstanceOf(AccessDeniedException.class);
  var after=read(old.getId());assertThat(after.getEstimateInputFingerprint()).isNull();assertThat(after.getEstimatedAt()).isNull();assertThat(after.getEstScore()).isEqualByComparingTo("10");
 }
}
