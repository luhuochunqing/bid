# Qualification 模块 (资质能力模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
资质模块负责资质信息查询与维护，为投标资格核验、资质展示与相关知识沉淀提供统一后端入口。
旧 `/api/knowledge/qualifications` 兼容入口仍走真实 `businessqualification` 应用服务；借阅请求中的字符串 `projectId` 在服务编排层校验并转换为项目访问断言，借阅记录返回前复用项目关联记录可见性纯核心过滤。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/QualificationController.java` | Controller | 资质接口 |
| `service/QualificationService.java` | Service | 资质兼容协议、借阅归还和项目关联记录权限编排 |
| `dto/QualificationDTO.java` | DTO | 资质视图对象 |
