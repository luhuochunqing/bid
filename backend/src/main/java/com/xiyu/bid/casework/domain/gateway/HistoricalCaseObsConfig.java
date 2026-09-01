// Input: 历史案例原文下载签发所需的 OBS 配置
// Output: 桶名与预签名有效期
// Pos: casework 模块出站端口（Hexagonal），由 infrastructure 层适配 ObsProperties
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.casework.domain.gateway;

/**
 * 历史案例原文下载的 OBS 配置读取端口。
 *
 * <p>application 层通过本端口读取桶名与预签名有效期，
 * 不直接依赖 infrastructure 层的 ObsProperties（对齐 file 模块 D2-1 修复模式）。</p>
 */
public interface HistoricalCaseObsConfig {

    /** 历史资产所在桶名。 */
    String bucket();

    /** 预签名下载 URL 有效期（秒），实现方应返回已配置值（默认 300）。 */
    int downloadUrlExpireSeconds();
}
