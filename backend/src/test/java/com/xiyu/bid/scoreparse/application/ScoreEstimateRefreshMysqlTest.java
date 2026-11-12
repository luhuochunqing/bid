package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.businessqualification.domain.valueobject.*;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.scoreparse.application.match.*;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceRecordEntity;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceRecordJpaRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.domain.WarehouseType;
import com.xiyu.bid.scoreparse.dto.ScoreParseItemsDTO;
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
 @EntityScan(basePackageClasses={ScoreItem.class,BusinessQualificationEntity.class,PerformanceRecordEntity.class,WarehouseEntity.class,com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.ManufacturerAuthorizationEntity.class})
 @EnableJpaRepositories(basePackageClasses={ScoreItemRepository.class,BusinessQualificationJpaRepository.class,PerformanceRecordJpaRepository.class,WarehouseRepository.class,com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.ManufacturerAuthorizationJpaRepository.class})
 @Import({BrandMatchService.class,CertMatchService.class,ProjectMatchService.class,WarehouseMatchService.class,EstimatedScoreService.class,ScoreScoringVersionService.class,ScoreExtractionCodec.class,ScoreEstimateFreshness.class,ScoreEstimateRefreshService.class})
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
 @Autowired com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.ManufacturerAuthorizationJpaRepository brandRecords;
 @Autowired ScoreItemRepository items;
 @Autowired BusinessQualificationJpaRepository certificates;
 @Autowired PerformanceRecordJpaRepository performances;
 @Autowired WarehouseRepository warehouseRecords;
 @Autowired ScoreScoringVersionService versions;
 @Autowired ScoreEstimateRefreshService refresh;
 @Autowired ScoreEstimateFreshness freshness;
 @Autowired ProjectAccessScopeService access;
 @Autowired JdbcTemplate jdbc;
 long project;
 static final String RULE="具有ISO9001质量管理体系认证、ISO14001环境管理体系认证、ISO45001职业健康安全管理体系认证的得10分。三项齐全得满分，缺少一项，不得分";
 @BeforeEach void setup(){
  SecurityContextHolder.clearContext();certificates.deleteAll();performances.deleteAll();warehouseRecords.deleteAll();brandRecords.deleteAll();
  jdbc.execute("CREATE TABLE IF NOT EXISTS projects(id BIGINT PRIMARY KEY)");
  project=System.nanoTime();jdbc.update("INSERT INTO projects(id) VALUES(?)",project);
 }
 @AfterEach void clearSecurity(){SecurityContextHolder.clearContext();}
 ScoreItem item(){return items.saveAndFlush(ScoreItem.builder().projectId(project).parseTaskId(1L).itemIndex(1).code("A1").dim("管理体系").detail(RULE).weight(BigDecimal.TEN).scoreType("OBJECTIVE").statusStage1("OK").estScore(BigDecimal.TEN).build());}
 void allCertificates(){for(String name:List.of("ISO9001","ISO14001","ISO45001"))certificates.saveAndFlush(BusinessQualificationEntity.builder().name(name).subjectName("isolated fixture").subjectType(QualificationSubjectType.values()[0]).category(QualificationCategory.values()[0]).status(QualificationStatus.IN_STOCK).expiryDate(LocalDate.now().plusYears(1)).build());}
 void authorizeExternalApi(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("integration",null,List.of(new SimpleGrantedAuthority("ROLE_EXTERNAL_API"))));}
 ScoreItem read(Long id){return items.findById(id).orElseThrow();}
 @Test void unknownScoringSyntaxStillProducesCountBasedCertificateEstimates(){
  var ids=new java.util.ArrayList<Long>();
  for(String dim:List.of("企业荣誉","管理体系","企业资质")) {
   for(String detail:List.of("投标人具有中国品牌信誉评价认证五星级证书得5分","须具备相关认证证书","任意未知条件","")) {
    var old=item();old.setDim(dim);old.setDetail(detail);items.saveAndFlush(old);ids.add(old.getId());
    assertThat(freshness.status(old,LocalDate.now())).as(dim+":"+detail).isEqualTo("STALE");
   }
  }
  allCertificates();authorizeExternalApi();refresh.refresh(project);
  for(Long id:ids){var estimated=read(id);assertThat(estimated.getEstScore()).isNotNull();
   assertThat(estimated.getEstBasis()).contains("知识库粗估");
   if(estimated.getDetail().contains("品牌信誉")) assertThat(estimated.getEstScore()).isZero();
   assertThat(freshness.status(estimated,LocalDate.now())).isEqualTo("FRESH");}
 }
 @Test void missingNamedCertificateEvidenceProducesNumericZeroEvenWithProofConditions(){
  var old=item();old.setDim("企业荣誉");old.setDetail("具有中国品牌信誉评价认证五星级证书；2、证明文件：投标人须提供认证证书扫描件，原件备查并须另具CMMI证书");
  items.saveAndFlush(old);assertThat(freshness.status(old,LocalDate.now())).isEqualTo("STALE");
  authorizeExternalApi();refresh.refresh(project);var pending=read(old.getId());
  assertThat(pending.getEstScore()).isZero();assertThat(pending.getStatusStage1()).isEqualTo("DANGER");
  assertThat(freshness.status(pending,LocalDate.now())).isEqualTo("FRESH");
 }
 @Test void legacyRefreshRetirementAndDateAreRealPersistedTransitions(){
  var old=item();allCertificates();assertThat(freshness.status(old,LocalDate.now())).isEqualTo("STALE");
  authorizeExternalApi();refresh.refresh(project);var fresh=read(old.getId());
  assertThat(fresh.getEstScore()).isEqualByComparingTo("10");assertThat(fresh.getEstimatedAt()).isNotNull();
  assertThat(freshness.status(fresh,LocalDate.now())).isEqualTo("FRESH");
  // Current fingerprint excludes the wall-clock day; unchanged, unexpired evidence stays fresh.
  assertThat(freshness.status(fresh,LocalDate.now().plusDays(1))).isEqualTo("FRESH");
  assertThat(freshness.status(fresh,LocalDate.now().plusYears(1).plusDays(1))).isEqualTo("STALE");
  var cert=certificates.findAll().getFirst();cert.setRetired(true);certificates.saveAndFlush(cert);
  assertThat(freshness.status(read(old.getId()),LocalDate.now())).isEqualTo("STALE");
  refresh.refresh(project);var missing=read(old.getId());assertThat(missing.getEstScore()).isEqualByComparingTo("6.67");
  assertThat(missing.getStatusStage1()).isEqualTo("PENDING");assertThat(freshness.status(missing,LocalDate.now())).isEqualTo("FRESH");
 }
 @Test void refreshPreservesCandidateRowsAndStageTwoBytes(){
  var old=item();allCertificates();
  objective(2,"业务业绩","近三年办公用品业绩，具有1份合同得10分");
  objective(3,"仓储能力","仓库面积不少于1000平方米得10分");
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
  var parser=parserWithReadFixture(project);
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

 /** Real PROJECT/WAREHOUSE matching catches cert-only refresh silently skipping every active row. */
 @Test void noCertificateProjectRecalculatesBusinessAndWarehouseAndClearsSubjectiveBasis() {
  performances.saveAndFlush(PerformanceRecordEntity.builder().contractName("办公用品合同")
   .projectType(com.xiyu.bid.performance.domain.valueobject.ProjectType.OFFICE)
   .signingDate(LocalDate.now()).contractAmount(new BigDecimal("2000000")).build());
  warehouseRecords.saveAndFlush(WarehouseEntity.builder().name("上海测试仓库").type(WarehouseType.SELF_OPERATED)
   .region("华东").province("上海").address("测试地址").area(new BigDecimal("2000"))
   .contactPerson("测试").startDate(LocalDate.now().minusYears(1)).endDate(LocalDate.now().plusYears(1))
   .lessor("出租方").lessee("承租方").hasPropertyCert(false).hasInvoice(false).hasPhotos(false)
   .hasLeaseContract(false).status(WarehouseStatus.IN_USE).build());
  var business=objective(1,"业务业绩","近三年办公用品业绩，具有1份合同得10分");
  var warehouse=objective(2,"仓储能力","仓库面积不少于1000平方米得10分");
  var subjective=objective(3,"服务方案","根据方案合理性评分");
  subjective.setScoreType("SUBJECTIVE");items.saveAndFlush(subjective);
  var financial=objective(4,"财务状况","根据财务报表评价");
  assertThat(certificates.count()).isZero();
  authorizeExternalApi();var receipt=refresh.refresh(project);
  assertThat(receipt.processedCount()).isEqualTo(4);
  assertThat(receipt.scoredCount()).isEqualTo(2);
  assertThat(receipt.pendingCount()).isEqualTo(2);
  for(var id:List.of(business.getId(),warehouse.getId())) {
   var persisted=read(id);
   assertThat(persisted.getEstScore()).isEqualByComparingTo("10");
   assertThat(persisted.getKbHit()).isTrue();assertThat(persisted.getStatusStage1()).isEqualTo("OK");
   assertThat(persisted.getEstBasis()).doesNotContain("主观项不计分");
  }
  assertThat(read(subjective.getId()).getEstScore()).isNull();
  assertThat(read(financial.getId()).getEstScore()).isNull();
  assertThat(receipt.pendingItems()).extracting(p -> p.id())
   .containsExactlyInAnyOrder(subjective.getId(),financial.getId());
  for(var pending:receipt.pendingItems()) {
   var persisted=read(pending.id());
   assertThat(pending.dim()).isEqualTo(persisted.getDim());
   assertThat(pending.reason()).isNotBlank().isEqualTo(persisted.getEstBasis());
   assertThat(persisted.getStatusStage1()).isEqualTo("PENDING");
  }
  // A second refresh must replace the obsolete subjective explanation when the rule becomes objective.
  subjective=read(subjective.getId());subjective.setScoreType("OBJECTIVE");items.saveAndFlush(subjective);
  var second=refresh.refresh(project);
  var changedId=subjective.getId();
  assertThat(read(changedId).getEstBasis()).doesNotContain("主观项不计分");
  assertThat(second.pendingItems()).filteredOn(p -> p.id().equals(changedId)).singleElement()
   .satisfies(p -> assertThat(p.reason()).isEqualTo(read(changedId).getEstBasis()).doesNotContain("主观项不计分"));
 }

 @Test void legacyNamesRefreshAcrossRealTransactionsWithoutChangingRuleOrStageTwoColumns() {
  warehouseRecords.saveAndFlush(WarehouseEntity.builder().name("测试面积仓").type(WarehouseType.SELF_OPERATED)
   .region("华东").province("上海").address("测试地址").area(new BigDecimal("20000"))
   .contactPerson("测试").startDate(LocalDate.now().minusYears(1)).endDate(LocalDate.now().plusYears(1))
   .lessor("出租方").lessee("承租方").hasPropertyCert(false).hasInvoice(false).hasPhotos(false)
   .hasLeaseContract(false).status(WarehouseStatus.IN_USE).build());
  var warehouse=objective(1,"技术","仓库面积不少于15000平方米得10分");
  warehouse.setCode("仓储能力");items.saveAndFlush(warehouse);
  var cert=objective(2,"商务",RULE);cert.setCode("管理体系");items.saveAndFlush(cert);allCertificates();
  var ranked=objective(3,"商务","按业绩份数排序，排名第一得10分");
  ranked.setCode("服务案例及业绩");items.saveAndFlush(ranked);
  var compound=objective(4,"技术","山西区域仓库不少于15000平方米，服务网点和代储承诺另计分");
  compound.setCode("仓储能力");items.saveAndFlush(compound);
  var brand=objective(5,"技术","二级类目每份品牌授权得1分，最多10分");
  brand.setCode("品牌及货源渠道管控及佐证材料");items.saveAndFlush(brand);
  var ruleColumns=jdbc.queryForList("SELECT id,code,dim,detail,weight,score_type FROM score_item WHERE project_id=? ORDER BY id",project);
  jdbc.update("INSERT INTO score_result(score_item_id,scoring_task_id,actual_score,status_stage2,evidence,created_at,updated_at) VALUES(?,1,7,'PENDING','legacy stage2',NOW(),NOW())",warehouse.getId());
  var stage2=jdbc.queryForMap("SELECT * FROM score_result WHERE score_item_id=?",warehouse.getId());
  authorizeExternalApi();var receipt=refresh.refresh(project);
  assertThat(receipt.processedCount()).isEqualTo(5);assertThat(receipt.scoredCount()).isEqualTo(5);
  assertThat(receipt.pendingCount()).isZero();
  assertThat(read(warehouse.getId()).getEstScore()).isEqualByComparingTo("10");
  assertThat(read(cert.getId()).getEstScore()).isEqualByComparingTo("10");
  assertThat(freshness.status(read(cert.getId()),LocalDate.now())).isEqualTo("FRESH");
  assertThat(read(ranked.getId()).getEstScore()).isZero();
  assertThat(read(compound.getId()).getEstScore()).isEqualByComparingTo("10");
  assertThat(read(brand.getId()).getEstScore()).isZero();
  for(var row:List.of(ranked,compound,brand)) {
   assertThat(read(row.getId()).getEstBasis()).contains("知识库粗估", "未核验").doesNotContain("未识别", "计算失败");
  }
  assertThat(jdbc.queryForList("SELECT id,code,dim,detail,weight,score_type FROM score_item WHERE project_id=? ORDER BY id",project)).isEqualTo(ruleColumns);
  assertThat(jdbc.queryForMap("SELECT * FROM score_result WHERE score_item_id=?",warehouse.getId())).isEqualTo(stage2);
 }

 @Test void fourProject1371LibraryFactorsProduceNumericEstimatesFromRealRepositories() {
  performances.saveAndFlush(PerformanceRecordEntity.builder().contractName("示例合同").projectType(com.xiyu.bid.performance.domain.valueobject.ProjectType.OFFICE)
   .signingDate(LocalDate.now()).contractAmount(new BigDecimal("2000000")).build());
  for(int index=1;index<=2;index++) brandRecords.saveAndFlush(
   com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.ManufacturerAuthorizationEntity.builder()
   .brandId("same-brand").brandName("示例品牌").manufacturerName("示例厂家").importDomestic("国产")
   .productLine(com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine.TOOLS)
   .authStartDate(LocalDate.now().minusYears(1)).authEndDate(LocalDate.now().plusYears(1))
   .status(com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus.ACTIVE).build());
  var operating=objective(1,"商务","平台运营年限和SKU排名");operating.setCode("运营能力及业绩");items.saveAndFlush(operating);
  var projectRow=objective(2,"商务","业绩按份数排名，需发票及材料");projectRow.setCode("服务案例及业绩");items.saveAndFlush(projectRow);
  var brand=objective(3,"技术","需提供2份品牌授权");brand.setCode("品牌及货源渠道管控及佐证材料");items.saveAndFlush(brand);
  var warehouse=objective(4,"技术","仓储地区覆盖及代储承诺");warehouse.setCode("仓储能力");items.saveAndFlush(warehouse);
  var rules=jdbc.queryForList("SELECT id,code,dim,detail,weight,score_type FROM score_item WHERE project_id=? ORDER BY id",project);
  authorizeExternalApi();var receipt=refresh.refresh(project);
  assertThat(receipt.processedCount()).isEqualTo(4);assertThat(receipt.scoredCount()).isEqualTo(4);assertThat(receipt.pendingCount()).isZero();
  assertThat(read(operating.getId()).getEstScore()).isEqualByComparingTo("10");
  assertThat(read(projectRow.getId()).getEstScore()).isEqualByComparingTo("10");
  assertThat(read(brand.getId()).getEstScore()).isEqualByComparingTo("5");
  assertThat(read(warehouse.getId()).getEstScore()).isZero();
  assertThat(jdbc.queryForList("SELECT id,code,dim,detail,weight,score_type FROM score_item WHERE project_id=? ORDER BY id",project)).isEqualTo(rules);
 }

 @Test void zeroScoreIsCountedAsScoredAndHttpReceiptKeepsReadShape() throws Exception {
  var warehouse=objective(1,"仓储能力","仓库面积不少于1000平方米得10分");
  var financial=objective(2,"财务状况","根据财务报表评价");
  authorizeExternalApi();
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
   new com.xiyu.bid.scoreparse.controller.ScoreEstimateController(refresh,parserWithReadFixture(project)))
   .setControllerAdvice(new com.xiyu.bid.exception.GlobalExceptionHandler()).build();
  var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
   .post("/api/projects/"+project+"/score-parse/estimates/refresh").accept(org.springframework.http.MediaType.APPLICATION_JSON))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
  var data=new ObjectMapper().readTree(response.getResponse().getContentAsByteArray()).path("data");
  assertThat(data.path("items").isArray()).isTrue();
  assertThat(data.path("summary").path("totalWeight").decimalValue()).isEqualByComparingTo("20");
  assertThat(data.path("meta").path("sourceFileName").asText()).isEqualTo("integration.pdf");
  var receipt=data.path("refresh");
  assertThat(receipt.path("processedCount").asInt(-1)).isEqualTo(2);
  assertThat(receipt.path("scoredCount").asInt(-1)).isEqualTo(1);
  assertThat(receipt.path("pendingCount").asInt(-1)).isEqualTo(1);
  assertThat(receipt.path("pendingItems").size()).isEqualTo(1);
  var pending=receipt.path("pendingItems").get(0);
  assertThat(pending.path("id").asLong()).isEqualTo(financial.getId());
  assertThat(pending.path("dim").asText()).isEqualTo("财务状况");
  assertThat(pending.path("reason").asText()).isNotBlank().isEqualTo(read(financial.getId()).getEstBasis());
  var zero=read(warehouse.getId());assertThat(zero.getEstScore()).isZero();
  assertThat(zero.getKbHit()).isFalse();assertThat(zero.getStatusStage1()).isEqualTo("DANGER");
  assertThat(zero.getEstBasis()).doesNotContain("主观项不计分");
 }

 ScoreItem objective(int index,String dim,String detail) {
  return items.saveAndFlush(ScoreItem.builder().projectId(project).parseTaskId(1L).itemIndex(index)
   .code("N"+index).dim(dim).detail(detail).weight(BigDecimal.TEN).scoreType("OBJECTIVE")
   .statusStage1("PENDING").estBasis("主观项不计分，历史依据").build());
 }
 ScoreParseAppService parserWithReadFixture(long projectId) {
  var parser=mock(ScoreParseAppService.class);
  var summary=new ScoreParseItemsDTO.Summary(new BigDecimal("20"),BigDecimal.ZERO,0,1,1,
   new BigDecimal("20"),BigDecimal.ZERO,true,null,null);
  var meta=new ScoreParseItemsDTO.Meta("integration.pdf",null,null,null,null,null,null,null,null,null,null,null);
  when(parser.getItems(projectId)).thenAnswer(inv -> new ScoreParseItemsDTO(
   items.findByProjectIdOrderByItemIndexAsc(projectId).stream().map(i ->
    new com.xiyu.bid.scoreparse.dto.ScoreItemDTO(i.getId(),i.getCode(),i.getDim(),i.getDetail(),i.getWeight(),
     i.getScoreType(),i.getStatusStage1(),i.getEstScore(),i.getEstBasis(),i.getKbHit(),null)).toList(),summary,meta));
  return parser;
 }

 @Test void concurrentResultOverwriteBeforeControllerReadRejectsOldReceipt() throws Exception {
  var warehouse=objective(1,"仓储能力","仓库面积不少于1000平方米得10分");
  authorizeExternalApi();var parser=parserWithReadFixture(project);
  var readResponse=parser.getItems(project);
  when(parser.getItems(project)).thenAnswer(inv -> {
   var changed=read(warehouse.getId());changed.setEstScore(null);changed.setStatusStage1("PENDING");
   changed.setEstBasis("另一次刷新已经改变结果");items.saveAndFlush(changed);
   return new ScoreParseItemsDTO(List.of(new com.xiyu.bid.scoreparse.dto.ScoreItemDTO(changed.getId(),
    changed.getCode(),changed.getDim(),changed.getDetail(),changed.getWeight(),changed.getScoreType(),
    changed.getStatusStage1(),changed.getEstScore(),changed.getEstBasis(),changed.getKbHit(),null)),
    readResponse.summary(),readResponse.meta());
  });
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
   new com.xiyu.bid.scoreparse.controller.ScoreEstimateController(refresh,parser))
   .setControllerAdvice(new com.xiyu.bid.exception.GlobalExceptionHandler()).build();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
   .post("/api/projects/"+project+"/score-parse/estimates/refresh"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
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
