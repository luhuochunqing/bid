package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.exception.TenderDuplicateException;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenderDeduplicationService")
class TenderDeduplicationServiceTest {

    @Mock
    private TenderRepository tenderRepository;

    private TenderDeduplicationService sut;

    @BeforeEach
    void setUp() {
        sut = new TenderDeduplicationService(tenderRepository);
    }

    @Test
    @DisplayName("purchaserName 为 null 时跳过查询，直接返回空列表")
    void findDuplicates_nullPurchaser_returnsEmpty() {
        Tender tender = Tender.builder()
                .title("项目X")
                .purchaserName(null)
                .build();

        List<Tender> result = sut.findDuplicates(tender);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("purchaserName 为空字符串时跳过查询，直接返回空列表")
    void findDuplicates_blankPurchaser_returnsEmpty() {
        Tender tender = Tender.builder()
                .title("项目X")
                .purchaserName("   ")
                .build();

        List<Tender> result = sut.findDuplicates(tender);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("无匹配重复时返回空列表")
    void findDuplicates_noMatch_returnsEmpty() {
        Tender current = Tender.builder()
                .title("项目A")
                .purchaserName("业主甲")
                .registrationDeadline(LocalDateTime.of(2026, 6, 1, 12, 0))
                .bidOpeningTime(LocalDateTime.of(2026, 6, 10, 9, 0))
                .build();

        Tender existing = Tender.builder()
                .title("项目B")
                .purchaserName("业主乙")  // 不同的业主
                .registrationDeadline(LocalDateTime.of(2026, 6, 1, 12, 0))
                .bidOpeningTime(LocalDateTime.of(2026, 6, 10, 9, 0))
                .build();

        when(tenderRepository.findByPurchaserNameAllIgnoreCase("业主甲"))
                .thenReturn(List.of(existing));

        List<Tender> result = sut.findDuplicates(current);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("三字段完全匹配时返回重复列表")
    void findDuplicates_allFieldsMatch_returnsDuplicates() {
        LocalDateTime regDeadline = LocalDateTime.of(2026, 6, 1, 12, 0);
        LocalDateTime bidOpenTime = LocalDateTime.of(2026, 6, 10, 9, 0);

        Tender current = Tender.builder()
                .title("项目X")
                .purchaserName("业主甲")
                .registrationDeadline(regDeadline)
                .bidOpeningTime(bidOpenTime)
                .build();

        Tender duplicate = Tender.builder()
                .id(99L)
                .title("项目A")
                .purchaserName("业主甲")
                .registrationDeadline(regDeadline)
                .bidOpeningTime(bidOpenTime)
                .build();

        when(tenderRepository.findByPurchaserNameAllIgnoreCase("业主甲"))
                .thenReturn(List.of(duplicate));

        List<Tender> result = sut.findDuplicates(current);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("报名截止时间不同则不算重复")
    void findDuplicates_regDeadlineMismatch_notDuplicate() {
        LocalDateTime bidOpenTime = LocalDateTime.of(2026, 6, 10, 9, 0);

        Tender current = Tender.builder()
                .purchaserName("业主甲")
                .registrationDeadline(LocalDateTime.of(2026, 6, 1, 12, 0))
                .bidOpeningTime(bidOpenTime)
                .build();

        Tender existing = Tender.builder()
                .purchaserName("业主甲")
                .registrationDeadline(LocalDateTime.of(2026, 6, 2, 12, 0))  // 不同时间
                .bidOpeningTime(bidOpenTime)
                .build();

        when(tenderRepository.findByPurchaserNameAllIgnoreCase("业主甲"))
                .thenReturn(List.of(existing));

        List<Tender> result = sut.findDuplicates(current);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("数据库读出时间带微秒仍应判定为重复")
    void findDuplicates_dbMicrosecondPrecision_stillDuplicate() {
        LocalDateTime regDeadline = LocalDateTime.of(2026, 6, 1, 12, 0);
        LocalDateTime bidOpenTime = LocalDateTime.of(2026, 6, 10, 9, 0);
        // 模拟从 DATETIME(6) 字段读出的数据带微秒
        LocalDateTime dbRegDeadline = regDeadline.plusNanos(999_000_000);
        LocalDateTime dbBidOpenTime = bidOpenTime.plusNanos(123_000_000);

        Tender current = Tender.builder()
                .purchaserName("业主甲")
                .registrationDeadline(regDeadline)
                .bidOpeningTime(bidOpenTime)
                .build();

        Tender existing = Tender.builder()
                .id(88L)
                .purchaserName("业主甲")
                .registrationDeadline(dbRegDeadline)
                .bidOpeningTime(dbBidOpenTime)
                .build();

        when(tenderRepository.findByPurchaserNameAllIgnoreCase("业主甲"))
                .thenReturn(List.of(existing));

        List<Tender> result = sut.findDuplicates(current);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(88L);
    }

    @Test
    @DisplayName("checkDuplicate 发现重复时抛出 TenderDuplicateException")
    void checkDuplicate_whenDuplicate_throws() {
        LocalDateTime regDeadline = LocalDateTime.of(2026, 6, 1, 12, 0);
        LocalDateTime bidOpenTime = LocalDateTime.of(2026, 6, 10, 9, 0);

        Tender current = Tender.builder()
                .purchaserName("业主甲")
                .registrationDeadline(regDeadline)
                .bidOpeningTime(bidOpenTime)
                .build();

        Tender existing = Tender.builder()
                .id(77L)
                .purchaserName("业主甲")
                .registrationDeadline(regDeadline)
                .bidOpeningTime(bidOpenTime)
                .build();

        when(tenderRepository.findByPurchaserNameAllIgnoreCase("业主甲"))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> sut.checkDuplicate(current))
                .isInstanceOf(TenderDuplicateException.class)
                .satisfies(ex -> {
                    TenderDuplicateException dup = (TenderDuplicateException) ex;
                    assertThat(dup.getCode()).isEqualTo(400);
                    assertThat(dup.getMessage()).isEqualTo("投标管理系统该标讯已存在");
                    assertThat(dup.getDuplicates()).hasSize(1);
                    assertThat(dup.getDuplicates().get(0).getId()).isEqualTo(77L);
                });
    }
}
