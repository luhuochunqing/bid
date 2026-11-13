package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.scoreparse.domain.ScoreVersionFingerprint;
import java.net.URI;

/** Secrets and signed query parameters never participate in diagnostics. */
public final class ScoringModelPolicy {
    private ScoringModelPolicy() {}
    public static String fingerprint(OpenAiBidAgentRequestConfig config) {
        URI provider=URI.create(config.baseUrl());
        String endpoint=provider.getScheme()+"://"+provider.getHost()+":"+provider.getPort()+provider.getPath();
        return ScoreVersionFingerprint.hash("actual-score-v2|"+endpoint+"|"+config.model()+"|"+config.apiStyle()
                +"|"+config.timeout()+"|sdk-retries=0|item-transmissions=2|"
                +ScoreParsePrompts.buildObjectiveAssessmentPrompt("",0,"",false)
                +ScoreParsePrompts.buildSubjectiveSuggestionPrompt("",""));
    }
}
