# Quickstart: 验证评分证据修复

## 后端定向测试

```bash
cd backend
mvn -Dtest=KnowledgeCategoryPolicyTest,ProjectEvidenceRequirementPolicyTest,EstimatedScoreServiceTest test
```

预期：金额下限提取正确；项目 1117 同类条款即使返回大量候选也保持待确认；简单可核验条款保持原计分。

## 前端定向测试

```bash
npx vitest run src/views/Project/stages/components/ScoreItemDetailModal.spec.js
```

预期：详情展示中文来源和准确数据源标签，用户可见文本不含内部英文枚举。

## 构建检查

```bash
cd backend && mvn -DskipTests compile
npm run build
```

## 线上只读复核

打开项目 1117 的评分解析详情只读观察既有结果。未部署前仍会显示旧结果，不得据此判定本地修复失败或声称线上已修复。部署与重算需要单独授权。
