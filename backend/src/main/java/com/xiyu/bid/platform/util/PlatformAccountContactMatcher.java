package com.xiyu.bid.platform.util;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.entity.PlatformAccount;

/**
 * 判断当前用户是否为平台账户的绑定联系人。
 *
 * <p>CO-390 已将 {@code PlatformAccount.contactPerson} 升级为 userId，
 * 直接比较账户的 contactPerson 与当前用户的 id 即可。</p>
 */
public final class PlatformAccountContactMatcher {

    private PlatformAccountContactMatcher() {
    }

    public static boolean isContactPerson(PlatformAccount account, User viewer) {
        Long contactPersonId = account.getContactPerson();
        return contactPersonId != null && viewer != null && contactPersonId.equals(viewer.getId());
    }
}
