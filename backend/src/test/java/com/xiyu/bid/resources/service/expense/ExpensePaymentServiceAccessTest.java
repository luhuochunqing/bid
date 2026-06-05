package com.xiyu.bid.resources.service.expense;

import com.xiyu.bid.resources.dto.ExpensePaymentCreateRequest;
import com.xiyu.bid.resources.repository.ExpensePaymentRecordRepository;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpensePaymentServiceAccessTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpensePaymentRecordRepository paymentRecordRepository;

    @Mock
    private ExpenseCommandService expenseCommandService;

    @Test
    void registerPayment_ShouldRejectInvisibleProjectBeforeWritingPaymentRecord() {
        ExpensePaymentService service = new ExpensePaymentService(
                expenseRepository,
                paymentRecordRepository,
                expenseCommandService
        );
        when(expenseCommandService.getExpenseEntityById(1L))
                .thenThrow(new AccessDeniedException("权限不足"));

        ExpensePaymentCreateRequest request = new ExpensePaymentCreateRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setPaidAt(LocalDateTime.now().minusHours(1));
        request.setPaidBy("cashier");

        assertThatThrownBy(() -> service.registerPayment(1L, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(paymentRecordRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
