// Input: 严格 ACTIVE 案例查询 / 按项目批量来源更新（附扫描快照期望值防过期写入）
// Output: 不可变候选快照 / 五个来源字段持久化（期望业务数据不符时拒绝整批写入）
// Pos: casework 应用端口，隔离回填编排与 JPA 实体
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.application.port;

import java.util.List;

public interface CaseProvenanceBackfillPort {
    List<Candidate> findActiveCandidates();

    void saveAll(List<Update> updates);

    /** 仅当五个来源字段在写入瞬间仍全为 NULL 时，按项目原子补齐。 */
    void saveAllIfProvenanceEmpty(List<Update> updates);

    record Candidate(Long id, Long sourceProjectId, String responseText, String scoringPointTitle,
                     String sourceSectionTitle, String sourceSectionPath, Integer sourceSectionIndex,
                     String sourceDocumentName, Integer sourcePageNumber) { }

    /**
     * 来源写回命令。expected* 为扫描时刻用于计算来源的关键业务数据快照：
     * 持久化必须原子校验它们未发生变化，否则拒绝写入（防扫描后正文被改导致的过期来源）。
     */
    record Update(Long id, String sourceSectionTitle, String sourceSectionPath, Integer sourceSectionIndex,
                  String sourceDocumentName, Integer sourcePageNumber,
                  String expectedResponseText, String expectedScoringPointTitle, Long expectedSourceProjectId) { }
}
