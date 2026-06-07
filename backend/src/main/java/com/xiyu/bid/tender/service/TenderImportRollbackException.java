// Input: 携带失败行错误详情的 TenderImportResultDTO
// Output: 触发 @Transactional 全量回滚，并由 controller 捕获后转 200 响应
// Pos: service/内部信号异常
// 维护声明: 仅作 service ↔ controller 之间的私有协议，禁止在 GlobalExceptionHandler 中处理；切勿在外层服务向上抛出。
package com.xiyu.bid.tender.service;

import com.xiyu.bid.tender.dto.TenderImportResultDTO;
import lombok.Getter;

/**
 * 内部信号异常：当 Excel 行级校验失败时由 {@link TenderImportService} 抛出，
 * 用于触发 Spring 的 {@code @Transactional} 整批回滚，
 * 同时由 {@code TenderController} 捕获后转换为 HTTP 200 + 错误清单响应。
 */
@Getter
public class TenderImportRollbackException extends RuntimeException {

    private final transient TenderImportResultDTO result;

    public TenderImportRollbackException(TenderImportResultDTO result) {
        super("Tender import rolled back: " + (result == null ? 0 : result.getFailureCount()) + " row(s) failed");
        this.result = result;
    }
}
