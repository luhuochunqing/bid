---
name: entropy-reduction
description: Design modular code with clear boundaries: one reason to change per module, no circular dependencies, and low complexity. Use when refactoring spaghetti code, fixing over-engineered systems, or deciding where logic belongs. Avoids "5 files for one function" pseudo-modularity. 模块化, 低复杂度, 循环依赖, 依赖倒置, SOLID
---

# Entropy Reduction

## Overview

**Modularization = encapsulating cognitive load, not splitting files.**

A module is a black box: external callers know **WHAT** it does, not **HOW** it works.

**The boundary is the interface.** If changing internal implementation affects external code, the boundary is wrong.

---

## When to Use

```
✓ "这个文件太长了，但不知道怎么拆分"
✓ "加一个功能需要改 5 个文件，太麻烦"
✓ "这段逻辑应该放在哪里？"
✓ "出现循环依赖错误了"
✓ "代码过度设计了，怎么简化？"
✓ "模块之间的边界不清晰"
✓ "职责划分混乱，不知道怎么划分"
```

**Don't use for:**
- 简单代码重构 → 直接重构即可
- 性能优化 → 用 profiler
- 学习设计模式 → 查阅文档

---

## Core Constraints ⚠️

这些是**硬规则**，不是建议。违反它们会增加系统熵：

| 指标 | 阈值 | 原因 |
|------|------|------|
| **圈复杂度** | < 10/模块 | 更高 = 分支逻辑多，难测试 |
| **扇出** | ≤ 5 | 模块依赖太多其他模块 |
| **扇入** | 高 = 好 | 很多模块依赖此 = 稳定抽象 |
| **模块深度** | ≤ 4 层 | 更深 = 难追踪调用流 |
| **文件长度** | < 300 行 | 更长 = 做太多事情 |

**扇入/扇出定义：**
```
扇入  = 导入此模块的其他模块数量
扇出  = 此模块导入的其他模块数量
```

---

## 什么是模块（What IS a Module）

模块有**唯一变更原因**（SRP）。变更来自：
- 业务规则变化
- 外部依赖 API 变化
- 性能需求
- 合规/监管要求

如果一个模块会因 >1 个原因变更，它有**多重职责**。

### 什么不是模块（What is NOT a Module）

```javascript
// ❌ 伪模块化：一个操作拆成 5 个文件
// modules/payment/alipay/request.js
export async function makeRequest(config) {
  return await fetch(config.url, config.options);
}
// modules/payment/alipay/parse.js
export function parseResponse(data) { return JSON.parse(data); }
// modules/payment/alipay/validate.js
export function validateInput(input) { return input && input.amount > 0; }
// modules/payment/alipay/log.js
export function logPayment(data) { console.log('[ALIPAY]', data); }
// modules/payment/alipay/status.js
export function checkStatus(status) { /* ... */ }
```

**问题**：每个文件都是简单包装，没有封装，只是重定向。

```javascript
// ✅ 内聚模块：一个抽象，完整操作
// modules/payment/alipay.js
export class AlipayProvider {
  async createPayment(order) {
    this.validate(order);
    const signed = this.signRequest(order);
    const response = await this.request(signed);
    return this.parseResponse(response);
  }

  validate(order) { /* 领域验证 */ }
  signRequest(order) { /* 支付宝签名 */ }
  request(params) { /* HTTP with retry, timeout */ }
  parseResponse(resp) { /* 支付宝解析 */ }
}
```

**更好**：一个文件，一个抽象，完整操作。内部细节私有。

---

## 层边界（Layer Boundaries）

```
┌─────────────────────────────────────────────────────┐
│ Domain Layer     │ 业务规则，无 I/O           │
│                  │ 纯函数，实体             │
├─────────────────────────────────────────────────────┤
│ Application Layer │ 编排，工作流              │
│                  │ 协调领域对象             │
├─────────────────────────────────────────────────────┤
│ Infrastructure Layer │ 数据库，API，邮件，文件   │
│                  │ 外部系统                 │
└─────────────────────────────────────────────────────┘
```

**依赖方向**：Infrastructure → Domain 禁止。Domain → Infrastructure 仅通过接口。

---

## 依赖规则

### 依赖倒置原则（DIP）

```javascript
// ❌ 违规：Domain 依赖 Infrastructure
class PaymentService {
  constructor() {
    this.db = new MySQLDatabase();  // 具体依赖
  }
}

// ✅ 正确：Domain 依赖抽象
class PaymentService {
  constructor(database) {  // 接口注入
    this.db = database;
  }
}
```

### 检测循环依赖

使用工具检测：
- `madge --circular src/` (JavaScript/TypeScript)
- `depcruise --output-type err src/`

**手动检查**：如果模块 A 导入模块 B，模块 B 也导入模块 A → **提取公共模块 C** 让两者都依赖。

### 接口隔离

```javascript
// ❌ 胖接口：强制客户端依赖不需要的方法
interface PaymentProvider {
  createPayment(): void;
  refundPayment(): void;
  getTransactionHistory(): void;
  downloadStatement(): void;  // 只有管理员需要
}

// ✅ 隔离：客户端只依赖使用的部分
interface PaymentProvider {
  createPayment(): void;
  refundPayment(): void;
}
interface AdminPaymentProvider extends PaymentProvider {
  getTransactionHistory(): void;
  downloadStatement(): void;
}
```

---

## 常见反模式

| 反模式 | 描述 | 修复 |
|---------|------|------|
| **伪模块化** | 很多小文件，每个包装一个函数 | 合并到内聚模块 |
| **上帝对象** | 一个模块知道所有（domain + infra + UI） | 按职责和层拆分 |
| **功能蔓延** | 模块随时间积累不相关功能 | 按功能提取新模块 |
| **模式堆砌** | 简单情况用 Strategy + Factory + Builder | 从简单开始，需要时再加模式 |
| **泄漏抽象** | 内部细节通过接口暴露 | 隐藏实现，导出最小接口 |

---

## 理性化反驳（Rationalization Block）

| 借口 | 现实 |
|------|------|
| "这是 Strategy 模式" | 模式不证明复杂性合理。真的需要吗？ |
| "每个文件单一职责" | SRP = 一个变更原因，不是每文件一个函数 |
| "这样测试更容易" | 测试包装器什么也测不了。测试实际行为。 |
| "我以后可能需要这个灵活性" | YAGNI。为当前需求设计，不为假设的未来设计。 |
| "这样更模块化" | 文件更多 ≠ 更模块化。内聚性更重要。 |
| "这是标准做法" | 盲目遵循"标准"而不理解会增加熵。 |

**违反规则的精神就是违反规则本身。** 没有例外。

---

## 快速检查清单

完成模块设计前：

**结构：**
- [ ] 每个模块圈复杂度 < 10
- [ ] 没有模块扇出 > 5
- [ ] 模块深度 ≤ 4 层
- [ ] 无循环依赖（工具验证）

**边界：**
- [ ] Domain 层无 I/O 操作
- [ ] Infrastructure 依赖 Domain 接口，不反向
- [ ] 每个模块有一个变更原因

**依赖：**
- [ ] 具体依赖通过构造函数注入
- [ ] 接口隔离（无胖接口）
- [ ] 同层模块间无直接耦合（需要时用 mediator）

**红旗警示：**
- [ ] 无伪装成模块的包装函数
- [ ] 无无正当理由使用的模式
- [ ] 无为假设的未来需求添加的"灵活性"
