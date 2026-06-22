package com.xiyu.bid.integration.organization.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "xiyu.integrations.organization")
public class OrganizationIntegrationProperties {
    private boolean enabled = true;
    private String webhookSecret = "";
    private String ipWhitelist = "";
    private int eventLogRetentionDays = 90;
    private List<String> allowedSourceApps = new ArrayList<>(List.of("oss", "customer-org"));
    private List<String> adminRoleCodes = new ArrayList<>();
    private List<String> managerRoleCodes = new ArrayList<>();
    private List<PositionToRoleMapping> positionToRoleMappings = new ArrayList<>();
    /** 按人员映射：通过员工邮箱/工号精确匹配 */
    private List<PersonToRoleMapping> personToRoleMappings = new ArrayList<>();
    /** 按部门映射：通过部门名称正则匹配 */
    private List<DepartmentToRoleMapping> departmentToRoleMappings = new ArrayList<>();
    /**
     * 是否启用 OSS 用户白名单过滤：未命中任何角色映射（人员/部门/岗位）的用户
     * 不会被创建/更新；若本地已存在，则禁用（禁止登录）。
     */
    private boolean skipUnmappedUsers = false;
    private Directory directory = new Directory();
    private EventSdk eventSdk = new EventSdk();
    private Retry retry = new Retry();
    private Reconciliation reconciliation = new Reconciliation();

    @Data
    public static class EventSdk {
        private boolean enabled = false;
        private String consumerGroup = "bms";
        /** 对外上报的主机地址/IP，默认空则使用 InetAddress.getLocalHost()，如 172.16.38.78 */
        private String advertisedHost = "";
        /** 对外上报的端口，0 则使用 server.port，如 8080 */
        private int advertisedPort = 0;
    }

    @Data
    public static class Directory {
        /** 测试环境默认值：https://base-oss-test.ehsy.com，生产环境需覆盖 */
        private String baseUrl = "https://base-oss-test.ehsy.com";
        /** YAPI 真实路径：POST /subscription/msg/user (form-urlencoded, body: userId) */
        private String userDetailPath = "/subscription/msg/user";
        /** YAPI 真实路径：POST /subscription/msg/dept (form-urlencoded, body: deptId) */
        private String departmentDetailPath = "/subscription/msg/dept";
        /** YAPI 真实路径：POST /subscription/msg/getUserByTimeWindow (json, body: startTime/endTime/index) */
        private String userWindowPath = "/subscription/msg/getUserByTimeWindow";
        /** YAPI 真实路径：POST /subscription/msg/getDeptByTimeWindow (json, body: startTime/endTime/index) */
        private String departmentWindowPath = "/subscription/msg/getDeptByTimeWindow";
        /** YAPI 真实路径：POST /subscription/msg/job (form-urlencoded, body: jobId) */
        private String jobDetailPath = "/subscription/msg/job";
        /** YAPI 真实路径：POST /oss/admin-web/v1/output/data/getUserJobListByJobNumberList (json, body: data=[jobNumbers]) */
        private String batchJobRoleLookupPath = "/oss/admin-web/v1/output/data/getUserJobListByJobNumberList";
        private String sourceApp = "";
        private String traceHeaderName = "EHSY-TraceID";
        private String sourceHeaderName = "EHSY-SRCAPP";
        /** 可选组织目录接口鉴权 Header 名；为空则不发送额外鉴权 Header */
        private String authHeaderName = "";
        /** 可选组织目录接口鉴权 Token；为空则不发送额外鉴权 Header */
        private String authToken = "";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
        /** 批量岗位/角色回查单批最大工号数 */
        private int batchQuerySize = 50;
        /** 批量岗位/角色回查连接超时（毫秒） */
        private int batchConnectTimeoutMs = 3000;
        /** 批量岗位/角色回查读取超时（毫秒），批量返回数据量较大，默认高于单条接口 */
        private int batchReadTimeoutMs = 10000;
        /** OSS 菜单树接口路径：GET /oauth/getUserPermission */
        private String userMenuTreePath = "/oauth/getUserPermission";
        /** OSS 菜单树查询 systemName，如 xiyu-bid-poc */
        private String userMenuTreeSystemName = "xiyu-bid-poc";
        /** OSS 菜单树查询类型：1=url，2=本地配置 */
        private int userMenuTreeRetrievalType = 2;
        /** OSS 菜单树用户工号 query 参数名 */
        private String userMenuTreeJobNumberParamName = "jobNumber";
        /** 菜单树接口连接超时（毫秒） */
        private int userMenuTreeConnectTimeoutMs = 3000;
        /** 菜单树接口读取超时（毫秒） */
        private int userMenuTreeReadTimeoutMs = 5000;
        /** 是否在组织架构同步时自动聚合 OSS 菜单权限 */
        private boolean autoSyncMenuPermissions = false;
        /** OSS 菜单编码 -> 内部权限码 映射（大小写不敏感） */
        private Map<String, String> menuCodeToPermissionKeyMappings = new HashMap<>();
        /** 未映射 OSS 菜单编码的默认处理：IGNORE 或 USE_NORMALIZED_CODE */
        private String unmappedMenuCodeBehavior = "IGNORE";
    }

    @Data
    public static class PersonToRoleMapping {
        /** 人员标识：西域员工邮箱或工号 */
        private String personIdentifier = "";
        /** 匹配后赋予的投标系统角色码，如 bid_admin、bid_lead、staff */
        private String roleCode = "";

        /** 判断此标识是否匹配给定值（忽略大小写、前后空白） */
        public boolean matches(String value) {
            if (value == null || value.isBlank()) {
                return false;
            }
            return value.trim().equalsIgnoreCase(personIdentifier.trim());
        }
    }

    @Data
    public static class DepartmentToRoleMapping {
        /** 部门名称正则表达式，如 "投标管理部"、"行政部" */
        private String departmentPattern = "";
        /** 匹配后赋予的投标系统角色码 */
        private String roleCode = "";

        /** 判断此部门名称是否匹配正则 */
        public boolean matches(String departmentName) {
            if (departmentName == null || departmentName.isBlank()
                    || departmentPattern == null || departmentPattern.isBlank()) {
                return false;
            }
            return java.util.regex.Pattern.compile(departmentPattern).matcher(departmentName).find();
        }
    }


    @Data
    public static class PositionToRoleMapping {
        private String positionPattern = "";
        private String roleCode = "";
    }

    @Data
    public static class Retry {
        private boolean enabled = true;
        private int maxAttempts = 5;
        private int batchSize = 50;
        private long fixedDelayMs = 60000;
    }

    @Data
    public static class Reconciliation {
        private boolean enabled = false;
        private String cron = "0 30 2 * * *";
        private int lookbackDays = 3;
    }
}
