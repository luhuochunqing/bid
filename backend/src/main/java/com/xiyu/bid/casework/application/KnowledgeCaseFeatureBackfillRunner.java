package com.xiyu.bid.casework.application;

import com.xiyu.bid.casework.application.port.KnowledgeCaseFeatureBackfillPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 存量案例的子主题和质量分回填。默认不跑。
 *
 * <p>只在启动时 XIYU_CASE_FEATURE_BACKFILL 为 1 或 true 时调用端口。端口只处理 sub_topic 仍为空的行。</p>
 */
@Component
@Profile({"dev", "prod", "mysql"})
public class KnowledgeCaseFeatureBackfillRunner implements ApplicationRunner {

    static final String FLAG = "XIYU_CASE_FEATURE_BACKFILL";

    private static final Logger log = LoggerFactory.getLogger(KnowledgeCaseFeatureBackfillRunner.class);

    private final KnowledgeCaseFeatureBackfillPort featureBackfillPort;

    public KnowledgeCaseFeatureBackfillRunner(KnowledgeCaseFeatureBackfillPort featureBackfillPort) {
        this.featureBackfillPort = featureBackfillPort;
    }

    @Override
    public void run(ApplicationArguments args) {
        runIfEnabled(System.getenv(FLAG));
    }

    void runIfEnabled(String flag) {
        if (!"1".equals(flag) && !"true".equalsIgnoreCase(flag)) {
            return;
        }
        int updated = featureBackfillPort.backfillMissingSubTopic();
        log.info("knowledge_case 特征回填 {} 条", updated);
    }
}
