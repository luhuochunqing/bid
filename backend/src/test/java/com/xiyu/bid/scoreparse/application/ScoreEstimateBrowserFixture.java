package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.businessqualification.domain.valueobject.*;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.dto.ApiResponse;
import com.xiyu.bid.scoreparse.application.match.CertMatchService;
import com.xiyu.bid.scoreparse.controller.ScoreEstimateController;
import com.xiyu.bid.scoreparse.entity.*;
import com.xiyu.bid.scoreparse.repository.*;
import com.xiyu.bid.service.ProjectAccessScopeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.*;

/** Local-only disposable browser fixture. No production component scanning or mocked persistence. */
@TestConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
@EntityScan(basePackageClasses={ScoreItem.class,BusinessQualificationEntity.class})
@EnableJpaRepositories(basePackageClasses={ScoreItemRepository.class,BusinessQualificationJpaRepository.class})
@Import({CertMatchService.class,ScoreScoringVersionService.class,ScoreEstimateFreshness.class,
 ScoreEstimateRefreshService.class,ScoreEstimateController.class,ScoreExtractionCodec.class,
 ScoreEstimateBrowserFixture.ReadController.class})
public class ScoreEstimateBrowserFixture {
 public static void main(String[] args) throws Exception {
  String url=System.getProperty("certificate.test.mysql.url");
  if(url==null||!url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/cert_score_test_browser_[a-zA-Z0-9_]+"))
   throw new IllegalArgumentException("Dedicated local cert_score_test_browser_* schema required");
  if(args.length!=0)throw new IllegalArgumentException("Use only validated system properties; CLI overrides prohibited");
  String assets=System.getProperty("certificate.test.assets");
  if(assets==null||!new java.io.File(assets).isDirectory())throw new IllegalArgumentException("certificate.test.assets must be an existing browser-dist directory");
  SpringApplication app=new SpringApplication(ScoreEstimateBrowserFixture.class);
  var properties=new java.util.HashMap<String,Object>();
  properties.putAll(Map.of("server.address","127.0.0.1","server.port","0","spring.datasource.url",url,
   "spring.datasource.username","root","spring.datasource.password","","spring.jpa.hibernate.ddl-auto","create-drop",
   "spring.flyway.enabled","false","spring.jpa.open-in-view","false",
   "spring.web.resources.static-locations",new java.io.File(assets).toURI().toString()));
  properties.forEach((key,value)->System.setProperty(key,value.toString()));
  app.setDefaultProperties(properties);
  var context=app.run();
  System.out.println("BROWSER_FIXTURE_PORT="+context.getEnvironment().getProperty("local.server.port"));
 }
 @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);}
 @Bean com.xiyu.bid.scoreparse.controller.ScoreExtractionController disabledExtraction(
   ScoreParseTaskRepository tasks,ScoreParseFactRepository facts,ScoreExtractionRecordRepository snapshots,
   ScoreActiveVersionRepository versions,ScoreItemRepository items,ScoreExtractionCodec codec,ProjectAccessScopeService access) {
  var view=new ScoreExtractionAppService(tasks,facts,snapshots,versions,items,null,codec,new ScoreExtractionVersionFeature(false),access);
  return new com.xiyu.bid.scoreparse.controller.ScoreExtractionController(view,null);
 }
 @Bean ProjectAccessScopeService guard(){return new ProjectAccessScopeService(null,null,null,null,null,null,null,null,null,null,null);}
 @Bean EstimatedScoreService estimates(ScoreItemRepository items,CertMatchService certs){return new EstimatedScoreService(items,certs,null,null,null,null);}
 @Bean ScoreParseAppService parser(ScoreParseTaskRepository tasks,ScoreItemRepository items,ScoreResultRepository results,
   ProjectAccessScopeService guard,EstimatedScoreService estimates){
  return new ScoreParseAppService(tasks,items,results,null,null,null,null,guard,null,estimates,null,null,null,null,null,null,null);
 }
 @Bean UserDetailsService users(){return new InMemoryUserDetailsManager(User.withUsername("fixture").password("{noop}fixture-local-only").roles("EXTERNAL_API").build());}
 @Bean SecurityFilterChain fixtureSecurity(HttpSecurity http) throws Exception {
  return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.requestMatchers("/api/**").authenticated().anyRequest().permitAll())
    .httpBasic(org.springframework.security.config.Customizer.withDefaults()).build();
 }
 @Bean ApplicationRunner seed(JdbcTemplate jdbc,ScoreItemRepository items,ScoreParseTaskRepository tasks,
   ScoreResultRepository results,BusinessQualificationJpaRepository certs,ScoreScoringVersionService versions){return args->{
  jdbc.execute("CREATE TABLE projects(id BIGINT PRIMARY KEY)");jdbc.update("INSERT INTO projects VALUES(1117)");
  var parse=tasks.saveAndFlush(ScoreParseTask.builder().taskId(UUID.randomUUID().toString()).projectId(1117L).taskType("PARSE")
   .status("COMPLETED").fileName("正式评分规则.pdf").completedAt(LocalDateTime.now().minusDays(2)).build());
  var item=items.saveAndFlush(ScoreItem.builder().projectId(1117L).parseTaskId(parse.getId()).itemIndex(1).code("A1").dim("管理体系")
   .detail("具有ISO9001质量管理体系认证、ISO14001环境管理体系认证、ISO45001职业健康安全管理体系认证的得10分。三项齐全得满分，缺少一项，不得分")
   .weight(BigDecimal.TEN).scoreType("OBJECTIVE").statusStage1("OK").estScore(BigDecimal.TEN).kbHit(true).estBasis("历史预判").build());
  for(String name:java.util.List.of("ISO9001","ISO14001","ISO45001"))certs.saveAndFlush(BusinessQualificationEntity.builder()
   .name(name).subjectName("本地浏览器隔离验收").subjectType(QualificationSubjectType.values()[0]).category(QualificationCategory.values()[0])
   .status(QualificationStatus.IN_STOCK).expiryDate(LocalDate.now().plusYears(1)).build());
  var scored=tasks.saveAndFlush(ScoreParseTask.builder().taskId(UUID.randomUUID().toString()).projectId(1117L).taskType("SCORING")
   .status("COMPLETED").fileName("投标材料.pdf").completedAt(LocalDateTime.now().minusDays(1)).pipelineVersion("SCORING_VERSION_V1")
   .itemSetHash(versions.capture(1117L).fingerprint()).build());
  results.saveAndFlush(ScoreResult.builder().scoreItemId(item.getId()).scoringTaskId(scored.getId()).actualScore(new BigDecimal("7"))
   .statusStage2("PENDING").evidence("阶段2保留值").reuseKind("FRESH").build());
  jdbc.update("INSERT INTO score_extraction_snapshot(parse_task_id,project_id,base_fingerprint,payload) VALUES(?,?,?,?)",parse.getId(),1117L,"pending-candidate","{\"candidate\":\"not activated\"}");
 };}
 @RestController @RequestMapping("/api/projects/{projectId}/score-parse")
 static class ReadController {
  private final ScoreParseAppService parser;private final ScoreScoringVersionService versions;private final ProjectAccessScopeService guard;
  ReadController(ScoreParseAppService parser,ScoreScoringVersionService versions,ProjectAccessScopeService guard){this.parser=parser;this.versions=versions;this.guard=guard;}
  @GetMapping("/items") Object items(@PathVariable Long projectId){return ApiResponse.success(parser.getItems(projectId));}
  @GetMapping("/results") Object results(@PathVariable Long projectId){guard.assertCurrentUserCanAccessProject(projectId);return ApiResponse.success(versions.read(projectId));}
 }
}
