# Quickstart Verification

```bash
cd backend
mvn -Dtest=BasicInfoAndAttachmentRulesTest,DeterministicCaCertificateExtractorTest,QualificationDetailReconcilerTest,QualificationQueryDelegateTest,ScoreTypeClassificationPolicyTest test
mvn -Dtest='*ArchitectureTest' test
git diff --check
```

生产重算、候选激活和业务 UAT 必须在部署后另行授权。
