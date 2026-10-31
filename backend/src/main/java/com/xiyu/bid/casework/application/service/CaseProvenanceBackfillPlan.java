// Input: ACTIVE 案例扫描快照与原始标书定位结果
// Output: 不可变的生产安全回填决策清单与分类统计
// Pos: casework 应用层——preview/apply 共享的审计计划模型
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.application.port.CaseProvenanceBackfillPort.Candidate;

import java.util.List;

public record CaseProvenanceBackfillPlan(List<Decision> decisions) {
    public CaseProvenanceBackfillPlan {
        decisions = List.copyOf(decisions);
    }

    public enum DecisionType {
        FILLABLE,
        SKIP_EXISTING,
        UNRESOLVED,
        PROJECT_UNAVAILABLE,
        ORPHAN
    }

    public record Proposed(String title, String path, Integer index, String documentName, Integer pageNumber) { }

    public record Decision(Candidate candidate, DecisionType type, String reason,
                           Proposed proposed, String evidenceSha256) {
        public Long caseId() {
            return candidate.id();
        }
    }

    public long count(DecisionType type) {
        return decisions.stream().filter(decision -> decision.type() == type).count();
    }

    public int scannedCount() {
        return decisions.size();
    }
}
