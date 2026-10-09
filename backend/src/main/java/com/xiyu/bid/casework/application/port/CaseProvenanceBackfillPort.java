// Input: 严格 ACTIVE 案例查询 / 按项目批量来源更新
// Output: 不可变候选快照 / 五个来源字段持久化
// Pos: casework 应用端口，隔离回填编排与 JPA 实体
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.application.port;

import java.util.List;

public interface CaseProvenanceBackfillPort {
    List<Candidate> findActiveCandidates();

    void saveAll(List<Update> updates);

    record Candidate(Long id, Long sourceProjectId, String responseText, String scoringPointTitle,
                     String sourceSectionTitle, String sourceSectionPath, Integer sourceSectionIndex,
                     String sourceDocumentName, Integer sourcePageNumber) { }

    record Update(Long id, String sourceSectionTitle, String sourceSectionPath, Integer sourceSectionIndex,
                  String sourceDocumentName, Integer sourcePageNumber) { }
}
