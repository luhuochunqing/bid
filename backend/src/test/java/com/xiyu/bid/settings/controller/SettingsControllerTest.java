package com.xiyu.bid.settings.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.client.AiProviderRuntimeConfig;
import com.xiyu.bid.ai.client.OpenAiCompatibleClient;
import com.xiyu.bid.settings.dto.AiModelTestRequest;
import com.xiyu.bid.settings.dto.SettingsUpdateRequest;
import com.xiyu.bid.settings.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    @MockBean
    private OpenAiCompatibleClient openAiCompatibleClient;

    @BeforeEach
    void setUp() {
        systemSettingRepository.deleteAll();
        reset(openAiCompatibleClient);
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void getSettings_AsAdmin_ShouldReturnCurrentSettings() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.systemConfig.sysName").exists())
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.deptDataScope").isArray())
                .andExpect(jsonPath("$.data.projectGroupScope").isArray())
                .andExpect(jsonPath("$.data.aiModelConfig.activeProvider").value("deepseek"))
                .andExpect(jsonPath("$.data.aiModelConfig.providers").isArray())
                .andExpect(jsonPath("$.data.aiModelConfig.providers[0].encryptedApiKey").doesNotExist());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void updateSettings_AsAdmin_ShouldPersistCriticalGovernanceSections() throws Exception {
        SettingsUpdateRequest request = SettingsUpdateRequest.builder()
                .systemConfig(SettingsUpdateRequest.SystemConfigUpdate.builder()
                        .sysName("西域数智化投标管理平台-整改版")
                        .depositWarnDays(10)
                        .qualWarnDays(45)
                        .enableAI(false)
                        .build())
                .roles(List.of(
                        SettingsUpdateRequest.RoleSettingUpdate.builder()
                                .code("admin")
                                .name("管理员")
                                .description("系统管理员")
                                .userCount(1)
                                .dataScope("all")
                                .menuPermissions(List.of("dashboard", "settings"))
                                .allowedProjects(List.of("pg1", "pg2"))
                                .allowedDepts(List.of("dept1"))
                                .build()
                ))
                .deptDataScope(List.of(
                        SettingsUpdateRequest.DeptDataScopeUpdate.builder()
                                .deptName("投标管理部")
                                .dataScope("all")
                                .canViewOtherDepts(true)
                                .allowedDepts(List.of("dept1", "dept2"))
                                .build()
                ))
                .projectGroupScope(List.of(
                        SettingsUpdateRequest.ProjectGroupScopeUpdate.builder()
                                .groupName("央企项目组")
                                .manager("张经理")
                                .memberCount(5)
                                .visibility("custom")
                                .allowedRoles(List.of("admin", "manager"))
                                .build()
                ))
                .aiModelConfig(SettingsUpdateRequest.AiModelConfigUpdate.builder()
                        .activeProvider("doubao")
                        .providers(List.of(SettingsUpdateRequest.AiProviderSettingUpdate.builder()
                                .providerCode("doubao")
                                .enabled(true)
                                .baseUrl("https://ark.cn-beijing.volces.com/api/v3/chat/completions")
                                .model("doubao-test")
                                .apiKeyPlaintext("sk-doubao-secret-1234")
                                .build()))
                        .build())
                .build();

        mockMvc.perform(put("/api/settings")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.systemConfig.sysName").value("西域数智化投标管理平台-整改版"))
                .andExpect(jsonPath("$.data.systemConfig.depositWarnDays").value(10))
                .andExpect(jsonPath("$.data.roles[0].menuPermissions[1]").value("settings"))
                .andExpect(jsonPath("$.data.deptDataScope[0].canViewOtherDepts").value(true))
                .andExpect(jsonPath("$.data.projectGroupScope[0].visibility").value("custom"))
                .andExpect(jsonPath("$.data.aiModelConfig.activeProvider").value("doubao"))
                .andExpect(jsonPath("$.data.aiModelConfig.providers[3].apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.aiModelConfig.providers[3].apiKeyMasked").value("sk-d****1234"))
                .andExpect(jsonPath("$.data.aiModelConfig.providers[3].encryptedApiKey").doesNotExist());

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemConfig.sysName").value("西域数智化投标管理平台-整改版"))
                .andExpect(jsonPath("$.data.roles[0].menuPermissions[1]").value("settings"))
                .andExpect(jsonPath("$.data.deptDataScope[0].allowedDepts[1]").value("dept2"))
                .andExpect(jsonPath("$.data.projectGroupScope[0].visibility").value("custom"))
                .andExpect(jsonPath("$.data.aiModelConfig.activeProvider").value("doubao"));
    }

    @Test
    @WithMockUser(roles = {"MANAGER"})
    void updateSettings_AsNonAdmin_ShouldReturnForbidden() throws Exception {
        SettingsUpdateRequest request = SettingsUpdateRequest.builder()
                .systemConfig(SettingsUpdateRequest.SystemConfigUpdate.builder()
                        .sysName("无权修改")
                        .depositWarnDays(9)
                        .qualWarnDays(30)
                        .enableAI(true)
                        .build())
                .build();

        mockMvc.perform(put("/api/settings")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void testAiModel_AsAdmin_ShouldReturnConnectionStatus() throws Exception {
        AiModelTestRequest request = AiModelTestRequest.builder()
                .providerCode("deepseek")
                .baseUrl("https://api.deepseek.com/chat/completions")
                .model("deepseek-chat")
                .apiKeyPlaintext("sk-deepseek-secret-1234")
                .build();

        mockMvc.perform(post("/api/settings/ai-models/test")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.providerCode").value("deepseek"))
                .andExpect(jsonPath("$.data.status").value("success"));

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiModelConfig.providers[1].providerCode").value("deepseek"))
                .andExpect(jsonPath("$.data.aiModelConfig.providers[1].apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.aiModelConfig.providers[1].apiKeyMasked").value("sk-d****1234"))
                .andExpect(jsonPath("$.data.aiModelConfig.providers[1].encryptedApiKey").doesNotExist());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void testAiModel_WhenProviderFails_ShouldReturnFailedStatus() throws Exception {
        doThrow(new RuntimeException("invalid api key")).when(openAiCompatibleClient).testConnection(any(AiProviderRuntimeConfig.class));

        AiModelTestRequest request = AiModelTestRequest.builder()
                .providerCode("openai")
                .baseUrl("https://api.openai.com/v1/chat/completions")
                .model("gpt-4o-mini")
                .apiKeyPlaintext("bad-key")
                .build();

        mockMvc.perform(post("/api/settings/ai-models/test")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerCode").value("openai"))
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.message").value("invalid api key"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void testAiModel_WhenDeepSeekBalanceInsufficient_ShouldKeepActionableMessage() throws Exception {
        String actionableMessage = "DeepSeek API 余额不足，请在 DeepSeek 控制台充值，或更换有余额的 API Key 后再测试。";
        doThrow(new RuntimeException(actionableMessage, new RuntimeException("402 Payment Required")))
                .when(openAiCompatibleClient)
                .testConnection(any(AiProviderRuntimeConfig.class));

        AiModelTestRequest request = AiModelTestRequest.builder()
                .providerCode("deepseek")
                .baseUrl("https://api.deepseek.com/chat/completions")
                .model("deepseek-chat")
                .apiKeyPlaintext("sk-test")
                .build();

        mockMvc.perform(post("/api/settings/ai-models/test")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerCode").value("deepseek"))
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.message").value(actionableMessage));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void testAiModel_WithUntrustedHost_ShouldReturnFailedWithoutCallingProvider() throws Exception {
        AiModelTestRequest request = AiModelTestRequest.builder()
                .providerCode("openai")
                .baseUrl("https://metadata.internal/latest")
                .model("gpt-4o-mini")
                .apiKeyPlaintext("sk-test")
                .build();

        mockMvc.perform(post("/api/settings/ai-models/test")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerCode").value("openai"))
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.message").value("AI API 地址必须匹配当前厂商的官方域名"));

        verify(openAiCompatibleClient, never()).testConnection(any(AiProviderRuntimeConfig.class));
    }

    @Test
    @WithMockUser(roles = {"MANAGER"})
    void testAiModel_AsNonAdmin_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/settings/ai-models/test")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
