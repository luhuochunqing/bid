package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.TenderDuplicateException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.core.TenderDeduplicationPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 标讯去重服务（应用服务）。
 * 按招标主体查询已有标讯后使用 {@link TenderDeduplicationPolicy} 过滤重复项。
 * 本类只做查询与异常抛出的编排。
 *
 * <p><b>入口覆盖说明</b>：本服务覆盖人工录入、批量导入、第三方平台推送三条路径。
 * 第三方平台推送路径 {@code TenderIntegrationCommandService.rejectDuplicateBusinessTender}
 * 也通过本服务进行去重判定，确保全链路去重维度一致。
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
        var purchaserName = tender.getPurchaserName();
        if (purchaserName == null || purchaserName.trim().isEmpty()) {
            return List.of();
        }
        var existing = tenderRepository.findByPurchaserNameAllIgnoreCase(purchaserName);
        return existing.stream()
                .filter(t -> TenderDeduplicationPolicy.isDuplicate(
                        tender.getPurchaserName(),
                        tender.getProjectType(),
                        tender.getRegistrationDeadline(),
                        tender.getBidOpeningTime(),
                        t.getPurchaserName(),
                        t.getProjectType(),
                        t.getRegistrationDeadline(),
                        t.getBidOpeningTime()))
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
}
