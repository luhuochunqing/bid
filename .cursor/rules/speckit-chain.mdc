---
name: "speckit-chain"
description: >-
  一键串行执行 spec-kit 完整开发工作流：constitution → specify → [clarify] → plan → [validate] → tasks → [analyze] → implement。
  严格按照官方 spec-kit 方法论串联 5 个核心命令 + 3 个官方推荐的质量门禁。
  触发词：全流程开发、全程开发、speckit 全流程、串行执行 speckit、用 spec kit 走完整套流程、帮我实现一个功能并用 speckit。
  非单步调试场景——如果用户只想执行其中某一步，应使用对应的独立技能而非本 chain skill。
argument-hint: "描述你想要实现的功能，可附带技术栈偏好"
compatibility: "Requires spec-kit project structure with .specify/ directory"
metadata:
  author: "custom"
user-invocable: true
disable-model-invocation: false
---

## User Input

```text
$ARGUMENTS
```

## Why This Skill Exists

The official spec-kit documentation defines a complete development pipeline with 5 core commands + 3 optional quality commands. Running them one by one is tedious and error-prone — context drops between steps. This skill chains them into a single seamless workflow: one feature description in, complete implementation out, with the official quality gates built in.

**Ground truth**: This skill follows the official spec-kit workflow documented at github.com/github/spec-kit. Each step delegates to the authoritative speckit skill via the `Skill` tool — do NOT re-implement any step's logic inline.

## Official Spec-Kit Workflow Reference

Per the spec-kit README and detailed process guide, the recommended end-to-end flow is:

| Order | Step | Command | Required? |
|---|---|---|---|
| 1 | Establish principles | `/speckit.constitution` | Core |
| 2 | Create specification | `/speckit.specify` | Core |
| 3 | Clarify requirements | `/speckit.clarify` | **Recommended before plan** |
| 4 | Generate implementation plan | `/speckit.plan` | Core |
| 5 | Validate the plan | Manual review | **Recommended** |
| 6 | Break down into tasks | `/speckit.tasks` | Core |
| 7 | Cross-artifact analysis | `/speckit.analyze` | **Recommended before implement** |
| 8 | Execute implementation | `/speckit.implement` | Core |
| — | Post-delivery: checklists, issues | `/speckit.checklist`, `/speckit.taskstoissues` | Optional |

Steps 3, 5, and 7 are marked **Recommended** in the official docs. This skill includes them as lightweight gates — it will present each recommendation, explain why the docs suggest it, and let you decide in one click.

## Execution Flow

---

### [Gate 0] Prerequisites Check

Before starting, verify:

1. **Spec-kit initialized**: `.specify/` directory exists. If missing, stop and instruct: `specify init . --force`.
2. **Project root**: Current directory is the repo root (where `.specify/` lives).
3. **Feature description**: `$ARGUMENTS` should contain at least a basic feature description. If empty, ask briefly. Don't over-interview — a few sentences suffice; the clarify phase will handle gaps.

---

### [Step 1] Constitution — Establish Project Principles

**What the official docs say**: *"The first step should be establishing your project's governing principles using the `/speckit.constitution` command. This helps ensure consistent decision-making throughout all subsequent development phases."*

**Goal**: Create or update `.specify/memory/constitution.md` with governing principles.

**Execution**:

1. Invoke `Skill({skill: "speckit-constitution", args: "<principles>"})`
   - If the user explicitly described principles in `$ARGUMENTS`, pass them
   - Otherwise infer from repo context (README, package.json, CLAUDE.md) — e.g., code quality, testing standards, performance requirements, security
2. The constitution skill handles: template loading, placeholder resolution, consistency propagation across dependent templates
3. **On success**: Summarize what was established — version, key principles
4. **On failure**: Report the error. Likely cause: missing template → suggest `specify init --force`

**⏱ Expected duration**: ~30 seconds

---

### [Step 2] Specify — Create Feature Specification

**What the official docs say**: *"Be as explicit as possible about what you are trying to build and why. Do not focus on the tech stack at this point."*

**Goal**: Generate `specs/<feature-name>/spec.md` with user stories, functional requirements, and success criteria. The specify skill also auto-generates a quality checklist at `checklists/requirements.md`.

**Execution**:

1. Compose a feature description from `$ARGUMENTS` + any follow-up from Gate 0
2. Invoke `Skill({skill: "speckit-specify", args: "<feature description>"})`
3. Wait for the specification process — it handles:
   - Feature short-name generation
   - Feature directory creation (e.g., `specs/003-user-auth/`)
   - User story extraction, requirement drafting, success criteria definition
   - Quality checklist generation and self-validation
   - [NEEDS CLARIFICATION] resolution (max 3 questions, asked inline)
4. **On success**: Note the feature directory and spec file path
5. **On failure**: Report and ask how to proceed

---

### [Gate A] Spec Review + Clarification (Official Recommendation)

**What the official docs say**: *"You should run the structured clarification workflow before creating a technical plan to reduce rework downstream."*

After the spec is created:

1. Present a concise summary:
   ```
   ## Specification Complete
   - Feature: <name>
   - User stories: <N> (priorities: P1, P2, ...)
   - Spec file: specs/<name>/spec.md
   - Quality checklist: specs/<name>/checklists/requirements.md
   ```

2. **Recommend clarify** — per official docs:
   ```
   The spec-kit docs recommend running `/speckit.clarify` before planning
   to catch underspecified areas early. This typically takes 2-5 minutes
   and asks up to 5 targeted questions.
   
   Options: a) Run clarify (recommended) → directly runs /speckit.clarify
            b) Skip clarify → proceed to planning
            c) Refine spec → I'll update the spec first
   ```

3. Based on user choice:
   - **Run clarify**: Invoke `Skill({skill: "speckit-clarify"})`, wait for it to finish, return to this gate for approval
   - **Skip**: Explain that this bypasses an official recommendation, then proceed to Step 3
   - **Refine**: Ask what to change, re-invoke `speckit-specify`, return to this gate

---

### [Step 3] Plan — Create Implementation Plan

**What the official docs say**: *"You can now be specific about the tech stack and other technical requirements."*

**Goal**: Generate plan artifacts — `plan.md`, `data-model.md`, `contracts/`, `research.md`, `quickstart.md`.

**Execution**:

1. Collect tech stack and architecture guidance:
   - From `$ARGUMENTS` if the user mentioned specific technologies
   - Otherwise, infer from project conventions (`package.json`, `pom.xml`, `build.gradle`, etc.)
2. Invoke `Skill({skill: "speckit-plan", args: "<tech stack and architecture guidance>"})`
3. Wait for planning — it handles:
   - Research phase (resolves unknowns, generates `research.md`)
   - Design phase (data model, contracts, architecture decisions)
   - Agent context update (writes plan reference into CLAUDE.md)
4. **On success**: Summarize the plan — tech stack, architecture, data model, interfaces
5. **On failure**: Common failure — missing tech stack info. Ask user for preferences.

---

### [Gate A5] Plan Validation (Official Recommendation)

**What the official docs say**: *"With the plan in place, you should have Claude Code run through it to make sure that there are no missing pieces."*

After the plan is generated:

1. Present a summary:
   ```
   ## Plan Complete
   - Tech stack: <languages, frameworks, database>
   - Architecture: <key decisions>
   - Data model: <N entities>
   - Plan file: specs/<name>/plan.md
   - Research: specs/<name>/research.md (decisions documented)
   ```

2. Ask validation question:
   ```
   Does the plan look correct? Check for:
   - Over-engineered components (Claude Code can be over-eager)
   - Missing pieces in the implementation sequence
   - Tech stack decisions you disagree with
   
   Say 'continue' to proceed to task generation, or describe what should change.
   ```

3. Based on response:
   - **Continue**: Proceed to Step 4
   - **Changes**: Describe what to adjust, then I'll re-invoke `speckit-plan` with refinements
   - **Stop**: Wait for further instructions

---

### [Step 4] Tasks — Generate Actionable Task Breakdown

**What the official docs say**: *"Use the `/speckit.tasks` command to automatically generate a detailed task breakdown from your implementation plan."*

**Goal**: Create `tasks.md` with dependency-ordered, executable tasks per user story.

**Execution**:

1. Invoke `Skill({skill: "speckit-tasks"})`
   - No arguments needed — it reads spec + plan artifacts automatically
2. Wait for task generation — it produces:
   - Phase 1: Setup tasks (project initialization)
   - Phase 2: Foundational tasks (blocking prerequisites)
   - Phase 3+: One phase per user story (in priority order)
   - Final Phase: Polish & cross-cutting concerns
   - Parallel execution markers [P] for independent tasks
3. **On success**: Summarize total task count, phases, parallel opportunities
4. **On failure**: Common if spec/plan artifacts are missing → check feature directory

---

### [Gate B] Tasks Review + Analysis (Official Recommendation)

**What the official docs say**: *"Run `/speckit.analyze` after tasks, before implement — cross-artifact consistency & coverage analysis."*

After tasks are generated:

1. Present:
   ```
   ## Task Breakdown Complete
   - Total tasks: <N>
   - Phases: <N> (setup + foundational + <N> user stories + polish)
   - Parallelizable: <N> tasks marked [P]
   - Task file: specs/<name>/tasks.md
   ```

2. **Recommend analyze** — per official docs:
   ```
   The spec-kit docs recommend running `/speckit.analyze` before implementation
   to check cross-artifact consistency across spec, plan, and tasks.
   This is a quick non-destructive check that can catch gaps early.
   
   Options: a) Run analyze (recommended) → directly runs /speckit.analyze
            b) Skip → proceed to implementation
            c) Review tasks first → I'll show you the full task list
   ```

3. Based on user choice:
   - **Run analyze**: Invoke `Skill({skill: "speckit-analyze"})`, wait for results, present findings, then return to this gate
   - **Skip**: Explain this bypasses an official recommendation, then proceed
   - **Review**: Show task list, wait for explicit "continue"

---

### [Step 5] Implement — Execute Implementation

**What the official docs say**: *"The `/speckit.implement` command will validate prerequisites, parse tasks.md, and execute tasks in dependency order."*

**Goal**: Run all implementation tasks to produce working code.

**Execution**:

1. Invoke `Skill({skill: "speckit-implement", args: "<any implementation guidance>"})`
   - If the user gave specific guidance (e.g., "follow existing patterns", "TDD", "skip tests"), pass as args
   - Otherwise pass empty args — the implement skill reads from tasks.md
2. The implement skill handles:
   - Prerequisites check (including checklist validation)
   - Project setup verification (gitignore, dockerignore, etc.)
   - Phase-by-phase task execution (setup → foundational → user stories → polish)
   - Progress reporting per completed task
   - Task checkbox marking in tasks.md
3. **Monitor**: Report progress as tasks complete. This step can be long-running.
4. **On success**: Summary of what was built — files changed, features delivered
5. **On failure**: Report the specific failed task, its error context, suggest next steps

**Important**: The implement step may prompt you about incomplete checklists (from the specify phase). If so, present the checklist status to the user and ask whether to proceed.

---

### [Final] Completion Summary

Present a structured completion report:

```
## Pipeline Complete

### Summary
- ✅ Constitution — v<version>
- ✅ Specification — specs/<feature>/spec.md (<N> user stories)
- ✅ Clarification — <completed/skipped>
- ✅ Plan — specs/<feature>/plan.md
- ✅ Tasks — specs/<feature>/tasks.md (<N> tasks)
- ✅ Implementation — <N> files changed

### What Was Built
<1-2 sentence description>

### Key Files
- <file paths and brief descriptions of key deliverables>
```

Then offer next steps:

```
## Next Steps

Choose what to do next:

1. **Verify**: Run verification to confirm the implementation works
2. **Quality checklists**: `/speckit.checklist <domain>` — generate custom checklists (security, UX, performance)
3. **GitHub issues**: `/speckit.taskstoissues` — convert tasks to GitHub issues for tracking
4. **Implementation notes**: I can create an implementation-notes.md documenting decisions made during development that weren't in the original spec
5. **New feature**: Start another feature with `/speckit.chain`
```

**If the user's CLAUDE.md or project conventions mention implementation-notes**: After implementation completes, create or update `implementation-notes.md` in the feature directory documenting deviations from the spec, tradeoffs made, and decisions that weren't in the original plan.

---

## Error Recovery Reference

| Failure Point | Likely Cause | Action |
|---|---|---|
| Constitution | Template missing or git HEAD not detached | Run `specify init --force`, verify git state |
| Specify | `$ARGUMENTS` too vague | Ask for more details; if none, exit with guidance |
| Clarify | No spec found | Verify feature directory and `.specify/feature.json` |
| Plan | Missing tech stack info | Ask user for preferences; fallback to project conventions |
| Plan | Script execution failure | Check `.specify/scripts/bash/` permissions |
| Tasks | Missing spec/plan artifacts | Run `/speckit.plan` first |
| Analyze | Missing tasks.md | Run `/speckit.tasks` first |
| Implement | Missing dependencies, build errors | Report error context, suggest fix, offer retry |
| Implement | Extension hook failure | Check `.specify/extensions.yml`, ask user |
| Any step | Extension hooks | Each sub-skill handles its own before/after hooks. User interaction may be needed for optional or mandatory hook commands. |

**General rule**: If a step fails, report the error concisely (what, why, suggested fix) and ask the user to retry, skip (if safe), or stop. Do NOT silently skip steps.

---

## Important Behavior Notes

- **Delegation, not re-implementation**: Each step invokes the corresponding speckit skill via `Skill()`. Do NOT try to replicate constitution/specify/plan/tasks/implement logic inline. The official speckit skills are the source of truth.
- **Arguments flow forward**: The user's `$ARGUMENTS` at invocation time carries through all steps — principles for constitution, feature description for specify, tech stack for plan. Refer back to it.
- **Feature directory persistence**: After Step 2 (specify), the feature directory is written to `.specify/feature.json`. Subsequent steps read from this file. If you need to locate the current feature directory, check this file.
- **Context duration**: The full pipeline may take 10-30 minutes. Each sub-skill invocation via `Skill()` loads fresh instructions — context from earlier steps is preserved in conversation history, not assumed in skill state.
- **Implementation duration warning**: For large features (>30 tasks), Step 5 can take a long time. Warn the user and offer to run in phases if needed.
- **Hooks may prompt**: Each speckit skill checks `.specify/extensions.yml` for before/after hooks. When an extension hook fires, the user may see additional prompts (e.g., branch creation, git commit). This is normal spec-kit behavior.
