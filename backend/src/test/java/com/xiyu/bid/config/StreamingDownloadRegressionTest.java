package com.xiyu.bid.config;

import com.xiyu.bid.apikey.infrastructure.ApiKeyAuthenticationFilter;
import com.xiyu.bid.auth.JwtAuthenticationFilter;
import com.xiyu.bid.auth.UserDetailsServiceImpl;
import com.xiyu.bid.idempotency.IdempotencyFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(controllers = StreamingDownloadRegressionTest.ProbeController.class)
@AutoConfigureMockMvc
@Import({
        SecurityConfig.class,
        StreamingDownloadRegressionTest.ProbeController.class,
        StreamingDownloadRegressionTest.FilterRegistration.class
})
class StreamingDownloadRegressionTest {

    static final byte[] PAYLOAD = "bundle-bytes".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @MockBean
    private IdempotencyFilter idempotencyFilter;

    @BeforeEach
    void passThroughCustomFilters() throws Exception {
        pass(jwtAuthenticationFilter);
        pass(apiKeyAuthenticationFilter);
        pass(idempotencyFilter);
    }

    @Test
    void authenticatedStreamReachesTheClientWithStatus200() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/probe/stream").with(user("bid").roles("USER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PAYLOAD));
    }

    @Test
    void authenticatedEmptyStreamStays200() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/probe/empty").with(user("bid").roles("USER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    void synchronousBytesStillReachTheClient() throws Exception {
        mockMvc.perform(get("/api/probe/bytes").with(user("bid").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PAYLOAD));
    }

    @Test
    void anonymousDownloadStays401() throws Exception {
        mockMvc.perform(get("/api/probe/stream"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void asyncRedispatchWithoutJwtStillReturnsTheFile() throws Exception {
        mockMvc.perform(get("/api/probe/bytes").with(request -> {
            request.setDispatcherType(DispatcherType.ASYNC);
            return request;
        }))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PAYLOAD));
    }

    @Test
    void asyncAndErrorDispatchesAreNotAuthorizedAgain() throws Exception {
        AuthorizationFilter authorization = filterChainProxy.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .filter(AuthorizationFilter.class::isInstance)
                .map(AuthorizationFilter.class::cast)
                .findFirst()
                .orElseThrow();
        assertFalse(flag(authorization, "filterAsyncDispatch"));
        assertFalse(flag(authorization, "filterErrorDispatch"));
    }

    private static void pass(Filter filter) throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(filter).doFilter(any(), any(), any());
    }

    private static boolean flag(AuthorizationFilter filter, String name) throws Exception {
        Field field = AuthorizationFilter.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(filter);
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/probe/stream")
        ResponseEntity<StreamingResponseBody> stream() {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(PAYLOAD.length)
                    .body(out -> out.write(PAYLOAD));
        }

        @GetMapping("/api/probe/empty")
        ResponseEntity<StreamingResponseBody> empty() {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(out -> { });
        }

        @GetMapping("/api/probe/bytes")
        ResponseEntity<byte[]> bytes() {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(PAYLOAD);
        }
    }

    @Configuration
    static class FilterRegistration {
        @Bean
        FilterRegistrationBean<AccessLogFilter> accessLogFilter() {
            FilterRegistrationBean<AccessLogFilter> registration =
                    new FilterRegistrationBean<>(new AccessLogFilter());
            registration.setOrder(1);
            return registration;
        }

        @Bean
        FilterRegistrationBean<JwtAuthenticationFilter> jwtNotAServletFilter(
                JwtAuthenticationFilter filter) {
            return disabled(filter);
        }

        @Bean
        FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyNotAServletFilter(
                ApiKeyAuthenticationFilter filter) {
            return disabled(filter);
        }

        @Bean
        FilterRegistrationBean<IdempotencyFilter> idempotencyNotAServletFilter(
                IdempotencyFilter filter) {
            return disabled(filter);
        }

        private static <T extends Filter> FilterRegistrationBean<T> disabled(T filter) {
            FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        }
    }
}
