// Input: 构造的组织部门节点（含 sourceApp 分区键）与选中集合
// Output: prepare 产出的匹配展开/展示桶/正反双向映射断言
// Pos: Test/部门两级展示口径纯函数单测（FP-Java 纯核心，无框架依赖）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsDepartmentHierarchySupportTest {

    private static final String OSS = "oss";

    private static final List<AnalyticsDepartmentHierarchySupport.DeptNode> ORG = List.of(
            node(OSS, "D01", null, "销售部"),
            node(OSS, "D0101", "D01", "销售一部"),
            node(OSS, "D0102", "D01", "销售二部"),
            node(OSS, "D02", null, "财务部"),
            node(OSS, "D03", null, "行政部")
    );

    /** 三级树：销售部 → 销售一部 → 华东组（用于验证深层选择优先规则与三级坍缩）。 */
    private static final List<AnalyticsDepartmentHierarchySupport.DeptNode> ORG3 = List.of(
            node(OSS, "D01", null, "销售部"),
            node(OSS, "D0101", "D01", "销售一部"),
            node(OSS, "D010101", "D0101", "华东组"),
            node(OSS, "D0102", "D01", "销售二部")
    );

    private static AnalyticsDepartmentHierarchySupport.DeptNode node(
            String sourceApp, String code, String parentCode, String name) {
        return new AnalyticsDepartmentHierarchySupport.DeptNode(sourceApp, code, parentCode, name);
    }

    private static AnalyticsDepartmentHierarchySupport.Prepared prepare(
            List<AnalyticsDepartmentHierarchySupport.DeptNode> nodes, List<String> selected) {
        return AnalyticsDepartmentHierarchySupport.prepare(nodes, selected);
    }

    @Test
    void 未启用筛选时保持不过滤语义且分桶恒等() {
        var prepared = prepare(ORG, null);
        assertNull(prepared.matchNames());
        assertFalse(prepared.hasSelection());
        assertTrue(prepared.bucketNames().isEmpty());
        assertEquals("任意名", prepared.bucketMapper().apply("任意名"));
        // 恒等口径下反解只含标签自身（下钻退化为精确等值语义）
        assertEquals(Set.of("财务部"), prepared.namesInBucket("财务部"));
    }

    @Test
    void 勾选一级展开为自身加全部后代名() {
        assertEquals(Set.of("销售部", "销售一部", "销售二部"), prepare(ORG, List.of("销售部")).matchNames());
    }

    @Test
    void 勾选二级不向父回溯也不波及其他分支() {
        assertEquals(Set.of("销售一部"), prepare(ORG, List.of("销售一部")).matchNames());
    }

    @Test
    void 勾选有二级的一级时由二级成桶且一级不保留汇总柱() {
        // 销售部有二级 → 展示二级、不保留一级柱；行政部无二级 → 自成一桶
        assertEquals(Set.of("销售一部", "销售二部", "行政部"),
                prepare(ORG, List.of("销售部", "行政部")).bucketNames());
    }

    @Test
    void 树外兜底选中名自成一桶() {
        assertEquals(Set.of("客服部"), prepare(ORG, List.of("客服部")).bucketNames());
    }

    @Test
    void 分桶覆盖范围内外均按名称归属_保证与筛选范围数目守恒() {
        var mapper = prepare(ORG, List.of("销售部")).bucketMapper();
        assertEquals("销售一部", mapper.apply("销售一部"));
        assertEquals("销售二部", mapper.apply("销售二部"));
        // 被选一级直挂用户（罕见脏数据）回退自身桶，项目不丢
        assertEquals("销售部", mapper.apply("销售部"));
    }

    /**
     * 深层选择优先规则的可回归形态：三级树 + 两种勾选提交顺序必须得到同一口径。
     * 两级 fixture 下该规则对顺序不敏感（曾使删除排序的变异体全部存活），故必须用三级。
     */
    @Test
    void 三级嵌套选择时深层优先且与勾选提交顺序无关() {
        var parentFirst = prepare(ORG3, List.of("销售部", "销售一部"));
        var childFirst = prepare(ORG3, List.of("销售一部", "销售部"));

        for (var prepared : List.of(parentFirst, childFirst)) {
            // 深者优先：销售一部 自身被勾选且有子部门 → 它的子树再下沉一层，华东组 独立成桶，
            // 不再归到一级选择展开出的"销售一部"桶（两级 fixture 观察不到该差异，故必须三级）
            assertEquals("华东组", prepared.bucketMapper().apply("华东组"));
            assertEquals("销售一部", prepared.bucketMapper().apply("销售一部"));
            assertEquals(Set.of("销售一部", "销售二部", "华东组"), prepared.bucketNames());
            assertEquals(Set.of("销售部", "销售一部", "销售二部", "华东组"), prepared.matchNames());
        }
    }

    /**
     * 跨组织源分区：organization_departments 主键为 (source_app, department_code)，
     * 同一 code 可在多棵组织树并存（V1076）。父子索引若按裸 code 建，会把两棵树缝成一棵，
     * 勾一边将另一边的整棵子树拉进统计范围。
     */
    @Test
    void 跨source_app同code不得串树() {
        var twoSources = List.of(
                node("oss", "D01", null, "销售部"),
                node("oss", "D0101", "D01", "销售一部"),
                node("customer-org", "D01", null, "技术部"),
                node("customer-org", "D09", "D01", "客服部"));

        var sales = prepare(twoSources, List.of("销售部"));
        assertEquals(Set.of("销售部", "销售一部"), sales.matchNames(),
                "另一组织源的客服部不应进入销售部的统计范围");
        assertEquals(Set.of("销售一部"), sales.bucketNames());

        var tech = prepare(twoSources, List.of("技术部"));
        assertEquals(Set.of("技术部", "客服部"), tech.matchNames(), "只展开同源后代");
        assertEquals(Set.of("客服部"), tech.bucketNames(), "隔壁组织源的部门不得顶替本端子部门成桶");
    }

    /**
     * 正向映射（图表分桶）与反向索引（下钻反解）必须同源闭合：
     * 桶标签反解出的每个部门名，正向映射必须回到同一桶；否则"图上柱值 ≠ 明细条数"。
     */
    @Test
    void 正反映射同源闭合() {
        var selections = List.of(List.of("销售部"), List.of("销售部", "销售一部"),
                List.of("销售一部"), List.of("销售一部", "销售部"), List.of("财务部"), List.of("客服部"));
        for (var tree : List.of(ORG, ORG3)) {
            for (List<String> selection : selections) {
                var prepared = prepare(tree, selection);
                for (String bucket : prepared.bucketNames()) {
                    for (String name : prepared.namesInBucket(bucket)) {
                        String mapped = prepared.bucketMapper().apply(name);
                        assertTrue(mapped.equals(bucket) || name.equals(bucket),
                                () -> "树 " + tree.hashCode() + " 选 " + selection
                                        + "：桶 " + bucket + " 反解出 " + name + " 但正向映射到 " + mapped);
                    }
                }
            }
        }
    }

    @Test
    void 三级坍缩桶反解包含三级成员() {
        var prepared = prepare(ORG3, List.of("销售部"));
        // 图上柱"销售一部"含三级成员华东组，下钻 IN 集合必须一起命中
        assertEquals(Set.of("销售一部", "华东组"), prepared.namesInBucket("销售一部"));
    }

    @Test
    void 空code节点构造即失败_byDesign脏行应在网关层跳过() {
        assertThrows(IllegalArgumentException.class,
                () -> node(OSS, " ", null, "无码部门"));
    }

    @Test
    void 环状脏数据不死循环() {
        var cyclic = List.of(
                node(OSS, "A", "B", "甲部"),
                node(OSS, "B", "A", "乙部"));
        var prepared = new HashSet<>(prepare(cyclic, List.of("甲部")).matchNames());
        assertEquals(Set.of("甲部", "乙部"), prepared, "成环时收敛到可达集合即可，不得栈溢出");
    }
}
