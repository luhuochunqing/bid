# Validation
在沙盒backend执行mvn test -Dtest=ResponsivenessReviewPolicyTest，先记录原句RED，再验证真实自述及同页规则不误豁免。后续资格/状态/位置按tasks逐片运行JUnit与Vitest。最终执行关联compliance回归、三架构、npm run build和git diff --check。生产冻结PDF验收另行执行，不把本地测试当上线证明。
