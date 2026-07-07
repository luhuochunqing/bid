package com.xiyu.bid.tender.core;

/**
 * CO-501 第二步：本地招标主体一致性校验（纯核心）。
 *
 * <p>规则：
 * <ul>
 *   <li>标讯招标主体为空 → 直接允许关联</li>
 *   <li>标讯招标主体有值 → 必须等于商机集团名称或商机招标主体名称其中之一，否则拒绝</li>
 * </ul>
 *
 * <p>无副作用，不依赖数据库或外部服务；受 {@code FPJavaArchitectureTest} 门禁保护。
 */
public final class TenderSubjectConsistencyPolicy {

    /** 校验未通过时的标准错误提示（CO-501 原文）。 */
    public static final String REJECT_MESSAGE = "招标主体不一致，请到 CRM 中修改";

    private TenderSubjectConsistencyPolicy() { /* utility */ }

    /**
     * 校验标讯招标主体与商机记录的一致性。
     *
     * @param purchaserName       标讯的招标主体名称（purchaserName）
     * @param chanceGroupName     商机的集团名称（CRM CustomerChanceVO.groupName）
     * @param chanceTenderSubject 商机的招标主体名称（CRM CustomerChanceVO.tenderSubject）
     * @return 校验结果
     */
    public static Result check(String purchaserName, String chanceGroupName, String chanceTenderSubject) {
        if (purchaserName == null || purchaserName.isBlank()) {
            return Result.ok();
        }
        boolean matchesGroup = purchaserName.equals(chanceGroupName);
        boolean matchesSubject = purchaserName.equals(chanceTenderSubject);
        if (matchesGroup || matchesSubject) {
            return Result.ok();
        }
        return Result.rejected(REJECT_MESSAGE);
    }

    /**
     * 校验结果。
     */
    public record Result(boolean allowed, String errorMessage) {

        public static Result ok() {
            return new Result(true, null);
        }

        public static Result rejected(String errorMessage) {
            return new Result(false, errorMessage);
        }
    }
}
