export const CUSTOMER_INFO_COLUMNS = [
  { key: 'roleLabel', label: '客户信息（角色名）', width: 200, type: 'fixed' },
  { key: 'NAME', label: '姓名', width: 140, type: 'text', placeholder: '请输入姓名' },
  { key: 'POSITION', label: '职位', width: 160, type: 'position', placeholder: '请选择' },
  { key: 'XIYU_CONTACT', label: '西域项目负责人', width: 160, type: 'text', placeholder: '请输入负责人' },
  { key: 'CONTACT_METHOD', label: '触达方式', width: 140, type: 'contactMethod', placeholder: '请选择' },
  { key: 'INFO_TENDENCY_BASIS', label: '倾向性评估依据', width: 200, type: 'text', placeholder: '请输入依据' },
  { key: 'CONTACTED', label: '是否触达', width: 120, type: 'yesno' },
  { key: 'HIGH_LEVEL_EXCHANGE', label: '是否有正式高层交流', width: 170, type: 'yesno' },
  { key: 'GUIDED_BID', label: '是否向此人引导标书', width: 170, type: 'yesno' },
  { key: 'CAN_GET_KEY_INFO', label: '是否可获取关键信息', width: 170, type: 'yesno' },
  { key: 'CAN_REMOVE_ADVERSE', label: '是否可删除不利项', width: 170, type: 'yesno' },
  { key: 'KEY_TARGET', label: '是否为重点攻克对象', width: 170, type: 'yesno' },
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

export const POSITION_OPTIONS = [
  '董事长',
  '总经理',
  '副总经理',
  '部门负责人',
  '项目负责人',
  '采购负责人',
  '技术负责人',
  '财务负责人',
  '法务负责人',
  '评标专家',
  '经办人',
  '外部顾问',
  '其他决策人',
  '其他',
]

export const CONTACT_METHOD_OPTIONS = [
  '电话',
  '微信',
  '邮件',
  '拜访',
  '会议',
  '第三方引荐',
  '未触达',
]

export const IMPACT_OPTIONS = [
  { label: '极高', value: 'VERY_HIGH' },
  { label: '高', value: 'HIGH' },
  { label: '中', value: 'MEDIUM' },
  { label: '低', value: 'LOW' },
  { label: '极低', value: 'VERY_LOW' },
  { label: '无影响', value: 'NONE' },
]
