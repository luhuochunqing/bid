# 西域投标系统部署 Skill

## 触发暗语
- "部署" / "发版" / "上线" / "更新到服务器"

## 前置条件
- 代码已提交到远端分支
- SSH 密钥在 `/tmp/xiyu-prod-deploy-crm-test`（如果密钥过期/缺失，通过 JumpServer 重新添加）

## 服务器信息
- **IP**：172.16.38.78
- **用户**：jetty
- **SSH 命令**：`ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -i /tmp/xiyu-prod-deploy-crm-test jetty@172.16.38.78`

## 环境配置
- **backend.env**：`/etc/xiyu-bid/backend.env`（需要 sudo 读取）
- **数据库**：`winbid-01.test.rds.ehsy.com`，`winbid`，用户 `ea_bid`
- **Redis**：`winbid-01.test.redis.ehsy.com:6379`
- **后端运行端口**：`18080`
- **前端 nginx**：`/srv/www/xiyu-bid/`

## 部署步骤

### 1. 构建
```bash
# 后端
cd /path/to/worktree/backend && mvn package -DskipTests -q

# 前端
cd /path/to/worktree && VITE_API_MODE=api VITE_API_BASE_URL=http://172.16.38.78:8080 npm run build
```

### 2. 上传到服务器
```bash
# 后端 jar
scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -i /tmp/xiyu-prod-deploy-crm-test \
  backend/target/bid-poc-1.0.3.jar \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/app.jar.new

# 前端 dist
tar czf /tmp/xiyu-frontend-dist.tar.gz -C dist .
scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -i /tmp/xiyu-prod-deploy-crm-test \
  /tmp/xiyu-frontend-dist.tar.gz \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/frontend-dist.tar.gz
```

### 3. 服务器端执行
```bash
ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
  -i /tmp/xiyu-prod-deploy-crm-test jetty@172.16.38.78 '

# 3a. 停止后端
sudo systemctl stop xiyu-bid-backend

# 3b. 备份旧 jar
cp /opt/xiyu-bid/shared/backend/app.jar /opt/xiyu-bid/backups/app.jar.$(date +%Y%m%d-%H%M%S)

# 3c. 替换 jar
cp /opt/xiyu-bid/incoming/app.jar.new /opt/xiyu-bid/shared/backend/app.jar

# 3d. 替换前端
sudo rm -rf /srv/www/xiyu-bid/*
sudo cp -r /opt/xiyu-bid/frontend/dist/. /srv/www/xiyu-bid/

# 3e. 重启服务
sudo systemctl start xiyu-bid-backend
sudo nginx -s reload
'
```

### 4. 验证
```bash
ssh -i /tmp/xiyu-prod-deploy-crm-test jetty@172.16.38.78 "
  TOKEN=\$(curl -s http://localhost:18080/api/auth/login -X POST \\
    -H 'Content-Type: application/json' \\
    -d '{\"username\":\"admin\",\"password\":\"LYHEx0t0LiXbZfBvWtBC\"}' \\
    | python3 -c 'import sys,json; print(json.load(sys.stdin)[\"data\"][\"token\"])')
  curl -s http://localhost:18080/api/tenders/8 -H 'Authorization: Bearer \$TOKEN'
"
```

### 5. 数据库变更
如果有新的 Flyway migration 文件，部署后会自动执行。
如果需要手动执行 SQL，用：
```bash
mysql -h winbid-01.test.rds.ehsy.com -u ea_bid -p"<password>" winbid -e "SQL"
```

## 常见问题

### 登录限流（rate_limit_exceeded）
清除 Redis 中的限流 key：
```bash
redis-cli -h winbid-01.test.redis.ehsy.com -a <password> DEL "rate_limit:login:0:0:0:0:0:0:0:1"
```

### 密钥过期
通过 JumpServer 登录后执行：
```bash
echo "ssh-ed25519 <public_key> <comment>" >> ~/.ssh/authorized_keys
```

### 检查后端日志
```bash
sudo journalctl -u xiyu-bid-backend --since "5 min ago" --no-pager
```

### 部署后前端没变化
检查 nginx 前端目录是否正确更新：
```bash
strings /srv/www/xiyu-bid/assets/*.js | grep "<新代码关键词>" | head -3
```
如果还是旧代码，确认 dist 解压到了 `/srv/www/xiyu-bid/` 而不是 `/opt/xiyu-bid/frontend/dist/`。
