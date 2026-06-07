package com.xiyu.bid.audit.aspect;

import com.xiyu.bid.annotation.Auditable;
import com.xiyu.bid.aspect.AuditableAspect;
import com.xiyu.bid.audit.core.AuditActionPolicy;
import com.xiyu.bid.audit.service.AuditLogService;
import com.xiyu.bid.audit.service.IAuditLogService;
import com.xiyu.bid.dto.ApiResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditableAspectQueryActionTest {

    @Mock
    private IAuditLogService auditLogService;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    private AuditableAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AuditableAspect(auditLogService, new AuditActionPolicy());
        when(joinPoint.getSignature()).thenReturn(signature);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @MethodSource("queryActionMethods")
    void queryActionsProceedWithoutWritingAuditLog(Method method) throws Throwable {
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.auditMethod(joinPoint);

        assertThat(result).isEqualTo("ok");
        verify(auditLogService, never()).log(any());
    }

    @Test
    void mutatingActionStillWritesAuditLog() throws Throwable {
        when(signature.getMethod()).thenReturn(method("create"));
        when(joinPoint.proceed()).thenReturn("created");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"42"});

        Object result = aspect.auditMethod(joinPoint);

        assertThat(result).isEqualTo("created");
        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getAction()).isEqualTo("CREATE");
        assertThat(entryCaptor.getValue().getEntityType()).isEqualTo("Project");
        assertThat(entryCaptor.getValue().getEntityId()).isEqualTo("created");
    }

    @Test
    void mutatingActionExtractsEntityIdFromApiResponseBody() throws Throwable {
        when(signature.getMethod()).thenReturn(method("create"));
        when(joinPoint.proceed()).thenReturn(ResponseEntity.ok(ApiResponse.success(new CreatedRecord(99L))));
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        aspect.auditMethod(joinPoint);

        ArgumentCaptor<AuditLogService.AuditLogEntry> entryCaptor =
                ArgumentCaptor.forClass(AuditLogService.AuditLogEntry.class);
        verify(auditLogService).log(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getEntityId()).isEqualTo("99");
    }

    static Stream<Method> queryActionMethods() {
        return Stream.of("read", "query", "view", "search", "list", "get")
                .map(AuditableAspectQueryActionTest::method);
    }

    private static Method method(String name) {
        try {
            return TargetActions.class.getMethod(name);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    static final class TargetActions {
        @Auditable(action = "READ", entityType = "Project")
        public String read() {
            return "ok";
        }

        @Auditable(action = "QUERY", entityType = "Project")
        public String query() {
            return "ok";
        }

        @Auditable(action = "VIEW", entityType = "Project")
        public String view() {
            return "ok";
        }

        @Auditable(action = "SEARCH", entityType = "Project")
        public String search() {
            return "ok";
        }

        @Auditable(action = "LIST", entityType = "Project")
        public String list() {
            return "ok";
        }

        @Auditable(action = "GET", entityType = "Project")
        public String get() {
            return "ok";
        }

        @Auditable(action = "CREATE", entityType = "Project")
        public String create() {
            return "created";
        }
    }

    record CreatedRecord(Long id) {
    }
}
