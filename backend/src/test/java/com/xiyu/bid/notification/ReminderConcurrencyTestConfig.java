package com.xiyu.bid.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.notification.entity.Notification;
import com.xiyu.bid.notification.outbound.application.NotificationDeliveryJobService;
import com.xiyu.bid.notification.outbound.application.NotificationFailureClassifier;
import com.xiyu.bid.notification.outbound.entity.OutboundLog;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTask;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryTaskRepository;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryDlq;
import com.xiyu.bid.notification.outbound.infrastructure.NotificationDeliveryDlqRepository;
import com.xiyu.bid.notification.outbound.repository.OutboundLogRepository;
import com.xiyu.bid.notification.outbound.service.WeComPushService;
import com.xiyu.bid.notification.repository.NotificationRepository;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.platform.async.domain.AsyncDecisionResolver;
import com.xiyu.bid.platform.async.infrastructure.AsyncObservabilityRecorder;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.service.WarehouseExpiryScanTask;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import com.xiyu.bid.notification.outbound.service.NotificationDeliveryResult;
import jakarta.persistence.EntityManager;

import javax.sql.DataSource;
import java.util.Map;

import static org.mockito.Mockito.mock;

@Configuration
@Profile("reminder-concurrency-test")
@EnableTransactionManagement
class ReminderConcurrencyTestConfig {
    @Bean DataSource dataSource() {
        String url = System.getProperty("reminder.test.url", "jdbc:h2:mem:reminder_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        if (!url.startsWith("jdbc:h2:mem:reminder_concurrency") && !url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/reminder_verify(\\?.*)?")) {
            throw new IllegalArgumentException("Only an isolated reminder_verify test database is allowed");
        }
        return new DriverManagerDataSource(url, System.getProperty("reminder.test.username", "sa"),
                System.getProperty("reminder.test.password", ""));
    }
    @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(source);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setManagedTypes(PersistenceManagedTypes.of(WarehouseEntity.class.getName(), Notification.class.getName(),
                NotificationDeliveryTask.class.getName(), NotificationDeliveryDlq.class.getName(), OutboundLog.class.getName()));
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
        return factory;
    }
    @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
    @Bean JpaRepositoryFactoryBean<WarehouseRepository, WarehouseEntity, Long> warehouseRepository(EntityManagerFactory factory) {
        return repository(WarehouseRepository.class, factory);
    }
    @Bean JpaRepositoryFactoryBean<NotificationRepository, Notification, Long> notificationRepository(EntityManagerFactory factory) {
        return repository(NotificationRepository.class, factory);
    }
    @Bean JpaRepositoryFactoryBean<NotificationDeliveryTaskRepository, NotificationDeliveryTask, Long> taskRepository(EntityManagerFactory factory) {
        return repository(NotificationDeliveryTaskRepository.class, factory);
    }
    @Bean JpaRepositoryFactoryBean<NotificationDeliveryDlqRepository, NotificationDeliveryDlq, Long> dlqRepository(EntityManagerFactory factory) {
        return repository(NotificationDeliveryDlqRepository.class, factory);
    }
    @Bean JpaRepositoryFactoryBean<OutboundLogRepository, OutboundLog, Long> outboundLogRepository(EntityManagerFactory factory) {
        return repository(OutboundLogRepository.class, factory);
    }
    private <T, R extends Repository<T, Long>> JpaRepositoryFactoryBean<R, T, Long> repository(
            Class<R> repositoryType, EntityManagerFactory factory) {
        var bean = new JpaRepositoryFactoryBean<R, T, Long>(repositoryType);
        bean.setEntityManager(SharedEntityManagerCreator.createSharedEntityManager(factory));
        return bean;
    }
    @Bean TransactionalPushDependency transactionalPushDependency(EntityManagerFactory factory) {
        return new TransactionalPushDependency(SharedEntityManagerCreator.createSharedEntityManager(factory));
    }
    static class TransactionalPushDependency {
        private final EntityManager entityManager;
        TransactionalPushDependency(EntityManager entityManager) { this.entityManager = entityManager; }
        @Transactional(readOnly = true)
        public NotificationDeliveryResult fail() {
            entityManager.createNativeQuery("select 1").getSingleResult();
            throw new IllegalStateException("timeout reading push configuration");
        }
    }
    @Bean UserRepository userRepository() { return mock(UserRepository.class); }
    @Bean NotificationApplicationService notificationApplicationService() { return mock(NotificationApplicationService.class); }
    @Bean WeComPushService pushService() { return mock(WeComPushService.class); }
    @Bean AsyncObservabilityRecorder observabilityRecorder() { return mock(AsyncObservabilityRecorder.class); }
    @Bean WarehouseExpiryScanTask scanner(WarehouseRepository warehouses, UserRepository users,
            NotificationApplicationService notifications, NotificationRepository records, PlatformTransactionManager manager) {
        return new WarehouseExpiryScanTask(warehouses, users, notifications, records, manager);
    }
    @Bean NotificationDeliveryJobService worker(NotificationDeliveryTaskRepository tasks, NotificationDeliveryDlqRepository dlq,
            OutboundLogRepository logs, WeComPushService push, AsyncObservabilityRecorder recorder, PlatformTransactionManager manager) {
        return new NotificationDeliveryJobService(tasks, dlq, logs, push, new NotificationFailureClassifier(),
                new AsyncDecisionResolver(), recorder, new ObjectMapper(), manager);
    }
}
