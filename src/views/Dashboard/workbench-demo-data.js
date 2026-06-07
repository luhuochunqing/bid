// Input: none
// Output: static demo data used by the Dashboard Workbench page
// Pos: src/views/Dashboard/ - Dashboard demo fixtures
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

export const demoProjects = [
  { id: 'P001', name: '某央企智慧办公平台', status: '编制中', progress: 45, deadline: '03-05', manager: '小王', priority: 'high' },
  { id: 'P002', name: '华南电力集团集采项目', status: '评审中', progress: 70, deadline: '03-10', manager: '张经理', priority: 'high' },
  { id: 'P003', name: 'XX区数字政府平台', status: '编制中', progress: 35, deadline: '03-15', manager: '张经理', priority: 'medium' },
  { id: 'P004', name: '西部云数据中心建设', status: '即将开标', progress: 95, deadline: '03-12', manager: '小王', priority: 'urgent' },
  { id: 'P005', name: '深圳地铁自动化系统', status: '编制中', progress: 55, deadline: '03-20', manager: '张经理', priority: 'medium' },
  { id: 'P006', name: '华东电网信息化项目', status: '评审中', progress: 60, deadline: '03-18', manager: '小王', priority: 'medium' },
  { id: 'P007', name: 'XX县政府数字平台', status: '编制中', progress: 25, deadline: '03-25', manager: '李工', priority: 'low' },
  { id: 'P008', name: '制造执行系统(MES)', status: '技术方案编写', progress: 40, deadline: '03-08', manager: '李工', priority: 'high' },
]

export const hotTenders = [
  { id: 'B001', title: '某央企智慧办公平台采购项目', budget: 500, region: '北京', aiScore: 92, scoreLevel: 'high', probability: 'high', probibilityText: '高概率' },
  { id: 'B003', title: 'XX市大数据中心建设项目', budget: 800, region: '深圳', aiScore: 85, scoreLevel: 'medium', probability: 'medium', probibilityText: '中等概率' },
  { id: 'B005', title: '某省政务云平台采购', budget: 1200, region: '杭州', aiScore: 78, scoreLevel: 'medium', probability: 'medium', probibilityText: '中等概率' },
]

export const followUpCustomers = [
  { id: 1, name: '陈总', company: '某央企信息部', status: '待拜访', statusType: 'warning' },
  { id: 2, name: '林经理', company: '华南电力集团', status: '跟进中', statusType: 'primary' },
  { id: 3, name: '王主任', company: 'XX市政府', status: '意向明确', statusType: 'success' },
  { id: 4, name: '赵工', company: '西部云科技', status: '需求确认', statusType: 'info' },
]

export const teamMembers = [
  { id: 1, name: '李工', tasks: [{ id: 1, title: '技术方案编写', priority: 'high' }, { id: 2, title: '需求分析', priority: 'medium' }, { id: 3, title: '文档整理', priority: 'low' }], workload: '95%', workloadLevel: 'high' },
  { id: 2, name: '王工', tasks: [{ id: 1, title: '商务方案', priority: 'high' }, { id: 2, title: '资质准备', priority: 'medium' }], workload: '70%', workloadLevel: 'medium' },
  { id: 3, name: '赵工', tasks: [{ id: 1, title: '标书制作', priority: 'medium' }], workload: '40%', workloadLevel: 'low' },
]

export const myTechnicalTasks = [
  { id: 1, title: '某央企项目技术方案编写', project: '某央企项目', deadline: '03-06', priority: 'high', done: false },
  { id: 2, title: 'MES项目需求分析', project: '制造执行系统', deadline: '03-08', priority: 'high', done: false },
  { id: 3, title: 'XX县项目技术文档整理', project: 'XX县项目', deadline: '03-09', priority: 'medium', done: false },
  { id: 4, title: '参与技术方案评审', project: '数字政府平台', deadline: '03-05', priority: 'medium', done: false },
]

export const pendingReviews = [
  { id: 1, title: '深圳地铁项目 - 技术方案', author: '王工', time: '今天 10:00' },
  { id: 2, title: '华东电网项目 - 需求文档', author: '张经理', time: '昨天 15:30' },
]

export const teamPerformance = [
  { dept: '华南销售部', size: 8, progress: 85, color: '#3B82F6', wins: 3, active: 5 },
  { dept: '投标管理部', size: 12, progress: 78, color: '#10B981', wins: 4, active: 8 },
  { dept: '技术部', size: 18, progress: 92, color: '#F59E0B', wins: 6, active: 12 },
  { dept: '商务部', size: 7, progress: 65, color: '#EF4444', wins: 2, active: 3 },
]

export const activities = [
  { id: 1, type: 'success', text: 'XX项目技术方案评审通过', time: '10分钟前' },
  { id: 2, type: 'warning', text: 'XX项目需要补充业绩材料', time: '1小时前' },
  { id: 3, type: 'info', text: '新标讯：XX市大数据平台采购', time: '2小时前' },
  { id: 4, type: 'success', text: 'XX县项目成功中标！', time: '昨天' },
]

export const quickActions = [
  { key: 'support', title: '标书支持申请', desc: '申请技术/商务支持', icon: 'Document', iconStyle: 'background: linear-gradient(135deg, #DBEAFE 0%, #BFDBFE 100%); color: #1E40AF;' },
  { key: 'borrow', title: '资质/合同借阅', desc: '申请借阅相关文件', icon: 'FolderOpened', iconStyle: 'background: linear-gradient(135deg, #D1FAE5 0%, #A7F3D0 100%); color: #059669;' },
  { key: 'expense', title: '投标费用申请', desc: '保证金/标书费', icon: 'Wallet', iconStyle: 'background: linear-gradient(135deg, #FEF3C7 0%, #FDE68A 100%); color: #D97706;' },
]
