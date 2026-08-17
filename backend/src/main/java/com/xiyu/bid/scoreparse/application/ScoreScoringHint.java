package com.xiyu.bid.scoreparse.application;

final class ScoreScoringHint {

    private ScoreScoringHint() {
    }

    static String outcomeOf(String stage) {
        if (stage == null) {
            return null;
        }
        if (stage.startsWith("INCREMENTAL")) {
            return "INCREMENTAL";
        }
        return "SKIPPED".equals(stage) || "FULL".equals(stage) ? stage : null;
    }

    static String text(String outcome, Integer reused, int total, String stage) {
        if ("SKIPPED".equals(outcome)) {
            return "文件未变化";
        }
        if ("FULL".equals(outcome)) {
            return "全量打分";
        }
        if (!"INCREMENTAL".equals(outcome)) {
            return null;
        }
        int pipe = stage == null ? -1 : stage.indexOf('|');
        String titles = pipe > 0 && pipe < stage.length() - 1 ? stage.substring(pipe + 1) : "章节有改动";
        int n = reused == null ? 0 : Math.max(0, total - reused);
        return "重评 " + n + " 项（" + titles + "）";
    }
}
