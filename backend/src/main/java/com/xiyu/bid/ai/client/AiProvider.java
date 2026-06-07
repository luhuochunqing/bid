package com.xiyu.bid.ai.client;

import com.xiyu.bid.ai.dto.AiAnalysisResponse;

import java.util.Map;

/**
 * AI Provider Interface
 * Defines the contract for AI analysis providers (Mock, OpenAI, etc.)
 */
public interface AiProvider {

    /**
     * Analyze tender content synchronously.
     *
     * @param content The tender content to analyze
     * @param context Additional context information (budget, deadline, etc.)
     * @return analysis results
     */
    AiAnalysisResponse analyzeTender(String content, Map<String, Object> context);

    /**
     * Analyze project synchronously.
     *
     * @param projectId The project ID to analyze
     * @param context Additional context information
     * @return analysis results
     */
    AiAnalysisResponse analyzeProject(Long projectId, Map<String, Object> context);
}
