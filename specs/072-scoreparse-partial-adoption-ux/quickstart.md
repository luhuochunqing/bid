# Quickstart / Acceptance

1. Backend: `cd backend && mvn -Dtest=ScoreParseReviewAppServiceTest test`
2. Frontend: `npm run test:unit -- src/views/Project/stages/components/ScoreCandidateReviewCard.spec.js`
3. Expected: 91/100 can be manually adopted and stored as 91; 101/100, conflict, unknown multi-table total, and out-of-scheme facts remain blocked.
4. Expected UI: state says “候选合计 91 分，原文声明 100 分，尚差 9 分；核对后可人工采纳”；known source/dimension/weight issues are shown in Chinese without machine identifiers or JSON.
5. Real-site acceptance after deployment: reparse project 1384 DOCX, inspect all candidates, confirm adoption is enabled, manually adopt and verify saved scores and 9-point gap audit. PR/local results do not substitute for this step.
