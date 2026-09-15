// Input: projectId
// Output: Optional<TenderQualificationInfo>（包含 qualificationRequirements 与 rawTenderText）
// Pos: casework/application/port/ - 招标文件资格项/原文查询端口（Hexagonal Port）
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.casework.application.port;

import java.util.List;
import java.util.Optional;

/**
 * 招标文件资格项/原文查询端口。
 *
 * <p>Application 层通过此端口获取项目最新招标文件解析出的资质要求列表及正文原文，
 * 避免直接依赖 biddraftagent 的 JPA Snapshot Repository，保证模块边界与端口适配器模式。</p>
 */
public interface TenderQualificationQueryPort {

    record TenderQualificationInfo(
            List<String> qualificationRequirements,
            String rawTenderText
    ) {}

    /**
     * 根据项目 ID 查询最新招标文件的资格项与全文信息。
     *
     * @param projectId 项目 ID
     * @return 资格项与原文信息，若未解析或不存在则返回 empty
     */
    Optional<TenderQualificationInfo> findByProjectId(Long projectId);
}
