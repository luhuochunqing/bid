# Semantic boundary 技术债：逐方法拆分清单

基线：`e49dcc4e37a01a17244b6d5627b1a2e548d984ea`（origin/main，2026-11-10）。本清单是实施提案，本清单已按用户批准实施迁移；能力收口的差异另见规格analysis。

## 统计与证据口径

源文件 `backend/src/test/java/com/xiyu/bid/compliance/domain/policy/Quality1360UnblockTest.java` 共 3387 行。按完整限定名与简写注解静态提取，共 **151 个测试方法**，不是 92 个。初步ValueSource静态提取为685槽，漏掉MethodSource/CsvSource；实施时JUnit实跑为 **773项，0失败/错误/跳过**，现以Surefire XML更正各行计数，不含方法内断言循环乘积。最初提交 `8cc0cb008` 中为 9 个测试方法，`b0d4ee849` 时为 38 个；“最初84例”需要对应 Surefire XML 才能确认。

当前文件头注释自称真实场景，但不等于每条字符串均有原件来源。真实样本晋升契约须补：项目/报告ID、原件hash、页码、脱敏摘录、预期依据。未获这些证据前，称“业务契约候选”，不得称已认证真实样本。

## 分类原则

1. 业务契约保留明确业务条件、输入事实与结果；解析与HTML契约继续进入门禁，迁文件不等于降级。
2. 参数化方法按源码数组从1计数；保留 #1，剩余参数完整迁入带 `@Tag("heuristic-corpus")` 的语料类。方法内循环本轮保留，不以拆文件偷偷减少断言。后续如要裁剪循环必须给逐输入差异清单。
3. 整方法标注迁移的，所有输入和断言原样保留。选择迁移而非删除，便于追溯。
4. 语料类的旧断言是历史观察；跨句能力撤回时，其失败须报告为已批准能力收口的差异，不能据此扩正则。明确同句缺陷、直接自认未持证、授权缺项、证据不定位不得因迁移而放宽。
5. 业务契约中若后来发现仅靠跨句推断的断言，先记录差异并调整归类，不能改期望假装纯重构。代表参数 #1 只是可复现的首轮裁剪选择，不宣称覆盖所有语义分支；补充代表输入必须有主路径需求或真实样本依据。

## 分配汇总

- 业务契约（门禁）：67 个原方法。
- 启发式语料（非门禁）：62 个原方法。
- HTML契约（门禁）：12 个原方法。
- 解析契约（门禁）：10 个原方法。

## 完整清单（151/151）

所有目标测试类均位于源测试相同 package；行号固定于上述基线。参数剩余部分迁到语料类的同名方法，避免重复命名歧义。

| 原方法（基线行） | 基线JUnit调用数 | 处置 / 目标类 | 留存与迁移粒度 | 理由 |
|---|---:|---|---|---|
| `fullScoringFieldBlankRetainsOnlyLocatedFormalFail`（27） | 3 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 仅参数 #1 "项目经理业绩"；其余参数迁语料 | 明确对象、证据/适用性或原有业务放行与反例 |
| `nonPlanScoringAdmissionRetainsLocatedFail`（46） | 2 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 仅参数 #1 "项目经理业绩证明"；其余参数迁语料 | 明确对象、证据/适用性或原有业务放行与反例 |
| `certificateSampleDefectsCannotContaminateFormalStatement`（60） | 3 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 仅参数 #1 "本公司未取得ISO9001认证。"；其余参数迁语料 | 明确对象、证据/适用性或原有业务放行与反例 |
| `formattedBusinessSampleHeadingsOwnSamePageAndFollowingPageBlanks`（76） | 4 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `filledReferencesAndParenthesesCannotProveScoringDefect`（101） | 5 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 仅参数 #1 "技术方案：（详见附件一）"；其余参数迁语料 | 明确对象、证据/适用性或原有业务放行与反例 |
| `scoringExampleScopeSurvivesPaginationAndEndsAtFormalBody`（111） | 2 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `scoringFailWithLocatedConcreteDefectIsPreserved`（128） | 7 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 仅参数 #1 "本次必填技术方案：____"；其余参数迁语料 | 明确对象、证据/适用性或原有业务放行与反例 |
| `scoringFailWithoutConcreteDefectRemainsManual`（140） | 3 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 仅参数 #1 "合同样例\n技术方案：____"；其余参数迁语料 | 明确对象、证据/适用性或原有业务放行与反例 |
| `unrelatedPageTextCannotEraseLocatedScoringDefect`（158） | 7 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `locatedQuoteDoesNotEscapeItsTemplateContext`（178） | 9 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 仅参数 #1 "合同样例\n技术方案：____"；其余参数迁语料 | 明确对象、证据/适用性或原有业务放行与反例 |
| `ungroundedScoringQuoteCannotRestoreFail`（188） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `signatureRequirementAlongsidePositiveObservationKeepsPass`（202） | 3 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 仅参数 #1 "本授权委托书须签字盖章后生效"；其余参数迁语料 | 明确对象、证据/适用性或原有业务放行与反例 |
| `bareCertificateDefectHasSameVerdictAcrossPages`（220） | 16 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `unrelatedAuthorizationStampCannotSignGrant`（252） | 3 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 仅参数 #1 "签名：无"；其余参数迁语料 | 明确对象、证据/适用性或原有业务放行与反例 |
| `certificateAuthenticityContradictionCannotPass`（274） | 9 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 仅参数 #1 "该证书为伪造证书"；其余参数迁语料 | 明确对象、证据/适用性或原有业务放行与反例 |
| `unknownCertificateStatusRequiresReviewDespiteMatchedKnowledge`（305） | 24 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 仅参数 #1 "本公司具有ISO9001认证（证书编号：ABCD1234）但系借用"；其余参数迁语料 | 明确对象、证据/适用性或原有业务放行与反例 |
| `omittedCertificateObjectUnknownStateCannotPassAcrossSentenceOrPage`（331） | 56 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `knownCertificateObjectPrefixesPreserveMatchedPass`（371） | 5 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `containingMandatoryConditionCannotResolveUnknownCertificateIdentity`（387） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `unresolvedContainedCertificateDefectsCannotFailKnownBidderCondition`（396） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `combinedIsoMappingPreservesApplicabilityButExactIdentityTakesPriority`（417） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `headinglessHtmlRequirementIsCleanedBeforeFusionAndMatching`（429） | 1 | HTML契约（门禁） / `QualityHtmlEvidenceContractTest` | 全部断言原样保留 | 单次解码、比较符和原位证据保护 |
| `headinglessEntitiesAreDecodedOnceAndKeepOriginalEvidence`（445） | 1 | HTML契约（门禁） / `QualityHtmlEvidenceContractTest` | 全部断言原样保留 | 单次解码、比较符和原位证据保护 |
| `encodedMarkupSurvivesParagraphClassification`（459） | 1 | HTML契约（门禁） / `QualityHtmlEvidenceContractTest` | 全部断言原样保留 | 单次解码、比较符和原位证据保护 |
| `encodedMarkupSurvivesTableAndRequirementsReconstruction`（469） | 1 | HTML契约（门禁） / `QualityHtmlEvidenceContractTest` | 全部断言原样保留 | 单次解码、比较符和原位证据保护 |
| `legacyRequirementsDecodeOnceBeforeReconstruction`（484） | 1 | HTML契约（门禁） / `QualityHtmlEvidenceContractTest` | 全部断言原样保留 | 单次解码、比较符和原位证据保护 |
| `legacyRequirementsKeepRawQuoteAcrossReconstruction`（492） | 1 | HTML契约（门禁） / `QualityHtmlEvidenceContractTest` | 全部断言原样保留 | 单次解码、比较符和原位证据保护 |
| `vetoHtmlMatchesRetainActualSubstantivePageAndRawQuotes`（502） | 1 | HTML契约（门禁） / `QualityHtmlEvidenceContractTest` | 全部断言原样保留 | 单次解码、比较符和原位证据保护 |
| `tableEvidenceLocatesCleanedBrAndNumericEntities`（530） | 1 | HTML契约（门禁） / `QualityHtmlEvidenceContractTest` | 全部断言原样保留 | 单次解码、比较符和原位证据保护 |
| `htmlCleaningPreservesQualificationComparisons`（542） | 1 | HTML契约（门禁） / `QualityHtmlEvidenceContractTest` | 全部断言原样保留 | 单次解码、比较符和原位证据保护 |
| `htmlEntitiesPreserveCertificateIdentity`（550） | 1 | HTML契约（门禁） / `QualityHtmlEvidenceContractTest` | 全部断言原样保留 | 单次解码、比较符和原位证据保护 |
| `structuredRequirementsDoNotSufferDoubleEntityDecodingInMatching`（564） | 1 | HTML契约（门禁） / `QualityHtmlEvidenceContractTest` | 全部断言原样保留 | 单次解码、比较符和原位证据保护 |
| `htmlCleaningDecodesEntitiesWithoutConsumingComparisons`（577） | 1 | HTML契约（门禁） / `QualityHtmlEvidenceContractTest` | 全部断言原样保留 | 单次解码、比较符和原位证据保护 |
| `tocAndLaterSectionsCannotPolluteQualificationConditions`（587） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `parentConditionSubjectWithAffiliateParentheticalIsMandatoryBidder`（618） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `certificateClauseWithoutBidderContextRetainsTargetUnknown`（628） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `certificateCategoryWithoutHolderContextRequiresManualConfirmation`（636） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `unrelatedOrEmptyKnowledgeQuotesAreExcludedFromCertificateEvidence`（654） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `genericKnowledgeQuotesCannotIdentifyRequiredCertificate`（669） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `shortCertificateCodesCannotIdentifyRequiredCertificate`（688） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `certificateEvidenceMustCoverEveryRequiredIdentityCode`（708） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `certificateWithoutEnglishCodeRetainsEvidenceButRequiresBidProof`（733） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `longFieldFragmentsPreserveParsingAndRejectMalformedValues`（746） | 1 | 解析契约（门禁） / `QualityVerdictParsingContractTest` | 全部断言原样保留 | 结构化解析与截断保护，独立于语义启发式 |
| `invalidTopLevelVerdictCannotBorrowNestedStatus`（762） | 1 | 解析契约（门禁） / `QualityVerdictParsingContractTest` | 全部断言原样保留 | 结构化解析与截断保护，独立于语义启发式 |
| `malformedVerdictCannotCombineFieldsAcrossObjects`（774） | 1 | 解析契约（门禁） / `QualityVerdictParsingContractTest` | 全部断言原样保留 | 结构化解析与截断保护，独立于语义启发式 |
| `fieldFragmentRequiresCompleteNonBlankConclusion`（785） | 1 | 解析契约（门禁） / `QualityVerdictParsingContractTest` | 全部断言原样保留 | 结构化解析与截断保护，独立于语义启发式 |
| `truncatedArrayObjectsCannotReplaceCompletedVerdictFields`（807） | 1 | 解析契约（门禁） / `QualityVerdictParsingContractTest` | 全部断言原样保留 | 结构化解析与截断保护，独立于语义启发式 |
| `truncatedArrayStringsAndNestedContainersPreserveOnlyCompletedVerdict`（820） | 1 | 解析契约（门禁） / `QualityVerdictParsingContractTest` | 全部断言原样保留 | 结构化解析与截断保护，独立于语义启发式 |
| `truncatedArraysCannotSupplyMissingTopLevelConclusionOrStatus`（835） | 1 | 解析契约（门禁） / `QualityVerdictParsingContractTest` | 全部断言原样保留 | 结构化解析与截断保护，独立于语义启发式 |
| `truncatedVerdictRequiresCompleteNonBlankConclusion`（846） | 1 | 解析契约（门禁） / `QualityVerdictParsingContractTest` | 全部断言原样保留 | 结构化解析与截断保护，独立于语义启发式 |
| `truncatedOrMalformedLlmResponseIsHealedSuccessfully`（867） | 1 | 解析契约（门禁） / `QualityVerdictParsingContractTest` | 全部断言原样保留 | 结构化解析与截断保护，独立于语义启发式 |
| `authorizationLetterInConditionIsNotExtractedAsCertificateRequirement`（900） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `substantiveCertificateFactsInBidAllowPassForQualificationMatchAndVeto`（908） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `htmlBrTagInTenderRequirementIsSanitizedAndProducesHumanizedRemark`（939） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `certificateNameWithoutBidderRequiresConfirmation`（985） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `bareCertificateNameWithoutFactsStaysManualReview`（1010） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `projectNumberCannotSupplyCertificateFactsOnSamePage`（1030） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `completeBothSidesIdFactsPassIdAuthLetterAndBoundary`（1051） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `warrantyAgreementTemplateDoesNotShortCircuitScoringResponse`（1070） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `differenceTableWithNoDifferencePassesResponsiveness`（1091） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `supplierPenaltyRuleCitationDoesNotTriggerVetoKeyword`（1109） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `documentCompletenessWithTocAndCoreSectionsPasses`（1119） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `tenderWithoutDepositRequirementPassesAsNotApplicable`（1140） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `validMatchedNameReferencesCannotUpgradeManualReview`（1164） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `missingDepositTermsCannotBecomeStructuredExemption`（1186） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `matchedDetailsCannotOverrideUnresolvedApplicabilityOrPlaceholderFailure`（1200） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `exemptionConclusionAloneCannotBypassVision`（1223） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `negatedOrQuotedExemptionsCannotBypassRequiredDepositVision`（1232） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `systemDiscountWithBidderCommitmentRequiresSubmissionReview`（1250） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `authorizationLetterLlmPassCannotOverrideIncompleteFields`（1264） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `completeAuthorizationFieldsPreserveExpiryFailureAndEvidence`（1274） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `completeAuthorizationFieldsPreserveSignatureReviewAndEvidence`（1285） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `tenderRequirementsParserRejectsTruncatedScoringBeforeMerge`（1297） | 1 | 解析契约（门禁） / `QualityVerdictParsingContractTest` | 全部断言原样保留 | 结构化解析与截断保护，独立于语义启发式 |
| `mixedKnownAndUnresolvedCertificatesStayManual`（1319） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `systemPricingMentionCannotProveSubmission`（1333） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `guidelinesAcceptCompleteTranscriptionElements`（1345） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `matchedQualificationWithoutBidderRequiresConfirmation`（1352） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `expiredCertificateInSameClauseCannotPreserveVetoPass`（1363） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `unrelatedStandardCodesElsewhereInBidDoNotDemoteMatchedVetoPass`（1374） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `qualificationTitlesTemplatesAndPartialFactsCannotProveAllApplicableRequirements`（1389） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `ordinaryBodyOrTenderEvidenceCannotProveCompositeQualification`（1405） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `genericSalutationPrefixCannotHideWrongRecipient`（1418） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `genericSalutationsCannotHideRecipientsOfUnlistedOrganizationTypes`（1437） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `completeGenericSalutationsRemainAccepted`（1455） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `truncatedHardRequirementsMustRemainIncomplete`（1469） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `longQualificationMaterialListsCannotProveCompliance`（1478） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `unrelatedOrPartialKnowledgeMatchesCannotProveVetoCompliance`（1491） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `kbRequiresBidderPositiveCertificateStatement`（1505） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `thirdPartyCertificateMaterialsCannotProveBidderCertification`（1526） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `crossSentenceThirdPartyOwnershipBlocksEntryAndFinalBoundary`（1551） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `completeOwnershipSubjectControlsEntryBoundaryAndEngine`（1597） | 11 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `deniedCertificationCannotBeOverriddenByKnowledgeEvidence`（1620） | 6 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `deniedBidderOwnershipCannotBeOverriddenByKnowledgeEvidence`（1653） | 18 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `unrelatedDefectsAndUnexpiredCertificatePreserveKbPass`（1678） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `unrelatedNamedCertificateDefectsDoNotDemoteKbPass`（1724） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `compositeCertificateSingleDefectBlocksKbPass`（1746） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `contradictoryCertificatePageBlocksKbPassRegardlessOfPageOrder`（1762） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `certificatePronounContinuationCannotBypassContradictionCheck`（1783） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `thirdPartyCertificateHoldersRequireReviewAtEntryBoundaryAndEngine`（1814） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `namedCertificateHolderRequiresConfirmedBidderIdentity`（1850） | 9 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `explanatorySentencesCannotEraseCertificateContradictionsAtEntryAndBoundary`（1873） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `engineKeepsExpiredCertificateForReviewAcrossExplanatorySentences`（1892） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `formattedQualificationSummaryPreservesCertificateIdentityThroughEngine`（1907） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `expiredValidityContinuationCannotPassEngine`（1929） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `datedCertificateValidityRequiresConfirmationDespiteValidKbRecord`（1947） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `engineRequiresIdImagesAndCompleteBothSidesTranscription`（1973） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `unrelatedImageCannotProveIdTranscriptionOnAnotherPage`（2000） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `unsignedAuthorizationTranscriptionCannotKeepProposedPass`（2018） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `authorizationTitleAliasesRequireSignatureEvidenceThroughEngine`（2030） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `signedAuthorizationTranscriptionWithImageKeepsProposedPass`（2056） | 1 | 业务契约（门禁） / `Quality1360BusinessContractTest` | 全部断言原样保留 | 明确对象、证据/适用性或原有业务放行与反例 |
| `explicitMissingSignatureFieldCannotPassThroughEngine`（2076） | 14 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `blankOrNegatedSignatureLabelsWithImageCannotKeepPass`（2096） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `blankSignatureCannotConsumeFollowingField`（2131） | 8 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `uncertainSignatureObservationCannotPassThroughEngine`（2168） | 34 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `mixedPositiveAndNegativeSignatureDescriptionsMustBlockPass`（2230） | 38 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `validPositiveVisualSignatureOrStampKeepsPass`（2257） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `ordinaryCertificationExplanationAndWrappedValidityCannotPass`（2349） | 70 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `namedCertificateSwitchesContextBeforeDefect`（2373） | 6 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `otherCertificateOwnershipDoesNotDemoteCurrentCertificate`（2400） | 6 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `exampleHeaderAcrossLinesPreservesManualReview`（2436） | 14 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `legitimateStatementAfterExampleBlockPasses`（2458） | 2 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `legitimateStatementAfterTemplateHeadingPasses`（2484） | 3 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `missingCertificateMaterialDefectPreservesManualReview`（2550） | 44 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `originalAuthorizationDefectCannotBeHiddenByTranscription`（2573） | 4 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `unrelatedBusinessStatusCannotInheritCertificateContext`（2607） | 20 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `intermediateExplanationPageWithDefectCannotPass`（2637） | 3 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `negatedInvalidStatusPreservesValidCertificate`（2664） | 6 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `crossPageExplicitCertificateDefectCannotPass`（2714） | 70 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `certificateMaterialHeadingPreservesPositiveStatement`（2744） | 3 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `certificateWithSlashCodeMatchesPositiveStatement`（2765） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `certificateValidityEndedCannotPass`（2789） | 9 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `certificateMaterialMissingCannotPass`（2815） | 5 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `accompanyingSubmissionStatusControlsEntryBoundaryAndEngine`（2846） | 9 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `unrelatedBusinessDefectsDoNotChangeCertificateStatus`（2876） | 8 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `certificateMaterialLocationCannotClearCertificateContext`（2909） | 11 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `negatedOwnershipAndUnsubmittedCertificateCannotPass`（2939） | 15 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `noCertificateAdmissionCannotBeOverriddenByKnowledgeEvidence`（2969） | 6 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `blankIntermediatePagePreservesCertificateDefectAssociation`（2998） | 8 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `neutralIntermediatePagesPreserveCertificateDefectAssociation`（3030） | 3 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `incidentalBusinessMentionsPreserveCertificateDefectAssociation`（3065） | 4 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `explicitObjectSwitchAcrossIntermediatePagesClearsCertificateAssociation`（3100） | 3 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `blankIntermediatePageDoesNotInheritUnrelatedContext`（3134） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `precedingCertificateDefectSurvivesPrefaceExplanationAndPagination`（3166） | 9 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `namedCertificateSwitchIsIndependentOfPagination`（3202） | 7 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `precedingDefectRetainsIdentityBoundariesAndNegatedStatus`（3238） | 7 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `explicitCertificateDefectAcrossUnrelatedContextBoundaryCannotPass`（3250） | 5 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `ordinaryInstructionSentenceCannotMaskSubsequentCertificateExpiration`（3274） | 1 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `unconfirmedCertificateContinuationTurnsToManualReview`（3297） | 9 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `unconfirmedCertificateContinuationAcrossPagesTurnsToManualReview`（3323） | 3 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `ordinarySectionAfterExampleCannotHideCertificateContradiction`（3351） | 4 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |
| `ordinarySectionAfterExampleRetainsLocatedScoringBlank`（3373） | 4 | 启发式语料（非门禁） / `SemanticHeuristicCorpusTest` | 整方法迁移；不删除 | 跨句/跨页关联或自然语言变形；保留历史观察，不作为扩规则要求 |

## 实施对账（2026-11-10）

原773项与迁移后773项逐方法调用次数完全相等，0失败/错误/跳过。三类契约67+12+10=89，显式语料684；语料含原62整方法及11个参数拆分方法，共73方法。完整迁移/纯结构阶段断言原样；最终行为收口另加4个主路径范围契约，默认契约93项。

### 最终能力收口的断言级归类修正

`certificateSampleDefectsCannotContaminateFormalStatement` 的代表参数#1同时混有“样例隔离”和“该证书有效”的跨句指代断言。前者及明确缺陷保护继续留契约；后者连同两个原分页输入、原预期原样迁入语料方法 `sampleBoundaryContinuationKeepsHistoricalObservation`。此修正不改旧期望，避免与用户撤回跨句能力冲突。最终语料增加1个调用方法，原773输入完整性已在迁移及结构阶段证实。
