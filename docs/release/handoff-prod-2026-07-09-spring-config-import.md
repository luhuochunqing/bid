# 生产环境 Handoff：消除 SPRING_CONFIG_IMPORT 外部配置漂移

> **日期**：2026-07-09
> **事件**：测试环境第 65/66 次部署中，OSS 用户 06234 登录失败事故
> **Handoff 目标**：生产环境部署负责人
> **相关 PR**：Gitee !1956

---

## 一、事故摘要

2026-07-09 第 65 次部署后，OSS 用户 **06234（郑蓉蓉）** 无法登录系统，报错：

```
ROLE_NOT_AUTHORIZED: 无有效 OSS 角色，不允许登录
```

回滚到第 64 次版本后恢复。开发者修复代码（PR !1949）后第 66 次部署，**问题仍然存在**。最终排查发现根因不是代码 bug，而是**服务器外部配置文件覆盖了 jar 内配置**。

### 根因

测试服务器 `/etc/xiyu-bid/backend.env` 中配置了：

```bash
SPRING_CONFIG_IMPORT=optional:file:/etc/xiyu-bid/application-org-mappings.yml
```

Spring Boot 的 `SPRING_CONFIG_IMPORT` 导入的外部配置文件优先级**高于** jar 内 `application.yml`。

外部配置文件 `/etc/xiyu-bid/application-org-mappings.yml` 中，06234 的 `role-code` 是旧值 `bid-SystemAdmin`：

```yaml
person-to-role-mappings:
  - person-identifier: "06234"
    role-code: bid-SystemAdmin   # ❌ 旧错误值
```

而 jar 内 `application.yml` 中 06234 的 `role-code` 是修复后的正确值 `/bidAdmin`：

```yaml
person-to-role-mappings:
  - person-identifier: "06234"
    role-code: /bidAdmin         # ✅ 正确值
```

由于外部配置覆盖，`bid-SystemAdmin` 被解析为实际生效值。但 `bid-SystemAdmin` 不在 `RoleProfileCatalog` 的 7 个标准角色（`admin`、 `/bidAdmin`、`bid-TeamLeader`、`bid-projectLeader`、`bid-Team`、`bid-administration`、`bid-otherDept`）中，触发 `LoginRoleWhitelist.isAllowed()` 拒绝，最终导致 403 登录失败。

### 关键教训

**jar 内配置正确 ≠ 运行时配置正确。** `SPRING_CONFIG_IMPORT` 引入的外部配置文件是不可见的配置漂移源。部署预检必须检查外部配置覆盖。

---

## 二、测试环境已执行的修复

### 2.1 立即止血（2026-07-09 18:26 CST）

1. 备份 `/etc/xiyu-bid/application-org-mappings.yml`
2. 将 06234 和 `tina_zheng1@ehsy.com` 的 `role-code` 从 `bid-SystemAdmin` 改为 `/bidAdmin`
3. 重启后端服务
4. 验证 06234 登录恢复，`roleCode=/bidAdmin`

### 2.2 根因消除（2026-07-09 18:47 CST）

为彻底避免配置漂移，已从测试环境删除 `SPRING_CONFIG_IMPORT` 外部配置依赖：

1. 从 `/etc/xiyu-bid/backend.env` 删除：

   ```bash
   SPRING_CONFIG_IMPORT=optional:file:/etc/xiyu-bid/application-org-mappings.yml
   ```

2. 向 `/etc/xiyu-bid/backend.env` 添加：

   ```bash
   RATE_LIMIT_LOGIN_MAX=20
   ```

   > 原因：原外部配置文件中包含 `rate.limit.login.max-attempts: 20`，而 jar 内默认值为 5。删除外部配置后，需通过环境变量保持测试环境登录限流策略不变。

3. 重启后端服务
4. 验证 06234 登录正常，67 个 authorities 正常构建

---

## 三、生产环境需要你做的事情

### 3.1 必做：同步删除 SPRING_CONFIG_IMPORT

请在生产环境执行以下操作：

```bash
# 1. SSH 到生产后端服务器
ssh <prod-user>@<prod-backend-ip>

# 2. 备份 backend.env
sudo cp /etc/xiyu-bid/backend.env /etc/xiyu-bid/backend.env.bak.$(date +%Y%m%d%H%M%S)

# 3. 检查当前是否存在 SPRING_CONFIG_IMPORT
grep "SPRING_CONFIG_IMPORT" /etc/xiyu-bid/backend.env

# 4. 删除 SPRING_CONFIG_IMPORT 行
sudo sed -i '/SPRING_CONFIG_IMPORT/d' /etc/xiyu-bid/backend.env

# 5. 添加 RATE_LIMIT_LOGIN_MAX=20（保持登录限流策略不变）
echo "RATE_LIMIT_LOGIN_MAX=20" | sudo tee -a /etc/xiyu-bid/backend.env > /dev/null

# 6. 确认修改结果
grep -E "SPRING_CONFIG_IMPORT|RATE_LIMIT_LOGIN_MAX" /etc/xiyu-bid/backend.env
# 期望只看到 RATE_LIMIT_LOGIN_MAX=20，没有 SPRING_CONFIG_IMPORT
```

### 3.2 必做：保留或归档外部配置文件

建议不要立即删除 `/etc/xiyu-bid/application-org-mappings.yml`，而是备份归档，以备审计：

```bash
sudo mv /etc/xiyu-bid/application-org-mappings.yml /etc/xiyu-bid/application-org-mappings.yml.deprecated.$(date +%Y%m%d%H%M%S)
```

### 3.3 必做：部署 PR !1956

PR !1956 已在 jar 内 `application.yml` 补全了外部配置中独有的 5 个人员映射：

| person-identifier | role-code | 说明 |
|---|---|---|
| `dean_zhang@ehsy.com` | `/bidAdmin` | 张頔邮箱别名 |
| `tina_zheng1@ehsy.com` | `/bidAdmin` | 郑蓉蓉邮箱别名 |
| `suki_yuan@ehsy.com` | `bid-TeamLeader` | 袁思琪邮箱别名 |
| `09118` | `bid-otherDept` | 跨部门协同人员 |
| `03063` | `bid-otherDept` | 跨部门协同人员 |

请按正常发布流程部署 PR !1956。

### 3.4 部署后验证清单

部署完成后，请在生产环境验证：

```bash
# 1. 确认 backend.env 中没有 SPRING_CONFIG_IMPORT
grep "SPRING_CONFIG_IMPORT" /etc/xiyu-bid/backend.env || echo "✅ 已删除"

# 2. 确认 RATE_LIMIT_LOGIN_MAX=20 已配置
grep "RATE_LIMIT_LOGIN_MAX" /etc/xiyu-bid/backend.env

# 3. 确认后端健康检查通过
curl -s http://<prod-backend>:8080/actuator/health | head -c 200

# 4. 确认 06234 能正常登录（roleCode=/bidAdmin）
# 登录后查看日志中是否有：
# UserDetails authorities built: user=06234 isOssUser=true roleCode=/bidAdmin
```

---

## 四、风险与回滚

### 4.1 如果生产环境也有 `bid-SystemAdmin` 角色

请检查生产环境 `/etc/xiyu-bid/application-org-mappings.yml` 中是否还有其他人使用了 `bid-SystemAdmin` 或其他非标准角色码：

```bash
grep "role-code:" /etc/xiyu-bid/application-org-mappings.yml \
  | grep -v -E "role-code: (admin|/bidAdmin|bid-TeamLeader|bid-projectLeader|bid-Team|bid-administration|bid-otherDept)\s*$" \
  | grep -v "^\s*#"
```

如果有输出，请将这些非标准角色码修正为 7 个标准角色之一，或在 PR 合并前反馈给开发团队。

### 4.2 回滚方案

如果删除 `SPRING_CONFIG_IMPORT` 后出现问题，可以立即回滚：

```bash
# 从备份恢复 backend.env
sudo cp /etc/xiyu-bid/backend.env.bak.<timestamp> /etc/xiyu-bid/backend.env

# 恢复外部配置文件
sudo cp /etc/xiyu-bid/application-org-mappings.yml.deprecated.<timestamp> /etc/xiyu-bid/application-org-mappings.yml

# 重启后端
sudo systemctl restart xiyu-bid-backend
```

---

## 五、配置漂移防范（已固化到流程）

### 5.1 部署流程更新

已在 `docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md` §6.2 新增外部配置覆盖检查点：

1. 确认 `SPRING_CONFIG_IMPORT` 引入的外部配置文件
2. 对比外部配置文件与 jar 内 `application.yml` 的 person-to-role-mappings 是否一致
3. 检查所有 `role-code` 是否为 7 个标准角色之一

### 5.2 经验文档沉淀

已在 `docs/lessons/lessons-learned.md` §49 新增事故沉淀：

- 完整时间线
- 根因分析
- 故障链
- 经验教训表
- 验证方法

---

## 六、联系方式

如有任何疑问或发现生产环境配置与测试环境不一致，请立即联系开发团队确认。

---

**附录：测试环境外部配置文件原始内容（已 deprecated）**

```yaml
xiyu:
  integrations:
    organization:
      skip-unmapped-users: true
      position-to-role-mappings:
        - position-pattern: "^项目经理$"
          role-code: sales
        - position-pattern: "^项目总监$"
          role-code: sales
        - position-pattern: "^主管$"
          role-code: sales
        - position-pattern: "^BD经理$"
          role-code: sales
      department-to-role-mappings:
        - department-pattern: "投标管理部"
          role-code: bid-Team
        - department-pattern: "行政部"
          role-code: bid-administration
      person-to-role-mappings:
        - person-identifier: "11484"
          role-code: bid-TeamLeader
        - person-identifier: "suki_yuan@ehsy.com"
          role-code: bid-TeamLeader
        - person-identifier: "03595"
          role-code: /bidAdmin
        - person-identifier: "dean_zhang@ehsy.com"
          role-code: /bidAdmin
        - person-identifier: "06234"
          role-code: /bidAdmin   # 已修复
        - person-identifier: "tina_zheng1@ehsy.com"
          role-code: /bidAdmin   # 已修复
        - person-identifier: "04727"
          role-code: bid-otherDept
        - person-identifier: "06708"
          role-code: bid-otherDept
        - person-identifier: "07440"
          role-code: bid-otherDept
        - person-identifier: "09118"
          role-code: bid-otherDept
        - person-identifier: "03063"
          role-code: bid-otherDept
        - person-identifier: "03895"
          role-code: bid-otherDept
        - person-identifier: "03483"
          role-code: bid-otherDept
rate:
  limit:
    login:
      max-attempts: 20
      window-minutes: 15
```

注意：外部配置中的 `position-to-role-mappings` 使用的 `sales` 角色、`department-pattern` 未使用 `.*` 正则包裹，这些差异在 PR !1956 的 jar 内配置中已统一为标准写法。删除外部配置后，jar 内配置将成为唯一真相源。
