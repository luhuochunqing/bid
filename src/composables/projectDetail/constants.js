export const taskTemplates = {
  government: [
    { id: 'GT01', name: '招标文件研读', description: '仔细阅读招标文件，标记关键要求', owner: '项目经理', priority: 'high', deadlineOffset: 1, needsDeliverable: false },
    { id: 'GT02', name: '资质文件准备', description: '准备营业执照、ISO认证、相关资质证书', owner: '行政专员', priority: 'high', deadlineOffset: 3, needsDeliverable: true, deliverableType: 'qualification' },
    { id: 'GT03', name: '技术方案编制', description: '根据招标要求编制技术方案', owner: '技术负责人', priority: 'high', deadlineOffset: 7, needsDeliverable: true, deliverableType: 'technical' },
    { id: 'GT04', name: '商务应答编制', description: '编制商务条款应答文件', owner: '商务负责人', priority: 'high', deadlineOffset: 7, needsDeliverable: true, deliverableType: 'document' },
    { id: 'GT05', name: '报价单制作', description: '根据招标要求制作报价单', owner: '财务', priority: 'high', deadlineOffset: 5, needsDeliverable: true, deliverableType: 'quotation' },
    { id: 'GT06', name: '合同条款审核', description: '法务审核合同条款', owner: '法务', priority: 'medium', deadlineOffset: 5, needsDeliverable: false },
    { id: 'GT07', name: '内部评审', description: '组织内部评审会议', owner: '项目经理', priority: 'high', deadlineOffset: 8, needsDeliverable: false },
    { id: 'GT08', name: '标书装订封装', description: '按招标要求装订封装标书', owner: '行政专员', priority: 'medium', deadlineOffset: 9, needsDeliverable: false },
  ],
  energy: [
    { id: 'ET01', name: '招标文件分析', description: '分析技术要求和评分标准', owner: '项目经理', priority: 'high', deadlineOffset: 1, needsDeliverable: false },
    { id: 'ET02', name: '资质文件审核', description: '确认相关行业资质', owner: '行政专员', priority: 'high', deadlineOffset: 2, needsDeliverable: true, deliverableType: 'qualification' },
    { id: 'ET03', name: '技术方案设计', description: '设计符合能源行业要求的技术方案', owner: '技术总监', priority: 'high', deadlineOffset: 6, needsDeliverable: true, deliverableType: 'technical' },
    { id: 'ET04', name: '产品选型', description: '确定投标产品型号和参数', owner: '产品经理', priority: 'high', deadlineOffset: 4, needsDeliverable: true, deliverableType: 'document' },
    { id: 'ET05', name: '报价测算', description: '测算项目成本和投标报价', owner: '财务', priority: 'high', deadlineOffset: 5, needsDeliverable: true, deliverableType: 'quotation' },
    { id: 'ET06', name: '技术偏离表编制', description: '编制技术响应偏离表', owner: '技术负责人', priority: 'medium', deadlineOffset: 6, needsDeliverable: false },
    { id: 'ET07', name: '商务偏离表编制', description: '编制商务条款偏离表', owner: '商务负责人', priority: 'medium', deadlineOffset: 6, needsDeliverable: false },
    { id: 'ET08', name: '内部评审', description: '组织技术和商务联合评审', owner: '项目经理', priority: 'high', deadlineOffset: 7, needsDeliverable: false },
  ],
  traffic: [
    { id: 'TT01', name: '招标文件解读', description: '解读招标文件技术规范', owner: '项目经理', priority: 'high', deadlineOffset: 1, needsDeliverable: false },
    { id: 'TT02', name: '行业资质确认', description: '确认交通行业相关资质', owner: '行政专员', priority: 'high', deadlineOffset: 2, needsDeliverable: true, deliverableType: 'qualification' },
    { id: 'TT03', name: '系统方案设计', description: '设计自动化系统整体方案', owner: '技术总监', priority: 'high', deadlineOffset: 7, needsDeliverable: true, deliverableType: 'technical' },
    { id: 'TT04', name: '设备清单编制', description: '编制设备材料清单', owner: '技术负责人', priority: 'medium', deadlineOffset: 5, needsDeliverable: true, deliverableType: 'document' },
    { id: 'TT05', name: '项目实施计划', description: '编制项目实施进度计划', owner: '项目助理', priority: 'medium', deadlineOffset: 5, needsDeliverable: false },
    { id: 'TT06', name: '报价分析', description: '分析竞争对手报价策略', owner: '商务经理', priority: 'high', deadlineOffset: 4, needsDeliverable: true, deliverableType: 'quotation' },
    { id: 'TT07', name: '综合评审', description: '组织综合评审', owner: '项目经理', priority: 'high', deadlineOffset: 8, needsDeliverable: false },
  ],
  default: [
    { id: 'DT01', name: '招标文件研读', description: '仔细阅读招标文件', owner: '项目经理', priority: 'high', deadlineOffset: 1, needsDeliverable: false },
    { id: 'DT02', name: '资质文件准备', description: '准备相关资质文件', owner: '行政专员', priority: 'high', deadlineOffset: 3, needsDeliverable: true, deliverableType: 'qualification' },
    { id: 'DT03', name: '技术方案编制', description: '编制技术方案', owner: '技术负责人', priority: 'high', deadlineOffset: 6, needsDeliverable: true, deliverableType: 'technical' },
    { id: 'DT04', name: '商务应答编制', description: '编制商务应答文件', owner: '商务负责人', priority: 'high', deadlineOffset: 6, needsDeliverable: true, deliverableType: 'document' },
    { id: 'DT05', name: '报价单制作', description: '制作报价单', owner: '财务', priority: 'high', deadlineOffset: 4, needsDeliverable: true, deliverableType: 'quotation' },
    { id: 'DT06', name: '内部评审', description: '组织内部评审', owner: '项目经理', priority: 'medium', deadlineOffset: 7, needsDeliverable: false },
  ],
}

export const deliverableTypeMap = {
  qualification: '资质文件',
  technical: '技术方案',
  document: '文档',
  quotation: '报价单',
  other: '其他',
}
