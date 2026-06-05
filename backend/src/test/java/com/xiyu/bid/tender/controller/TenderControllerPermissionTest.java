package com.xiyu.bid.tender.controller;

import com.xiyu.bid.tender.dto.TenderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class TenderControllerPermissionTest {

    @Test
    void createTender_allowsSalesStaffToSubmitTenderInformation() throws NoSuchMethodException {
        PreAuthorize annotation = TenderController.class
                .getMethod("createTender", com.xiyu.bid.tender.dto.TenderRequest.class,
                        org.springframework.security.core.userdetails.UserDetails.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')");
    }
}
