// Input: 案例 id
// Output: 案例 OBS 原版标书对象键引用（存在性 + 对象键）
// Pos: casework 模块领域网关端口（Hexagonal 端口接口）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.domain.gateway;

import com.xiyu.bid.casework.domain.model.CaseObsBidFile;

/**
 * 历史案例原版标书对象键读取端口（Hexagonal 端口接口）。
 *
 * <p>application 层通过此端口读取 {@code knowledge_case.obs_bid_file_key}，
 * 不直接依赖 infrastructure 层的 JPA 实体与仓储（对齐 D2-1 修复模式）。</p>
 */
public interface CaseObsBidFileReader {

    /**
     * 按案例 id 查询原文对象键引用。
     *
     * @param caseId 案例 id
     * @return exists=false 表示案例不存在；否则携带可为 null/blank 的对象键
     */
    CaseObsBidFile findByCaseId(Long caseId);
}
