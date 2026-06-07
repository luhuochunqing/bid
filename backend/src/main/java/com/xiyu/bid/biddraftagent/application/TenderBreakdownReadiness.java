package com.xiyu.bid.biddraftagent.application;

public record TenderBreakdownReadiness(
        boolean ready,
        String providerName,
        String envKey,
        String settingsPath,
        String message
) {

    private static final String PROVIDER_NAME = "DeepSeek";
    private static final String ENV_KEY = "DEEPSEEK_API_KEY";
    private static final String SETTINGS_PATH = "/settings";
    private static final String READY_MESSAGE = "DeepSeek 已配置，可以解析招标文件。";
    private static final String MISSING_MESSAGE = "DeepSeek API Key 未配置。请管理员到系统设置 → AI 模型配置中填写 DeepSeek provider key，或在服务端设置 DEEPSEEK_API_KEY 后重启。";

    public static TenderBreakdownReadiness configured() {
        return new TenderBreakdownReadiness(true, PROVIDER_NAME, ENV_KEY, SETTINGS_PATH, READY_MESSAGE);
    }

    public static TenderBreakdownReadiness missingDeepSeekKey() {
        return new TenderBreakdownReadiness(false, PROVIDER_NAME, ENV_KEY, SETTINGS_PATH, MISSING_MESSAGE);
    }
}
