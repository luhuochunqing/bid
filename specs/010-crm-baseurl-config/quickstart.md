# Quickstart: CRM BaseUrl 配置重构

## 开发环境配置

### 1. 后端配置

在 `application-dev.yml` 中添加：

```yaml
app:
  crm:
    # 向后兼容：保留旧配置
    base-url: https://base-oss-test.ehsy.com
    
    # 新增：多域名配置（优先于 base-url）
    auth-base-url: https://base-oss-test.ehsy.com
    customer-base-url: https://cac-test.ehsy.com
    message-base-url: https://crm-api-java-test6.ehsy.com
    
    # 运行时参数
    token-cache-ttl-seconds: 3600
    max-retries: 3
    retry-base-delay-ms: 1000
    connect-timeout-ms: 5000
    read-timeout-ms: 30000
```

### 2. 系统设置页面

访问 `http://localhost:1315/settings`，在「系统集成」Tab 中找到 CRM 配置卡片。

### 3. 验证配置

```bash
# 检查配置是否生效
curl http://localhost:18081/actuator/configprops | grep crm

# 测试 CRM 连接
curl http://localhost:18081/api/xiyu/crm/customers?keyword=测试
```

## 联调检查清单

- [ ] YAPI 路径已确认并更新到 `CrmProperties`
- [ ] 3 个 BaseUrl 可访问
- [ ] Token 申请成功
- [ ] 客户查询返回数据
- [ ] 消息发送成功
