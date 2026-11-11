package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.application.port.KnowledgeCaseFeatureBackfillPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class KnowledgeCaseFeatureBackfillRunnerTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "false", "yes"})
    @DisplayName("开关未打开时不调用回填端口")
    void disabledFlagDoesNotTouchPort(String flag) {
        KnowledgeCaseFeatureBackfillPort port = mock(KnowledgeCaseFeatureBackfillPort.class);
        KnowledgeCaseFeatureBackfillRunner runner = new KnowledgeCaseFeatureBackfillRunner(port);

        runner.runIfEnabled(flag);

        verify(port, never()).backfillMissingSubTopic();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "true", "TRUE"})
    @DisplayName("开关为 1 或 true 时调用回填端口")
    void enabledFlagCallsPort(String flag) {
        KnowledgeCaseFeatureBackfillPort port = mock(KnowledgeCaseFeatureBackfillPort.class);
        KnowledgeCaseFeatureBackfillRunner runner = new KnowledgeCaseFeatureBackfillRunner(port);

        runner.runIfEnabled(flag);

        verify(port).backfillMissingSubTopic();
    }
}
