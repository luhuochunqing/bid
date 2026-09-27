package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.BidChapterDirtySet;
import com.xiyu.bid.scoreparse.domain.ScoreItemChapterMatch;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 决定本次要重评哪些评分项：脏章 + 手动范围。切不出章或对不上则全量。 */
final class ScoreScoringItemPicker {

    record Plan(List<ScoreItem> toAssess, String outcome, String hint, List<String> dirtyTitles) {
        String stageToken() {
            if (!"INCREMENTAL".equals(outcome) || dirtyTitles == null || dirtyTitles.isEmpty()) {
                return outcome;
            }
            String token = "INCREMENTAL|" + String.join("、", dirtyTitles);
            return token.length() <= 50 ? token : token.substring(0, 50);
        }
    }

    Plan plan(List<ScoreItem> items, Map<Long, ScoreResult> oldResults, String bidText,
              ScoreParseTask lastSuccess, String scope, List<Long> itemIds) {
        Map<Long, String> previousStatuses = new java.util.HashMap<>();
        oldResults.forEach((id, result) -> previousStatuses.put(id, result.getStatusStage2()));
        return plan(items, oldResults, bidText, lastSuccess, scope, itemIds, previousStatuses);
    }

    Plan plan(List<ScoreItem> items, Map<Long, ScoreResult> oldResults, String bidText,
              ScoreParseTask lastSuccess, String scope, List<Long> itemIds,
              Map<Long, String> previousStatuses) {
        List<ScoreItem> scoped = applyScope(items, previousStatuses, scope, itemIds);
        if (scoped.stream().anyMatch(i -> !oldResults.containsKey(i.getId())) || lastSuccess == null || lastSuccess.getChapterHashes() == null) {
            return new Plan(scoped, "FULL", "全量打分", List.of());
        }
        var chapters = BidChapterDirtySet.split(bidText);
        if (chapters.isEmpty()) {
            return new Plan(scoped, "FULL", "无法识别章节，已全量打分", List.of());
        }
        List<String> dirty = BidChapterDirtySet.dirtyTitles(
                chapters, BidChapterHashCodec.decode(lastSuccess.getChapterHashes()));
        if (dirty.isEmpty()) {
            return new Plan(List.of(), "SKIPPED", "文件未变化", List.of());
        }
        List<ScoreItem> related = new ArrayList<>();
        for (ScoreItem item : scoped) {
            ScoreResult old = oldResults.get(item.getId());
            if (ScoreItemChapterMatch.related(item.getDim(), item.getDetail(),
                    old == null ? null : old.getQuote(), old == null ? null : old.getEvidence(), dirty)) {
                related.add(item);
            }
        }
        if (related.isEmpty()) {
            return new Plan(scoped, "FULL", "无法把评分项对应到章节，已全量打分", dirty);
        }
        return new Plan(related, "INCREMENTAL",
                "重评 " + related.size() + " 项（" + String.join("、", dirty) + "）", dirty);
    }

    private List<ScoreItem> applyScope(List<ScoreItem> items, Map<Long, String> previousStatuses,
                                       String scope, List<Long> itemIds) {
        if ("ITEMS".equals(scope)) {
            Set<Long> want = itemIds == null ? Set.of() : Set.copyOf(itemIds);
            return items.stream().filter(item -> want.contains(item.getId())).toList();
        }
        if ("UNSATISFIED".equals(scope)) {
            return items.stream()
                    .filter(item -> "DANGER".equals(previousStatuses.get(item.getId())))
                    .toList();
        }
        return items;
    }
}
