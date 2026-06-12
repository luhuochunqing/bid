package com.xiyu.bid.integration.organization.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
        private String sourceApp = "";
        private String traceHeaderName = "EHSY-TraceID";
        private String sourceHeaderName = "EHSY-SRCAPP";
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
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
