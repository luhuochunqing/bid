package com.xiyu.bid.tender.service;

import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-261 根因二-b: 标讯转派成功后不给新负责人发通知。
 * TenderTransferService.transfer() 只写审计日志，不调用通知。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenderTransferService 转派通知（CO-261 根因二-b）")
class TenderTransferServiceTest {

    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenderAssignmentRecordRepository assignmentRecordRepository;
    @Mock
    private TenderAuditService tenderAuditService;
    @Mock
    private TenderAssignmentNotifier assignmentNotifier;

    private TenderTransferService service;

    @BeforeEach
    void setUp() {
        service = new TenderTransferService(
                tenderRepository, userRepository, assignmentRecordRepository,
                tenderAuditService, assignmentNotifier);
    }

    @Test
    @DisplayName("CO-261: 转派成功后给新负责人发站内通知")
    void transfer_ShouldNotifyNewOwner() {
        Tender tender = Tender.builder()
                .id(1L).title("测试标讯").status(Tender.Status.TRACKING)
                .projectManagerId(10L).projectManagerName("旧负责人").build();
        User newOwner = User.builder().id(20L).fullName("新负责人").departmentName("销售部").enabled(true).build();

        when(tenderRepository.findById(1L)).thenReturn(Optional.of(tender));
        when(userRepository.findById(20L)).thenReturn(Optional.of(newOwner));
        when(userRepository.findById(99L)).thenReturn(Optional.of(
                User.builder().id(99L).fullName("操作人").build()));

        service.transfer(1L, 20L, 99L);

        // CO-261: 转派成功后必须通知新负责人（Notifier 内部保证异常不影响主事务）
        verify(assignmentNotifier).notifyTransferred(any(Tender.class), anyLong(), anyString(), anyString(), anyLong());
    }
}
