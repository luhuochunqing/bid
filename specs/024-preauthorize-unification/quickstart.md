# Quickstart: 消除 @PreAuthorize hasAnyRole 双轨制技术债

**Feature**: 024-preauthorize-unification
**Date**: 2026-07-02

本文件提供验证本特性交付物的最小可执行命令清单。

---

## P1 验证（立即修复 — TaskExtendedFieldController）

### 单元/集成测试

```bash
cd backend

# 运行 TaskExtendedField 相关的安全测试（应有权限角色 200）
mvn test -Dtest='*TaskExtendedField*Test'

# 全角色矩阵验证（bid-otherDept / bid-administration 应返回 200）
mvn test -Dtest='*SecurityTest'
```

### 真实接口验证（部署后，需主工作区或服务器）

```bash
# 用 bid-otherDept 角色用户的会话访问
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Cookie: <bid-otherDept 用户的会话>" \
  http://127.0.0.1:8080/api/task-extended-fields
# 期望：200（修复前为 403）

# 服务器日志确认（不再出现该接口 403）
sudo journalctl -u xiyu-bid-backend --since "5 min ago" | grep "task-extended-fields"
```

---

## P2 验证（ArchitectureTest 守卫）

```bash
cd backend

# 守卫规则存在且当前全绿（177 处都在豁免清单内）
mvn test -Dtest='ArchitectureTest'

# 验证豁免清单与实际使用点数量一致（数量不一致则失败）
# 守卫内部已自验，运行测试即可

# 负向验证（手动）：故意新增一个 hasAnyRole 注解，测试应失败
# 1. 在任意 Controller 加一个测试方法：
#    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
#    public void testGuard() {}
# 2. 运行 mvn test -Dtest=ArchitectureTest → 应失败
# 3. 从豁免清单外的新增使用点会触发"禁止 hasAnyRole"错误
# 4. 删除测试方法恢复
```

---

## P3 验证（分批迁移进度）

```bash
# 迁移进度指标：使用点数应从 177 逐步降至 0
cd backend
grep -rn "@PreAuthorize.*hasAnyRole\|@PreAuthorize.*hasRole" src/main/java --include="*.java" | grep -v test | wc -l

# 每批迁移完成后，对应模块的 Controller 应只剩 isAuthenticated/hasAuthority
grep -rn "@PreAuthorize" src/main/java/com/xiyu/bid/<module>/controller/ --include="*.java"

# 最终目标：全仓无 hasAnyRole（SecurityConfig 路径级兜底除外）
grep -rn "hasAnyRole\|hasRole" src/main/java --include="*.java" | grep -v test | grep -v "SecurityConfig"
# 期望：空输出

# 守卫升级为硬失败（豁免清单清空后）
mvn test -Dtest='ArchitectureTest'
```

---

## 回归测试矩阵（每批 PR 必须覆盖）

| 角色 | 预期（有权限接口） | 预期（无权限接口） |
|---|---|---|
| admin | 200 | 200（admin 拥有 all 权限） |
| bid-projectLeader | 200（若有 permissionKey） | 403 |
| bid-TeamLeader | 200（若有 permissionKey） | 403 |
| bid-Team | 200（若有 permissionKey） | 403 |
| bid-administration | 200（仅限公开读取/字典） | 403 |
| bid-otherDept | 200（仅限 task 相关） | 403 |
| anonymous（未登录） | 401 | 401 |

---

## 完成定义（Definition of Done）

- [ ] P1: TaskExtendedFieldController 方法级 `@PreAuthorize` 删除，回归测试覆盖 bid-otherDept 200
- [ ] P2: ArchitectureTest 新增守卫，含 177 处豁免清单 + 数量一致性自验
- [ ] P3 批次 1-7: 每批 Controller 迁移 + SecurityTest + 豁免清单删减
- [ ] 最终: `grep hasAnyRole` 输出为空（SecurityConfig 除外），守卫升级硬失败
- [ ] 连续部署观察：1 个月内无新增因 hasAnyRole 漏配的 403 PR
