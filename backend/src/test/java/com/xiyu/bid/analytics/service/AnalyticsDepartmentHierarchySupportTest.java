// Input: 组织部门节点与勾选名
// Output: expand / bucketNames / toBucket 断言（两级回退：L1-only 按一级、L2-only 按二级、混选忽略不完整一级；
//         锚定 0001 三级：按显示层级成柱，子树勾满折叠、部分勾选忽略上级保留下级）
// Pos: Test/部门筛选与 X 轴口径纯函数（两级回退 + 锚定三级）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnalyticsDepartmentHierarchySupportTest {

    private static final List<AnalyticsDepartmentHierarchySupport.DeptNode> ORG = List.of(
            node("D01", null, "销售部"),
            node("D0101", "D01", "销售一部"),
            node("D010101", "D0101", "华东组"),
            node("D0102", "D01", "销售二部"),
            node("D02", null, "财务部"),
            node("D0201", "D02", "财务一部"));

    private static AnalyticsDepartmentHierarchySupport.DeptNode node(
            String code, String parentCode, String name) {
        return new AnalyticsDepartmentHierarchySupport.DeptNode(code, parentCode, name);
    }

    @Test
    void 勾选一级展开为自身加全部后代名() {
        assertEquals(Set.of("销售部", "销售一部", "华东组", "销售二部"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ORG, List.of("销售部"))));
    }

    @Test
    void 勾选二级不向父回溯但含三级() {
        assertEquals(Set.of("销售一部", "华东组"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ORG, List.of("销售一部"))));
    }

    @Test
    void 只选一级时X轴按一级成柱() {
        assertEquals(List.of("销售部", "财务部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ORG, List.of("销售部", "财务部")));
        assertEquals("销售部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, List.of("销售部"), "华东组"));
        assertEquals("销售部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, List.of("销售部"), "销售一部"));
    }

    @Test
    void 只选二级时X轴按二级成柱() {
        assertEquals(List.of("销售一部", "财务一部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ORG, List.of("销售一部", "财务一部")));
        assertEquals("销售一部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, List.of("销售一部"), "华东组"));
    }

    @Test
    void 混选一级和二级时X轴只保留二级() {
        List<String> mixed = List.of("销售部", "销售二部", "财务一部");
        assertEquals(List.of("销售二部", "财务一部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ORG, mixed));
        assertEquals(Set.of("销售二部", "财务一部"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ORG, mixed)));
        assertEquals("销售二部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, mixed, "销售二部"));
        assertEquals("华东组",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, mixed, "华东组"));
        List<String> mixedKeepL2WithL3 = List.of("销售部", "销售一部");
        assertEquals(List.of("销售一部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ORG, mixedKeepL2WithL3));
        assertEquals(Set.of("销售一部", "华东组"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ORG, mixedKeepL2WithL3)));
        assertEquals("销售一部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, mixedKeepL2WithL3, "华东组"));
    }

    @Test
    void 全选可见一二级时按一级成柱且含一级直属() {
        List<String> selectAll = List.of("销售部", "销售一部", "销售二部", "财务部", "财务一部");
        assertEquals(List.of("销售部", "财务部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ORG, selectAll));
        assertEquals(Set.of("销售部", "销售一部", "华东组", "销售二部", "财务部", "财务一部"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ORG, selectAll)));
        assertEquals("销售部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, selectAll, "销售部"));
        assertEquals("销售部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, selectAll, "华东组"));
    }

    @Test
    void 一级下全部二级都勾了视为完整一级() {
        List<String> fullSales = List.of("销售部", "销售一部", "销售二部");
        assertEquals(List.of("销售部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ORG, fullSales));
        assertEquals(Set.of("销售部", "销售一部", "华东组", "销售二部"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ORG, fullSales)));
        List<String> fullSalesPlusOtherL2 = List.of("销售部", "销售一部", "销售二部", "财务一部");
        assertEquals(List.of("销售部", "财务一部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ORG, fullSalesPlusOtherL2));
        assertEquals("销售部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, fullSalesPlusOtherL2, "销售部"));
    }

    @Test
    void 未勾选时expand返回null表示不过滤() {
        assertNull(AnalyticsDepartmentHierarchySupport.expand(ORG, null));
        assertNull(AnalyticsDepartmentHierarchySupport.expand(ORG, List.of()));
        assertEquals(List.of(), AnalyticsDepartmentHierarchySupport.bucketNames(ORG, List.of()));
    }

    @Test
    void 脏行跳过且叶子勾选toBucket恒等() {
        assertNull(AnalyticsDepartmentHierarchySupport.nodeOrNull(null, "D01", "销售部"));
        assertNull(AnalyticsDepartmentHierarchySupport.nodeOrNull("D01", null, " "));
        assertEquals("财务部",
                AnalyticsDepartmentHierarchySupport.toBucket(ORG, List.of("财务部"), "财务部"));
    }

    //=== 锚定西域集团(0001)子树三级显示口径（与 FilterOptionsQueryService.fetchDepartmentTree 对齐）===//
    // 显示层级：一级=锚定节点（供应链/威斯汀爱睿），二级=业务中心等，三级=销售部等；锚点自身与杂项根不显示。
    private static final String SUPPLY_CHAIN = "西域智慧供应链（上海）股份公司";
    private static final List<AnalyticsDepartmentHierarchySupport.DeptNode> ANCHORED_ORG = List.of(
            node("0001", null, "西域集团"),
            node("0002", "0001", SUPPLY_CHAIN),
            node("0003", "0001", "威斯汀爱睿"),
            node("0002001", "0002", "业务中心"),
            node("0002002", "0002", "运营中心"),
            node("000200101", "0002001", "销售一部"),
            node("000200102", "0002001", "销售二部"),
            node("T001", null, "测试1"));

    @Test
    void 锚定后只勾三级部门按三级成柱() {
        assertEquals(List.of("销售一部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ANCHORED_ORG, List.of("销售一部")));
        assertEquals(List.of("销售一部"),
                AnalyticsDepartmentHierarchySupport.expand(ANCHORED_ORG, List.of("销售一部")));
        assertEquals("销售一部",
                AnalyticsDepartmentHierarchySupport.toBucket(ANCHORED_ORG, List.of("销售一部"), "销售一部"));
    }

    @Test
    void 锚定后只勾二级部门按二级成柱且展开含三级() {
        assertEquals(List.of("业务中心"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ANCHORED_ORG, List.of("业务中心")));
        assertEquals(Set.of("业务中心", "销售一部", "销售二部"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ANCHORED_ORG, List.of("业务中心"))));
        assertEquals("业务中心",
                AnalyticsDepartmentHierarchySupport.toBucket(ANCHORED_ORG, List.of("业务中心"), "销售一部"));
    }

    @Test
    void 锚定后只勾一级锚定节点按一级成柱且展开全子树() {
        assertEquals(List.of(SUPPLY_CHAIN),
                AnalyticsDepartmentHierarchySupport.bucketNames(ANCHORED_ORG, List.of(SUPPLY_CHAIN)));
        assertEquals(Set.of(SUPPLY_CHAIN, "业务中心", "运营中心", "销售一部", "销售二部"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ANCHORED_ORG, List.of(SUPPLY_CHAIN))));
        assertEquals(SUPPLY_CHAIN,
                AnalyticsDepartmentHierarchySupport.toBucket(ANCHORED_ORG, List.of(SUPPLY_CHAIN), "销售一部"));
    }

    @Test
    void 锚定后二级勾满全部三级时折叠按二级成柱() {
        List<String> full = List.of("业务中心", "销售一部", "销售二部");
        assertEquals(List.of("业务中心"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ANCHORED_ORG, full));
        assertEquals(Set.of("业务中心", "销售一部", "销售二部"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ANCHORED_ORG, full)));
    }

    @Test
    void 锚定后二级只勾部分三级时忽略二级保留三级() {
        List<String> partial = List.of("业务中心", "销售一部");
        assertEquals(List.of("销售一部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ANCHORED_ORG, partial));
        assertEquals(List.of("销售一部"),
                AnalyticsDepartmentHierarchySupport.expand(ANCHORED_ORG, partial));
        assertEquals("销售一部",
                AnalyticsDepartmentHierarchySupport.toBucket(ANCHORED_ORG, partial, "销售一部"));
        assertEquals("销售二部",
                AnalyticsDepartmentHierarchySupport.toBucket(ANCHORED_ORG, partial, "销售二部"));
    }

    @Test
    void 锚定后一级勾满全部二级时折叠按一级成柱() {
        List<String> full = List.of(SUPPLY_CHAIN, "业务中心", "运营中心");
        assertEquals(List.of(SUPPLY_CHAIN),
                AnalyticsDepartmentHierarchySupport.bucketNames(ANCHORED_ORG, full));
        List<String> fullWithL3 = List.of(SUPPLY_CHAIN, "业务中心", "运营中心", "销售一部", "销售二部");
        assertEquals(List.of(SUPPLY_CHAIN),
                AnalyticsDepartmentHierarchySupport.bucketNames(ANCHORED_ORG, fullWithL3));
    }

    @Test
    void 锚定后一级只勾部分二级时忽略一级保留二级() {
        assertEquals(List.of("业务中心"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ANCHORED_ORG, List.of(SUPPLY_CHAIN, "业务中心")));
        assertEquals(Set.of("业务中心", "销售一部", "销售二部"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ANCHORED_ORG, List.of(SUPPLY_CHAIN, "业务中心"))));
    }

    @Test
    void 锚定后一级加二级加部分三级时逐层忽略只留三级() {
        List<String> deep = List.of(SUPPLY_CHAIN, "业务中心", "销售一部");
        assertEquals(List.of("销售一部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ANCHORED_ORG, deep));
        assertEquals(List.of("销售一部"),
                AnalyticsDepartmentHierarchySupport.expand(ANCHORED_ORG, deep));
    }

    @Test
    void 锚定后跨锚定节点混选各自成柱() {
        assertEquals(List.of(SUPPLY_CHAIN, "威斯汀爱睿"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ANCHORED_ORG, List.of(SUPPLY_CHAIN, "威斯汀爱睿")));
        assertEquals(List.of("威斯汀爱睿", "销售一部"),
                AnalyticsDepartmentHierarchySupport.bucketNames(ANCHORED_ORG, List.of("威斯汀爱睿", "销售一部")));
        assertEquals(Set.of("威斯汀爱睿", "销售一部"),
                Set.copyOf(AnalyticsDepartmentHierarchySupport.expand(ANCHORED_ORG, List.of("威斯汀爱睿", "销售一部"))));
    }

    @Test
    void 未锚定回退两级时物理三级勾选不入轴() {
        // ORG 无 0001 锚点：回退全树两级口径，物理三级（华东组）不进轴（与前端两级截断对齐）
        assertEquals(List.of(),
                AnalyticsDepartmentHierarchySupport.bucketNames(ORG, List.of("华东组")));
        assertEquals(List.of(),
                AnalyticsDepartmentHierarchySupport.expand(ORG, List.of("华东组")));
    }
}
