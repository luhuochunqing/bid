// Input: configuration state request
// Output: OCR gateway credentials, legacy Baidu keys and enabled status
// Pos: scoreparse/infrastructure/baidu - credentials provider SPI contract
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.scoreparse.infrastructure.baidu;

/**
 * 百度智能云表格识别动态凭证提供者 SPI 契约。
 * <p>允许外部配置中心（如系统动态设置）向 infrastructure 提供动态凭据，避免直接耦合业务服务。
 */
public interface BaiduOcrCredentialsProvider {

    /**
     * 当前百度表格识别是否在配置中处于启用状态。
     */
    boolean isEnabled();

    /**
     * 获取有效的 API Key（若未配置返回 null）。
     */
    String getApiKey();

    /**
     * 获取有效的 Secret Key（若未配置返回 null）。
     */
    String getSecretKey();
    /** APIHub 用户名，旧百度 Key 不作为回退。 */
    default String getUsername() { return null; }

    /** APIHub 密码，允许与百度凭据独立配置。 */
    default String getPassword() { return null; }
}
