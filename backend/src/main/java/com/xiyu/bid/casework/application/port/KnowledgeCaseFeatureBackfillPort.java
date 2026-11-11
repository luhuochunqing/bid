// Input: 无。由启动回填在开关打开后调用。
// Output: 已写入子主题和质量分的空主题案例条数。
// Pos: casework 应用端口，把回填写库留在基础设施适配器。
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.application.port;

public interface KnowledgeCaseFeatureBackfillPort {

    int backfillMissingSubTopic();
}
