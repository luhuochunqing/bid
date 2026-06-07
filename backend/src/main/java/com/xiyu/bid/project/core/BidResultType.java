// Input: bid result outcome from registration form
// Output: 4-class result type for retrospective branching (PRD §3.4)
// Pos: project/core/ - pure enum
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

public enum BidResultType {
    WON,        // 中标
    LOST,       // 未中标
    FAILED,     // 流标
    ABANDONED   // 弃标
}
