# 西域数智化投标管理平台 — 第 86 次部署报告（测试环境）

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 环境 | 测试 (test) |
| 服务器 | winbid-01 (172.16.38.78) |
| Release ID | `a4748ff79-api8080` |
| 上一版本 | `0ac531776-api8080`（2026-07-13 06:18 部署） |
| 部署时间 | 2026-07-13 15:36:41 CST |
| 增量 commit | 10 个（PR !2065~!2068） |
| Flyway 迁移 | 无新增（DB V1165 无变化） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| GitHub 镜像 | ✅ 已同步 |

## 2. 基线信息

- 分支：`agent/trae-init`（锚点分支，ff-only 同步）
- HEAD commit：`a4748ff79` (!2068)
- 工作区状态：干净（1 个未追踪文件非本次产物）
- GitHub 镜像落后 12 commit → 部署后已同步

## 3. PR 列表与改动范围

| PR | 标题 | 改动类型 |
|----|------|----------|
| !2065 | fix(bid-upload): #file slot 添加上传进度显示 | Bug 修复 |
| !2066 | fix(project-doc): 审核人无法下载项目文档/招标文件 | Bug 修复 |
| !2067 | revert: 撤销 !2066（改错对象——应为 InitiationStage 招标文件下载） | 回退 |
| !2068 | fix(ai): TenderRequirementOutput 补充 projectType 字段修复 AI 解析失败 | **P0 Bug 修复** |

**本次部署核心目标**：修复 PR !2052 引入的 AI 解析 P0 bug（prompt 新增 projectType 字段但未同步 DTO，导致 SDK 解析失败）。

## 4. Flyway 预检结果

| 步骤 | 结果 |
|------|------|
| Step 1: validate | ✅ 228 migrations, all checksums match |
| Step 2: DB 版本对比 | ✅ DB V1165 = 源码最新版本 |
| Step 3: remote-deploy 内置 validate | ✅ VALIDATE OK - all checksums match |

无新增迁移文件，无需 DB 备份（脚本仍执行了备份作为保险）。

## 5. 部署步骤

1. ✅ 环境门禁确认（测试环境 172.16.38.78）
2. ✅ 早操三连 + 基线确认（HEAD = a4748ff79）
3. ✅ 服务器现状检查（上一版本 0ac531776, health UP）
4. ✅ Flyway 预检 3 步法（228 migrations 全绿）
5. ✅ 本地打包（RELEASE_ID=a4748ff79-api8080, VITE_OBS_ENABLED=true, COPYFILE_DISABLE=1）
6. ✅ 产物校验（obsEnabled=true, 227 迁移文件无重复, .upload(=2, 前端入口 index-DCOBWgCF.js）
7. ✅ 上传 + 部署（remote-deploy.sh, SYSTEMCTL_SUDO=true）
8. ✅ 前端资源保留（从 0ac531776 保留旧 assets）
9. ✅ 健康检查（4 分 33 秒后 UP，Kafka SDK readiness 延迟属已知行为）
10. ✅ Smoke 测试 5 项全通过
11. ✅ GitHub 镜像同步
12. ✅ 临时配置检查（SHOW_DETAILS=always 用户决定保留）

## 6. 验证结果

### 后端健康检查

| 端点 | HTTP | 状态 |
|------|------|------|
| /actuator/health | 200 | UP（所有组件 UP） |
| /actuator/health/readiness | 200 | UP |
| /api/auth/login (空密码) | 400 | ✅ 预期 |
| /api/projects (需认证) | 403 | ✅ 预期 |
| /api/integration/crm/health (需认证) | 401 | ✅ 预期 |

### 前端验证

| 项 | 结果 |
|----|------|
| 首页 HTTP | 200 |
| /login HTTP | 200 |
| 前端入口 JS | assets/index-DCOBWgCF.js（与本地一致） |
| OBS .upload( 调用数 | 4（新版本 2 + 旧版本保留 2） |
| obsEnabled | true |

### 已知行为

- **Kafka SDK readiness 延迟**：服务重启后 /actuator/health 持续 503 约 4 分 33 秒后恢复 UP。remote-deploy.sh 健康检查（120 次 ×2s = 4 分钟）刚好卡在边界，但服务实际已正常启动，API 200 响应。此为第 8/9/10/13/15 次均出现的已知行为，非故障。

## 7. 回滚信息

- 当前版本：`a4748ff79-api8080`
- 上一版本：`0ac531776-api8080`（release 目录保留）
- 回滚方式：恢复 jar + 前端（本次无迁移变更，无需 DB 回滚）
- 回滚命令：
  ```bash
  ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/0ac531776-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
    sudo cp -r /opt/xiyu-bid/releases/0ac531776-api8080/frontend/* /srv/www/xiyu-bid/ && \
    sudo systemctl restart xiyu-bid-backend'
  ```

## 8. 经验沉淀应用

| 经验 | 应用情况 |
|------|----------|
| Flyway 预检 3 步法 | ✅ 执行（无新迁移） |
| OBS 直传三层防护 | ✅ obsEnabled=true, .upload(=2 |
| 同源构建 baseURL="" | ✅ VITE_API_BASE_URL= |
| COPYFILE_DISABLE=1 | ✅ 防止 macOS ._* 残留 |
| 前端资源保留 | ✅ 从 0ac531776 保留旧 assets |
| Kafka readiness 延迟 | ✅ 识别为已知行为，未误判为故障 |
| GitHub 镜像同步 | ✅ 已同步 |
| SYSTEMCTL_SUDO=true | ✅ 服务重启成功 |

## 9. 风险提示

1. **AI 解析修复需用户验证**：PR !2068 修复了 projectType 字段缺失导致的 AI 解析失败，但单元测试 mock 了 SDK 无法捕获此类 bug。建议上传一个测试文件验证 AI 解析成功。
2. **生产环境仍存在 P0 bug**：生产环境（172.16.10.149）仍部署在 `b1126a2b3`（第 9 次生产部署），AI 解析 P0 bug 未修复。需尽快部署到生产。

## 10. 部署确认清单

- [x] 环境门禁确认（测试环境）
- [x] 早操三连 + 基线确认
- [x] Flyway 预检 3 步法
- [x] 产物校验（OBS + 迁移文件 + 前端入口）
- [x] 部署成功（health UP）
- [x] Smoke 测试 5 项
- [x] 前端资源保留
- [x] GitHub 镜像同步
- [x] 临时配置检查
- [x] 部署报告生成
