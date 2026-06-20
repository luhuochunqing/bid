export const CUSTOMER_INFO_COLUMNS = [
  { key: 'NAME', label: '姓名', width: 140, type: 'text', placeholder: '请输入姓名' },
  { key: 'CONTACT_INFO', label: '联系方式', width: 160, type: 'text', placeholder: '手机号/电话/邮箱' },
  { key: 'POSITION', label: '职位', width: 220, type: 'position', placeholder: '请选择' },
  { key: 'XIYU_CONTACT', label: '西域项目负责人', width: 160, type: 'text', placeholder: '请输入负责人' },
  { key: 'CONTACT_METHOD', label: '触达方式', width: 180, type: 'contactMethod', placeholder: '请选择' },
  { key: 'INFO_TENDENCY_BASIS', label: '倾向性评估依据', width: 200, type: 'text', placeholder: '请输入依据' },
  { key: 'CONTACTED', label: '是否触达', width: 120, type: 'yesno' },
  { key: 'GUIDED_BID', label: '是否向此人引导标书', width: 170, type: 'yesno' },
  { key: 'CAN_GET_KEY_INFO', label: '是否可获取关键信息', width: 170, type: 'yesno' },
  { key: 'CAN_REMOVE_ADVERSE', label: '是否可删除不利项', width: 170, type: 'yesno' },
  { key: 'CAN_SYNC_EVAL', label: '是否可同步评标信息', width: 170, type: 'yesno' },
  { key: 'TENDENCY', label: '对我司的倾向性', width: 160, type: 'tendency' },
  { key: 'INFO_CLEAR_WINNER_BID', label: '是否给出明确中标信息', width: 180, type: 'switch' },
  { key: 'INFO_WIN_RATE_IMPACT', label: '对中标影响率', width: 140, type: 'impact' },
]

export const CUSTOMER_INFO_ROWS = [
  { roleKey: 'PROJECT_HIGHEST_DECISION_MAKER', roleLabel: '项目最高决策人' },
  { roleKey: 'MATERIALS_COMPANY_CHAIRMAN', roleLabel: '物资公司董事长' },
  { roleKey: 'MATERIALS_COMPANY_ELECTRONICS_LEADER', roleLabel: '物资公司分管电商领导' },
  { roleKey: 'ELECTRONICS_COMPANY_CHAIRMAN', roleLabel: '电商公司董事长' },
  { roleKey: 'ELECTRONICS_COMPANY_GENERAL_MANAGER', roleLabel: '电商公司总经理' },
  { roleKey: 'ELECTRONICS_COMPANY_DEPUTY_GENERAL_MANAGER', roleLabel: '电商公司副总经理' },
  { roleKey: 'ELECTRONICS_COMPANY_OPERATIONS_LEADER', roleLabel: '电商公司运营负责人' },
  { roleKey: 'BID_DOCUMENT_PREPARER', roleLabel: '招标文件制作人' },
  { roleKey: 'OTHER_KEY_DECISION_MAKER_1', roleLabel: '其他关键决策人1' },
  { roleKey: 'OTHER_KEY_DECISION_MAKER_2', roleLabel: '其他关键决策人2' },
  { roleKey: 'OTHER_KEY_DECISION_MAKER_3', roleLabel: '其他关键决策人3' },
  { roleKey: 'EXPERT_1', roleLabel: '专家1' },
  { roleKey: 'EXPERT_2', roleLabel: '专家2' },
  { roleKey: 'EXPERT_3', roleLabel: '专家3' },
]

export function getCustomerInfoRoleLabel(roleKey, roleLabel) {
  if (roleLabel && roleLabel !== roleKey) return roleLabel
  const fixed = CUSTOMER_INFO_ROWS.find(row => row.roleKey === roleKey)
  if (fixed) return fixed.roleLabel
  const externalRole = /^EXTERNAL_ROLE_(\d+)$/.exec(roleKey || '')
  if (externalRole) return `外部对接人${externalRole[1]}`
  return roleLabel || roleKey || ''
}

export const POSITION_OPTIONS = [
  { label: '项目最高决策人', value: '1' },
  { label: '物资公司董事长', value: '2' },
  { label: '物资公司分管电商领导', value: '3' },
  { label: '电商公司董事长', value: '4' },
  { label: '电商公司总经理', value: '5' },
  { label: '电商公司副总经理', value: '6' },
  { label: '电商公司运营负责人', value: '7' },
  { label: '招标文件制作人', value: '8' },
  { label: '其他关键决策人1', value: '9' },
  { label: '其他关键决策人2', value: '10' },
  { label: '其他关键决策人3', value: '11' },
  { label: '专家1', value: '12' },
  { label: '专家2', value: '13' },
  { label: '专家3', value: '14' },
]

export const CONTACT_METHOD_OPTIONS = [
  { label: '自主开发', value: '1' },
  { label: '老客户转介绍', value: '2' },
  { label: '供应商渠道推荐', value: '3' },
  { label: '展会/行业峰会', value: '4' },
  { label: '政府/协会推荐', value: '5' },
  { label: '客户主动咨询', value: '6' },
  { label: '其他', value: '7' },
]

export const TENDENCY_OPTIONS = [
  { label: '支持', value: '1' },
  { label: '中立', value: '2' },
  { label: '反对', value: '3' },
]

export const IMPACT_OPTIONS = [
  { label: '100%', value: '1' },
  { label: '80%', value: '2' },
  { label: '60%', value: '3' },
  { label: '50%', value: '4' },
  { label: '30%', value: '5' },
  { label: '10%', value: '6' },
]
