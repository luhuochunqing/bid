// Input: HTTP 请求、handler 元数据、Idempotency-Key 头
// Output: 复用首次响应或放行进入 controller
// Pos: Idempotency/过滤器层
// 维护声明: 仅维护幂等流程；写入语义请同步 IdempotencyStore.
package com.xiyu.bid.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Component
@Order(50)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

    private final ObjectProvider<HandlerMapping> handlerMappingProvider;
    private final ObjectProvider<IdempotencyStore> storeProvider;

    @org.springframework.beans.factory.annotation.Autowired
    public IdempotencyFilter(
            @Qualifier("requestMappingHandlerMapping")
            ObjectProvider<HandlerMapping> handlerMappingProvider,
            ObjectProvider<IdempotencyStore> storeProvider
    ) {
        this.handlerMappingProvider = handlerMappingProvider;
        this.storeProvider = storeProvider;
    }

    // Test-only constructor (legacy non-provider)
    IdempotencyFilter(HandlerMapping handlerMapping, IdempotencyStore store) {
        this.handlerMappingProvider = new SimpleProvider<>(handlerMapping);
        this.storeProvider = new SimpleProvider<>(store);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String headerKey = request.getHeader(HEADER_IDEMPOTENCY_KEY);
        if (headerKey == null || headerKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        IdempotencyStore store = storeProvider.getIfAvailable();
        HandlerMapping handlerMapping = handlerMappingProvider.getIfAvailable();
        if (store == null || handlerMapping == null) {
            chain.doFilter(request, response);
            return;
        }

        Idempotent annotation = resolveIdempotentAnnotation(handlerMapping, request);
        if (annotation == null) {
            chain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new BufferingRequestWrapper(request);
        // Pre-buffer body so getContentAsByteArray() and downstream read both work.
        byte[] requestBytes = readAllBytes(wrappedRequest);
        String requestBodyHash = digestBytes(requestBytes);
        String cacheKey = buildCacheKey(wrappedRequest, headerKey);

        Optional<IdempotencyStore.CachedResponse> cached = store.find(cacheKey);
        if (cached.isPresent()) {
            IdempotencyStore.CachedResponse hit = cached.get();
            if (hit.getRequestBodyHash() != null && !hit.getRequestBodyHash().equals(requestBodyHash)) {
                response.setStatus(422);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"success\":false,\"code\":422,\"message\":\"Idempotency-Key conflict: request body differs from first request\"}"
                );
                return;
            }
            log.debug("Idempotency cache HIT key={} status={}", cacheKey, hit.getStatus());
            response.setStatus(hit.getStatus());
            if (hit.getContentType() != null) {
                response.setContentType(hit.getContentType());
            }
            byte[] body = hit.getBody();
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            byte[] bodyBytes = wrappedResponse.getContentAsByteArray();
            int status = wrappedResponse.getStatus();
            if (status >= 200 && status < 300) {
                IdempotencyStore.CachedResponse snapshot = new IdempotencyStore.CachedResponse(
                        status,
                        wrappedResponse.getContentType(),
                        bodyBytes,
                        requestBodyHash
                );
                store.save(cacheKey, snapshot, Duration.ofSeconds(annotation.ttlSeconds()));
                log.debug("Idempotency cache STORE key={} status={}", cacheKey, status);
            }
            wrappedResponse.copyBodyToResponse();
        }
    }

    private byte[] readAllBytes(ContentCachingRequestWrapper request) throws IOException {
        try (var is = request.getInputStream()) {
            return is.readAllBytes();
        }
    }

    /**
     * Variant that exposes the cached body to downstream filters/controllers via getInputStream().
     * Spring's default {@link ContentCachingRequestWrapper} only caches bytes after the input stream
     * has been read; once the input stream is consumed, subsequent reads return EOF and Spring MVC
     * sees an empty body. This subclass returns a fresh stream over the cached bytes on every call.
     */
    private static final class BufferingRequestWrapper extends ContentCachingRequestWrapper {
        private byte[] buffered;

        BufferingRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() throws IOException {
            if (buffered == null) {
                buffered = super.getInputStream().readAllBytes();
            }
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(buffered);
            return new jakarta.servlet.ServletInputStream() {
                @Override
                public boolean isFinished() { return bis.available() == 0; }

                @Override
                public boolean isReady() { return true; }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener readListener) {}

                @Override
                public int read() { return bis.read(); }
            };
        }

        @Override
        public java.io.BufferedReader getReader() throws IOException {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(),
                    java.nio.charset.Charset.forName(getCharacterEncoding() == null ? "UTF-8" : getCharacterEncoding())));
        }

        @Override
        public byte[] getContentAsByteArray() {
            if (buffered != null) {
                return buffered.clone();
            }
            return super.getContentAsByteArray();
        }
    }

    private Idempotent resolveIdempotentAnnotation(HandlerMapping handlerMapping, HttpServletRequest request) {
        try {
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain == null) {
                return null;
            }
            Object handler = chain.getHandler();
            if (handler instanceof HandlerMethod handlerMethod) {
                Idempotent direct = handlerMethod.getMethodAnnotation(Idempotent.class);
                if (direct != null) {
                    return direct;
                }
                return handlerMethod.getBeanType().getAnnotation(Idempotent.class);
            }
            return null;
        } catch (Exception ex) {
            log.debug("Could not resolve handler for idempotency check: {}", ex.getMessage());
            return null;
        }
    }

    private String buildCacheKey(HttpServletRequest request, String headerKey) {
        return userScope() + ":" + request.getMethod() + ":" + request.getRequestURI() + ":" + headerKey;
    }

    private String userScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return "anon";
        }
        return authentication.getName();
    }

    private String digestBytes(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes);
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(new String(bytes, StandardCharsets.UTF_8).hashCode());
        }
    }

    private static final class SimpleProvider<T> implements ObjectProvider<T> {
        private final T value;

        SimpleProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject() { return value; }

        @Override
        public T getObject(Object... args) { return value; }

        @Override
        public T getIfAvailable() { return value; }

        @Override
        public T getIfUnique() { return value; }
    }
}
