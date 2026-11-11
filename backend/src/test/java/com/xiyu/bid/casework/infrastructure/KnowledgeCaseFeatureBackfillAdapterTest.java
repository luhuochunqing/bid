package com.xiyu.bid.casework.infrastructure;

import com.xiyu.bid.casework.application.port.KnowledgeCaseFeatureBackfillPort;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(KnowledgeCaseFeatureBackfillAdapterTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation = Propagation.NEVER)
class KnowledgeCaseFeatureBackfillAdapterTest {

    @Autowired private KnowledgeCaseRepository repository;
    @Autowired private KnowledgeCaseFeatureBackfillPort adapter;
    @Autowired private SnapshotHook hook;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        hook.afterRead = () -> {};
        repository.deleteAllInBatch();
    }

    @Test
    @DisplayName("没有空主题行时不保存")
    void emptyPendingDoesNotSave() {
        KnowledgeCase assigned = pending();
        assigned.setSubTopic("OTHER");
        assigned.setQualityScore(81);
        repository.saveAndFlush(assigned);

        assertThat(adapter.backfillMissingSubTopic()).isZero();
        assertThat(repository.findById(assigned.getId()).orElseThrow().getQualityScore()).isEqualTo(81);
    }

    @Test
    @DisplayName("短正文回填子主题，质量分为 0")
    void shortBodyGetsTopicAndZeroQuality() {
        KnowledgeCase row = repository.saveAndFlush(pending());

        assertThat(adapter.backfillMissingSubTopic()).isEqualTo(1);
        KnowledgeCase stored = repository.findById(row.getId()).orElseThrow();
        assertThat(stored.getSubTopic()).isEqualTo("LOGISTICS_WAREHOUSE");
        assertThat(stored.getQualityScore()).isZero();
        assertThat(adapter.backfillMissingSubTopic()).isZero();
    }

    @Test
    @DisplayName("真实事务交错：回填不撤销已提交的下架、置顶、复用次数与来源修改")
    void concurrentBusinessUpdateIsPreserved() {
        KnowledgeCase row = repository.saveAndFlush(pending());
        interleave(row, current -> {
            current.setStatus("OFF_SHELF");
            current.setIsPinned(true);
            current.setReuseCount(9);
            current.setSourceDocumentName("新版标书.pdf");
            current.setSourceProjectId(99L);
        });

        assertThat(adapter.backfillMissingSubTopic()).isEqualTo(1);

        KnowledgeCase stored = repository.findById(row.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("OFF_SHELF");
        assertThat(stored.getIsPinned()).isTrue();
        assertThat(stored.getReuseCount()).isEqualTo(9);
        assertThat(stored.getSourceDocumentName()).isEqualTo("新版标书.pdf");
        assertThat(stored.getSourceProjectId()).isEqualTo(99L);
        assertThat(stored.getSubTopic()).isEqualTo("LOGISTICS_WAREHOUSE");
        assertThat(stored.getQualityScore()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"title, replace", "title, case", "title, space",
            "body, replace", "body, case", "body, space"})
    @DisplayName("真实事务交错：标题或正文改变（含大小写及尾随空格）则跳过旧快照")
    void changedCalculationInputSkipsBackfill(String field, String change) {
        KnowledgeCase row = pending();
        row.setScoringPointTitle("仓储配送方案ABC");
        row.setResponseText("仓库布局ABC");
        repository.saveAndFlush(row);
        String original = "title".equals(field) ? row.getScoringPointTitle() : row.getResponseText();
        String changed = switch (change) {
            case "case" -> original.replace("ABC", "abc");
            case "space" -> original + " ";
            default -> "新内容";
        };
        interleave(row, current -> {
            if ("title".equals(field)) {
                current.setScoringPointTitle(changed);
            } else {
                current.setResponseText(changed);
            }
        });

        assertThat(adapter.backfillMissingSubTopic()).isZero();
        KnowledgeCase stored = repository.findById(row.getId()).orElseThrow();
        assertThat(stored.getSubTopic()).isNull();
        assertThat(stored.getQualityScore()).isEqualTo(60);
        assertThat("title".equals(field) ? stored.getScoringPointTitle() : stored.getResponseText())
                .isEqualTo(changed);
    }

    @Test
    @DisplayName("真实事务交错：其他写入方已补齐特征则保留，计数只含实际成功行")
    void concurrentFeatureAssignmentIsPreserved() {
        KnowledgeCase row = repository.saveAndFlush(pending());
        repository.saveAndFlush(pending());
        interleave(row, current -> {
            current.setSubTopic("OTHER");
            current.setQualityScore(88);
        });

        assertThat(adapter.backfillMissingSubTopic()).isEqualTo(1);
        KnowledgeCase stored = repository.findById(row.getId()).orElseThrow();
        assertThat(stored.getSubTopic()).isEqualTo("OTHER");
        assertThat(stored.getQualityScore()).isEqualTo(88);
    }

    @Test
    @DisplayName("真实事务交错：质量分单独被改动也不覆盖")
    void concurrentQualityUpdateIsPreserved() {
        KnowledgeCase row = repository.saveAndFlush(pending());
        interleave(row, current -> current.setQualityScore(90));

        assertThat(adapter.backfillMissingSubTopic()).isZero();
        KnowledgeCase stored = repository.findById(row.getId()).orElseThrow();
        assertThat(stored.getSubTopic()).isNull();
        assertThat(stored.getQualityScore()).isEqualTo(90);
    }

    private KnowledgeCase pending() {
        KnowledgeCase row = new KnowledgeCase();
        row.setSourceProjectId(1L);
        row.setSourceProjectName("测试项目");
        row.setScoringPointTitle("仓储配送方案");
        row.setRequirementRaw("评分要求");
        row.setResponseText("仓库布局和配送时效见正文。");
        row.setStatus("ACTIVE");
        return row;
    }

    // 查询实际执行后、回填写入前，让另一线程的真实事务提交，精确复现覆盖窗口。
    private void interleave(KnowledgeCase row, Consumer<KnowledgeCase> change) {
        hook.afterRead = () -> {
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    KnowledgeCase current = repository.findById(row.getId()).orElseThrow();
                    change.accept(current);
                    repository.saveAndFlush(current);
                })).get(10, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new AssertionError("并发业务事务未成功提交", exception);
            }
        };
    }

    static class SnapshotHook {
        Runnable afterRead = () -> {};
    }

    @TestConfiguration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            // VARIABLE_BINARY 避免 H2 把无长度的 CAST AS BINARY 截为一字节；对齐 MySQL 的变长语义。
            return new DriverManagerDataSource("jdbc:h2:mem:case_feature_backfill;MODE=MySQL;"
                    + "VARIABLE_BINARY=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(KnowledgeCase.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean JpaRepositoryFactoryBean<KnowledgeCaseRepository, KnowledgeCase, Long> repository(
                EntityManagerFactory factory) {
            var bean = new JpaRepositoryFactoryBean<KnowledgeCaseRepository, KnowledgeCase, Long>(
                    KnowledgeCaseRepository.class);
            bean.setEntityManager(SharedEntityManagerCreator.createSharedEntityManager(factory));
            return bean;
        }

        @Bean SnapshotHook snapshotHook() { return new SnapshotHook(); }

        @Bean KnowledgeCaseFeatureBackfillAdapter adapter(KnowledgeCaseRepository repository, SnapshotHook hook) {
            KnowledgeCaseRepository intercepted = (KnowledgeCaseRepository) Proxy.newProxyInstance(
                    KnowledgeCaseRepository.class.getClassLoader(), new Class<?>[]{KnowledgeCaseRepository.class},
                    (proxy, method, args) -> {
                        Object result;
                        try {
                            result = method.invoke(repository, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                        if (method.getName().equals("findBySubTopicIsNull")) {
                            Runnable afterRead = hook.afterRead;
                            hook.afterRead = () -> {};
                            afterRead.run();
                        }
                        return result;
                    });
            return new KnowledgeCaseFeatureBackfillAdapter(intercepted);
        }
    }
}
