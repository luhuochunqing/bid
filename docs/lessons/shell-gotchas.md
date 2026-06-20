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
