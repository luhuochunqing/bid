---
name: self-verifying-tests
description: Write tests that verify system consistency: UI matches database, state transitions are valid, docs align with code. Use when building E2E tests, test infrastructure, or detecting hidden bugs between layers. Replaces "click and check" testing. 自验证测试, 跨层验证, 状态机测试
---

# Self-Verifying Tests

## Overview

Traditional E2E tests verify "user clicks → UI shows success". Self-verifying tests verify "system narrative remains consistent". We test not just behavior, but **self-consistency**.

**Core principle:** Hidden bugs live in the gap between "UI looks fine, internal structure is broken". Cross-layer validation eliminates this blind spot.

---

## When to Use

```
✓ "UI 显示成功但数据库没更新"
✓ "需要验证状态机转换是否正确"
✓ "测试总是过但上线后出 bug"
✓ "文档和代码不一致导致问题"
✓ "需要从 manifest 自动生成测试"
✓ "想验证跨层数据一致性"
✓ "UI 和后端状态不同步"
```

**Don't use for:**
- 简单 UI 烟雾测试 → 用传统 E2E
- 单元测试 → 用 Vitest/Jest
- 视觉回归测试 → 用专用工具

---

## Four Verification Layers

### Layer 1: Manifest-Driven Generation
Read `manifest.config.ts` → Generate test skeleton automatically.

### Layer 2: Cross-Layer Validation (Shadow Inspector)
UI action + Database verification + Audit log check.

### Layer 3: State Symmetry (FSM)
Model-based testing of state transitions.

### Layer 4: Doc-Code Integrity
Verify documentation matches code before tests run.

---

## Quick Reference

| Layer | Command | What It Verifies |
|-------|---------|------------------|
| Generate | `npm run test:generate` | Manifest → test skeleton |
| Shadow | `verifyInvariant(page, id)` | UI === DB === Audit |
| FSM | `test:gremlin` | State transition legality |
| Doc Check | `npm run test:doc-integrity` | Doc ↔ Code alignment |

---

## Implementation

### Test Generator

```typescript
// Scan all manifests and generate tests
const scanner = new ManifestScanner()
const manifests = await scanner.scanAll()

for (const m of manifests) {
  // Generates: test.describe(`@manifest:${m.id}`, () => {...})
  generateTestSkeleton(m)
}
```

### Shadow Inspector Pattern

```typescript
test('archive: shadow invariant check', async ({ page }) => {
  await page.click('[data-testid="archive-submit"]')
  const id = await page.evaluate(() => window.lastArchiveId)

  // Critical: Verify database directly
  const dbRecord = await db.query('SELECT * FROM archives WHERE id = $1', [id])
  const auditLog = await db.query('SELECT * FROM audit_logs WHERE entity_id = $1', [id])

  // Invariant: UI === DB === Audit
  expect(dbRecord.status).toBe('ARCHIVED')
  expect(auditLog.length).toBeGreaterThan(0)
})
```

### FSM State Testing

**CRITICAL:** Never hard-code or assume current state in Gremlin tests. Always fetch actual state from DB or UI.

```typescript
test('gremlin: random transitions respect FSM', async ({ page }) => {
  for (let i = 0; i < 100; i++) {
    // MUST fetch real state, never assume!
    const state = await getCurrentStateFromDB(page) // or UI
    const valid = validTransitions.filter(t => t.from === state)
    const action = valid[Math.floor(Math.random() * valid.length)]

    await page.click(`[data-action="${action.trigger}"]`)
    const newState = await getCurrentStateFromDB(page)

    expect(validStates).toContain(newState) // Always valid
  }
})

// Helper: Get state from database (preferred)
async function getCurrentStateFromDB(page: Page): Promise<string> {
  const id = await page.evaluate(() => window.currentEntityId)
  const response = await fetch(`/api/pages/${id}`)
  const data = await response.json()
  return data._status // 'draft' or 'published'
}
```

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|----------------|-----|
| Only checking UI toast | Can miss DB corruption | Always verify DB state |
| Hard-coded test cases | Maintenance nightmare | Generate from manifest |
| Testing happy path only | Misses edge cases | Use FSM random testing |
| Ignoring doc drift | Future bugs from outdated docs | Run doc-integrity check |
| **Assuming state in Gremlin tests** | Defeats the purpose of state testing | Fetch real state from DB/API |

---

## Red Flags - You're Doing It Wrong

- "I just check the toast message" → You're missing the point
- "We'll write tests manually" → Violates manifest-driven principle
- "State testing is overkill" → That's where the bugs hide
- "Docs are separate concern" → Doc drift causes future bugs
- **"I'll assume current state for simplicity"** → Defeats state machine testing
- **"Fetching DB state is too complex"** → That's the entire point of Shadow Inspector

**All of these mean: Re-read the Overview.**
