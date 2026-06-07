// Input: none
// Output: Bidding list page option constants, nationwide region options, and default form factories
// Pos: src/views/Bidding/list/ - Local constants for the bidding list page
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

export const REGION_OPTIONS = [
  '北京',
  '天津',
  '河北',
  '山西',
  '内蒙古',
  '辽宁',
  '吉林',
  '黑龙江',
  '上海',
  '江苏',
  '浙江',
  '安徽',
  '福建',
  '江西',
  '山东',
  '河南',
  '湖北',
  '湖南',
  '广东',
  '广西',
  '海南',
  '重庆',
  '四川',
  '贵州',
  '云南',
  '西藏',
  '陕西',
  '甘肃',
  '青海',
  '宁夏',
  '新疆',
  '台湾',
  '香港',
  '澳门',
]

export const INDUSTRY_OPTIONS = ['政府', '能源', '交通', '数据中心', '金融', '医疗', '教育']

export const CUSTOMER_TYPE_OPTIONS = ['政府机关/事业单位/高校', '央企', '地方国企', '民企', '港澳台及外企']

// 项目类型：工业品/办公/综合/集采/其他
export const PROJECT_TYPE_OPTIONS = ['工业品', '办公', '综合', '集采', '其他']

// 来源平台选项：人工录入/CRM商机转入/第三方标讯平台名称（第三方平台名称待后端对接后动态获取）
export const SOURCE_PLATFORM_OPTIONS = ['人工录入', 'CRM商机转入', '第三方标讯平台名称']

export const PRIORITY_OPTIONS = [
  {
    value: 'S',
    label: 'S 级',
    desc: '战略级高价值客户',
    standard: '年度合作额超 5000 万存量客户及超大型央企集团',
  },
  {
    value: 'A',
    label: 'A 级',
    desc: '高价值重点客户',
    standard: '年度合作额超 1000 万存量客户及其他央企集团',
  },
  {
    value: 'B',
    label: 'B 级',
    desc: '重要潜力客户',
    standard: '省属/市属国企，或营收超 100 亿制造业民营/外资企业',
  },
  {
    value: 'C',
    label: 'C 级',
    desc: '潜力客户',
    standard: '营收 50-100 亿制造业民营/外资企业',
  },
]

export const SOURCE_OPTIONS = [
  { label: '第三方平台', value: 'EXTERNAL_PLATFORM' },
  { label: 'CRM 商机', value: 'CRM_OPPORTUNITY' },
  { label: '人工录入', value: 'MANUAL_SINGLE' },
  { label: '批量导入', value: 'BULK_IMPORT' },
]

export const SOURCE_TYPE_OPTIONS = [
  { label: '全部来源', value: '' },
  { label: '第三方平台', value: 'EXTERNAL_PLATFORM' },
  { label: 'CRM 商机', value: 'CRM_OPPORTUNITY' },
  { label: '人工录入', value: 'MANUAL_SINGLE' },
  { label: '批量导入', value: 'BULK_IMPORT' },
]

export const SOURCE_KEYWORD_OPTIONS = [
  'MRO 工具',
  '工具耗材',
  '焊接',
  '刀具',
  '量具',
  '机床',
  '磨具',
  '润滑',
  '胶粘',
  '车间化学品',
  '劳保',
  '安全消防',
  '搬运存储',
  '工控',
  '低压',
  '电工',
  '照明',
  '轴承',
  '液压',
  '管阀',
  '泵',
]

export const ASSIGN_RULES = [
  { value: 'region', label: '按总部所在地分发', desc: '根据标讯总部所在地自动分配' },
  { value: 'score', label: '按 AI 评分', desc: '高分优先给候选人' },
  { value: 'average', label: '平均分配', desc: '均匀分配给可选人员' },
]

export const DEFAULT_SEARCH_FORM = Object.freeze({
  keyword: '',
  region: '',
  status: [],
  source: [],
  customerType: [],
  priority: [],
  registrationDeadlineFrom: null,
  registrationDeadlineTo: null,
  bidOpeningTimeFrom: null,
  bidOpeningTimeTo: null,
  projectType: '',
  projectManagerId: null,
  creatorId: null,
  createdAtFrom: null,
  createdAtTo: null,
})

export const DEFAULT_SOURCE_CONFIG = Object.freeze({
  platforms: ['人工录入'],
  apiEndpoint: '',
  apiKey: '',
  keywords: [],
  regions: [],
  minBudget: 0,
  maxBudget: 1000,
  autoSync: false,
  syncInterval: 6,
  autoSave: true,
  enableDedupe: true,
})

export const DEFAULT_FETCH_RESULT = Object.freeze({
  visible: false,
  saved: 0,
  skipped: 0,
  message: '',
})

export function createManualTenderForm() {
  return {
    title: '',
    purchaser: '',             // 招标主体（必填）
    region: '',
    deadline: null,
    bidOpeningTime: null,
    customerType: '',
    priority: '',
    projectType: '',
    sourcePlatform: '人工录入',
    // 联系人1
    contact: '',
    phone: '',
    landline: '',
    mail: '',
    // 联系人2
    contact2: '',
    phone2: '',
    landline2: '',
    mail2: '',
    // 其他
    description: '',
    tenderInfo: '',
    attachments: [],
    sourceDocumentName: '',
    sourceDocumentFileType: '',
    sourceDocumentFileUrl: '',
    pastedText: '',
    crmOpportunityId: null,
  }
}

// 手机号校验：1开头，第二位3-9，共11位数字
const PHONE_REGEX = /^1[3-9]\d{9}$/
// 座机校验：区号可选（0+区号2-3位，如010/0571），号码7-8位，分隔符-可选
// 示例：010-12345678 / 12345678 / 0571-87654321
const LANDLINE_REGEX = /^(0\d{2,3}-?)?\d{7,8}$/
// 邮箱校验
const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/

/**
 * 联系人组交叉校验：
 * 联系人1和联系人2各自独立判断：
 * - 若填写了联系人姓名，则联系方式（手机/座机/邮箱）三选一必填
 * - 若未填写联系人姓名，则联系方式可为空
 * 表单级别校验由 TenderCreatePage.vue 的 validateContacts() 负责
 */
export const MANUAL_FORM_RULES = {
  title: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  purchaser: [{ required: true, message: '请输入招标主体', trigger: 'blur' }],
  region: [{ required: true, message: '请选择总部所在地', trigger: 'change' }],
  deadline: [{ required: true, message: '请选择报名截止时间', trigger: 'change' }],
  bidOpeningTime: [{ required: true, message: '请选择开标时间', trigger: 'change' }],
  customerType: [{ required: true, message: '请选择客户类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  // 联系人1手机号格式校验
  phone: [
    { pattern: PHONE_REGEX, message: '请输入正确的手机号格式', trigger: 'blur' },
  ],
  // 联系人1座机格式校验
  landline: [
    { pattern: LANDLINE_REGEX, message: '请输入正确的座机格式（如 010-12345678）', trigger: 'blur' },
  ],
  // 联系人1邮箱格式校验
  mail: [
    { pattern: EMAIL_REGEX, message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  // 联系人2手机号格式校验
  phone2: [
    { pattern: PHONE_REGEX, message: '请输入正确的手机号格式', trigger: 'blur' },
  ],
  // 联系人2座机格式校验
  landline2: [
    { pattern: LANDLINE_REGEX, message: '请输入正确的座机格式（如 010-12345678）', trigger: 'blur' },
  ],
  // 联系人2邮箱格式校验
  mail2: [
    { pattern: EMAIL_REGEX, message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  // 标讯描述 max 5000
  description: [
    { max: 5000, message: '标讯描述不能超过5000字符', trigger: 'blur' },
  ],
  // 标讯信息 max 5000
  tenderInfo: [
    { max: 5000, message: '标讯信息不能超过5000字符', trigger: 'blur' },
  ],
}
