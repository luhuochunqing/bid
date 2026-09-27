package com.xiyu.bid.scoreparse.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** One default-off flag. Each parse captures this choice before dispatch. */
@Component
public record ScoreExtractionVersionFeature(boolean enabled) {
    public ScoreExtractionVersionFeature(@Value("${app.score-parse.extraction-version.enabled:false}") boolean enabled) { this.enabled=enabled; }
}
