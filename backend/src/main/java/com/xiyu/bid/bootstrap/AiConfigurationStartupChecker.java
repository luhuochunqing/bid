package com.xiyu.bid.bootstrap;

import com.xiyu.bid.ai.client.AiProviderRuntimeConfig;
import com.xiyu.bid.ai.client.RoutingAiProvider;
import com.xiyu.bid.settings.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class AiConfigurationStartupChecker implements ApplicationRunner {

    private final Environment environment;
    private final AiConfigService aiConfigService;
    private final RoutingAiProvider routingAiProvider;

    @Override
    public void run(ApplicationArguments args) {
        boolean isProd = isProductionProfile();
        boolean aiEnabled = aiConfigService.isAiEnabled();

        if (!aiEnabled) {
            if (isProd) {
                log.warn("""
                        ============================================================
                        ⚠️  生产环境警告：AI 功能已在系统设置中关闭！
                        ============================================================
                        影响范围：
                          - 标讯 AI 自动识别将不可用
                          - 项目 AI 评分将不可用
                          - AI 标书审查将不可用
                        
                        请检查：
                          1. 系统设置 -> AI模型设置 是否正确配置
                          2. AI 功能开关是否已开启
                          3. AI 提供商 API Key 是否配置
                        ============================================================
                        """);
            } else {
                log.info("AI 功能当前处于关闭状态（非生产环境）");
            }
            return;
        }

        AiProviderRuntimeConfig config;
        try {
            config = routingAiProvider.resolveActiveConfig();
        } catch (Exception e) {
            log.error("AI 配置解析失败: {}", e.getMessage(), e);
            if (isProd) {
                log.error("""
                        ============================================================
                        ❌ 生产环境严重错误：AI 配置解析失败！
                        ============================================================
                        错误信息：{}
                        
                        请检查：
                          1. 系统设置 -> AI模型设置 是否正确配置
                          2. AI 提供商是否正确选择
                          3. 环境变量是否正确设置
                        ============================================================
                        """, e.getMessage());
            }
            return;
        }

        if (config == null) {
            if (isProd) {
                log.warn("""
                        ============================================================
                        ⚠️  生产环境警告：AI 配置为 mock 模式！
                        ============================================================
                        当前状态：AI 功能使用 mock 模拟数据，未调用真实 AI 服务。
                        
                        影响范围：
                          - 标讯 AI 自动识别将返回模拟结果
                          - 项目 AI 评分将返回模拟结果
                          - AI 标书审查将返回模拟结果
                        
                        请检查：
                          1. 系统设置 -> AI模型设置 中 AI提供商 是否配置
                          2. 环境变量 AI_PROVIDER 是否设置
                          3. API Key 和 Base URL 是否正确配置
                        ============================================================
                        """);
            } else {
                log.info("AI 配置为 mock 模式（非生产环境）");
            }
            return;
        }

        log.info("AI 配置状态：provider={}, model={}, baseUrlConfigured={}, apiKeyConfigured={}",
                config.providerCode(),
                config.model(),
                config.baseUrl() != null && !config.baseUrl().isBlank(),
                config.apiKey() != null && !config.apiKey().isBlank());

        if (isProd) {
            if (config.apiKey() == null || config.apiKey().isBlank()) {
                log.error("""
                        ============================================================
                        ❌ 生产环境严重错误：AI API Key 未配置！
                        ============================================================
                        当前提供商：{}
                        
                        请立即配置：
                          1. 系统设置 -> AI模型设置 中配置 API Key
                          2. 或通过环境变量配置（如 OPENAI_API_KEY、ARK_API_KEY 等）
                        ============================================================
                        """, config.providerCode());
            }
            if (config.baseUrl() == null || config.baseUrl().isBlank()) {
                log.error("""
                        ============================================================
                        ❌ 生产环境严重错误：AI Base URL 未配置！
                        ============================================================
                        当前提供商：{}
                        
                        请立即配置：
                          1. 系统设置 -> AI模型设置 中配置 Base URL
                          2. 或通过环境变量配置（如 OPENAI_BASE_URL 等）
                        ============================================================
                        """, config.providerCode());
            }
        }
    }

    private boolean isProductionProfile() {
        String[] profiles = environment.getActiveProfiles();
        for (String profile : profiles) {
            if (profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production")) {
                return true;
            }
        }
        return false;
    }
}
