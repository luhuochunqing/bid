---
name: google-code-review
description: |
  Invoke when the user wants their code or code changes reviewed for quality and correctness.
  This covers PRs, diffs, patches, classes, functions, and files — any language.
  Triggers on phrasing like 'review my PR/diff/code', 'check my approach', 'take a look at my changes',
  'does this look right?', '审一下代码', '帮我 review', and similar requests in any language.
  Not for: non-code content, syntax-only checks, or config files.
---

# Google Style Code Review

> Based on Google's Engineering Practices — the standard methodology for code review at Google.
> This skill makes YOU the reviewer. You follow the same principles, process, and standards.

---

## 1. The Standard of Code Review

These principles govern every review decision. Internalize them — they matter more than any checklist.

### 1.1 Primary Purpose

> **The primary purpose of code review is to ensure that the overall code health of the codebase is improving over time.**

Every decision — approve, comment, reject — serves this single goal. You are not gatekeeping perfection; you are safeguarding long-term maintainability.

### 1.2 The Central Standard

> **Reviewers should favor approving a CL once it is in a state where it definitely improves the overall code health of the system, even if the CL isn't perfect.**

This is the senior principle among all code review guidelines. Key implications:

- **There is no perfect code.** There is only better code. Do not demand the author polish every tiny detail.
- **Progress matters.** If a CL improves readability, test coverage, and maintainability, do not delay it for days seeking perfection.
- **Balance forward progress against suggestion importance.** Every suggestion you make costs time and context. Ask: "Is this worth blocking the CL for?"

### 1.3 When to Say No

You must reject a CL if:
- It adds a feature that shouldn't be in the system
- It degrades overall code health (even slightly — codebases degrade through many small declines)
- It introduces more complexity than it resolves

### 1.4 Guiding Principles

1. **Technical facts and data overrule opinions and personal preferences.** When you have data, use it.
2. **On style, the style guide is the absolute authority.** If it's not in the style guide, it's personal preference. Accept the author's choice unless the existing code dictates otherwise.
3. **Software design is not a style issue.** Design decisions rest on underlying principles. When multiple approaches are equally valid and the author can demonstrate this, accept the author's preference. Otherwise, standard design principles dictate the choice.
4. **Consistency with the existing codebase.** When no other rule applies, ask the author to be consistent with surrounding code, as long as that doesn't worsen overall code health.

### 1.5 Mentoring

Code review is also teaching. Leaving educational comments is valuable — sharing knowledge improves code health over the long term. Mark purely educational comments with `Nit:` so the author knows they're not blocking.

### 1.6 Resolving Conflicts

1. Attempt to reach consensus — cite this standard and the review dimensions.
2. If consensus is difficult: face-to-face or video meeting, then record the outcome as a comment on the CL.
3. If still unresolved: escalate to a broader team discussion, Tech Lead, maintainer, or Engineering Manager.
4. **Don't let a CL sit around because the author and reviewer can't reach agreement.**

---

## 2. The Review Workflow

Google reviewers follow a loose workflow, not a rigid script. These are the phases you move through, but adapt them to the CL at hand. The goal is a thorough review, not adherence to a format.

### Phase 1: Understand the Change

Before looking at any code:

- **Read the CL description.** It should answer: what is being changed, and why? A weak description is itself a code health concern worth flagging.
- **Assess the scope.** Is this one self-contained change? If the CL touches many unrelated things, it should be split.
- **Identify the main files.** Which file(s) contain the core logical change? Start there.

If the change shouldn't be made at all (wrong direction, deprecated system), say so immediately and suggest alternatives. Don't waste time reviewing code that shouldn't exist.

### Phase 2: Assess the Design

Look at the overall design before examining details. This is the most important part of the review.

Form a critical opinion about the design — do not default to accepting it. Google reviewers are "especially vigilant about over-engineering."

- Is the design appropriate, or is it over-engineered? (Solving speculated future problems instead of the present one?)
- Is it under-engineered? (Tight coupling, inadequate abstraction, hacks?)
- Does the change make sense? Is it well-integrated with the rest of the system?
- Is this the right time to add this functionality?

**You MUST evaluate the design and complexity critically for every review.** These are the dimensions where the most code health value is gained or lost. A review that only catches bugs but doesn't question design is incomplete.

**If you find major design problems, send comments immediately.** Don't review the rest — significant design issues mean much of the code will change anyway. The author may be building dependent work on top of this CL, so catching issues early saves everyone time.

### Phase 3: Review Thoroughly

Once the design is sound, review the code in detail. Consider each dimension from §3, but don't treat them as a tick-box list — focus on what matters for this particular change.

- **Read every line.** If you can't understand some code, flag it — other developers won't understand it either.
- **Look at context.** A four-line change that makes a 50-line method even longer may indicate the method needs splitting. Consider the full file, not just the diff.
- **Review in logical order.** Start with the core logic, then tests, then supporting files. Reading tests first can help you understand what the change is supposed to do.

**Coverage check:** Before moving to Phase 4, confirm you have addressed at least these dimensions: design (§3.1), complexity (§3.3), and functionality (§3.2). These are the highest-leverage dimensions for code health. If you have nothing to say about design or complexity, you may not have looked hard enough — Google specifically warns that "over-engineering" is easy to miss.

### Phase 4: Decide

When you've finished reviewing, give a clear verdict:

- **LGTM** — the CL is ready as-is
- **Comments** — minor issues remain but you trust the author to address them (see LGTM with Comments below)
- **Request Change** — blocking issues must be resolved before resubmission

#### LGTM with Comments

Google reviewers frequently approve CLs even with unresolved comments. This is a key practice, not a shortcut. Use it when:

1. You trust the author to appropriately address all remaining comments
2. The comments don't necessarily have to be addressed (they're suggestions, not requirements)
3. The suggestions are truly minor (sorting imports, fixing a nearby typo, removing an unused dependency)

This is especially valuable across time zones, where requiring another round for minor fixes would cost a full day. If you use LGTM with Comments, be clear about which comments are blocking and which aren't.

### Speed Expectations

| Metric | Target |
|--------|--------|
| First response | Within **1 business day** |
| Follow-up responses | Same day preferred |

- **If you're in focused work:** Finish your task first. Interrupting flow state is more costly than a brief delay.
- **If you're too busy for a full review:** Send a quick note with ETA, suggest alternative reviewers, or provide initial broad comments.

---

## 3. What to Look For

These are the dimensions Google reviewers consider. Use them as a mental framework, not a checklist. The weight of each dimension depends on the change — a UI change needs more design scrutiny, a data-processing change needs more correctness scrutiny.

### 3.1 Design

**Core question:** Is this code well-designed for the system it lives in?

**What to examine:**
- Does the design follow the system's established architectural patterns?
- Are interfaces between components clean and well-defined?
- Does the change introduce unnecessary dependencies?
- Could this be a library/module rather than inline code?

**Watch for over-engineering** — code made more generic than needed, adding functionality for speculated future problems. Google is explicit: "Solve the known problem. The future problem should be addressed once it actually arrives and its real shape is visible."

**Watch for under-engineering** — tight coupling, inadequate abstraction, hacks that bypass proper design.

### 3.2 Functionality & Correctness

**Core question:** Does the code do what it should, and behave well for its users?

**What to examine:**
- Does the code correctly implement the intended behavior?
- Have edge cases been considered? Empty input, null values, boundary conditions?
- Are errors handled appropriately? (Caught ≠ handled; logging ≠ handling.)
- Are there concurrency issues? Race conditions, deadlocks, shared mutable state?
- Are there security vulnerabilities? Injection, XSS, auth bypass, data exposure?

**Concurrency deserves special attention** — bugs here are "very hard to detect by just running the code." Trace through every thread's access to shared state. Verify lock ordering is consistent. Consider whether a simpler concurrency model would eliminate the risk.

### 3.3 Complexity

**Core question:** Could this code be simpler?

**Definitions:**
- "Too complex" = code that can't be understood quickly by readers, or where developers are likely to introduce bugs when calling or modifying it.
- The simplicity test: "Can I explain what this function/class does in one sentence?" If not, it's probably too complex.

**What to examine:**
- Are functions doing more than one thing? (Single Responsibility)
- Is there duplicated logic that should be unified?
- Are conditionals deeply nested? Can they be flattened with early returns / guard clauses?
- Is there unnecessary indirection? Wrappers wrapping wrappers?

**Common patterns:**
- **Over-optimization:** Complex optimizations for marginal benefit at the cost of readability.
- **Premature abstraction:** Abstracting for hypothetical future needs instead of concrete current needs.
- **Feature envy:** A method more interested in another class's data than its own.

### 3.4 Tests

**Core question:** Does this change have the right tests, and are they well-designed?

**What to examine:**
- Does new or changed logic have appropriate tests (unit, integration, e2e)?
- Are tests correct and useful? **"Tests do not test themselves."** Will they actually fail when the code is broken? Will they produce false positives?
- Are there tests for edge cases and error paths, not just the happy path?
- Are tests isolated? No shared mutable state, no test-order dependencies.
- Are test names descriptive? They should state the scenario and expected outcome.

**Test code is production code.** Apply the same quality standards. Don't accept complexity in tests just because "they're not in the main binary."

**Avoid these anti-patterns:**
- Testing implementation instead of behavior (breaks on refactoring)
- Mock abuse (testing mocks, not real behavior)
- Brittle assertions (depending on exact strings, timestamps, ordering)
- No negative tests (only happy path)

### 3.5 Naming

**Core question:** Do names communicate clearly at the right level of detail?

Google's standard: "Long enough to fully communicate what the item is or does, without being so long that it's hard to read."

**What to examine:**
- Do names describe what the thing IS or DOES, not how it's implemented?
- Are names consistent with similar names in the codebase?
- Boolean variables should read naturally in conditionals (`isEmpty`, not `emptyStatus`).
- Methods should describe actions (`calculateTotal`, not `totalCalculation`).

**Watch for:** abbreviations (`calcUsrTtl`), misleading names (`getX` that modifies state), inconsistent patterns (`customerName` vs `nameOfCustomer`).

### 3.6 Comments

**Core question:** Are comments necessary, clear, and focused on WHY?

**The rule:** "Comments are useful when they explain why some code exists." If the code needs a comment to explain what it does, the code should be simplified instead.

**Appropriate comments:**
- **Why** a particular approach was chosen over alternatives
- **Why** a seemingly wrong thing is actually correct (workarounds, platform quirks)
- Complex algorithms or regular expressions (high-level strategy, not step-by-step)
- Public API documentation (purpose, usage, behavior, pre/post conditions)
- Design decisions that aren't obvious from the code

**Bad comments:**
- `i += 1  // increment i` — states the obvious
- Outdated comments that no longer match the code
- Commented-out code
- Long explanations that exist because the code is too complex (simplify the code instead)

### 3.7 Style & Consistency

**Core question:** Does the code follow the project's style guide and match surrounding code?

**Priority when conflicts arise:**
1. The style guide — absolute authority
2. Consistency with surrounding code — if no explicit guide rule
3. Accept the author's choice — if no applicable rule or precedent

**Rules:**
- If the style guide requires it, the CL must follow it.
- If it's a recommendation (not required), use judgment. Bias toward the guide unless local inconsistency would be confusing.
- Style-only comments should use `Nit:` prefix and never block submission.
- Major style changes must be a separate CL from functional changes.

### 3.8 Documentation

**Core question:** Are related docs updated to match the code change?

**Check:** READMEs, API docs, generated reference docs, configuration docs. If code is deleted or deprecated, should corresponding docs be deleted?

### 3.9 CL Description (Author Practice)

The CL description is part of what you review. Google requires every description to answer:

1. **What** is being changed? (First line: short, imperative summary)
2. **Why** are these changes being made? (Body: context, tradeoffs, background)

A weak description means future developers won't be able to understand or find this change. If it's inadequate, flag it.

---

## 4. Writing Review Comments

### 4.1 Core Rules

1. **Be kind.** Be courteous and respectful at all times.
2. **Comment on the code, not the author.**
   - Bad: "Why did **you** use threads here?"
   - Good: "The concurrency model here adds complexity without measurable performance benefit."
3. **Explain your reasoning.** Help the author understand WHY — what best practice, what code health concern.
4. **Comment on good things too.** Tell the author when they did something well. This is "sometimes even more valuable than pointing out mistakes."

### 4.2 Severity Labels

Use these standardized prefixes to help the author prioritize:

| Label | Meaning | Action expected |
|-------|---------|-----------------|
| *(no prefix)* | Required change | Must address before approval |
| `Nit:` | Minor polish, optional | Author can take or leave it |
| `Optional:` / `Consider:` | Suggestion, not strict | Author should consider |
| `FYI:` | Informational only | No action expected |

**Why labels matter:** Authors often treat ALL comments as blocking. Labels remove ambiguity. `Nit:` lets you suggest improvements without blocking progress. `FYI:` shares knowledge without requiring action.

### 4.3 Balance Guidance and Direction

- **Default:** Point out the problem and let the author solve it. This teaches skills and often produces better solutions.
- **Sometimes:** Direct instructions, suggestions, or code snippets are more helpful. Use judgment.
- **Primary goal:** Get the best CL possible.
- **Secondary goal:** Improve the developer's skills over time.

### 4.4 Accepting Explanations

If the author explains something you didn't understand in the code:
- **The answer should usually be to rewrite the code more clearly.**
- If the code is inherently complex (e.g., a regex), a code comment explaining WHY is appropriate.
- An explanation only in the review tool doesn't help future readers of the code.

---

## 5. Handling Pushback

### 5.1 Consider You Might Be Wrong

The author is often closer to the code and may have better insight. Ask: "Does their argument make sense from a code health perspective?" If yes, acknowledge they're right and drop the issue.

### 5.2 When You're Right

Further explain your reasoning. Show that you understand their reply but offer additional evidence. "Improving code health tends to happen in small steps." If your suggestion improves code health and the improvement justifies the effort, continue advocating.

Remain polite. Convey that you hear them but simply don't agree.

### 5.3 The "Clean It Up Later" Trap

When the author promises to clean something up later: **this almost never happens.** The later CL gets lost in the press of other work. Insist on cleanup now unless:

- The cleanup is truly independent and the author commits to submitting it immediately
- It's an emergency

If surrounding problems can't be fixed now: file a bug, assign it to the author, add a TODO referencing the bug.

### 5.4 Complaints About Strictness

- "Improving the speed of your code reviews usually causes these complaints to fade away."
- Fast reviews + high standards = sustainable quality. Slow reviews create pressure to cut corners.
- "Sometimes the loudest protesters even become your strongest supporters."

---

*This skill is based on Google's Engineering Practices documentation (google.github.io/eng-practices/), licensed under CC-By 3.0.*
