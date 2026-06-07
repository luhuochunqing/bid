package com.xiyu.bid.tendersource.application;

import com.xiyu.bid.tendersource.domain.TenderSourceConnectionResult;
import com.xiyu.bid.tendersource.domain.TenderSourceConnectionTestPolicy;
import com.xiyu.bid.tendersource.dto.TenderSourceTestRequest;
import com.xiyu.bid.tendersource.dto.TenderSourceTestResponse;
import org.springframework.stereotype.Service;

/**
 * 标讯源连接测试服务（Imperative Shell）
 * 
 * 负责：
 * - 参数验证与边界转换
 * - 调用纯核心策略
 * - 构建响应DTO
 */
@Service
public class TenderSourceConnectionTestService {

    public TenderSourceTestResponse testConnection(TenderSourceTestRequest request) {
        if (request == null) {
            return TenderSourceTestResponse.failure("请求参数不能为空");
        }

        TenderSourceConnectionResult result = TenderSourceConnectionTestPolicy.testThirdPartyConnection(
                request.getPlatform(),
                request.getApiEndpoint(),
                request.getApiKey()
        );

        if (result.isSuccess()) {
            return TenderSourceTestResponse.success();
        } else {
            return TenderSourceTestResponse.failure(result.getMessage());
        }
    }
}
