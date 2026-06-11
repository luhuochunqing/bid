package com.xiyu.bid.systems.external;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 提供外部统一组织架构系统的菜单列表.
 *
 * <p>数据源为程序内常量，与前端 sidebar-menu.js 和
 * RoleProfileCatalog 权限体系对齐。</p>
 */
@Service
public class ExternalMenuService {

    /** 系统code. */
    private static final String SYSTEM_CODE = "bid-platform";

    /** 系统名称. */
    private static final String SYSTEM_NAME = "西域数智化投标管理平台";

    /** 缓存响应（菜单为不可变数据，启动即构建）. */
    private final ExternalMenuResponse cachedResponse;

    /** 构造服务并构建菜单缓存. */
    public ExternalMenuService() {
        this.cachedResponse = buildResponse();
    }

    /**
     * 返回系统菜单列表（含缓存的系统标识）.
     *
     * @return 菜单响应体
     */
    public ExternalMenuResponse getMenus() {
        return cachedResponse;
    }

    private static ExternalMenuResponse buildResponse() {
        List<ExternalMenuTreeNode> menus = List.of(
                menu("dashboard", "工作台", "/dashboard", "workbench",
                        List.of("dashboard"), List.of()),

                menu("bidding", "标讯中心", "/bidding", "bidding",
                        List.of("bidding", "bidding-list"),
                        List.of(
                                leaf("bidding-list", "标讯列表", "/bidding",
                                        List.of("bidding", "bidding-list")),
                                leaf("bidding-create", "新建标讯",
                                        "/bidding/create",
                                        List.of("bidding.create")),
                                leaf("keyword-subscription", "关键词订阅",
                                        "/bidding/keyword-subscription",
                                        List.of("bidding", "bidding-list"))
                        )),

                menu("project", "投标项目", "/project", "project",
                        List.of("project"),
                        List.of(
                                leaf("project-list", "项目列表", "/project",
                                        List.of("project", "project-list")),
                                leaf("project-create", "创建项目",
                                        "/project/create",
                                        List.of("project.create"))
                        )),

                menu("knowledge", "知识库", "/knowledge", "knowledge",
                        List.of("knowledge"),
                        List.of(
                                leaf("knowledge-archive", "档案台账",
                                        "/knowledge/archive",
                                        List.of("knowledge",
                                                "knowledge-archive")),
                                leaf("knowledge-qualification", "资质库",
                                        "/knowledge/qualification",
                                        List.of("knowledge",
                                                "knowledge-qualification")),
                                leaf("knowledge-personnel", "人员库",
                                        "/knowledge/personnel",
                                        List.of("knowledge",
                                                "knowledge-personnel")),
                                leaf("knowledge-performance", "业绩库",
                                        "/knowledge/performance",
                                        List.of("knowledge",
                                                "knowledge-performance")),
                                leaf("knowledge-brand-auth", "品牌授权",
                                        "/knowledge/brand-auth",
                                        List.of("knowledge",
                                                "knowledge-brand-auth")),
                                leaf("knowledge-case", "案例库",
                                        "/knowledge/case",
                                        List.of("knowledge",
                                                "knowledge-case")),
                                leaf("knowledge-template", "模板库",
                                        "/knowledge/template",
                                        List.of("knowledge",
                                                "knowledge-template"))
                        )),

                menu("resource", "资源管理", "/resource", "resource",
                        List.of("resource"),
                        List.of(
                                leaf("resource-bar", "资产台账",
                                        "/resource/bar",
                                        List.of("resource",
                                                "resource-bar")),
                                leaf("resource-margin", "保证金管理",
                                        "/resource/margin",
                                        List.of("resource",
                                                "resource-expense")),
                                leaf("resource-expense", "费用管理",
                                        "/resource/expense",
                                        List.of("resource",
                                                "resource-expense")),
                                leaf("resource-account", "账户管理",
                                        "/resource/account",
                                        List.of("resource",
                                                "resource-account")),
                                leaf("resource-ca", "CA 管理",
                                        "/resource/ca-management",
                                        List.of("resource",
                                                "resource-ca")),
                                leaf("resource-contract-borrow",
                                        "合同借阅",
                                        "/resource/contract-borrow",
                                        List.of("resource")),
                                leaf("resource-bid-result", "结果闭环",
                                        "/resource/bid-result",
                                        List.of("resource"))
                        )),

                menu("ai-center", "AI 智能中心", "/ai-center", "ai-center",
                        List.of("ai-center"), List.of()),

                menu("analytics", "数据分析", "/analytics/dashboard",
                        "analytics",
                        List.of("analytics", "analytics-dashboard"),
                        List.of()),

                menu("operation-logs", "操作日志", "/operation-logs",
                        "history",
                        List.of("operation-logs"), List.of()),

                menu("audit-logs", "审计日志", "/audit-logs", "lock",
                        List.of("audit-logs"), List.of()),

                menu("settings", "系统设置", "/settings", "settings",
                        List.of("settings"),
                        List.of(
                                leaf("settings-org", "组织设置", "/settings",
                                        List.of("settings")),
                                leaf("settings-organization", "组织架构",
                                        "/settings/organization",
                                        List.of("settings")),
                                leaf("settings-workflow-forms",
                                        "流程表单配置",
                                        "/settings/workflow-forms",
                                        List.of("settings",
                                                "settings-workflow-forms")),
                                leaf("settings-alert-rules", "告警规则",
                                        "/settings/alert-rules",
                                        List.of("settings")),
                                leaf("settings-alert-history",
                                        "告警历史",
                                        "/settings/alert-history",
                                        List.of("settings"))
                        ))
        );

        return new ExternalMenuResponse(SYSTEM_CODE, SYSTEM_NAME, menus);
    }

    private static ExternalMenuTreeNode menu(
            final String code,
            final String name,
            final String path,
            final String icon,
            final List<String> permissionKeys,
            final List<ExternalMenuTreeNode> children) {
        return new ExternalMenuTreeNode(code, name, path, icon,
                permissionKeys, children);
    }

    private static ExternalMenuTreeNode leaf(
            final String code,
            final String name,
            final String path,
            final List<String> permissionKeys) {
        return new ExternalMenuTreeNode(code, name, path, null,
                permissionKeys, null);
    }
}
