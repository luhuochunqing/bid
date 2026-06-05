// Input: Spring 配置属性、环境变量、外部 bean 依赖
// Output: 配置 Bean、过滤器、线程池和启动级常量
// Pos: Config/基础设施层
// 维护声明: 仅维护配置与启动约束；业务规则变更请同步到对应 service/controller.
package com.xiyu.bid.config;

/**
 * 分页常量
 *
 * 统一分页参数限制，防止客户端请求过大数据集导致性能问题
 */
public final class PaginationConstants {

    private PaginationConstants() {
        // 工具类，禁止实例化
    }

    /**
     * 默认页码（从0开始）
     */
    public static final int DEFAULT_PAGE = 0;

    /**
     * 默认页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 允许的最大页大小
     *
     * 安全考虑：防止客户端请求过大数据集导致：
     * - 数据库压力过大
     * - 内存溢出
     * - 响应时间过长
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * 允许的排序字段列表（白名单机制）
     *
     * 在实际使用中，应该对可排序的字段进行限制，
     * 防止恶意客户端排序敏感字段或未索引字段
     */
    public static final String[] ALLOWED_SORT_FIELDS = {
        "id", "createdAt", "updatedAt", "name", "status", "amount"
    };
}
