package com.xiyu.bid.common.display;

/**
 * 可展示枚举接口。
 * 实现此接口的枚举可以对外提供 displayName（中文标签），
 * 由前端在渲染时直接使用，避免枚举值泄漏或前后端映射不一致。
 *
 * <h3>用法</h3>
 * <pre>{@code
 * public enum QualificationMatchStatus implements DisplayableEnum {
 *     SATISFIED("已满足"),
 *     ATTENTION("需关注"),
 *     UNSATISFIED("不满足");
 *
 *     private final String displayName;
 *     QualificationMatchStatus(String displayName) { this.displayName = displayName; }
 *     public String getDisplayName() { return displayName; }
 * }
 * }</pre>
 *
 * <h3>序列化</h3>
 * 配合 {@link EnumPairSerializer} 使用，在 DTO 字段上添加
 * {@code @JsonSerialize(using = EnumPairSerializer.class)}，
 * 即可输出 {@code {"name":"SATISFIED","displayName":"已满足"}} 格式，
 * 前端无需再维护独立的映射字典。
 *
 * @see EnumPairSerializer
 */
public interface DisplayableEnum {

    /** 返回枚举值的展示文本（中文标签）。 */
    String getDisplayName();
}
