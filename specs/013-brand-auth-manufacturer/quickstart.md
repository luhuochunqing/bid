# Quickstart: 品牌授权 §4.6a 本地验证

## Prerequisites

```bash
cd /Users/user/xiyu/worktrees/claude-blueprint-supplement
npm install
cd backend && mvn compile -q
```

## Start Services

```bash
# Terminal 1: Backend
export XIYU_DEV_CONFIRMED=1
source scripts/dev-env.sh
cd backend
JWT_SECRET=dev-secret-key-32bytes-minimum DB_PASSWORD=XiyuDB\!2026 \
  mvn spring-boot:run -Dspring-boot.run.profiles=dev,mysql \
  -Dspring-boot.run.arguments="--server.port=${BACKEND_PORT}" 2>&1 | tee /tmp/be-brandauth.log

# Terminal 2: Frontend
cd /Users/user/xiyu/worktrees/claude-blueprint-supplement
VITE_API_BASE_URL=http://127.0.0.1:18081 ./node_modules/.bin/vite --port 1315 --force
```

## Run E2E Tests

```bash
# Get auth tokens for all 4 roles
bash .claude/skills/blueprint-driven-development/scripts/get-tokens.sh

# Run brand auth E2E tests
npx playwright test e2e/brand-auth-manufacturer-flow.spec.js --config playwright.config.js
```

## Verify Permissions

| Role | View List | Create | Edit | Revoke | Export |
|------|:--:|:--:|:--:|:--:|:--:|
| bid_admin | ✅ | ✅ | ✅ | ✅ | ✅ |
| bid_lead | ✅ | ✅ | ✅ | ✅ | ✅ |
| bid_specialist | ✅ | ✅ | ✅ | ❌ | ✅ |
| sales | ❌ | ❌ | ❌ | ❌ | ❌ |

## Key URLs

- Frontend: `http://127.0.0.1:1315/knowledge/brand-auth`
- API: `http://127.0.0.1:18081/api/knowledge/brand-auth`
- Swagger: `http://127.0.0.1:18081/swagger-ui.html`
