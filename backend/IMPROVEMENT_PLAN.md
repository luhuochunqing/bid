# Spring Boot 最佳实践改进计划

## 需求重述

修复 `xiyu-bid-poc/backend` 项目中与 `springboot-patterns` 技能最佳实践的差距，提升代码质量和安全性。

## 识别的风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| IP 地址欺骗风险 | 高 - 日志可能记录伪造IP | 使用 `request.getRemoteAddr()` 或配置转发头处理 |
| 分页 DoS 风险 | 中 - 可能导致数据库压力 | 添加最大分页大小限制 |
| 配置变更影响 | 低 - 可能影响现有行为 | 仅添加配置，不修改业务逻辑 |

## 实施计划

### Phase 1: 配置改进 (5 分钟)

**文件**: `src/main/resources/application.yml`

#### 1.1 启用 RFC 7807 Problem Details
```yaml
spring:
  mvc:
    problemdetails:
      enabled: true  # 统一错误响应格式
```

#### 1.2 生产环境准备
```yaml
spring:
  jpa:
    open-in-view: false  # 关闭 Open-in-View（性能优化）
```

---

### Phase 2: 安全修复 (15 分钟)

**文件**: `src/main/java/com/xiyu/bid/exception/GlobalExceptionHandler.java`

#### 2.1 修复 IP 获取安全问题

**问题**: 直接读取 `X-Forwarded-For` 可被客户端伪造

**修复方案**:
```java
// 删除不安全的 X-Forwarded-For 读取
private String getClientIp(HttpServletRequest request) {
    // 直接使用 getRemoteAddr() - 当配置了 forward-headers-strategy
    // 会自动返回正确的客户端 IP
    return request.getRemoteAddr();
}
```

或者添加配置说明注释，如需使用代理模式：
```yaml
server:
  forward-headers-strategy: NATIVE  # 云平台自动处理
```

---

### Phase 3: 分页限制 (20 分钟)

**文件**: 所有带分页的 Controller

#### 3.1 创建分页常量类
```java
package com.xiyu.bid.config;

public final class PaginationConstants {
    private PaginationConstants() {}
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
}
```

#### 3.2 修改 Controller 中的分页参数处理
```java
@RequestParam(defaultValue = "0") int page,
@RequestParam(defaultValue = "10") int size

// 在方法开始添加限制
if (size > PaginationConstants.MAX_PAGE_SIZE) {
    size = PaginationConstants.MAX_PAGE_SIZE;
}
```

---

### Phase 4: 只读事务优化 (30 分钟)

**文件**: Service 查询方法

#### 4.1 为只读查询添加 `@Transactional(readOnly = true)`

目标文件:
- `ProjectService.java` - 查询方法
- `FeeService.java` - 查询方法
- `TenderService.java` - 查询方法
- `TemplateService.java` - 查询方法
- `QualificationService.java` - 查询方法
- `CaseService.java` - 查询方法

---

## 实施顺序

1. ✅ Phase 1: 配置改进 (低风险)
2. ✅ Phase 2: 安全修复 (高优先级)
3. ✅ Phase 3: 分页限制 (中优先级)
4. ✅ Phase 4: 只读事务 (低优先级)

## 完成摘要

| Phase | 描述 | 修改文件数 | 状态 |
|-------|------|-----------|------|
| 1 | 配置改进 | 1 | ✅ |
| 2 | 安全修复 | 1 | ✅ |
| 3 | 分页限制 | 5 | ✅ |
| 4 | 只读事务 | 3 | ✅ |

**总计**: 10 个文件修改

## 验证计划

- [ ] 启动应用无错误
- [ ] 测试分页接口（size=1000 应被限制）
- [ ] 测试异常响应格式符合 RFC 7807
- [ ] 检查日志中 IP 地址格式
- [ ] 运行测试确保无回归

---

**预计总时间**: 70 分钟

**请确认此计划后开始执行？**
