package com.xiyu.bid.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test: every custom request header the frontend sends must be whitelisted by the
 * backend CORS config. Without this, a dev can add a header on one side (e.g. Idempotency-Key)
 * without the other, and cross-origin preflight silently blocks the request — symptom is
 * "保存入库报错" with no stacktrace and no POST hitting the server.
 *
 * When adding a new custom header on the frontend, add it to {@link #FRONTEND_CUSTOM_HEADERS}
 * AND to SecurityConfig.allowedHeaders. Failing this test is the forcing function.
 */
@SpringBootTest(properties = {
        "management.health.redis.enabled=false",
        "cors.allowed-origins=http://localhost:1314,http://127.0.0.1:1314,http://127.0.0.1:1315"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsAllowedHeadersContractTest {

    private static final String[] FRONTEND_CUSTOM_HEADERS = {
            "Authorization",
            "Content-Type",
            "Idempotency-Key"
    };

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"Authorization", "Content-Type", "Idempotency-Key"})
    void preflightAllowsFrontendCustomHeader(String requestedHeader) throws Exception {
        mockMvc.perform(options("/api/tenders")
                        .header("Origin", "http://127.0.0.1:1315")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", requestedHeader))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:1315"))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase(requestedHeader)));
    }

    @Test
    void preflightAllowsAllFrontendCustomHeadersTogether() throws Exception {
        String combined = String.join(",", FRONTEND_CUSTOM_HEADERS);
        mockMvc.perform(options("/api/tenders")
                        .header("Origin", "http://127.0.0.1:1315")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", combined))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Headers"));
    }
}
