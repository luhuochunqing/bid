package com.xiyu.bid.projectworkflow.parser;

import java.util.List;

record DraftSeed(
        String scoreItemTitle,
        String scoreRuleText,
        String scoreValueText,
        String taskAction,
        String generatedTaskTitle,
        String generatedTaskDescription,
        List<String> deliverables
) {
}
