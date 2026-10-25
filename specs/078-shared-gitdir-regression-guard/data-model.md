# 数据模型

本功能不新增业务实体或持久化数据。校验只处理两个瞬时值：

| 值 | 来源 | 约束 |
|---|---|---|
| worktree path | 受管目录的直接子目录 | 必须是可解析的 Git worktree 或明确失败 |
| canonical common gitdir | `git rev-parse --path-format=absolute --git-common-dir` 后物理化 | 所有受管目录必须等于期望值 |
