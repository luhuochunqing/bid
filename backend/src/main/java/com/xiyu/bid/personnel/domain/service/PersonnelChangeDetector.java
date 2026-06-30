package com.xiyu.bid.personnel.domain.service;

import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog.ChangeDetail;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;
import com.xiyu.bid.personnel.domain.valueobject.Education;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 人员变更检测器（CO-417）：纯核心，生成结构化字段级 diff。
 *
 * <p>职责：对比 old/new Personnel 或子项列表，输出 {@link ChangeDetail} 三元组列表。
 * 不依赖 Spring、Repository、时间源等任何命令式外壳。
 *
 * <p>字段约定：
 * <ul>
 *   <li>基础字段 diff：{@code (fieldName, oldValue, newValue)}，如 {@code ("name", "张三", "李四")}</li>
 *   <li>证书新增：{@code ("certificate", "", 证书名)}</li>
 *   <li>证书删除：{@code ("certificate", 证书名, "")}</li>
 *   <li>证书字段修改：{@code (子字段名, 旧值, 新值)}，如 {@code ("certificateNumber", "C001", "C002")}</li>
 *   <li>教育经历新增/删除/修改：同上，field 名为 "education" 或具体子字段名</li>
 * </ul>
 */
public final class PersonnelChangeDetector {

    /** 检测 9 个基础字段 diff（不含 employeeNumber，工号变更由应用层单独处理警示） */
    public List<ChangeDetail> detectBasicFieldChanges(Personnel old, Personnel updated) {
        List<ChangeDetail> changes = new ArrayList<>();
        changes.addAll(changeIfDifferent("name", old.name(), updated.name()));
        changes.addAll(changeIfDifferent("departmentName", old.departmentName(), updated.departmentName()));
        changes.addAll(changeIfDifferent("gender", old.gender(), updated.gender()));
        changes.addAll(changeIfDifferent("entryDate", old.entryDate(), updated.entryDate()));
        changes.addAll(changeIfDifferent("birthDate", old.birthDate(), updated.birthDate()));
        changes.addAll(changeIfDifferent("phone", old.phone(), updated.phone()));
        changes.addAll(changeIfDifferent("education", old.education(), updated.education()));
        changes.addAll(changeIfDifferent("technicalTitle", old.technicalTitle(), updated.technicalTitle()));
        changes.addAll(changeIfDifferent("remark", old.remark(), updated.remark()));
        return changes;
    }

    /** 检测证书列表变更：新增/删除/字段级修改 */
    public List<ChangeDetail> detectCertificateChanges(List<Certificate> oldList, List<Certificate> newList) {
        List<ChangeDetail> changes = new ArrayList<>();

        for (Certificate oldCert : oldList) {
            if (oldCert.id() == null) continue;
            Certificate matched = findById(newList, oldCert.id());
            if (matched == null) {
                changes.add(new ChangeDetail("certificate", oldCert.name(), ""));
            } else {
                changes.addAll(certificateFieldDiff(oldCert, matched));
            }
        }

        for (Certificate newCert : newList) {
            if (newCert.id() == null) {
                changes.add(new ChangeDetail("certificate", "", newCert.name()));
            }
        }

        return changes;
    }

    /** 检测教育经历列表变更：新增/删除/字段级修改 */
    public List<ChangeDetail> detectEducationChanges(List<Education> oldList, List<Education> newList) {
        List<ChangeDetail> changes = new ArrayList<>();

        // 教育经历无 id 字段，按 (startDate, endDate) 元组匹配（时间最稳定）
        for (Education oldEdu : oldList) {
            if (findMatch(newList, oldEdu) == null) {
                changes.add(new ChangeDetail("education", oldEdu.schoolName(), ""));
            }
        }

        for (Education newEdu : newList) {
            if (findMatch(oldList, newEdu) == null) {
                changes.add(new ChangeDetail("education", "", newEdu.schoolName()));
            }
        }

        for (Education oldEdu : oldList) {
            Education matched = findMatch(newList, oldEdu);
            if (matched != null) {
                changes.addAll(educationFieldDiff(oldEdu, matched));
            }
        }

        return changes;
    }

    // ============ 内部辅助（纯函数，返回值） ============

    private List<ChangeDetail> changeIfDifferent(String field, Object oldVal, Object newVal) {
        String oldStr = normalize(oldVal);
        String newStr = normalize(newVal);
        if (Objects.equals(oldStr, newStr)) {
            return List.of();
        }
        return List.of(new ChangeDetail(field, oldStr, newStr));
    }

    private String normalize(Object val) {
        if (val == null) return "";
        if (val instanceof LocalDate ld) return ld.toString();
        return val.toString();
    }

    private Certificate findById(List<Certificate> list, Long id) {
        for (Certificate c : list) {
            if (id.equals(c.id())) return c;
        }
        return null;
    }

    private List<ChangeDetail> certificateFieldDiff(Certificate oldCert, Certificate newCert) {
        List<ChangeDetail> changes = new ArrayList<>();
        changes.addAll(changeIfDifferent("name", oldCert.name(), newCert.name()));
        changes.addAll(changeIfDifferent("certificateNumber", oldCert.certificateNumber(), newCert.certificateNumber()));
        changes.addAll(changeIfDifferent("type", oldCert.type(), newCert.type()));
        changes.addAll(changeIfDifferent("issueDate", oldCert.issueDate(), newCert.issueDate()));
        changes.addAll(changeIfDifferent("expiryDate", oldCert.expiryDate(), newCert.expiryDate()));
        changes.addAll(changeIfDifferent("attachmentUrl", oldCert.attachmentUrl(), newCert.attachmentUrl()));
        changes.addAll(changeIfDifferent("title", oldCert.title(), newCert.title()));
        changes.addAll(changeIfDifferent("isPermanent", oldCert.isPermanent(), newCert.isPermanent()));
        changes.addAll(changeIfDifferent("remark", oldCert.remark(), newCert.remark()));
        return changes;
    }

    private Education findMatch(List<Education> list, Education target) {
        for (Education e : list) {
            if (Objects.equals(e.startDate(), target.startDate())
                    && Objects.equals(e.endDate(), target.endDate())) {
                return e;
            }
        }
        return null;
    }

    private List<ChangeDetail> educationFieldDiff(Education oldEdu, Education newEdu) {
        List<ChangeDetail> changes = new ArrayList<>();
        changes.addAll(changeIfDifferent("schoolName", oldEdu.schoolName(), newEdu.schoolName()));
        changes.addAll(changeIfDifferent("startDate", oldEdu.startDate(), newEdu.startDate()));
        changes.addAll(changeIfDifferent("endDate", oldEdu.endDate(), newEdu.endDate()));
        changes.addAll(changeIfDifferent("highestEducation", oldEdu.highestEducation(), newEdu.highestEducation()));
        changes.addAll(changeIfDifferent("studyForm", oldEdu.studyForm(), newEdu.studyForm()));
        changes.addAll(changeIfDifferent("major", oldEdu.major(), newEdu.major()));
        changes.addAll(changeIfDifferent("isHighestEducationSchool", oldEdu.isHighestEducationSchool(), newEdu.isHighestEducationSchool()));
        return changes;
    }
}
