# Quickstart: 西域对接 — 组织架构SDK接入

## 前置条件

1. Maven 私服地址已配置
2. `ClientSDK` JAR 已在本地 Maven 仓库或私服可用
3. 西域已提供：
   - YAPI base URL
   - applyToken clientId + clientSecret
   - Kafka broker list + ZK servers
   - consumerGroup 命名

## 环境变量

```bash
# 启用组织架构集成
export XIYU_ORG_SYNC_ENABLED=true

# SDK 配置
export XIYU_ORG_EVENT_SDK_ENABLED=true
export XIYU_ORG_EVENT_CONSUMER_GROUP=bid-org-consumer-test

# YAPI 配置
export XIYU_ORG_DIRECTORY_BASE_URL=https://yapi.ehsy.com
export XIYU_ORG_DIRECTORY_AUTH_CLIENT_ID=<西域提供>
export XIYU_ORG_DIRECTORY_AUTH_CLIENT_SECRET=<西域提供>

# 重试（可选）
export XIYU_ORG_RETRY_ENABLED=true
export XIYU_ORG_RETRY_MAX_ATTEMPTS=5

# 对账（生产开启）
export XIYU_ORG_RECONCILIATION_ENABLED=false
```

## 启动

```bash
cd backend
./start.sh
```

## 验证

```bash
# 健康检查
curl http://localhost:18080/actuator/health

# 运维状态
curl -H "Authorization: Bearer <admin-jwt>" \
  http://localhost:18080/api/integrations/organization/operations/status
```

## Maven 配置（pom.xml）

```xml
<repositories>
    <repository>
        <id>ehsy-private</id>
        <url>https://<maven-repo-host>/repository/maven-releases/</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.ehsy.eventlibrary</groupId>
        <artifactId>ClientSDK</artifactId>
        <version>release_0.0.2</version>
    </dependency>
</dependencies>
```
