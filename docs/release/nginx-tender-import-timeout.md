# Nginx 标讯导入超时配置 Patch（spec 031 T041）

> **用途**：异步化上线前的临时防护，避免异步化未上线期间仍出现 504 Gateway Timeout。
> 异步化上线后此配置仍可保留作为兜底（后端 30s 内返回 taskId，不会触发 180s 超时）。

## 背景

spec 031 调查发现：批量导入 180 行 Excel 后端处理需 103.5s，而 Nginx 默认 `proxy_read_timeout 60s`，导致前端收到 504 但后端事务继续提交，数据实际导入成功。

异步化改造（US1）完成后，前端 3s 内收到 202 + taskId，不再阻塞。但同步路径（如回滚 fallback）仍可能耗时较长，建议同步调整 Nginx 超时作为兜底。

## 配置变更

**目标文件**：`/etc/nginx/conf.d/xiyu-bid.conf`（或实际 nginx 配置文件）

**变更位置**：`location /api/` 块

**新增配置**：

```nginx
location /api/ {
    # ... 现有配置 ...

    # spec 031: 标讯批量导入兜底超时（异步化后 3s 返回 202，此处仅作 fallback）
    proxy_read_timeout 180s;
    proxy_send_timeout 180s;
    proxy_connect_timeout 60s;
}
```

## 部署步骤

1. 登录生产服务器
2. 编辑 nginx 配置：`sudo vi /etc/nginx/conf.d/xiyu-bid.conf`
3. 在 `location /api/` 块内新增上述三行 `proxy_*_timeout` 配置
4. 验证配置语法：`sudo nginx -t`
5. 重载配置：`sudo nginx -s reload`
6. 验证：`curl -I https://your-domain/api/tenders/import-template`（应返回 200，不是 504）

## 回滚

删除新增的三行 `proxy_*_timeout` 配置，重载 nginx 即可恢复默认 60s 超时。

## 验证

部署后上传 180 行 Excel（异步化前）：
- 异步化前：Nginx 不再 504，前端等待后端完整返回（~103s）
- 异步化后：前端 3s 内收到 202 + taskId，进入轮询流程

## 关联

- spec 031 plan.md §FR-018
- 根因：Nginx `proxy_read_timeout 60s` < 后端处理 103.5s
- 修复方案 B/C：异步化（spec 031 US1）+ 性能优化（spec 031 US2）
