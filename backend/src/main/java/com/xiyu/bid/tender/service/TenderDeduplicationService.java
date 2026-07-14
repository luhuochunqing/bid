package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.TenderDuplicateException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.core.TenderDeduplicationPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标讯去重服务（应用服务）。
 * 按招标主体查询已有标讯后使用 {@link TenderDeduplicationPolicy} 过滤重复项。
 * 本类只做查询与异常抛出的编排。
 *
 * <p><b>入口覆盖说明</b>：本服务覆盖人工录入、批量导入、第三方平台推送三条路径，
 * 确保全链路去重维度一致（招标主体+项目类型+报名截止+开标时间）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenderDeduplicationService {

    private final TenderRepository tenderRepository;

    /**
     * 查找与给定标讯重复的已有标讯。
     * 招标主体为空时直接返回空列表。
     *
     * @param tender 待检查的标讯
     * @return 重复标讯列表（可能为空）
     */
    public List<Tender> findDuplicates(Tender tender) {
        return findDuplicates(tender.getPurchaserName(), tender.getProjectType(),
                tender.getRegistrationDeadline(), tender.getBidOpeningTime());
    }

    /**
     * 按字段查找重复标讯（避免调用方构造探针实体）。
     *
     * @param purchaserName     招标主体
     * @param projectType       项目类型（可为 null）
     * @param registrationDeadline 报名截止时间
     * @param bidOpeningTime    开标时间
     * @return 重复标讯列表（可能为空）
     */
    public List<Tender> findDuplicates(String purchaserName, String projectType,
                                       LocalDateTime registrationDeadline, LocalDateTime bidOpeningTime) {
        if (purchaserName == null || purchaserName.trim().isEmpty()) {
            return List.of();
        }
        return tenderRepository.findByPurchaserNameAllIgnoreCase(purchaserName).stream()
                .filter(t -> TenderDeduplicationPolicy.isDuplicate(
                        purchaserName, projectType, registrationDeadline, bidOpeningTime,
                        t.getPurchaserName(), t.getProjectType(),
                        t.getRegistrationDeadline(), t.getBidOpeningTime()))
                .toList();
    }

    /**
     * 检查标讯是否重复，若有重复则抛出 {@link TenderDuplicateException}。
     *
     * @param tender 待检查的标讯
     * @throws TenderDuplicateException 存在重复标讯时抛出
     */
    public void checkDuplicate(Tender tender) {
        var duplicates = findDuplicates(tender);
        if (!duplicates.isEmpty()) {
            log.warn("Duplicate tender detected for purchaser={}, deadline={}, bidOpenTime={}, count={}",
                    tender.getPurchaserName(), tender.getRegistrationDeadline(),
                    tender.getBidOpeningTime(), duplicates.size());
            throw new TenderDuplicateException(duplicates);
        }
    }

    /**
     * 按字段检查重复，若存在重复则抛出 {@link TenderDuplicateException}。
     * 适用于不想构造完整 Tender 实体的调用方（如第三方推送路径）。
     *
     * @param purchaserName     招标主体
     * @param projectType       项目类型（可为 null）
     * @param registrationDeadline 报名截止时间
     * @param bidOpeningTime    开标时间
     * @throws TenderDuplicateException 存在重复标讯时抛出
     */
    public void rejectIfDuplicate(String purchaserName, String projectType,
                                  LocalDateTime registrationDeadline, LocalDateTime bidOpeningTime) {
        var duplicates = findDuplicates(purchaserName, projectType, registrationDeadline, bidOpeningTime);
        if (!duplicates.isEmpty()) {
            log.warn("Duplicate tender business key rejected: existingId={}, purchaserName={}, projectType={}, regDeadline={}, bidOpen={}",
                    duplicates.get(0).getId(), purchaserName, projectType, registrationDeadline, bidOpeningTime);
            throw new TenderDuplicateException(duplicates);
        }
    }
}
