# Shell 与命令行陷阱

记录 shell 命令执行、特殊字符转义、数据库操作等命令行场景的陷阱和正确做法。

---

## 1. SQL 语句中 `$` 特殊字符被 shell 转义截断

### 问题

直接在命令行执行含 `$` 的 SQL UPDATE 语句时，BCrypt 哈希中的 `$2a$10$...` 被 shell 解析为变量引用，导致密码值被截断：

```bash
# ❌ 错误：直接在命令行执行
mysql -h ... -e "UPDATE winbid.users SET password = '$2a$10$N9qo8uLOickgx2ZMRZoMy...' WHERE source = 'OSS'"
# 结果：$2 被解析为空，密码变成 a$10$N9qo8uLOickgx2ZMRZoMy...（无效）
```

### 根因

- Shell 将 `$2a` 解析为变量 `$2`（第二个位置参数）+ `a`
- 在交互式 shell 中 `$2` 未定义，展开为空字符串
- 导致 `$2a$10$...` 变成 `a$10$...`，密码值被破坏

### 危害

1. 数据库中密码不是预期的 BCrypt 哈希，导致密码验证永远失败
2. 日志中出现 `Encoded password does not look like BCrypt`
3. 排查时容易误判为"代码问题"而非"数据问题"

### 正确做法

**方式 1：使用 heredoc 写入 SQL 文件再执行（推荐）**

```bash
# ✅ 正确：用 heredoc 避免 shell 解析
mysql -h winbid-01.test.rds.ehsy.com -P3306 -u ea_bid -p'ra(D7np+Z' winbid << 'EOF'
UPDATE winbid.users 
SET password = '$2a$10$FwCOuxKv3WA8f2uwiUE23umE0ooMOPDOoOs2JTK49zN8i8PYLxK4y'
WHERE source = 'OSS';
EOF
```

**方式 2：使用单引号 heredoc 定界符（`<< 'EOF'`）**

```bash
# ✅ 正确：单引号定界符禁止变量展开
cat > /tmp/fix_password.sql << 'EOF'
UPDATE winbid.users 
SET password = '$2a$10$FwCOuxKv3WA8f2uwiUE23umE0ooMOPDOoOs2JTK49zN8i8PYLxK4y'
WHERE source = 'OSS';
EOF
mysql -h ... winbid < /tmp/fix_password.sql
```

**方式 3：使用双反斜杠转义（不推荐，易出错）**

```bash
# ⚠️ 可读性差，容易遗漏
mysql -h ... -e "UPDATE ... SET password = '\$2a\$10\$...'"
```

### 通用规则

1. **任何含 `$` 的字符串在 shell 中都要小心**：包括密码、哈希、正则表达式、变量名等
2. **优先使用 SQL 文件 + 输入重定向**：避免命令行参数解析问题
3. **heredoc 使用单引号定界符（`<< 'EOF'`）**：彻底禁止变量展开
4. **执行后必须验证**：更新数据库后抽样检查实际存储的值

### 验证命令

```bash
# 检查数据库中的密码值是否正确（应为 60 字符）
mysql -h ... -e "SELECT LENGTH(password), password FROM winbid.users WHERE source = 'OSS' LIMIT 1"
# 期望输出：60 | $2a$10$...

# 检查密码是否被截断（长度小于 60 即有问题）
mysql -h ... -e "SELECT COUNT(*) FROM winbid.users WHERE source = 'OSS' AND LENGTH(password) != 60"
# 期望输出：0
```

---

## 2. `set -euo pipefail` 下 `grep` 无匹配返回非零导致脚本意外退出（PR !2059 / 2026-07-13）

### 问题

`scripts/release/package-release.sh` 在头部启用了 `set -euo pipefail`：

```bash
#!/usr/bin/env bash
set -euo pipefail
```

在 OBS 直传三层保护机制中，新增了一段「构建后验证 `dist/assets/Detail-*.js` 中 `.upload()` 调用次数」的逻辑：

```bash
# ❌ 错误：grep 无匹配时退出码非零，pipefail 让管道整体失败，set -e 直接终止脚本
_n=$(grep -o "\.upload(" "$_f" 2>/dev/null | wc -l | tr -d ' ')
```

当某个 `Detail-*.js` 文件中**不包含 `.upload(` 字符串**（例如 OBS 直传被关闭时），`grep` 返回 1，`pipefail` 让整个管道返回 1，`set -e` 让脚本立即退出，**导致打包流程在验证步骤中断**。

### 根因

- **`grep` 的退出码语义**：
  - `0`：找到匹配
  - `1`：未找到匹配（不是错误，是正常结果）
  - `>=2`：真正的错误（文件不存在、权限问题等）
- **`set -e`**：任何命令返回非零都会终止脚本，但 `grep` 返回 1 是「未匹配」而非「错误」。
- **`pipefail`**：管道的退出码取最后一个非零的命令，让 `grep` 的 1 传到管道整体。

### 危害

1. **打包中断**：构建产物验证步骤意外失败，`package-release.sh` 退出码非零。
2. **假阳性错误报告**：开发者误以为 OBS 直传配置错误，实际只是 `grep` 在某个文件里没匹配到。
3. **CI 假红**：CI 流水线运行 `package-release.sh` 时无故失败。

### 正确做法

**方式 1：管道末尾加 `|| true`（推荐，当前 `package-release.sh:92` 采用）**

```bash
# ✅ 正确：grep 无匹配时返回 1，|| true 让整体退出码为 0
_n=$(grep -o "\.upload(" "$_f" 2>/dev/null | wc -l | tr -d ' ' || true)
```

**方式 2：用 `if` 包裹 grep，区分"无匹配"和"错误"**

```bash
# ✅ 正确：显式区分 grep 的退出码语义
if _n=$(grep -o "\.upload(" "$_f" 2>/dev/null | wc -l | tr -d ' '); then
  : # 正常处理
else
  _n=0  # grep 未匹配，设为 0
fi
```

**方式 3：用 `grep -c` 直接输出匹配数（无匹配时输出 0，退出码仍为 1，但更容易处理）**

```bash
# ⚠️ 退出码仍为 1，需配合 || true
_n=$(grep -c "\.upload(" "$_f" 2>/dev/null || echo 0)
```

### 通用规则

1. **在 `set -euo pipefail` 下使用 `grep` 必须加 `|| true`**：`grep` 返回 1 是"未匹配"的正常结果，不是错误。
2. **不要用 `grep` 的退出码判断"找到/未找到"**：在 `set -e` 下会意外终止脚本；改用 `grep -c` 或 `if` 包裹。
3. **CI 脚本中的 `grep` 要特别小心**：CI 环境（如 GitLab CI、GitHub Actions）通常默认 `set -e`，`grep` 无匹配会导致整个 job 失败。
4. **审计现有 `set -euo pipefail` 脚本中的 `grep`**：
   ```bash
   # 查找 set -euo pipefail 脚本中未加 || true 的 grep
   grep -rn "set -euo pipefail" scripts/ | cut -d: -f1 | xargs -I{} sh -c 'grep -n "grep " {} | grep -v "|| true"'
   ```

### 验证命令

```bash
# 验证 package-release.sh 中的 grep 是否已加 || true
grep -n "grep" scripts/release/package-release.sh
# 期望输出：所有 grep 命令后都应跟 || true 或用 if 包裹
```

### 相关文档

- [package-release.sh](../../scripts/release/package-release.sh) — 修复落地（第 92 行）
- [lessons-learned.md §56](./lessons-learned.md) — OBS 直传三层保护（本次 gotcha 发现于该保护的构建后验证步骤）
- PR !2059 — OBS 直传三层保护 + 5 个绕过路径修复
