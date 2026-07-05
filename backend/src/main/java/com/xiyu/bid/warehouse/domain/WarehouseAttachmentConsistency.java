package com.xiyu.bid.warehouse.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class WarehouseAttachmentConsistency {

    private static final Map<WarehouseAttachmentType, SwitchAccessor> SWITCH_MAP = new EnumMap<>(WarehouseAttachmentType.class);

    static {
        SWITCH_MAP.put(WarehouseAttachmentType.PROPERTY_CERTIFICATE, WarehouseReadModel::getHasPropertyCert);
        SWITCH_MAP.put(WarehouseAttachmentType.INVOICE, WarehouseReadModel::getHasInvoice);
        SWITCH_MAP.put(WarehouseAttachmentType.PHOTOS, WarehouseReadModel::getHasPhotos);
        SWITCH_MAP.put(WarehouseAttachmentType.LEASE_CONTRACT, WarehouseReadModel::getHasLeaseContract);
    }

    private WarehouseAttachmentConsistency() {}

    public static Optional<String> checkDeleteAllowed(WarehouseReadModel warehouse,
                                                      WarehouseAttachmentType type,
                                                      long remainingCountAfterDelete) {
        if (remainingCountAfterDelete > 0) {
            return Optional.empty();
        }
        Boolean switchOn = SWITCH_MAP.getOrDefault(type, w -> false).isOn(warehouse);
        if (Boolean.TRUE.equals(switchOn)) {
            return Optional.of(type.displayName() + "开关已开启，至少需要保留一个附件；如需删除请先关闭对应开关");
        }
        return Optional.empty();
    }

    @FunctionalInterface
    private interface SwitchAccessor {
        Boolean isOn(WarehouseReadModel warehouse);
    }
}
