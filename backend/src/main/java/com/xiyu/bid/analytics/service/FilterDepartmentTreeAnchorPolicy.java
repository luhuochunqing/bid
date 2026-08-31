// Input: 已解包虚拟根的部门树 DTO 索引（code → DTO）
// Output: 锚定西域集团(0001)子树的三级展示根列表；未命中返回 null（调用方回退全树两级）
// Pos: Query Service/数据查询层（纯静态策略）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.AnalyticsDepartmentTreeDTO;

import java.util.List;
import java.util.Map;

/**
 * 数据分析 M1 部门筛选树锚定策略：
 * 以西域集团(0001)为展示根（自身不显示），其直接子节点为一级锚定节点，
 * 展示到锚定节点下面两级（二级/三级），其余根分支与四级以下不进入下拉。
 */
public final class FilterDepartmentTreeAnchorPolicy {

    /** 锚定根：西域集团（OSS 组织编码）。 */
    static final String ANCHOR_CODE = "0001";

    /** 锚定子树允许展示的最大层级（一级锚定节点、二级、三级）。 */
    private static final int ANCHOR_MAX_LEVEL = 3;

    private FilterDepartmentTreeAnchorPolicy() {
    }

    /**
     * 命中锚点时返回锚定子树的展示根列表（已截断到三级）；未命中返回 null，由调用方回退全树两级。
     */
    static List<AnalyticsDepartmentTreeDTO> tryAnchorRoots(Map<String, AnalyticsDepartmentTreeDTO> codeToDtoMap) {
        AnalyticsDepartmentTreeDTO anchor = codeToDtoMap.get(ANCHOR_CODE);
        if (anchor == null || anchor.getChildren() == null) {
            return null;
        }
        List<AnalyticsDepartmentTreeDTO> roots = anchor.getChildren();
        pruneFilterTree(roots, 1, ANCHOR_MAX_LEVEL);
        return roots;
    }

    /** 只留 maxLevel 级；超层 children 置 null。 */
    static void pruneFilterTree(List<AnalyticsDepartmentTreeDTO> nodes, int depth, int maxLevel) {
        if (nodes == null) return;
        for (AnalyticsDepartmentTreeDTO node : nodes) {
            if (depth >= maxLevel || node.getChildren() == null || node.getChildren().isEmpty()) node.setChildren(null);
            else pruneFilterTree(node.getChildren(), depth + 1, maxLevel);
        }
    }
}
