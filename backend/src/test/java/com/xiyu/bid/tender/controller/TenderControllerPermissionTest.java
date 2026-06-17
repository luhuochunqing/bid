package com.xiyu.bid.tender.controller;

import com.xiyu.bid.tender.dto.TenderRequest;
import com.xiyu.bid.tender.dto.TenderTransferRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;

class TenderControllerPermissionTest {

    @Test
    void createTender_allowsSalesStaffToSubmitTenderInformation() throws NoSuchMethodException {
        PreAuthorize annotation = TenderController.class
                .getMethod("createTender", TenderRequest.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR', 'SALES', 'BID_SPECIALIST')");
    }

    @Test
    void updateTender_allowsAdminAndManagerOnly() throws NoSuchMethodException {
        PreAuthorize annotation = TenderController.class
                .getMethod("updateTender", Long.class, TenderRequest.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'MANAGER')");
    }

    @Test
    void deleteTender_allowsAdminAndManagerOnly() throws NoSuchMethodException {
        PreAuthorize annotation = TenderController.class
                .getMethod("deleteTender", Long.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'MANAGER')");
    }

    @Test
    void transferTender_allowsAdminBidLeadAndBidSeniorOnly() throws NoSuchMethodException {
        PreAuthorize annotation = TenderTransferController.class
                .getMethod("transferTender", Long.class, TenderTransferRequest.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR')");
    }
}
