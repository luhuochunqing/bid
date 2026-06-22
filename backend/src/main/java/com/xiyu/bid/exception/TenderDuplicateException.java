package com.xiyu.bid.exception;

import com.xiyu.bid.entity.Tender;
import lombok.Getter;

import java.util.List;

/**
 * 标讯重复异常。
 * 用于在检测到重复标讯时携带重复标讯列表返回给前端。
 */
@Getter
public class TenderDuplicateException extends BusinessException {

    private final transient List<Tender> duplicates;

    public TenderDuplicateException(List<Tender> duplicates) {
        super(400, "投标管理系统该标讯已存在");
        this.duplicates = List.copyOf(duplicates);
    }
}
