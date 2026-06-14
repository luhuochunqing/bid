---
name: linear
description: |
  Use Symphony's `linear_graphql` client tool for raw Linear GraphQL
  operations (comment editing, state transitions, PR attachment).
  Adapted from openai/symphony@main/.codex/skills/linear/SKILL.md for
  xiyu-bid-poc — all operations go through Symphony's app-server tool,
  not direct API calls.
---

# Linear GraphQL (Symphony)

Use `linear_graphql` tool exposed by Symphony's app-server session.
Tool reuses the host's `LINEAR_API_KEY` auth.

## Tool input

```json
{
  "query": "query or mutation document",
  "variables": { "optional": "graphql variables object" }
}
```

- One GraphQL operation per tool call.
- Top-level `errors` array = failed operation (even if call returned).
- Keep queries narrowly scoped; ask only for the fields you need.

## Common workflows

### Query issue by key

```graphql
query IssueByKey($key: String!) {
  issue(id: $key) {
    id
    identifier
    title
    state { id name type }
    project { id name slug }
    labels { nodes { id name } }
    url
    description
    updatedAt
  }
}
```

### Move issue to different state

```graphql
mutation MoveIssueToState($id: String!, $stateId: String!) {
  issueUpdate(id: $id, input: { stateId: $stateId }) {
    success
    issue { id identifier state { name } }
  }
}
```

For state transitions, fetch team states first and use exact `stateId`
(do not hardcode names inside mutations).

### Create / update workpad comment

```graphql
mutation CreateComment($issueId: String!, $body: String!) {
  commentCreate(input: { issueId: $issueId, body: $body }) {
    success
    comment { id url }
  }
}

mutation UpdateComment($id: String!, $body: String!) {
  commentUpdate(id: $id, input: { body: $body }) {
    success
    comment { id }
  }
}
```

### Attach Gitee PR (not GitHub!)

```graphql
mutation AttachGiteePR($issueId: String!, $url: String!, $title: String) {
  attachmentLinkURL(
    issueId: $issueId
    url: $url
    title: $title
  ) {
    success
    attachment { id title url }
  }
}
```

`attachmentLinkURL` is generic; for xiyu-bid-poc we always link the Gitee
PR URL. Title format: `agent/symphony/<branch> — <Linear issue ID>`.

### Check required labels

```graphql
query IssueLabels($id: String!) {
  issue(id: $id) {
    labels { nodes { name } }
  }
}
```

Verify the issue has `symphony-eligible` label before any implementation
work. If missing, stop and move issue to `Human Review`.

## Usage rules

- Use `linear_graphql` for ALL Linear operations. Do not write shell scripts
  that call `https://api.linear.app/...` directly.
- Prefer narrowest issue lookup: `issue(id: $key)` → identifier search →
  internal id.
- All comments posted via Symphony must be prefixed with `[symphony]` so
  humans can distinguish them from human/Integrator comments.
- Do not introduce new raw-token shell helpers for GraphQL access.
