package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.biddraftagent.infrastructure.tenderdocument.LocalTenderDocumentStorage;
import com.xiyu.bid.biddraftagent.infrastructure.tenderdocument.MarkItDownSidecarTextExtractor;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity.ManufacturerAuthorizationEntity;
import com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.repository.ManufacturerAuthorizationJpaRepository;
import com.xiyu.bid.businessqualification.infrastructure.persistence.entity.BusinessQualificationEntity;
import com.xiyu.bid.businessqualification.infrastructure.persistence.repository.BusinessQualificationJpaRepository;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceRecordEntity;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceRecordJpaRepository;
import com.xiyu.bid.personnel.infrastructure.persistence.entity.PersonnelEntity;
import com.xiyu.bid.personnel.infrastructure.persistence.repository.PersonnelJpaRepository;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.service.ProjectDocumentFileStorage;
import com.xiyu.bid.scoreparse.application.match.BrandMatchService;
import com.xiyu.bid.scoreparse.application.match.CertMatchService;
import com.xiyu.bid.scoreparse.application.match.PersonMatchService;
import com.xiyu.bid.scoreparse.application.match.ProjectMatchService;
import com.xiyu.bid.scoreparse.application.match.WarehouseMatchService;
import com.xiyu.bid.scoreparse.controller.ScoreParseController;
import com.xiyu.bid.scoreparse.dto.ScoreParseReviewDTO;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseEvidenceRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseFactRepository;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import com.xiyu.bid.scoreparse.repository.ScoreResultRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real private PDF -> actual Sidecar HTTP -> production worker -> MySQL -> real review Controller. */
@Testcontainers
@EnabledIfSystemProperty(named = "scoreparse.realPdf", matches = ".+")
@SpringBootTest(classes = Project1133OfflineAcceptanceMysqlTest.Config.class, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.jpa.open-in-view=false"})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class Project1133OfflineAcceptanceMysqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("project1133_offline");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl); r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Configuration @EnableAutoConfiguration(exclude = {
            org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
            org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class})
    @EntityScan(basePackageClasses = {ScoreParseTask.class, ProjectDocument.class, BidTenderDocumentSnapshot.class,
            BusinessQualificationEntity.class, PersonnelEntity.class, PerformanceRecordEntity.class,
            ManufacturerAuthorizationEntity.class, WarehouseEntity.class})
    @EnableJpaRepositories(basePackageClasses = {ScoreParseTaskRepository.class, ProjectDocumentRepository.class,
            BidTenderDocumentSnapshotRepository.class, BusinessQualificationJpaRepository.class, PersonnelJpaRepository.class,
            PerformanceRecordJpaRepository.class, ManufacturerAuthorizationJpaRepository.class, WarehouseRepository.class})
    @ComponentScan(basePackageClasses = LocalTenderDocumentStorage.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ProjectDocumentFileStorage.class))
    @Import({ScoreParseTaskStateService.class, ScoreParseProgressService.class, ScoreItemPersistenceService.class,
            ScoreParseReviewAppService.class, EstimatedScoreService.class, CertMatchService.class, PersonMatchService.class,
            ProjectMatchService.class, WarehouseMatchService.class, BrandMatchService.class})
    static class Config {
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean ProjectAccessScopeService access() {
            // Actual ROLE_EXTERNAL_API authorization path; this test does not exercise OSS/user permission lookup.
            return new ProjectAccessScopeService(null, null, null, null, null, null, null, null, null, null, null);
        }
        @Bean TenderDocumentStorage storage() throws Exception {
            return new LocalTenderDocumentStorage(Files.createTempDirectory("1133-offline-storage-").toString(), "");
        }
    }
    @Autowired ObjectMapper mapper;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProjectDocumentRepository documents;
    @Autowired BidTenderDocumentSnapshotRepository snapshots;
    @Autowired ProjectDocumentFileStorage storage;
    @Autowired ScoreParseTaskRepository tasks;
    @Autowired ScoreParseFactRepository facts;
    @Autowired ScoreParseEvidenceRepository evidence;
    @Autowired ScoreItemRepository items;
    @Autowired ScoreResultRepository results;
    @Autowired ScoreParseTaskStateService state;
    @Autowired ScoreParseProgressService progress;
    @Autowired ProjectAccessScopeService access;
    @Autowired ScoreItemPersistenceService persistence;
    @Autowired EstimatedScoreService estimator;
    @Autowired ScoreParseReviewAppService reviews;
    @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS) Path temporary;

    @Test void actualPdfThroughWorkerDatabaseAndReviewRetainsEveryOriginalRule() throws Exception {
        try (var fixture = new Project1133OfflineFixture(temporary)) {
            long project = 1133L;
            jdbc.execute("CREATE TABLE IF NOT EXISTS projects(id BIGINT PRIMARY KEY)");
            jdbc.update("INSERT INTO projects(id) VALUES(?)", project);
            byte[] pdf = Files.readAllBytes(fixture.pdf);
            var stored = storage.store(project, "project1133.pdf", "application/pdf", pdf);
            documents.saveAndFlush(ProjectDocument.builder().projectId(project).name("project1133.pdf")
                    .fileType("application/pdf").fileUrl(stored.fileUrl()).documentCategory("TENDER").uploaderName("offline-acceptance").build());
            TenderDocumentTextExtractor textExtractor = new MarkItDownSidecarTextExtractor(new RestTemplate(), mapper,
                    fixture.url, Project1133OfflineFixture.KEY);
            var resolver = new InitiationTenderTextResolver(documents, snapshots, storage, context.getBean(TenderDocumentStorage.class),
                    textExtractor, null, null, null, null, 5_000_000L, 536_870_912L);
            var artifactExtractor = new SidecarDocumentArtifactExtractor(fixture.url, Project1133OfflineFixture.KEY, 120, mapper);
            var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
            beans.registerSingleton("actualSidecar", artifactExtractor);
            var worker = new ScoreParseAppService(tasks, items, results, documents, state, progress, snapshots, access,
                    null, estimator, persistence, resolver, null, beans.getBeanProvider(SidecarDocumentArtifactExtractor.class), null, null, null);
            String taskId = UUID.randomUUID().toString();
            state.createTask(taskId, project, "PARSE", "project1133.pdf", stored.fileUrl());
            worker.executeParseAsync(taskId);
            var task = tasks.findByTaskId(taskId).orElseThrow();
            assertThat(task.getErrorMessage()).isNull();
            assertThat(task.getStatus()).isEqualTo("COMPLETED");
            assertThat(task.getDecisionStatus()).isEqualTo("AUTO_APPROVED");
            assertThat(task.getPipelineVersion()).isEqualTo("EVIDENCE_PIPELINE_V1");
            assertThat(task.getFallbackReason()).isNull();
            assertThat(tasks.count()).isEqualTo(1);
            var originalRules = fixture.originalRules();
            var artifact = artifactExtractor.extract(pdf, "project1133.pdf");
            var sourceCells = artifact.tables().stream().flatMap(t -> t.cells().stream())
                    .collect(Collectors.toMap(c -> c.cellId(), c -> c.rawText(), (a, b) -> a));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("offline-1133", "unused",
                    List.of(new SimpleGrantedAuthority("ROLE_EXTERNAL_API"))));
            try {
                var mvc = MockMvcBuilders.standaloneSetup(new ScoreParseController(null, null, null, reviews)).build();
                var response = mvc.perform(get("/api/projects/{projectId}/score-parse/review", project)
                                .accept(org.springframework.http.MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                JsonNode reviewJson = mapper.readTree(response).path("data");
                var review = mapper.treeToValue(reviewJson, ScoreParseReviewDTO.class);
                assertReview(review, task, originalRules, sourceCells);
                Path output = Path.of(System.getProperty("scoreparse.offlineEvidenceDir", "target/project1133-offline-evidence"));
                Files.createDirectories(output);
                Files.copy(fixture.directory.resolve("sidecar.log"), output.resolve("sidecar.log"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.writeString(output.resolve("review.json"), response);
                mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("original-rules.json").toFile(), originalRules);
                mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("artifact.json").toFile(), artifact);
                Files.writeString(output.resolve("receipt.txt"), "pdfSha256=" + Project1133OfflineFixture.PDF_SHA
                        + "\ntaskId=" + taskId + "\npipeline=" + task.getPipelineVersion()
                        + "\ndatabase=" + MYSQL.getJdbcUrl() + "\nhistoricalBaiduResponse=UNVERIFIED\n");
            } finally { SecurityContextHolder.clearContext(); }
        }
    }

    private void assertReview(ScoreParseReviewDTO review, ScoreParseTask task, JsonNode oracle, java.util.Map<String, String> cells) throws Exception {
        assertThat(review.parseTaskId()).isEqualTo(task.getId());
        assertThat(review.taskId()).isEqualTo(task.getTaskId());
        assertThat(review.decisionStatus()).isEqualTo("AUTO_APPROVED");
        assertThat(review.schemes()).singleElement().satisfies(s -> {
            assertThat(s.title()).isEqualTo("第七章 评分标准"); assertThat(s.declaredTotal()).isEqualByComparingTo("100");
            assertThat(s.totalEvidenceStatus()).isEqualTo("VERIFIED");
        });
        assertThat(review.schemeSelectionRequired()).isFalse();
        assertThat(review.declaredTotal()).isEqualByComparingTo("100");
        assertThat(review.facts()).hasSize(12);
        assertThat(review.facts().stream().map(ScoreParseReviewDTO.Fact::weight).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");
        assertThat(review.facts().stream().filter(f -> "SUBJECTIVE".equals(f.scoreType()))).hasSize(6);
        var persisted = facts.findByParseTaskId(task.getId());
        var formal = items.findByProjectIdOrderByItemIndexAsc(task.getProjectId());
        assertThat(persisted).hasSize(12); assertThat(formal).hasSize(12);
        List<String> names = List.of("投标报价", "类似业绩", "体系认证", "总体组织实施方案", "商品覆盖程度", "货物配送能力",
                "电商平台便捷性", "商品质量保障", "售后服务方案", "项目团队", "专属采购服务平台建设", "仓储情况");
        List<String> weights = List.of("30", "10", "6", "10", "12", "10", "3", "3", "3", "3", "5", "5");
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i); JsonNode expected = oracle.get(i);
            assertThat(expected.path("name").asText()).isEqualTo(name);
            var f = review.facts().stream().filter(v -> Project1133OfflineFixture.normalized(v.dimension()).equals(name)).findFirst().orElseThrow();
            var db = persisted.stream().filter(v -> v.getFactId().equals(f.factId())).findFirst().orElseThrow();
            var item = formal.stream().filter(v -> Project1133OfflineFixture.normalized(v.getDim()).equals(name)).findFirst().orElseThrow();
            assertThat(f.weight()).as(name).isEqualByComparingTo(weights.get(i));
            String type = List.of(3, 4, 6, 7, 8, 9).contains(i) ? "SUBJECTIVE" : "OBJECTIVE";
            assertThat(f.scoreType()).as(name).isEqualTo(type);
            assertThat(item.getScoreType()).as(name).isEqualTo(type);
            assertThat(item.getWeight()).isEqualByComparingTo(f.weight());
            assertThat(f.sourceText()).as(name).isEqualTo(db.getSourceText()).doesNotContain("格式自拟", "合计");
            assertThat(Project1133OfflineFixture.normalized(f.sourceText())).as(name + " full original PDF rule")
                    .contains(Project1133OfflineFixture.normalized(expected.path("rule").asText()));
            assertThat(Project1133OfflineFixture.normalized(item.getDetail())).as(name + " formal full rule")
                    .contains(Project1133OfflineFixture.normalized(expected.path("rule").asText()));
            assertThat(f.sourceCellIds()).isEqualTo(db.getSourceCellIds());
            var ids = mapper.readTree(f.sourceCellIds()); assertThat(ids).isNotEmpty();
            StringBuilder bound = new StringBuilder();
            for (var id : ids) { assertThat(cells).containsKey(id.asText()); bound.append(cells.get(id.asText())); }
            assertThat(Project1133OfflineFixture.normalized(bound.toString())).as(name + " original referenced cells")
                    .contains(Project1133OfflineFixture.normalized(expected.path("rule").asText()));
            assertThat(f.scopeStatus()).isEqualTo("IN_SCHEME"); assertThat(f.candidateEligible()).isTrue();
            assertThat(f.schemeId()).isEqualTo(review.selectedSchemeId());
            assertThat(f.evidences()).isNotEmpty();
            assertThat(evidence.findByParseTaskId(task.getId()).stream().filter(e -> e.getFactId().equals(f.factId()))).isNotEmpty();
        }
    }
}
