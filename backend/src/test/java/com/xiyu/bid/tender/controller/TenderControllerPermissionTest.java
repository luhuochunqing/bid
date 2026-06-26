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

        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')");
    }

    @Test
    void updateTender_allowsAdminAndBidTeamRoles() throws NoSuchMethodException {
        PreAuthorize annotation = TenderController.class
                .getMethod("updateTender", Long.class, TenderRequest.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER')");
    }

    @Test
    void deleteTender_allowsAdminAndBidTeamRoles() throws NoSuchMethodException {
        PreAuthorize annotation = TenderController.class
                .getMethod("deleteTender", Long.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER')");
    }

    @Test
    void transferTender_allowsAdminBidLeadAndBidAdminOnly() throws NoSuchMethodException {
        PreAuthorize annotation = TenderTransferController.class
                .getMethod("transferTender", Long.class, TenderTransferRequest.class, UserDetails.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')");
    }
}
