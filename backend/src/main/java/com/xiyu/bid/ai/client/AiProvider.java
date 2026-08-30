package com.xiyu.bid.ai.client;

import com.xiyu.bid.ai.dto.AiAnalysisResponse;
import com.xiyu.bid.ai.dto.BidDocumentQualityAiPreviewDTO;

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

    BidDocumentQualityAiPreviewDTO previewBidDocumentQuality(
            String documentContent, String tenderText);

    /**
     * Generic chat completion returning the raw assistant text.
     * Used for structured JSON extraction prompts (e.g. tender requirements,
     * spec 049 US2). Implementations throw when AI is disabled/unconfigured;
     * callers degrade gracefully.
     *
     * @param prompt The full prompt to send
     * @param maxTokens Maximum tokens for the completion
     * @return Raw completion text
     */
    String chatCompletion(String prompt, int maxTokens);

    /**
     * Structured JSON chat completion (response_format=json_object mode).
     * When supported upstream, enforces standard JSON object output;
     * when unsupported by upstream gateway, automatically degrades to standard chatCompletion.
     *
     * @param prompt The prompt to send (should contain "JSON" instruction)
     * @param maxTokens Maximum tokens for the completion
     * @return Raw JSON completion text
     */
    default String chatCompletionJsonObject(String prompt, int maxTokens) {
        return chatCompletion(prompt, maxTokens);
    }

    /**
     * Generate a dense embedding vector for the given text.
     *
     * @param text The text to embed
     * @return embedding vector as float array
     */
    float[] embed(String text);
}
