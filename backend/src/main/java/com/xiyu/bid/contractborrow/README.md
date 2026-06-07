# Contract Borrow Module

> 一旦我所属的文件夹有所变化，请更新我。

`contractborrow` owns the standalone contract borrowing workflow.

## Responsibilities

- Store contract borrow applications as the workflow source of truth.
- Store immutable lifecycle events for submit, approve, reject, return, and cancel actions.
- Protect lifecycle transitions with optimistic locking so concurrent actions fail clearly.
- Serve list filtering and pagination from the repository layer instead of in-memory filtering.
- Keep pure lifecycle decisions in `domain`.
- Keep use-case orchestration in `application.service`.
- Keep JPA persistence details in `infrastructure.persistence`.
- Keep HTTP request and response adaptation in `controller`.

## Boundaries

- Do not use approval requests as the primary contract borrow table.
- Do not write borrow status into template or document assembly tables.
- Do not place repository, HTTP, or clock dependencies in `domain`.

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `domain/model/ContractBorrowApplication.java` | Domain Model | Contract borrow state snapshot and derived overdue status |
| `domain/service/ContractBorrowLifecyclePolicy.java` | Pure Policy | Approval, rejection, return, and cancellation decisions |
| `domain/valueobject/` | Value Object | Contract borrow status and event type enums |
| `application/command/` | Command Model | Use-case input records |
| `application/service/` | Application Service | Workflow orchestration and view assembly |
| `application/view/` | View Model | API response records |
| `controller/` | Controller | Contract borrow REST API and request records |
| `infrastructure/persistence/` | Persistence | Versioned JPA entities, repositories, and query specifications |
