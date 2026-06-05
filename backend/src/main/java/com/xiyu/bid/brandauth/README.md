一旦我所属的文件夹有所变化，请更新我。

# brandauth

供应商品牌授权管理，含到期预警与投标联动。

## 文件清单

| 文件 | 功能 |
|------|------|
| `domain/` | 品牌授权值对象、到期判定策略、仓储接口 |
| `application/` | 创建、更新、删除、列表、授权到期扫描等用例编排 |
| `infrastructure/` | JPA 实体、仓储适配、REST 控制器 |

## 提醒

- 授权到期提醒由 `AuthorizationExpiryScanAppService` 每日 09:00 扫描产生
