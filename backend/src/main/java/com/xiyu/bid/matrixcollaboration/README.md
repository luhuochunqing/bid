# Matrix Collaboration Module

一旦我所属的文件夹有所变化，请更新我。

本模块承载“立体矩阵式协作权限体系”的模块边界说明。协作成员与 CRM 客户权限镜像实体、仓库已收口到本模块；历史 `security` 下的控制器与服务仍作为鉴权边界适配层保留。

## 文件

| 文件 | 功能 |
| --- | --- |
| `README.md` | 说明矩阵协作权限模块边界、当前历史包兼容状态与后续迁移口径。 |
| `entity/CrmCustomerPermission.java` | CRM 客户权限镜像实体。 |
| `entity/ProjectMember.java` | 项目协作成员实体。 |
| `repository/CrmCustomerPermissionRepository.java` | CRM 客户权限镜像数据访问。 |
| `repository/ProjectMemberRepository.java` | 项目协作成员数据访问。 |
