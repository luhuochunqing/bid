---
name: architecture-defense
description: Enforce architectural rules automatically: block wrong dependencies, prevent layer violations, detect circular dependencies. Use when you need dependency-cruiser, ArchUnit, module guards, or CI architecture checks. Prevents "UI importing database", "cross-feature internal imports", and other boundary violations.
---

# Architecture Defense

**Architecture = Code.** Structure is defined by metadata within the code itself, not external documentation.

## The Four Vital Signs (J1-J4)

| Sign | Meaning | Goal |
|------|---------|------|
| **J1 Self-Description** | Each module has a "manifest" declaring boundaries | Know who owns what |
| **J2 Self-Check** | Built-in linters validate architecture continuously | Automated validation |
| **J3 Closed Rules** | Rules apply to the build process itself | Can't bypass |
| **J4 Reflex** | Violations cause "system shock" (CI block) | Fail fast |

**The Goal**: Wrong dependency calls are rejected before they leave the developer's machine.

---

## When to Use This Skill

```
✓ "Who owns this module?"
✓ "Why can UI import from database layer?"
✓ "Circular dependency hell"
✓ "Documentation doesn't match actual structure"
✓ "Need to enforce module boundaries"
✓ "Want to catch architecture violations in CI"
```

---

## Quick Start

### For Each New Module

Create this structure:

```
src/features/[module]/
├── manifest.config.ts    ← Module ID card
├── index.ts              ← Public API entry (ONLY export)
├── api/                  ← Public: What consumers use
├── internal/             ← Private: Implementation details
│   ├── services/
│   ├── utils/
│   └── types/
└── __tests__/
```

### Minimal Manifest

```typescript
// src/features/[feature-name]/manifest.config.ts
export const moduleManifest = {
  id: "feature.user-profile",
  owner: "team-platform",           // WHO owns this
  publicApi: "./index.ts",           // Single legal entry
  canImportFrom: [
    "src/shared/utils/**",
    "src/features/*/index.ts"        // Only public APIs
  ],
  restrictions: {
    disallowDeepImport: true
  }
};
```

---

## J2: Self-Check Tools

### Frontend: dependency-cruiser

**Install:**
```bash
npm install --save-dev dependency-cruiser
```

**Core Config:**
```javascript
// dependency-cruiser.config.cjs
module.exports = {
  forbidden: [
    {
      name: 'no-cross-feature-internal',
      from: { path: '^src/features/([^/]+)/.+' },
      to: {
        path: '^src/features/([^/]+)/.+',
        pathNot: '^src/features/\\2/index\\.ts$'
      },
      severity: 'error'
    },
    {
      name: 'no-ui-to-db',
      from: { path: '^src/(ui|components)/' },
      to: { path: '^src/(database|models)/' },
      severity: 'error'
    },
    {
      name: 'no-cycles',
      from: { path: '^src/' },
      to: { path: '^src/' },
      cycle: true,
      severity: 'error'
    }
  ]
};
```

**NPM Scripts:**
```json
{
  "scripts": {
    "check:arch": "depcruise --config dependency-cruiser.config.cjs src/",
    "check:arch:graph": "depcruise --config dependency-cruiser.config.cjs src/ --output-type dot | dot -T svg > arch.svg"
  }
}
```

### Backend: ArchUnit

**Install:**
```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

**Test Class:**
```java
@AnalyzeClasses(packages = "com.company")
public class ArchitectureTest {
    @ArchTest
    static final ArchRule no_cycles =
        slices().matching("com.company.features.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule controller_not_depend_on_repository =
        noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule domain_not_depend_on_infrastructure =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
}
```

---

## J3: Closed Rules (CI Integration)

### Pre-commit Hook
```bash
# .husky/pre-commit
npx depcruise --config dependency-cruiser.config.cjs \
  $(git diff --cached --name-only | grep '\.tsx?$')
```

### GitHub Actions
```yaml
# .github/workflows/architecture.yml
name: Architecture Check
on: [pull_request, push]
jobs:
  architecture:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: npm ci
      - run: npm run check:arch
      - run: mvn test -Dtest=ArchitectureTest  # Backend
```

### CODEOWNERS
```text
# Manifest changes require module owner approval
src/features/*/manifest.config.ts @platform-team
src/features/*/index.ts @platform-team
```

---

## J4: Reflex (Violation Response)

### Severity Levels

| Severity | Action | Use Case |
|----------|--------|----------|
| `error` | Blocks commit, fails CI | Critical violations |
| `warn` | Comments on PR | Style issues |

### Violation Message
```
✖ src/features/auth/ui/Login.tsx → src/features/user/internal/api.ts
  ⚠ no-cross-feature-internal

  Features cannot import other features' internal files.
  Fix: Import from src/features/user/index.ts instead
  Module owner: @team-platform
```

---

## Common Anti-Patterns

| Pattern | Problem | Fix |
|---------|---------|-----|
| **No manifest** | No ownership, no validation | Create manifest.config.ts |
| **Deep imports** | `import from ../../feature/internal/` | Import from index.ts |
| **UI → DB** | Components import models | Create type layer |
| **Manual reviews** | Humans miss violations | Automate with tools |
| **"It's shared utils"** | Shared modules bypass rules | Shared modules also need manifests |

---

## Legacy Codebase Strategy

For existing code, use **grandfathering**:

```typescript
// src/features/legacy-module/manifest.config.ts
export const moduleManifest = {
  id: "feature.legacy-module",
  owner: "team-backend",
  publicApi: "./index.ts",
  tags: ["legacy", "needs-refactor"],
  complianceTarget: "2025-06-01"
};
```

| Status | Rule | Deadline |
|--------|------|----------|
| New code | Full compliance | Immediately |
| Modified code | Add manifest before commit | Before PR merge |
| Untouched legacy | Add minimal manifest | 2 weeks |
| Hotfix | Add `exception: true` | Before deployment |

---

## Gradual Rollout

**Phase 1 (Week 1-2):** Discovery mode
```javascript
{ severity: 'warn' }  // Just track, don't block
```

**Phase 2 (Week 3-4):** Block new violations
```javascript
{
  severity: 'error',
  allowedInComments: ['ARCH-EXCEPT:', 'LEGACY:']
}
```
```typescript
// LEGACY: Temporary exception - refactor by 2025-03-01
import { internalUtil } from '../other-feature/internal/util';
```

**Phase 3 (Week 5+):** Full enforcement
```javascript
{ severity: 'error', allowedInComments: [] }
```

---

## Shared Modules

Shared modules ALSO need manifests:

```typescript
// src/shared/utils/manifest.config.ts
export const moduleManifest = {
  id: "shared.utils",
  owner: "team-platform",
  publicApi: "./index.ts",
  usedBy: ["src/features/**/*", "src/admin/**/*"],
  restrictions: {
    disallowDeepImport: true,
    allowSharedDependencies: true
  }
};
```

---

## Quick Checklist

**New Module:**
- [ ] `manifest.config.ts` with id, owner, publicApi
- [ ] Single `index.ts` entry point
- [ ] Internal code in `internal/`
- [ ] Added to dependency-cruiser config

**Existing Codebase:**
- [ ] Installed dependency-cruiser / ArchUnit
- [ ] Configured forbidden rules
- [ ] Pre-commit hooks
- [ ] CI workflow
- [ ] CODEOWNERS for manifests

---

## Key Principle

**"All of these mean: Add the manifest NOW."**

The file takes 2 minutes to create. Violating these rules violates the system's immune system. No exceptions.
