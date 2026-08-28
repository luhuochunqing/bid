// Input: 组织部门节点列表（sourceApp/code/parentCode/name）、已选部门名集合
// Output: Prepared——匹配展开名集（WHERE 用）、展示桶集（图表 X 轴用）、正/反映射（下钻反解用）
// Pos: Support/部门两级展示口径纯函数支持（无状态静态工具，禁框架依赖，可纯单测）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 数据分析部门两级展示口径支持（业务规则："选了一级只展示一级，只要有二级就展示二级"）。
 *
 * <p>单一入口 {@link #prepare} 对给定组织树与选中部门名集合一次完成三件事，供调用方复用：</p>
 * <ol>
 *   <li><b>匹配展开</b>：勾选父部门时统计范围扩展为其自身 ∪ 全部后代部门名
 *       （修复此前 {@code u.departmentName IN 选中集} 精确匹配漏掉挂二级人员数据的缺口）。</li>
 *   <li><b>展示桶</b>：无子部门的选中节点以自身为桶；有子部门的选中父节点不单独成柱，
 *       由其直接子部门各成一桶；被选父节点仍直挂用户的罕见脏数据回退自身名成桶，
 *       保证图上合计与筛选范围数目守恒。</li>
 *   <li><b>正/反映射</b>：用户部门名 → 桶标签（{@link Prepared#bucketMapper()}）与其倒排
 *       （{@link Prepared#namesInBucket}）同源构造，图表分桶与下钻反解共用，杜绝两侧演进漂移。</li>
 * </ol>
 *
 * <p>嵌套选择（父子同时勾选）时更深的选择优先（深者后写生效）；树外兜底选中名自成桶。</p>
 *
 * <p><b>组织源分区</b>：{@code organization_departments} 主键为
 * {@code (source_app, department_code)}（V1076，"使 oss/ehsy 等不同 source_app 的部门记录互不覆盖"），
 * 同一 code 可在多棵组织树并存。故父子索引一律以 {@code (sourceApp, code)} 为键
 * ——若只按裸 code 建索引，会把两棵互不相关的树缝成一棵，勾一边把另一边的整棵子树拉进统计范围。</p>
 *
 * <p><b>规模假设</b>：入参为单个租户的组织树，典型 ≤ 10³ 量级；{@code prepare} 为
 * O(n × 树高)（索引一次建成，其后全部查表，无全表线性回扫）。子树遍历为递归，
 * 栈深 = 树高，脏数据成环由 visited 兜住。超过该量级需改迭代式实现。</p>
 *
 * <p><b>为何与仓内同类实现不共用</b>（避免误当重复代码合并）：
 * {@code admin.settings.core.DepartmentGraph#descendantsOf}、
 * {@code service.AdminUserQueryService#collectDescendants}、
 * {@code FilterOptionsQueryService#fetchDepartmentTree} 也在做部门后代展开，
 * 但前者属 admin/settings 域模型、后两者只需后代集合或只产前端树；
 * 跨模块复用会引入错误依赖方向（同 {@code CompetitorDictionaryPolicy} 头注释 REUSE-OK 结论），
 * 且本类额外承载"展示桶"这一 analytics 专属口径。</p>
 */
public final class AnalyticsDepartmentHierarchySupport {

    private AnalyticsDepartmentHierarchySupport() {
    }

    /**
     * 组织部门最小视图 record：调用方从 OrganizationDepartmentEntity 映射，保持本类零框架依赖。
     * sourceApp 为组织源分区键之一（null 归一为空串），name 仅作输出标签。
     */
    public record DeptNode(String sourceApp, String code, String parentCode, String name) {

        public DeptNode {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("departmentCode must not be blank");
            }
            sourceApp = sourceApp == null ? "" : sourceApp;
        }
    }

    /** 预编译上下文：一次 {@link #prepare} 供展开/分桶/下钻反解复用。 */
    public record Prepared(
            Set<String> matchNames,
            Set<String> bucketNames,
            Map<String, String> bucketByName,
            Map<String, Set<String>> namesByBucket) {

        /** 匹配名列表视图；未启用筛选时返回 null（保持"不过滤"语义）。 */
        public List<String> matchNamesList() {
            return matchNames == null ? null : List.copyOf(matchNames);
        }

        /** 是否启用了部门筛选选择。 */
        public boolean hasSelection() {
            return matchNames != null;
        }

        /** 用户部门名 → 展示桶标签；不在映射内（树外名）恒等回自身。 */
        public Function<String, String> bucketMapper() {
            return userName -> bucketByName.getOrDefault(userName, userName);
        }

        /** 展示桶标签 → 归属该桶的部门名集合（下钻反解用，与 {@link #bucketMapper()} 同源）。 */
        public Set<String> namesInBucket(String bucket) {
            Set<String> names = new LinkedHashSet<>();
            names.add(bucket);
            names.addAll(namesByBucket.getOrDefault(bucket, Set.of()));
            return Collections.unmodifiableSet(names);
        }
    }

    /**
     * 构建两级口径上下文：一次遍历完成后代展开、展示桶与正/反映射。
     * 未启用筛选（selectedNames 空/null）时 matchNames=null、映射为空（即恒等口径）。
     */
    public static Prepared prepare(List<DeptNode> nodes, Collection<String> selectedNames) {
        if (selectedNames == null || selectedNames.isEmpty()) {
            return new Prepared(null, Set.of(), Map.of(), Map.of());
        }
        OrgTree tree = OrgTree.of(nodes);

        // 1. 匹配展开：选中 ∪ 全部后代名（组织树外选中名原样保留）
        Set<String> matchNames = new LinkedHashSet<>();
        for (String selected : selectedNames) {
            for (DeptNode root : tree.byName().getOrDefault(selected, List.of())) {
                collectSubtreeNames(tree, root, new HashSet<>(), matchNames);
            }
            matchNames.add(selected);
        }

        // 2+3. 展示桶与分桶：按节点深度升序处理，深者后写生效（嵌套选择优先）。
        //   无子代选中节点自成桶；有子代选中节点由其直接子部门各成一桶并覆盖其整棵子树
        Map<String, Integer> depthByName = new HashMap<>();
        for (String selected : selectedNames) {
            depthByName.put(selected, tree.minDepthOf(selected));
        }
        List<String> ordered = new ArrayList<>(new LinkedHashSet<>(selectedNames));
        ordered.sort(Comparator.comparingInt(name -> depthByName.getOrDefault(name, 0)));

        Map<String, String> bucketByName = new HashMap<>();
        Set<String> buckets = new LinkedHashSet<>();
        for (String selected : ordered) {
            for (DeptNode node : tree.byName().getOrDefault(selected, List.of())) {
                List<DeptNode> childNodes = tree.childrenOf(node);
                if (childNodes.isEmpty()) {
                    bucketByName.put(node.name(), node.name());
                    buckets.add(node.name());
                    continue;
                }
                for (DeptNode child : childNodes) {
                    if (child.name() == null || child.name().isBlank()) {
                        continue;
                    }
                    Set<String> subtreeNames = new LinkedHashSet<>();
                    collectSubtreeNames(tree, child, new HashSet<>(), subtreeNames);
                    for (String name : subtreeNames) {
                        bucketByName.put(name, child.name());
                    }
                    buckets.add(child.name());
                }
            }
        }
        // 树外选中名不在组织树内（byName 查不到），自成桶（数量为 0 也展示）；树内父级不在此列
        for (String selected : ordered) {
            if (!tree.byName().containsKey(selected) && !buckets.contains(selected)) {
                buckets.add(selected);
            }
        }
        return new Prepared(Collections.unmodifiableSet(new LinkedHashSet<>(matchNames)),
                Collections.unmodifiableSet(buckets),
                Collections.unmodifiableMap(new HashMap<>(bucketByName)),
                invert(bucketByName));
    }

    /** 正向桶映射求倒排（O(n) 一次），使正/反映射天然同源。 */
    private static Map<String, Set<String>> invert(Map<String, String> bucketByName) {
        Map<String, Set<String>> inverse = new HashMap<>();
        for (Map.Entry<String, String> entry : bucketByName.entrySet()) {
            inverse.computeIfAbsent(entry.getValue(), k -> new LinkedHashSet<>()).add(entry.getKey());
        }
        Map<String, Set<String>> frozen = new HashMap<>();
        inverse.forEach((bucket, names) -> frozen.put(bucket, Collections.unmodifiableSet(names)));
        return Collections.unmodifiableMap(frozen);
    }

    /** DFS 收集子树（含自身）全部部门名；visited 按 (sourceApp, code) 分区键防御环状脏数据。 */
    private static void collectSubtreeNames(OrgTree tree, DeptNode node, Set<String> visited, Set<String> acc) {
        if (!visited.add(OrgTree.keyOf(node))) {
            return;
        }
        if (node.name() != null && !node.name().isBlank()) {
            acc.add(node.name());
        }
        for (DeptNode child : tree.childrenOf(node)) {
            collectSubtreeNames(tree, child, visited, acc);
        }
    }

    /**
     * 分区组织树索引：一次构造 byKey / byName / children 三张表，全部查找走 O(1) 查表。
     * 父子关系只在同一 sourceApp 内解析（跨源同 code 属于两棵无关的树）。
     */
    private record OrgTree(Map<String, DeptNode> byKey,
                           Map<String, List<DeptNode>> byName,
                           Map<String, List<DeptNode>> childrenByKey) {

        static OrgTree of(List<DeptNode> nodes) {
            Map<String, DeptNode> byKey = new HashMap<>();
            Map<String, List<DeptNode>> byName = new HashMap<>();
            Map<String, List<DeptNode>> children = new HashMap<>();
            for (DeptNode node : nodes) {
                byKey.putIfAbsent(key(node.sourceApp(), node.code()), node);
                if (node.name() != null && !node.name().isBlank()) {
                    byName.computeIfAbsent(node.name(), k -> new ArrayList<>()).add(node);
                }
                if (node.parentCode() != null && !node.parentCode().isBlank()) {
                    children.computeIfAbsent(key(node.sourceApp(), node.parentCode()),
                            k -> new ArrayList<>()).add(node);
                }
            }
            return new OrgTree(byKey, byName, children);
        }

        List<DeptNode> childrenOf(DeptNode node) {
            return childrenByKey.getOrDefault(key(node.sourceApp(), node.code()), List.of());
        }

        /** 节点在分区索引里的唯一键：同一 code 在不同 sourceApp 下是两个不同节点。 */
        static String keyOf(DeptNode node) {
            return key(node.sourceApp(), node.code());
        }

        /** 选中名对应全部实例的最小深度（根为 0），用于嵌套选择的深浅排序。 */
        int minDepthOf(String name) {
            int min = Integer.MAX_VALUE;
            for (DeptNode node : byName.getOrDefault(name, List.of())) {
                min = Math.min(min, depthOf(node));
            }
            return min == Integer.MAX_VALUE ? 0 : min;
        }

        private int depthOf(DeptNode target) {
            int depth = 0;
            DeptNode current = target;
            Set<String> visited = new HashSet<>();
            while (current.parentCode() != null && !current.parentCode().isBlank()
                    && visited.add(key(current.sourceApp(), current.code()))) {
                DeptNode parent = byKey.get(key(current.sourceApp(), current.parentCode()));
                if (parent == null) {
                    break;
                }
                current = parent;
                depth++;
            }
            return depth;
        }

        private static String key(String sourceApp, String code) {
            // \u0000 分隔：避免 "a"+"bc" 与 "ab"+"c" 拼出同一键
            return sourceApp + '\u0000' + code;
        }
    }
}
