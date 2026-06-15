# EHSY ClientSDK — 内部化参考

> **盲区原因**：西域集团专有 SDK，`<scope>system</scope>` 本地 jar，未上公开 Maven/文档站。AI 训练集无此内容，必须投喂否则会写错集成代码。

## 依赖声明

来源：`backend/pom.xml`。

```xml
<dependency>
  <groupId>com.ehsy.eventlibrary</groupId>
  <artifactId>ClientSDK</artifactId>
  <scope>system</scope>
  <systemPath>${project.basedir}/libs/ClientSDK-release_0.0.2.jar</systemPath>
</dependency>
```

- jar 位置：`backend/libs/ClientSDK-release_0.0.2.jar`
- 版本：`0.0.2`
- 仓库：内部 Nexus `maven.ehsy.com`（离线/无权限时只能走 systemPath 本地 jar）

## 传递性运行时依赖

SDK 自带（不在本项目直接声明，但运行期必须存在）：
- Kafka / Zookeeper 客户端
- Apache HttpClient
- `com.alibaba:fastjson:1.2.83`（注意：旧版本，存在已知 CVE 链，仅作 ClientSDK 传递依赖，不要在本项目新代码里直接用 fastjson）

## 用途

- 组织架构事件广播（org-event）。
- 本项目对接点：组织目录同步、部门变更事件订阅。
- 详见 `docs/integration/organization-directory-runbook.md`、`.wiki/pages/integration-organization-event-sdk.md`。

## AI 写代码须知

- ❌ 不要假设它在 Maven Central；构建断网时 `systemPath` jar 必须存在。
- ❌ 不要新代码里直接 `import com.alibaba.fastjson.*`（用 Jackson 替代）。
- ✅ 集成代码参考现有 `organization` 模块的用法。

## 待补充

> 拿到 SDK 官方文档/javadoc 后，把关键类与方法签名补到本文件。
