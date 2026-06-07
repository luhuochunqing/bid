package com.xiyu.bid.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotencyFilterTest {

    private HandlerMapping handlerMapping;
    private InMemoryIdempotencyStore store;
    private IdempotencyFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        handlerMapping = mock(HandlerMapping.class);
        store = new InMemoryIdempotencyStore();
        filter = new IdempotencyFilter(handlerMapping, store);
        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, java.util.Collections.emptyList())
        );
    }

    @Test
    void shouldBypassWhenNoIdempotencyHeader() throws Exception {
        MockHttpServletRequest req = post("{\"a\":1}", null);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = okChain(201, "{\"id\":1}");

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(201);
        assertThat(resp.getContentAsString()).isEqualTo("{\"id\":1}");
        assertThat(store.find("alice:POST:/api/tenders:k1")).isEmpty();
    }

    @Test
    void shouldBypassWhenHandlerHasNoIdempotentAnnotation() throws Exception {
        MockHttpServletRequest req = post("{\"a\":1}", "k-bypass");
        stubHandler(req, "handlerWithoutAnnotation");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = okChain(201, "{\"id\":2}");

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(201);
        assertThat(store.find("alice:POST:/api/tenders:k-bypass")).isEmpty();
    }

    @Test
    void shouldCacheAndReplaySameKey() throws Exception {
        MockHttpServletRequest req1 = post("{\"a\":1}", "k-same");
        stubHandler(req1, "idempotentHandler");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        MockFilterChain chain1 = okChain(201, "{\"id\":99}");

        filter.doFilter(req1, resp1, chain1);

        assertThat(resp1.getStatus()).isEqualTo(201);

        MockHttpServletRequest req2 = post("{\"a\":1}", "k-same");
        stubHandler(req2, "idempotentHandler");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        MockFilterChain chain2 = new MockFilterChain();

        filter.doFilter(req2, resp2, chain2);

        assertThat(resp2.getStatus()).isEqualTo(201);
        assertThat(resp2.getContentAsString()).isEqualTo("{\"id\":99}");
        // chain2 must not have been invoked — verify by ensuring request attribute not set
        assertThat(chain2.getRequest()).isNull();
    }

    @Test
    void shouldReturn422WhenRequestBodyDiffers() throws Exception {
        MockHttpServletRequest req1 = post("{\"a\":1}", "k-diff");
        stubHandler(req1, "idempotentHandler");
        filter.doFilter(req1, new MockHttpServletResponse(), okChain(201, "{\"id\":1}"));

        MockHttpServletRequest req2 = post("{\"a\":2}", "k-diff");
        stubHandler(req2, "idempotentHandler");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();

        filter.doFilter(req2, resp2, new MockFilterChain());

        assertThat(resp2.getStatus()).isEqualTo(422);
        assertThat(resp2.getContentAsString()).contains("Idempotency-Key conflict");
    }

    @Test
    void shouldNotCacheErrorResponses() throws Exception {
        MockHttpServletRequest req1 = post("{\"a\":1}", "k-err");
        stubHandler(req1, "idempotentHandler");
        filter.doFilter(req1, new MockHttpServletResponse(), okChain(500, "{\"error\":\"boom\"}"));

        MockHttpServletRequest req2 = post("{\"a\":1}", "k-err");
        stubHandler(req2, "idempotentHandler");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        MockFilterChain chain2 = okChain(201, "{\"retry\":true}");

        filter.doFilter(req2, resp2, chain2);

        assertThat(resp2.getStatus()).isEqualTo(201);
        assertThat(resp2.getContentAsString()).isEqualTo("{\"retry\":true}");
    }

    private MockHttpServletRequest post(String body, String idempotencyKey) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/tenders");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (idempotencyKey != null) {
            request.addHeader("Idempotency-Key", idempotencyKey);
        }
        return request;
    }

    private void stubHandler(HttpServletRequest request, String handlerMethodName) throws Exception {
        Method method = StubController.class.getMethod(handlerMethodName);
        HandlerMethod handlerMethod = new HandlerMethod(new StubController(), method);
        HandlerExecutionChain chain = new HandlerExecutionChain(handlerMethod);
        when(handlerMapping.getHandler(any(HttpServletRequest.class))).thenReturn(chain);
    }

    private MockFilterChain okChain(int status, String body) {
        return new MockFilterChain(new jakarta.servlet.GenericServlet() {
            @Override
            public void service(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws java.io.IOException {
                HttpServletResponse resp = (HttpServletResponse) response;
                resp.setStatus(status);
                resp.setContentType("application/json");
                resp.getWriter().write(body);
                resp.getWriter().flush();
            }
        });
    }

    public static class StubController {
        @Idempotent
        public void idempotentHandler() {}

        public void handlerWithoutAnnotation() {}
    }
}
