# 前端热更新部署后动态 import chunk 404

## 问题现象

部署前端更新后，页面加载时报错：

```
GET https://winbid-test.ehsy.com/assets/expensePageShared-BVImIf-l.js net::ERR_ABORTED 404 (Not Found)
TypeError: Failed to fetch dynamically imported module: https://winbid-test.ehsy.com/assets/MainLayout-CO2OMnCd.js
```

## 根因分析

Vite 构建产物中，`index.html` 只直接引用入口 JS/CSS（约 7 个文件）：

```html
<script type="module" crossorigin src="/assets/index-Bqav3qtK.js"></script>
<link rel="modulepreload" crossorigin href="/assets/vendor-IfaPzR1Q.js">
<!-- ... -->
```

但入口 JS（`index-Bqav3qtK.js`）内部通过**动态 import()** 加载大量 chunk 文件：

```javascript
// index-Bqav3qtK.js 内部
const expensePageShared = () => import('./expensePageShared-BVImIf-l.js')
const MainLayout = () => import('./MainLayout-CO2OMnCd.js')
// ... 数十个动态 chunk
```

部署时执行的清理脚本只保留了 `index.html` 直接引用的文件，导致动态 chunk 被误删：

```bash
# 错误脚本：只保留 index.html 引用的文件
CURRENT_FILES=$(grep -oE "[a-zA-Z0-9_-]+-[A-Za-z0-9_-]+\.(js|css)" index.html)
for f in *.js *.css; do
  if ! echo "$CURRENT_FILES" | grep -qx "$f"; then
    rm -f "$f"  # 误删动态 import 的 chunk！
  fi
done
```

## 影响范围

- 服务器：`winbid-01` (172.16.38.78)
- 时间：2026-06-19
- 修复前：页面白屏/功能不可用

## 修复方案

重新部署完整的 `dist/` 目录所有文件（167 个 assets 文件）：

```bash
cd /srv/www/xiyu-bid
rm -rf assets/*
tar xzf frontend-complete.tar.gz
```

## 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| 清理脚本只检查 index.html 引用 | Vite 动态 import 的 chunk 不在 index.html 中 | 前端部署必须保留完整 dist 目录 |
| 先清理旧文件再部署新文件 | 清理和部署顺序错误 | 先部署新文件 → 验证通过 → 再清理旧版本 |
| 缺少部署后验证 | 未检查动态 chunk 是否完整 | 部署后检查 assets 目录文件数是否与本地 dist 一致 |

## 正确做法

### 方式 1：直接覆盖（推荐热更新场景）

```bash
rm -rf /srv/www/xiyu-bid/*
cp -R dist/. /srv/www/xiyu-bid/
```

### 方式 2：版本化目录切换（推荐正式发布）

```bash
# 解压到新版本目录
mkdir -p /opt/xiyu-bid/releases/<hash>
tar xzf release.tar.gz -C /opt/xiyu-bid/releases/<hash>/

# 原子切换软链
ln -sfn /opt/xiyu-bid/releases/<hash>/frontend /srv/www/xiyu-bid
```

## 验证命令

```bash
# 检查本地 dist 文件数
ls dist/assets/ | wc -l

# 检查服务器文件数（应一致）
ssh jetty@172.16.38.78 'ls /srv/www/xiyu-bid/assets/ | wc -l'

# 检查关键动态 chunk 是否存在
ssh jetty@172.16.38.78 'ls /srv/www/xiyu-bid/assets/expensePageShared-*'
```

## 相关文档

- `docs/lessons/lessons-learned.md` — 通用工程教训
