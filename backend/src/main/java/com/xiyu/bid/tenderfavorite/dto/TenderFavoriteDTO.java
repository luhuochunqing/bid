package com.xiyu.bid.tenderfavorite.dto;

import com.xiyu.bid.tender.dto.TenderDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标讯收藏 DTO
 * <p>包含收藏信息与标讯详情。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderFavoriteDTO {

    /** 收藏记录ID */
    private Long favoriteId;

    /** 标讯详情 */
    private TenderDTO tender;

    /** 收藏时间 */
    private LocalDateTime favoritedAt;
}
