// Input: 案例查询结果（存在性 + OBS 原版标书对象键）
// Output: 不可变案例原文引用
// Pos: casework 模块领域模型（Hexagonal 端口出入参）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.domain.model;

/**
 * 案例原文引用（端口出入参，不可变）。
 *
 * <p>{@code exists} 区分"案例不存在"（404）与"存在但无原文"（409），
 * 让应用层无需依赖 infrastructure 实体即可编排签发流程。</p>
 */
public record CaseObsBidFile(boolean exists, String obsBidFileKey) {
}
