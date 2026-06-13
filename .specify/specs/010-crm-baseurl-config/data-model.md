# Data Model: CRM BaseUrl 配置重构

## Entity: CrmProperties (扩展)

```java
@Data
@Component
@ConfigurationProperties(prefix = "app.crm")
public class CrmProperties {
    // 原有字段（向后兼容）
    private String baseUrl;
    private String clientId;
    private String clientSecret;
    
    // 新增：多域名配置
    private String authBaseUrl;      // 鉴权/组织架构/菜单/员工
    private String customerBaseUrl;  // 客户查询
    private String messageBaseUrl;   // 消息推送
    
    // 新增：YAPI 路径配置
    private CrmAuthPaths auth = new CrmAuthPaths();
    private CrmCustomerPaths customer = new CrmCustomerPaths();
    private CrmMessagePaths message = new CrmMessagePaths();
    
    // 运行时参数（可从 Settings 覆盖）
    private int tokenCacheTtlSeconds = 3600;
    private int maxRetries = 3;
    private int retryBaseDelayMs = 1000;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
    
    @Data
    public static class CrmAuthPaths {
        private String applyTokenPath = "/auth/applyToken";
        private String logoutPath = "/auth/logout";
        private String menuTreePath = "/menu/tree";
        private String employeePath = "/employee/info";
    }
    
    @Data
    public static class CrmCustomerPaths {
        private String searchPath = "/customer/search";
        private String contactsPath = "/customer/contacts/batch";
    }
    
    @Data
    public static class CrmMessagePaths {
        private String sendPath = "/message/send";
    }
    
    /**
     * 获取实际使用的 BaseUrl
     */
    public String getEffectiveAuthBaseUrl() {
        return StringUtils.hasText(authBaseUrl) ? authBaseUrl : baseUrl;
    }
    
    public String getEffectiveCustomerBaseUrl() {
        return StringUtils.hasText(customerBaseUrl) ? customerBaseUrl : baseUrl;
    }
    
    public String getEffectiveMessageBaseUrl() {
        return StringUtils.hasText(messageBaseUrl) ? messageBaseUrl : baseUrl;
    }
}
```

## Entity: Settings (扩展)

复用现有 `Settings` 实体，新增 `crmConfig` JSON 字段：

```java
@Entity
@Table(name = "settings")
public class Settings {
    // 现有字段...
    
    @Column(name = "crm_config", columnDefinition = "json")
    private String crmConfig;  // JSON 格式存储 CRM 运行时参数
}
```

**JSON 结构示例**：
```json
{
  "tokenCacheTtlSeconds": 3600,
  "maxRetries": 3,
  "retryBaseDelayMs": 1000,
  "connectTimeoutMs": 5000,
  "readTimeoutMs": 30000,
  "authBaseUrl": "https://base-oss-test.ehsy.com",
  "customerBaseUrl": "https://cac-test.ehsy.com",
  "messageBaseUrl": "https://crm-api-java-test6.ehsy.com"
}
```

## Entity: CrmToken (不变)

```java
public record CrmToken(
    String accessToken,
    long expiresIn,
    Instant acquiredAt
) {}
```

## 配置优先级

1. **最高**：Settings 表中的 `crmConfig` JSON
2. **次之**：`application-{profile}.yml` 中的 `app.crm.*`
3. **兜底**：代码默认值

## 数据库迁移

无需新增表。如需独立字段存储，可后续迁移：

```sql
-- 可选：将 JSON 展开为独立字段（未来优化）
ALTER TABLE settings 
ADD COLUMN crm_auth_base_url VARCHAR(255),
ADD COLUMN crm_customer_base_url VARCHAR(255),
ADD COLUMN crm_message_base_url VARCHAR(255);
```
