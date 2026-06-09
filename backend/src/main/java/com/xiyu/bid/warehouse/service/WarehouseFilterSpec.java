package com.xiyu.bid.warehouse.service;

import com.xiyu.bid.warehouse.domain.WarehouseFilterCriteria;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * 将 WarehouseFilterCriteria（纯核心值对象）转换为 JPA Specification 的适配层。
 * WarehouseFilterCriteria 本身是纯核心无 I/O；本类承担 JPA 适配职责。
 */
@Component
public class WarehouseFilterSpec {

    public Specification<WarehouseEntity> toSpec(WarehouseFilterCriteria c) {
        if (c == null || isEmpty(c)) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            // 关键词搜索（仓库名称/具体地址/出租方/承租方）
            if (c.keyword() != null && !c.keyword().isBlank()) {
                String kw = "%" + c.keyword().trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("name"), kw),
                        cb.like(root.get("address"), kw),
                        cb.like(root.get("lessor"), kw),
                        cb.like(root.get("lessee"), kw)
                ));
            }

            // 仓库类型（多选 IN）
            if (!c.types().isEmpty()) {
                predicates.add(root.get("type").in(c.types()));
            }

            // 仓库状态（多选 IN）
            if (!c.statuses().isEmpty()) {
                predicates.add(root.get("status").in(c.statuses()));
            }

            // 仓库所在省份（多选）
            if (c.provinces() != null && !c.provinces().isEmpty()) {
                predicates.add(root.get("province").in(c.provinces()));
            }

            // 结束时间范围
            if (c.endDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), c.endDateFrom()));
            }
            if (c.endDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("endDate"), c.endDateTo()));
            }

            // 区域联系人关键词
            if (c.contactPersonKeyword() != null && !c.contactPersonKeyword().isBlank()) {
                predicates.add(cb.like(root.get("contactPerson"),
                        "%" + c.contactPersonKeyword().trim() + "%"));
            }

            // 三证开关 → JOIN attachment 表 EXISTS 子查询
            if (Boolean.TRUE.equals(c.hasPropertyCert())) {
                predicates.add(hasAttachmentOfType(root, query, cb, "PROPERTY_CERTIFICATE"));
            }
            if (Boolean.TRUE.equals(c.hasInvoice())) {
                predicates.add(hasAttachmentOfType(root, query, cb, "INVOICE"));
            }
            if (Boolean.TRUE.equals(c.hasPhotos())) {
                predicates.add(hasAttachmentOfType(root, query, cb, "PHOTOS"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Predicate hasAttachmentOfType(Root<WarehouseEntity> root,
                                        CriteriaQuery<?> query,
                                        CriteriaBuilder cb,
                                        String type) {
        var sub = query.subquery(Long.class);
        var attachmentRoot = sub.from(com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentEntity.class);
        sub.select(cb.literal(1L)).where(
                cb.equal(attachmentRoot.get("warehouse").get("id"), root.get("id")),
                cb.equal(attachmentRoot.get("type"), type)
        );
        return cb.exists(sub);
    }

    private boolean isEmpty(WarehouseFilterCriteria c) {
        return (c.keyword() == null || c.keyword().isBlank())
                && c.types().isEmpty()
                && c.statuses().isEmpty()
                && c.regions().isEmpty()
                && c.provinces().isEmpty()
                && c.endDateFrom() == null
                && c.endDateTo() == null
                && c.hasPropertyCert() == null
                && c.hasInvoice() == null
                && c.hasPhotos() == null
                && (c.contactPersonKeyword() == null || c.contactPersonKeyword().isBlank());
    }
}
