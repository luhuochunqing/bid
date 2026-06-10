# systems 模块

一旦我所属的文件夹有所变化，请更新我。

## 说明

外部系统集成模块，提供统一组织架构系统拉取本系统菜单列表等能力。

## 目录结构

```
systems/
└── external/          # 外部系统对接
    ├── ExternalMenuResponse.java
    ├── ExternalMenuService.java
    ├── ExternalMenuTreeNode.java
    ├── SystemsExternalMenuController.java
    └── package-info.java
```

## 文件清单

| 文件 | 功能 |
|------|------|
| external/ExternalMenuResponse.java | 外部菜单响应 DTO |
| external/ExternalMenuService.java | 外部菜单服务（构建系统菜单树） |
| external/ExternalMenuTreeNode.java | 菜单树节点 |
| external/SystemsExternalMenuController.java | 外部系统菜单接口 Controller |
| external/package-info.java | 包说明文档 |
