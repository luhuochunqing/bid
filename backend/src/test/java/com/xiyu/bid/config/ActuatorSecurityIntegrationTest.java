package com.xiyu.bid.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.health.redis.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.xiyu.bid.docinsight.infrastructure.config.SidecarHealthIndicator sidecarHealthIndicator;

    @MockBean
    private com.xiyu.bid.ai.config.AiProviderHealthIndicator aiProviderHealthIndicator;

    @Test
    void healthRemainsPublicAndInfoRequiresAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/vnd.spring-boot.actuator.v3+json")));

        // /actuator/info must NOT be anonymously accessible (defense-in-depth against prod over-exposure).
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void publicResponsesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("default-src 'self'")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy",
                        org.hamcrest.Matchers.containsString("camera=()")));
    }

    @Test
    void prometheusIsNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().is4xxClientError());
    }
}
