package com.xiyu.bid.personnel.domain.port;

import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;

import java.util.List;

public interface PersonnelNotificationPort {

    void notifyCertificateExpiry(List<Personnel> personnelWithExpiringCerts, int warningDays);

    void notifyCertificateExpired(Personnel personnel, List<Certificate> expiredCerts);
}
