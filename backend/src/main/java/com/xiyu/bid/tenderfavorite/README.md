# TenderFavorite 模块 (标讯收藏)

> 一旦我所属的文件夹有所变化，请更新我。

## 功能
提供标讯收藏功能，允许用户收藏感兴趣的标讯。

## 项目结构

```
tenderfavorite/
  entity/TenderFavorite.java          -- 收藏关系 JPA 实体
  repository/TenderFavoriteRepository.java -- 数据访问接口
  dto/TenderFavoriteDTO.java          -- 收藏 DTO
  service/TenderFavoriteService.java  -- 业务逻辑服务
  controller/TenderFavoriteController.java -- REST API
```

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/tender-favorites/{tenderId}` | 切换收藏 |
| GET | `/api/tender-favorites/ids` | 获取当前用户收藏的所有标讯ID |
| GET | `/api/tender-favorites` | 分页获取收藏标讯列表 |
| DELETE | `/api/tender-favorites/{tenderId}` | 取消收藏 |
| GET | `/api/tender-favorites/check/{tenderId}` | 检查是否已收藏 |
