# changetracking 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
跨模块变更通知桥梁：业务模块发 `EntityChangedEvent`，本模块监听事务提交后事件，计算字段级 diff，并按实体类型触发 DOCUMENT_CHANGE / TASK_UPDATE 等通知给订阅者。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `ChangeDiffPolicy.java` | Core Policy | 字段级 diff 纯函数 |
| `FieldChange.java` | Core DTO | 字段变更 record |
| `EntityChangedEvent.java` | Event | 实体变更领域事件，可携带通知 payload metadata |
| `EntityChangedNotificationListener.java` | Listener | 订阅者扇出 + 按实体类型派发通知 |

## 使用方式

业务模块注入 `ApplicationEventPublisher` 后：

```java
eventPublisher.publishEvent(new EntityChangedEvent(
    "DOCUMENT", sectionId, actorId, beforeSnapshot, afterSnapshot, section.getTitle()
));
```

本模块监听 `@TransactionalEventListener(AFTER_COMMIT)`，在事务成功提交后才触发通知，避免业务模块直接耦合 notification 模块。
