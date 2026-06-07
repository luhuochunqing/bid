---
name: html-first-workflow
description: >-
  Use when user requests a deliverable document that will be reviewed, shared,
  or archived — specs, PRDs, architecture designs, code review reports,
  weekly/monthly/quarterly reports, competitive analysis, technical option
  comparisons, SOPs, meeting notes, or interactive prototypes. Symptoms: user
  says "出一份方案/文档", "正式一点", "给老板看", "存档", "对比方案",
  "周报/月报", or asks for diagrams + structured analysis together. NOT for:
  chat replies, code comments, simple API notes, throwaway scratch work,
  single-paragraph answers.
---

# HTML-First Workflow

## Core Principle

Deliverable documents → single-file HTML artifact (Tailwind via CDN).
Internal notes / API docs / code comments / chat → markdown.

## When to Use vs NOT

| Output | Format | Why |
|--------|--------|-----|
| Spec / PRD / 需求 / 规格 | HTML | Reviewed, archived, has diagrams |
| 架构设计 / 系统设计 | HTML | Diagrams + components |
| Code review report (PR-level) | HTML | Severity badges, diff, navigation |
| 周报 / 月报 / 季度总结 | HTML | Stat cards, charts, print-ready |
| 方案对比 / 技术选型 | HTML | Tabs, comparison tables |
| 会议纪要 / SOP / 竞品分析 | HTML | Structured, shared |
| Interactive prototype | HTML | Live demo |
| API doc / code comment | Markdown | Reference, in-repo |
| Chat reply / quick answer | Markdown | Ephemeral |
| Single-paragraph note | Markdown | Overkill in HTML |

## Workflow

1. Check the table above. If output ≠ HTML row → stop, use markdown.
2. Start from `reference/skeleton.md` — copy the complete HTML skeleton (includes Tailwind CDN, dark mode, print CSS, nav, cover, footer).
3. Pick a structure from `examples.md` (10 document types indexed) and adapt.
4. Pull components from `reference/_index.md` — **load only the sections you need**, not the whole reference.
5. Diagrams → `mermaid-guide.md` (only if diagrams are in scope).
6. Save to `docs/artifacts/[module]-[type]-v[N]-YYYYMMDD.html`.

## Required in Every HTML Artifact

- Tailwind CDN + `tailwind.config = { darkMode: 'class' }`
- Mobile responsive (`md:` / `lg:` breakpoints)
- Print CSS (`@media print` block with `.no-print`)
- Semantic HTML (`<nav>` / `<main>` / `<section>` / `<footer>`)
- Sticky top nav with dark-mode toggle + print button
- Cover section: title, version, date, status badge

## Common Mistakes

- Generating HTML for chat replies, 1-paragraph answers, or code comments
- Loading the entire `reference.md` when you only need 2-3 components
- Forgetting print CSS — artifact looks broken when user exports to PDF
- Hardcoding light-mode colors (no `dark:` variants on every color class)
- Inline `<style>` blocks instead of Tailwind utilities (breaks consistency)
- Using HTML for API docs that live in the repo (markdown is the right answer)
- Skipping the "When to Use vs NOT" table and defaulting to HTML for everything

## Red Flags — Stop and Use Markdown Instead

- User is having a back-and-forth conversation, not asking for a document
- Output will be a simple, single-paragraph answer
- Content is a code snippet with one paragraph of easy context
- File will live next to source code (README, CONTRIBUTING, inline docs)
- User explicitly says "不要文档" / "直接告诉我" / "just tell me"

## File Naming

```
[module]-[type]-v[N]-YYYYMMDD.html
# Examples:
# auth-requirements-v2-20260512.html
# pr-123-review-20260512.html
# weekly-report-20260512.html
```

## Document Folders

```
docs/
├── artifacts/      # Working drafts
├── specs/          # Approved specs (source of truth)
├── reports/        # Status / review / weekly
├── architecture/   # System design
├── research/       # PoC, comparisons
└── prototypes/     # Interactive prototypes
```

## Shortcuts

| Command | Action |
|---------|--------|
| `/html` | Force HTML output |
| `/spec` | Generate/update spec |
| `/review` | Code review HTML |
| `/report` | Status/summary report |
| `/explore` | Multi-option comparison |
| `/doc` | General documentation |
| `/prd` | Product requirements doc |
| `/sop` | Standard operating procedure |

## Files in This Skill

- `skeleton.md` — Complete HTML base template (start here)
- `examples.md` — 10 document skeletons (pick structure)
- `reference/_index.md` — component library index (load by section)
- `mermaid-guide.md` — diagram patterns (only when diagrams needed)
