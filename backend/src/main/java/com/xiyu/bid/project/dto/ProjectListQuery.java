// Input: 列表接口解析出的 16 个筛选条件与分页参数
// Output: 一个不可变的查询意图对象，并回答"本次是否一个筛选都没传"
// Pos: DTO/查询意图
// 维护声明: 只承载查询意图与准入判据，不含取数逻辑.
package com.xiyu.bid.project.dto;

/**
 * 项目列表接口的一次查询意图：16 个筛选条件 + 分页参数。
 *
 * <p>用 record 而不是把 14 个"否决参数"平铺进方法签名，是因为后者的失效方式是静默的：
 * 只要调用方传错一个位置，"这次没有筛选"就会被误判成"可以下推"，分页结果随之改变而没有任何报错。
 * 把判据收在数据旁边（{@link #hasNoEffectiveFilter()}）就没有这个位置可传错。</p>
 */
public record ProjectListQuery(
        String name,
        String ownerUnit,
        String projectType,
        String customerType,
        String priority,
        String sourceModule,
        String bidStatus,
        String stage,
        Long projectLeaderId,
        Long biddingLeaderId,
        String projectLeaderName,
        String biddingLeaderName,
        String leaderDepartment,
        String region,
        String biddingPlatform,
        String bidMonth,
        Integer page,
        Integer size) {

    /**
     * 本次是否**一个筛选都没传**（16 个筛选全部缺省或空白）——即能否让 SQL 只做排序与分页。
     *
     * <p><b>注意这里的判据是"无筛选"，不是"筛选可下推"。</b>本次**不做任何筛选下推**，原因是下面
     * 两条各自独立成立、且任一条就足以否掉下推：</p>
     *
     * <ol>
     *   <li><b>SQL 的 {@code =} 与 Java 的 {@code String.equals} 不是同一个语义。</b>
     *       {@code projects} 建表 collation 是 {@code utf8mb4_unicode_ci}
     *       （{@code B73__full_schema_baseline.sql}，列重建时未带 {@code COLLATE}，继承表默认），
     *       该排序规则**大小写不敏感**：{@code ?stage=drafting} 在慢路径下与 {@code DRAFTING} 不等
     *       （返回空），在快路径下却会命中 ⇒ 结果是**被放大**的。而 H2 的 {@code VARCHAR} 比较默认
     *       区分大小写，测试环境对这一条**天然无证明力**。此外空白值折叠也不同：慢路径对每个筛选都有
     *       {@code !isBlank()} 守卫，而 SQL 里 {@code stage = ''} 会真的去匹配空串。</li>
     *   <li>其余 15 个筛选另有各自的理由，三类：<b>被富化覆写类</b>——{@code customerType} 与
     *       {@code sourceModule} 虽是 {@code Project} 的真列，但慢路径筛的是**富化后的 DTO**，而
     *       {@code ProjectListEnrichmentSupport} 会用关联标讯的值覆写它们；
     *       <b>模糊匹配类</b>——{@code name} 会同时命中 name / customer / ownerUnit 三列而 ownerUnit
     *       不是 {@code Project} 的列，{@code region} 与 {@code biddingPlatform} 是 {@code contains}
     *       语义而 {@code LIKE} 的元字符含义与它不同；<b>无对应列 / 跨表类</b>——{@code ownerUnit}、
     *       {@code projectType}、{@code priority}、{@code bidStatus}、{@code bidMonth} 在
     *       {@code Project} 上没有对应列，负责人相关的四个筛选来自立项详情与负责人指派表且在富化之后
     *       才存在。</li>
     * </ol>
     *
     * <p>所以凡出现下列之一即返回 false，请求回落到"全量取出后内存筛选"的既有路径。
     * 将来的改动若要让某个筛选走 SQL，必须先证明它同时满足"真列 + 等值比较 + 富化不改写
     * + <b>SQL 等值语义与 {@code String.equals} 一致</b>"四条——第 4 条是这次评审补上的，
     * 前 3 条当时都过了、仍然不等价。</p>
     */
    public boolean hasNoEffectiveFilter() {
        return isBlank(name)
                && isBlank(ownerUnit)
                && isBlank(projectType)
                && isBlank(customerType)
                && isBlank(sourceModule)
                && isBlank(priority)
                && isBlank(bidStatus)
                && isBlank(stage)
                && isBlank(projectLeaderName)
                && isBlank(biddingLeaderName)
                && isBlank(leaderDepartment)
                && isBlank(region)
                && isBlank(biddingPlatform)
                && isBlank(bidMonth)
                && projectLeaderId == null
                && biddingLeaderId == null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
