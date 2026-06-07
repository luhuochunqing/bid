# 服务启动脚本

## 前后端联调启动

```bash
export XIYU_DEV_CONFIRMED=1
source scripts/dev-env.sh

DB_NAME=${DB_NAME} JWT_SECRET="..." DB_PASSWORD="..." \
mvn spring-boot:run -Dspring-boot.run.profiles=dev,mysql \
  -Dspring-boot.run.arguments="--server.port=${BACKEND_PORT}"

VITE_API_BASE_URL=http://127.0.0.1:${BACKEND_PORT} ./node_modules/.bin/vite \
  --port ${FRONTEND_PORT} --force
```
