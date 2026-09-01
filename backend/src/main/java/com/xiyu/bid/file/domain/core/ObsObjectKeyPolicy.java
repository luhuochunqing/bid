// Input: 历史切片导入管道写入的 OBS 对象键
// Output: 对象键合法性判定与文件名提取
// Pos: file 模块纯核心（无框架依赖，可独立单测）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.file.domain.core;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * OBS 对象键策略（纯核心）。
 *
 * <p>历史案例原文下载链路中，对象键由离线导入管道写入 {@code knowledge_case}，
 * 签发端点只信库内值；本类提供入库侧校验与下载侧文件名提取，
 * 两个方向共用同一套规则，避免校验逻辑散落在脚本与端点两处。</p>
 */
public final class ObsObjectKeyPolicy {

    /** 历史投标资产在桶内的根前缀（与离线切片脚本的挂载目录对应）。 */
    public static final String HISTORICAL_ROOT_PREFIX = "2026年（投标文件）/";

    private ObsObjectKeyPolicy() {
    }

    /**
     * 校验导入侧对象键是否可入库。
     *
     * <p>拒绝：空值、绝对路径、路径穿越、历史根前缀之外的路径——
     * 防止脏数据入库后被签发端点直接暴露。</p>
     *
     * @param key 候选对象键（桶内相对路径）
     * @return true 表示可入库
     */
    public static boolean isValidHistoricalKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        if (key.startsWith("/")) {
            return false;
        }
        if (key.contains("..")) {
            return false;
        }
        return key.startsWith(HISTORICAL_ROOT_PREFIX) && key.length() > HISTORICAL_ROOT_PREFIX.length();
    }

    /**
     * 从对象键提取下载文件名（最后一个 '/' 之后的部分，URL 解码后）。
     *
     * @param key 对象键，非空
     * @return 文件名；键中不含 '/' 时原样返回
     * @throws IllegalArgumentException key 为空
     */
    public static String filenameOf(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("对象键不能为空");
        }
        int idx = key.lastIndexOf('/');
        String raw = idx >= 0 ? key.substring(idx + 1) : key;
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }
}
