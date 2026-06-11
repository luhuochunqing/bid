// CO-155 fix: 将 category / subjectType 静态选项从 QualFormDialog.vue 拆出，遵循 line-budget 约束。
// 与后端枚举值严格对齐：businessqualification/domain/valueobject/QualificationCategory.java
//                         businessqualification/domain/valueobject/QualificationSubjectType.java

export const QUALIFICATION_CATEGORY_OPTIONS = [
  { label: '企业资质', value: 'LICENSE' },
  { label: '人员证书', value: 'PERSONNEL' },
  { label: '产品资质', value: 'PRODUCT' },
  { label: '其他', value: 'OTHER' }
]

export const QUALIFICATION_SUBJECT_TYPE_OPTIONS = [
  { label: '公司', value: 'COMPANY' },
  { label: '子公司', value: 'SUBSIDIARY' }
]
