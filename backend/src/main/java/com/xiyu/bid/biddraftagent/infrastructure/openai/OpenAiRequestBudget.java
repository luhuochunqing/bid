package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.time.Duration;

/** Explicit budget shared by schema fallback, image fallback and recoverable retries of one item. */
public final class OpenAiRequestBudget {
    private final long deadline;
    private int attempts;
    private final OpenAiBidAgentRequestConfig configuration;
    public OpenAiRequestBudget(long deadline){this(deadline,null);}
    public OpenAiRequestBudget(long deadline,OpenAiBidAgentRequestConfig configuration){this.deadline=deadline;this.configuration=configuration;}
    public OpenAiBidAgentRequestConfig configuration(){return configuration;}
    public synchronized Duration acquire(Duration configured) {
        long remaining=deadline-System.currentTimeMillis();
        if(remaining<=0)throw new IllegalStateException("SCORING_DEADLINE_EXCEEDED");
        if(attempts>=2)throw new IllegalStateException("SCORING_ATTEMPTS_EXHAUSTED");
        attempts++;
        return Duration.ofMillis(Math.min(configured.toMillis(),remaining));
    }
    public synchronized int attempts(){return attempts;}
    public synchronized boolean available(){return attempts<2 && deadline>System.currentTimeMillis();}
    public Duration remaining(Duration configured) {
        long left=deadline-System.currentTimeMillis();
        if(left<=0)throw new IllegalStateException("SCORING_DEADLINE_EXCEEDED");
        return Duration.ofMillis(Math.min(left,configured.toMillis()));
    }
}
