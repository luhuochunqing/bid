# Rollback Runbook

## Purpose
在发布失败或上线后出现 blocker 时，快速恢复到上一个稳定版本，并保证数据库与应用状态一致。

## Preconditions
- 最近一次数据库备份已生成
- 上一个稳定版本工件可获取
- 回滚负责人已确认窗口

## Database Backup
在发布前执行：

```bash
bash scripts/release/backup-db.sh
```

默认按 MySQL 8.0 执行。设置 `DB_ENGINE=mysql`、`DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD`，脚本会生成 `.sql` 备份；脚本也兼容历史变量 `DB_USER`。

## Application Rollback
1. 停止当前版本流量接入
2. 回退前端静态资源到上一个稳定版本
3. 回退后端应用工件到上一个稳定版本
4. 重启服务并检查健康状态

## Database Restore
如果数据库已经被破坏性变更影响，执行：

```bash
CONFIRM_RESTORE=YES bash scripts/release/restore-db.sh <backup-file>
```

MySQL 8.0 环境恢复也必须显式声明数据库引擎：

```bash
CONFIRM_RESTORE=YES DB_ENGINE=mysql bash scripts/release/restore-db.sh <backup-file.sql>
```

如果本机没有 `mysqldump/mysql`，可设置 `MYSQL_CONTAINER_NAME=<mysql-container>` 使用 docker exec 回退路径。

## Verification After Rollback
- `GET /actuator/health` 返回 `UP`
- 登录成功
- 项目/标讯列表可访问
- Knowledge 主链路可访问
- 资源主链路可访问
- 监控错误率恢复正常

## Incident Record
- 发布时间：
- 回滚时间：
- 触发条件：
- 影响范围：
- 恢复耗时：
- 后续修复 owner：
