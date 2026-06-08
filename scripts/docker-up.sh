# Input: docker compose build / up with local dev overrides
# Output: starts frontend + backend containers per scripts/docker-compose.yml
# Pos: scripts/ - Docker 全容器化一键启动入口 (companion to start.sh / dev-services.sh)
# 维护声明: scripts/docker-compose.yml 端口或 service 拓扑变更时同步更新本文件 + CLAUDE.md §推荐命令。

server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript image/svg+xml;
    gzip_min_length 1024;

    # static assets with cache
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # API proxy to backend container
    location /api/ {
        proxy_pass http://backend:18080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }

    # SPA fallback
    location / {
        try_files $uri $uri/ /index.html;
    }
}
