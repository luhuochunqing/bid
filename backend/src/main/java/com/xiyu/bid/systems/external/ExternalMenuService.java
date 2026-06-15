package com.xiyu.bid.systems.external;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExternalMenuService {

    private final ExternalMenuResponse cachedResponse;

    public ExternalMenuService() {
        this.cachedResponse = new ExternalMenuResponse(
                "bid-platform",
                "西域数智化投标管理平台",
                buildMenuTree()
        );
    }

    public List<ExternalMenuTreeNode> getMenuList() {
        return cachedResponse.getMenus();
    }

    private List<ExternalMenuTreeNode> buildMenuTree() {
        return List.of(
                node("1001", "工作台", "0"),
                node("1002", "标讯中心", "0",
                        node("100201", "标讯列表", "1002"),
                        node("100202", "新建标讯", "1002"),
                        node("100203", "关键词订阅", "1002")),
                node("1003", "投标项目", "0",
                        node("100301", "项目列表", "1003"),
                        node("100302", "创建项目", "1003")),
                node("1004", "知识库", "0",
                        node("100401", "档案台账", "1004"),
                        node("100402", "资质库", "1004"),
                        node("100403", "人员库", "1004"),
                        node("100404", "业绩库", "1004"),
                        node("100405", "品牌授权", "1004"),
                        node("100406", "案例库", "1004"),
                        node("100407", "模板库", "1004")),
                node("1005", "资源管理", "0",
                        node("100501", "资产台账", "1005"),
                        node("100502", "保证金管理", "1005"),
                        node("100503", "费用管理", "1005"),
                        node("100504", "账户管理", "1005"),
                        node("100505", "CA 管理", "1005"),
                        node("100506", "合同借阅", "1005"),
                        node("100507", "结果闭环", "1005")),
                node("1006", "AI 智能中心", "0"),
                node("1007", "数据分析", "0"),
                node("1008", "操作日志", "0"),
                node("1009", "审计日志", "0"),
                node("1010", "系统设置", "0",
                        node("101001", "组织设置", "1010"),
                        node("101002", "组织架构", "1010"),
                        node("101003", "流程表单配置", "1010"),
                        node("101004", "告警规则", "1010"),
                        node("101005", "告警历史", "1010"))
        );
    }

    private ExternalMenuTreeNode node(String code, String name, String parentId, ExternalMenuTreeNode... children) {
        return new ExternalMenuTreeNode(code, name, parentId, code, List.of(children));
    }
}
