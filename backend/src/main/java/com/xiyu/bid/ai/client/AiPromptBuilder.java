package com.xiyu.bid.ai.client;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AiPromptBuilder {

    public String buildTenderAnalysisPrompt(String content, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the following tender opportunity and provide a comprehensive assessment.\n\n");
        if (content != null && !content.isEmpty())
            prompt.append("TENDER CONTENT:\n").append(content).append("\n\n");
        if (context != null && !context.isEmpty()) {
            prompt.append("ADDITIONAL CONTEXT:\n");
            context.forEach((k, v) -> prompt.append("- ").append(k).append(": ").append(v).append("\n"));
            prompt.append("\n");
        }
        prompt.append("""
                Please provide your analysis in JSON format with the following structure:
                {
                  "score": <integer 0-100>,
                  "riskLevel": <"LOW", "MEDIUM", or "HIGH">,
                  "strengths": ["<strength 1>", "<strength 2>", ...],
                  "weaknesses": ["<weakness 1>", "<weakness 2>", ...],
                  "recommendations": ["<recommendation 1>", "<recommendation 2>", ...],
                  "dimensionScores": [
                    {"dimension": "Technical", "score": <0-100>, "details": "<explanation>"},
                    {"dimension": "Financial", "score": <0-100>, "details": "<explanation>"},
                    {"dimension": "Timing", "score": <0-100>, "details": "<explanation>"}
                  ]
                }
                """);
        return prompt.toString();
    }

    public String buildProjectAnalysisPrompt(Long projectId, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the following project and provide a comprehensive assessment.\n\n");
        prompt.append("PROJECT ID: ").append(projectId).append("\n\n");
        if (context != null && !context.isEmpty()) {
            prompt.append("PROJECT CONTEXT:\n");
            context.forEach((k, v) -> prompt.append("- ").append(k).append(": ").append(v).append("\n"));
            prompt.append("\n");
        }
        prompt.append("""
                Please provide your analysis in JSON format with the following structure:
                {
                  "score": <integer 0-100>,
                  "riskLevel": <"LOW", "MEDIUM", or "HIGH">,
                  "strengths": ["<strength 1>", "<strength 2>", ...],
                  "weaknesses": ["<weakness 1>", "<weakness 2>", ...],
                  "recommendations": ["<recommendation 1>", "<recommendation 2>", ...],
                  "dimensionScores": [
                    {"dimension": "Team", "score": <0-100>, "details": "<explanation>"},
                    {"dimension": "Resources", "score": <0-100>, "details": "<explanation>"},
                    {"dimension": "Risk", "score": <0-100>, "details": "<explanation>"}
                  ]
                }
                """);
        return prompt.toString();
    }
}
