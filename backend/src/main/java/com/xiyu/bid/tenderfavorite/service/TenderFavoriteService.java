package com.xiyu.bid.tenderfavorite.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.service.TenderMapper;
import com.xiyu.bid.tenderfavorite.dto.TenderFavoriteDTO;
import com.xiyu.bid.tenderfavorite.entity.TenderFavorite;
import com.xiyu.bid.tenderfavorite.repository.TenderFavoriteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 标讯收藏服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TenderFavoriteService {

    private final TenderFavoriteRepository tenderFavoriteRepository;
    private final TenderRepository tenderRepository;
    private final TenderMapper tenderMapper;

    /**
     * 切换收藏状态（已收藏则取消，未收藏则添加）
     *
     * @param userId   当前用户ID
     * @param tenderId 标讯ID
     * @return true=已收藏, false=已取消收藏
     */
    @Transactional
    public boolean toggleFavorite(Long userId, Long tenderId) {
        // 确保标讯存在
        if (!tenderRepository.existsById(tenderId)) {
            throw new ResourceNotFoundException("Tender", tenderId.toString());
        }

        var existing = tenderFavoriteRepository.findByUserIdAndTenderId(userId, tenderId);
        if (existing.isPresent()) {
            // 已收藏 → 取消收藏
            tenderFavoriteRepository.delete(existing.get());
            log.info("User {} removed favorite for tender {}", userId, tenderId);
            return false;
        } else {
            // 未收藏 → 添加收藏
            TenderFavorite favorite = TenderFavorite.builder()
                    .userId(userId)
                    .tenderId(tenderId)
                    .build();
            tenderFavoriteRepository.save(favorite);
            log.info("User {} added favorite for tender {}", userId, tenderId);
            return true;
        }
    }

    /**
     * 判断用户是否已收藏某标讯
     */
    public boolean isFavorited(Long userId, Long tenderId) {
        return tenderFavoriteRepository.existsByUserIdAndTenderId(userId, tenderId);
    }

    /**
     * 获取用户收藏的所有标讯ID
     */
    public List<Long> getFavoriteTenderIds(Long userId) {
        return tenderFavoriteRepository.findByUserId(userId).stream()
                .map(TenderFavorite::getTenderId)
                .toList();
    }

    /**
     * 分页获取收藏标讯列表（含标讯详情），按收藏时间降序
     */
    public Page<TenderFavoriteDTO> getFavoriteTenders(Long userId, Pageable pageable) {
        Page<TenderFavorite> favoritePage = tenderFavoriteRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable);

        if (favoritePage.isEmpty()) {
            return Page.empty(pageable);
        }

        // 批量查询标讯详情
        Set<Long> tenderIds = favoritePage.getContent().stream()
                .map(TenderFavorite::getTenderId)
                .collect(Collectors.toSet());

        Map<Long, Tender> tenderMap = tenderRepository.findAllById(tenderIds).stream()
                .collect(Collectors.toMap(Tender::getId, t -> t));

        // 组装 DTO
        List<TenderFavoriteDTO> dtos = favoritePage.getContent().stream()
                .map(fav -> {
                    Tender tender = tenderMap.get(fav.getTenderId());
                    if (tender == null) {
                        return null;
                    }
                    return TenderFavoriteDTO.builder()
                            .favoriteId(fav.getId())
                            .tender(tenderMapper.toDTO(tender))
                            .favoritedAt(fav.getCreatedAt())
                            .build();
                })
                .filter(dto -> dto != null)
                .toList();

        return new PageImpl<>(dtos, pageable, favoritePage.getTotalElements());
    }

    /**
     * 取消收藏
     */
    @Transactional
    public void removeFavorite(Long userId, Long tenderId) {
        tenderFavoriteRepository.deleteByUserIdAndTenderId(userId, tenderId);
        log.info("User {} removed favorite for tender {} (explicit)", userId, tenderId);
    }

    /**
     * 获取用户收藏总数
     */
    public long getFavoriteCount(Long userId) {
        return tenderFavoriteRepository.countByUserId(userId);
    }
}
