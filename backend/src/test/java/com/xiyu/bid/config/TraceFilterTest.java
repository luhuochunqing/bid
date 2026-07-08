package com.xiyu.bid.config;

import com.xiyu.bid.security.CurrentUserResolver;
import com.xiyu.bid.security.CurrentUserResolver.UserMdcContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class TraceFilterTest {

    @Mock
    private CurrentUserResolver currentUserResolver;

    private TraceFilter traceFilter;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        traceFilter = new TraceFilter(currentUserResolver);
        MDC.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        MDC.clear();
        mocks.close();
    }

    @Test
    void injectsTraceIdAndUserContext() throws ServletException, IOException {
        when(currentUserResolver.getUserMdcContext())
                .thenReturn(new UserMdcContext("42", "admin"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MdcCaptureServlet capture = new MdcCaptureServlet();

        new MockFilterChain(capture, traceFilter).doFilter(request, response);

        assertThat(capture.traceId.get()).isNotBlank();
        assertThat(capture.userId.get()).isEqualTo("42");
        assertThat(capture.roleCode.get()).isEqualTo("admin");
        assertThat(response.getHeader(TraceConstants.X_TRACE_ID)).isEqualTo(capture.traceId.get());
    }

    @Test
    void anonymousUserWhenNotAuthenticated() throws ServletException, IOException {
        when(currentUserResolver.getUserMdcContext()).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MdcCaptureServlet capture = new MdcCaptureServlet();

        new MockFilterChain(capture, traceFilter).doFilter(request, response);

        assertThat(capture.userId.get()).isEqualTo("anonymous");
        assertThat(capture.roleCode.get()).isEqualTo("anonymous");
    }

    @Test
    void reusesTraceIdFromRequestHeader() throws ServletException, IOException {
        when(currentUserResolver.getUserMdcContext()).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.addHeader(TraceConstants.X_TRACE_ID, "existing-trace-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MdcCaptureServlet capture = new MdcCaptureServlet();

        new MockFilterChain(capture, traceFilter).doFilter(request, response);

        assertThat(capture.traceId.get()).isEqualTo("existing-trace-id");
        assertThat(response.getHeader(TraceConstants.X_TRACE_ID)).isEqualTo("existing-trace-id");
    }

    @Test
    void skipsStaticAndActuatorPaths() {
        assertThat(traceFilter.shouldNotFilter(request("/actuator/health"))).isTrue();
        assertThat(traceFilter.shouldNotFilter(request("/swagger-ui/index.html"))).isTrue();
        assertThat(traceFilter.shouldNotFilter(request("/v3/api-docs"))).isTrue();
        assertThat(traceFilter.shouldNotFilter(request("/webjars/jquery.js"))).isTrue();
        assertThat(traceFilter.shouldNotFilter(request("/favicon.ico"))).isTrue();
        assertThat(traceFilter.shouldNotFilter(request("/static/logo.png"))).isTrue();
        assertThat(traceFilter.shouldNotFilter(request("/api/projects"))).isFalse();
    }

    private HttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private static class MdcCaptureServlet extends HttpServlet {
        final AtomicReference<String> traceId = new AtomicReference<>();
        final AtomicReference<String> userId = new AtomicReference<>();
        final AtomicReference<String> roleCode = new AtomicReference<>();

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) {
            traceId.set(MDC.get(TraceConstants.MDC_TRACE_KEY));
            userId.set(MDC.get(TraceConstants.MDC_USER_ID_KEY));
            roleCode.set(MDC.get(TraceConstants.MDC_ROLE_CODE_KEY));
        }
    }
}
