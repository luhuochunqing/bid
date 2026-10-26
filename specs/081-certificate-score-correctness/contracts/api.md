# 知识库预判更新契约
POST /api/projects/{projectId}/score-parse/estimates/refresh，无body。认证且必须通过ProjectAccessScopeService；403不可读取/修改目标。同步仅更新已生效score_item估分及元数据，返回ApiResponse<ScoreParseItemsDTO>。并发规则切换未能保存时409，不显示更新成功。无评分项400。候选表和score_result零写入。
GET items的ScoreItemDTO新增estimateStatus（FRESH/STALE/NOT_APPLICABLE）与estimatedAt；旧构造器保持兼容。陈旧证书项status=PENDING，estScore=null，kbHit=false，依据明确待更新；汇总不得累计其历史分。
