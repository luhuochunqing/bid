package com.xiyu.bid.systems.external;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * 提供外部统一组织架构系统的菜单树结构.
 *
 * <p>数据源为程序内常量，返回客户方规范的 {@link ExternalMenuResponse}。
 * id 和 menuCode 相同，parentId="0" 表示根节点。</p>
 *
 * <p>CO-155 顺带修：!443 重构半完成，service.getMenus() 之前只返 List&lt;TreeNode&gt;；
 * 现在按测试契约返 ExternalMenuResponse（含 systemCode/systemName/menus）。</p>
 */
@Service
public class ExternalMenuService {

    /** 客户方系统标识. */
    private static final String SYSTEM_CODE = "bid-platform";

    /** 客户方系统展示名. */
    private static final String SYSTEM_NAME = "西域数智化投标管理平台";

    /** 菜单树缓存. */
    private final ExternalMenuResponse cachedResponse;

    /** 构造服务并构建菜单树缓存. */
    public ExternalMenuService() {
        this.cachedResponse = new ExternalMenuResponse(
                SYSTEM_CODE, SYSTEM_NAME, buildMenuTree());
    }

    /**
     * 返回外部系统菜单响应（含系统信息 + 菜单树）.
     *
     * @return 外部菜单响应
     */
    public ExternalMenuResponse getMenus() {
        return cachedResponse;
    }

    private static List<ExternalMenuTreeNode> buildMenuTree() {
        return List.of(
                // ── 工作台 ──
                node("1001", "工作台", "0", "1001", "/dashboard", List.of("dashboard")),

                // ── 标讯中心 ──
                node("1002", "标讯中心", "0", "1002", "/bidding", List.of("bidding"),
                        List.of(
                                node("100201", "标讯列表", "1002", "100201", "/bidding/list", List.of("bidding-list", "bidding")),
                                node("100202", "新建标讯", "1002", "100202", "/bidding/create", List.of("bidding.create", "bidding")),
                                node("100203", "关键词订阅", "1002", "100203", "/bidding/subscription", List.of("bidding"))
                        )),

                // ── 投标项目 ──
                node("1003", "投标项目", "0", "1003", "/project", List.of("project"),
                        List.of(
                                node("100301", "项目列表", "1003", "100301", "/project/list", List.of("project-list", "project")),
                                node("100302", "创建项目", "1003", "100302", "/project/create", List.of("project.create", "project"))
                        )),

                // ── 知识库 ──
                node("1004", "知识库", "0", "1004", "/knowledge", List.of("knowledge"),
                        List.of(
                                node("100401", "档案台账", "1004", "100401", "/knowledge/archive", List.of("knowledge-archive", "knowledge")),
                                node("100402", "资质库", "1004", "100402", "/knowledge/qualification", List.of("knowledge-qualification", "knowledge")),
                                node("100403", "人员库", "1004", "100403", "/knowledge/personnel", List.of("knowledge-personnel", "knowledge")),
                                node("100404", "业绩库", "1004", "100404", "/knowledge/performance", List.of("knowledge-performance", "knowledge")),
                                node("100405", "品牌授权", "1004", "100405", "/knowledge/brand-auth", List.of("knowledge-brand-auth", "knowledge")),
                                node("100406", "案例库", "1004", "100406", "/knowledge/case", List.of("knowledge-case", "knowledge")),
                                node("100407", "模板库", "1004", "100407", "/knowledge/template", List.of("knowledge-template", "knowledge"))
                        )),

                // ── 资源管理 ──
                node("1005", "资源管理", "0", "1005", "/resource", List.of("resource"),
                        List.of(
                                node("100501", "资产台账", "1005", "100501", "/resource/bar", List.of("resource-bar", "resource")),
                                node("100502", "保证金管理", "1005", "100502", "/resource/deposit", List.of("resource-deposit", "resource")),
                                node("100503", "费用管理", "1005", "100503", "/resource/expense", List.of("resource-expense", "resource")),
                                node("100504", "账户管理", "1005", "100504", "/resource/account", List.of("resource-account", "resource")),
                                node("100505", "CA 管理", "1005", "100505", "/resource/ca", List.of("resource-ca", "resource")),
                                node("100506", "合同借阅", "1005", "100506", "/resource/contract", List.of("resource-contract", "resource")),
                                node("100507", "结果闭环", "1005", "100507", "/resource/bid-result", List.of("resource-bid-result", "resource"))
                        )),

                // ── AI 智能中心 ──
                node("1006", "AI 智能中心", "0", "1006", "/ai-center", List.of("ai-center")),

                // ── 数据分析 ──
                node("1007", "数据分析", "0", "1007", "/analytics", List.of("analytics"),
                        List.of(
                                node("100701", "数据看板", "1007", "100701", "/analytics/dashboard", List.of("analytics-dashboard", "analytics"))
                        )),

                // ── 操作日志 ──
                node("1008", "操作日志", "0", "1008", "/operation-logs", List.of("operation-logs")),

                // ── 审计日志 ──
                node("1009", "审计日志", "0", "1009", "/audit-logs", List.of("audit-logs")),

                // ── 系统设置 ──
                node("1010", "系统设置", "0", "1010", "/settings", List.of("settings"),
                        List.of(
                                node("101001", "组织设置", "1010", "101001", "/settings/organization", List.of("settings-organization", "settings")),
                                node("101002", "组织架构", "1010", "101002", "/settings/org-chart", List.of("settings-org-chart", "settings")),
                                node("101003", "流程表单配置", "1010", "101003", "/settings/workflow-forms", List.of("settings-workflow-forms", "settings")),
                                node("101004", "告警规则", "1010", "101004", "/settings/alert-rules", List.of("settings-alert-rules", "settings")),
                                node("101005", "告警历史", "1010", "101005", "/settings/alert-history", List.of("settings-alert-history", "settings"))
                        ))
        );
    }

    /** 构造无子菜单的叶子节点. */
    private static ExternalMenuTreeNode node(final String id, final String menuName,
                                              final String parentId, final String menuCode,
                                              final String path, final List<String> permissionKeys) {
        return new ExternalMenuTreeNode(id, menuName, parentId, menuCode, menuCode, menuName, path, permissionKeys, List.of());
    }

    /** 构造有子菜单的父节点. */
    private static ExternalMenuTreeNode node(final String id, final String menuName,
                                              final String parentId, final String menuCode,
                                              final String path, final List<String> permissionKeys,
                                              final List<ExternalMenuTreeNode> children) {
        return new ExternalMenuTreeNode(id, menuName, parentId, menuCode, menuCode, menuName, path, permissionKeys, children);
    }
}
