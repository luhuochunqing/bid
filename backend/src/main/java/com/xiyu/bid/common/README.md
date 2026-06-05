一旦我所属的文件夹有所变化，请更新我。

# common

共享领域模型和值对象，与具体业务模块无关。

## 文件清单

| 文件 | 功能 |
|------|------|
| `domain/PagedResult.java` | 分页结果记录类型 |

## 设计原则

- 纯 Java record，无框架依赖
- 避免污染业务 domain 层，保持 FP-Java Profile 纯核心约束
- 可被所有业务模块的 domain/port 层安全引用
