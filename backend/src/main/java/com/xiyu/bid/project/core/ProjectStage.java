// Input: stage code from persistence/API
// Output: 6-stage tender lifecycle enum (PRD §3.1-§3.6)
// Pos: project/core/ - pure enum, no Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

/**
 * 投标项目 6 阶段线性 FSM。CLOSED 为终态。
 */
public enum ProjectStage {
    INITIATED,        // 立项
    DRAFTING,         // 标书编制
    EVALUATING,       // 评标
    RESULT_PENDING,   // 结果确认
    RETROSPECTIVE,    // 复盘
    CLOSED;           // 结项（终态）

    public boolean isTerminal() {
        return this == CLOSED;
    }
}
