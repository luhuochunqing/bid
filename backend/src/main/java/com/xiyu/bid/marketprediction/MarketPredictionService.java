package com.xiyu.bid.marketprediction;

import com.xiyu.bid.crm.application.CrmMessageService;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.marketprediction.domain.MarketPredictionPolicy;
import com.xiyu.bid.marketprediction.domain.MarketPredictionResult;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 商机时间预测服务 - Application Service
 *
 * 职责:
 * 1. 协调数据获取和策略执行
 * 2. 处理事务边界
 * 3. 记录预测日志
 *
 * 纯核心: MarketPredictionPolicy
 * 副作用: 数据库读取、日志
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MarketPredictionService {

    private final TenderRepository tenderRepository;
    private final MarketPredictionPolicy predictionPolicy;
    private final CrmMessageService crmMessageService;

    /**
     * 根据业主哈希获取商机预测
     *
     * @param purchaserHash 业主单位哈希
     * @return 预测结果，可能为空
     */
    public Optional<MarketPredictionResult> predictByPurchaserHash(String purchaserHash) {
        if (purchaserHash == null || purchaserHash.isBlank()) {
            log.debug("Purchaser hash is empty, skipping prediction");
            return Optional.empty();
        }

        log.debug("Fetching prediction for purchaser hash: {}", purchaserHash);

        // 纯核心: 收集历史数据
        List<Tender> historicalTenders = tenderRepository
                .findByPurchaserHashOrderByPublishDateDesc(purchaserHash);

        if (historicalTenders.size() < predictionPolicy.minimumHistoricalCount()) {
            log.info("Insufficient historical data for purchaser hash: {}, count: {}",
                    purchaserHash, historicalTenders.size());
            return Optional.of(MarketPredictionResult.insufficientData());
        }

        // 纯核心: 执行预测
        MarketPredictionResult result = predictionPolicy.predict(historicalTenders);

        log.info("Prediction result for purchaser hash {}: nextDate={}, confidence={}, historicalCount={}",
                purchaserHash, result.nextTenderDate(), result.confidence(), result.historicalCount());

        return Optional.of(result);
    }

    /**
     * 获取预测所需的最低历史数据条数
     */
    public int getMinimumHistoricalCount() {
        return predictionPolicy.minimumHistoricalCount();
    }

    /**
     * 批量获取预测
     *
     * @param purchaserHashes 业主哈希列表
     * @return 每个哈希对应的预测结果
     */
    public List<MarketPredictionDTO> batchPredict(List<String> purchaserHashes) {
        return purchaserHashes.stream()
                .map(hash -> {
                    Optional<MarketPredictionResult> opt = predictByPurchaserHash(hash);
                    return opt.isPresent()
                            ? new MarketPredictionDTO(hash, opt.get())
                            : MarketPredictionDTO.noData(hash);
                })
                .toList();
    }

    /**
     * 将商机预测结果推送到 CRM 事件库（企微+站内信），让用户感知 AI 在工作。
     *
     * 降级策略：推送失败不影响主流程，仅记录日志并返回 false。
     *
     * @param purchaserHash 业主单位哈希
     * @param recipientNos  接收人工号列表
     * @return true 推送成功；false 推送失败或无可推送预测
     */
    public boolean pushPredictionToCrm(String purchaserHash, List<String> recipientNos) {
        if (purchaserHash == null || purchaserHash.isBlank()) {
            log.debug("Purchaser hash is empty, skipping CRM push");
            return false;
        }

        Optional<MarketPredictionResult> opt = predictByPurchaserHash(purchaserHash);
        if (opt.isEmpty()) {
            log.info("No prediction available for purchaser hash: {}, skipping CRM push", purchaserHash);
            return false;
        }

        MarketPredictionResult result = opt.get();
        if (result.nextTenderDate() == null) {
            log.info("Prediction has no next tender date for purchaser hash: {}, skipping CRM push",
                    purchaserHash);
            return false;
        }

        String title = "AI 商机预测提醒 - " + purchaserHash;
        String content = buildPushContent(result);

        try {
            CrmResponseHandler.CrmApiResponse response = crmMessageService.sendMessage(
                    recipientNos, title, content, 1);
            if (response.success()) {
                log.info("CRM push succeeded for purchaser hash: {}", purchaserHash);
                return true;
            }
            log.warn("CRM push failed for purchaser hash: {}, code={}, msg={}",
                    purchaserHash, response.code(), response.msg());
            return false;
        } catch (RuntimeException e) {
            log.warn("CRM push degraded for purchaser hash: {}, error: {}",
                    purchaserHash, e.getMessage());
            return false;
        }
    }

    private String buildPushContent(MarketPredictionResult result) {
        return String.format(
                "AI 预测到下次招标时间: %s, 置信度: %.2f, 历史数据: %d 条, 平均间隔: %.0f 天, 说明: %s",
                result.nextTenderDate(),
                result.confidence(),
                result.historicalCount(),
                result.averageIntervalDays(),
                result.note());
    }
}
