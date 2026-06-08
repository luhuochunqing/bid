export const hiddenApiMenuNames = new Set(['CustomerOpportunityCenter', 'OperationLogs', 'AuditLogs'])

export const sidebarMenuConfig = [
  {
    path: '/dashboard',
    name: 'Dashboard',
    meta: { title: '工作台', icon: 'workbench', permissionKeys: ['dashboard'] }
  },
  {
    path: '/bidding',
    name: 'Bidding',
    meta: { title: '标讯中心', icon: 'bidding', permissionKeys: ['bidding', 'bidding-list'] }
  },
  {
    path: '/project',
    name: 'Project',
    meta: { title: '投标项目', icon: 'project', permissionKeys: ['project'] },
    children: [
      {
        path: '/project',
        name: 'ProjectList',
        meta: { title: '项目列表', permissionKeys: ['project', 'project-list'] }
      }
    ]
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    meta: { title: '知识库', icon: 'knowledge', permissionKeys: ['knowledge'] },
    children: [
      {
        path: '/knowledge/archive',
        name: 'ProjectArchive',
        meta: { title: '档案台账', permissionKeys: ['knowledge', 'knowledge-archive'] }
      },
      {
        path: '/knowledge/qualification',
        name: 'Qualification',
        meta: { title: '资质列表', permissionKeys: ['knowledge', 'knowledge-qualification'] }
      },
      {
        path: '/knowledge/personnel',
        name: 'Personnel',
        meta: { title: '人员库', permissionKeys: ['knowledge', 'knowledge-personnel'] }
      },
      {
        path: '/knowledge/performance',
        name: 'Performance',
        meta: { title: '业绩库', permissionKeys: ['knowledge', 'knowledge-performance'] }
      },
      {
        path: '/knowledge/brand-auth',
        name: 'BrandAuth',
        meta: { title: '品牌授权', permissionKeys: ['knowledge', 'knowledge-brand-auth'] }
      },
      {
        path: '/knowledge/case',
        name: 'Case',
        meta: { title: '案例库', permissionKeys: ['knowledge', 'knowledge-case'] }
      },
      {
        path: '/knowledge/template',
        name: 'Template',
        meta: { title: '模板库', permissionKeys: ['knowledge', 'knowledge-template'] }
      }
    ]
  },
  {
    path: '/resource',
    name: 'Resource',
    meta: { title: '资源管理', icon: 'resource', permissionKeys: ['resource'] },
    children: [
      {
        path: '/resource/bar',
        name: 'BAR',
        meta: { title: '资产台账', permissionKeys: ['resource', 'resource-bar'] }
      },
      {
        path: '/resource/bar/sites',
        name: 'BAR_SiteList',
        meta: { title: '站点台账', permissionKeys: ['resource', 'resource-bar'] }
      },
      {
        path: '/resource/margin',
        name: 'MarginManagement',
        meta: { title: '保证金管理', permissionKeys: ['resource', 'resource-expense'] }
      },
      {
        path: '/resource/expense',
        name: 'Expense',
        meta: { title: '费用管理', permissionKeys: ['resource', 'resource-expense'] }
      },
      {
        path: '/resource/account',
        name: 'Account',
        meta: { title: '账户管理', permissionKeys: ['resource', 'resource-account'] }
      },
      {
        path: '/resource/ca-management',
        name: 'CAManagement',
        meta: { title: 'CA 管理', permissionKeys: ['resource', 'resource-ca'] }
      },
      {
        path: '/resource/contract-borrow',
        name: 'ContractBorrow',
        meta: { title: '合同借阅', permissionKeys: ['resource'] }
      },
      {
        path: '/resource/bid-result',
        name: 'BidResult',
        meta: { title: '结果闭环', permissionKeys: ['resource'] }
      }
    ]
  },
  {
    path: '/ai-center',
    name: 'AICenter',
    meta: { title: 'AI 智能中心', icon: 'ai-center', permissionKeys: ['ai-center'] }
  },
  {
    path: '/analytics/dashboard',
    name: 'AnalyticsDashboard',
    meta: { title: '数据分析', icon: 'analytics', permissionKeys: ['analytics', 'analytics-dashboard'] }
  },
  {
    path: '/operation-logs',
    name: 'OperationLogs',
    meta: { title: '操作日志', icon: 'history', permissionKeys: ['operation-logs'] }
  },
  {
    path: '/audit-logs',
    name: 'AuditLogs',
    meta: { title: '审计日志', icon: 'lock', permissionKeys: ['audit-logs'] }
  },
  {
    path: '/settings',
    name: 'Settings',
    meta: { title: '系统设置', icon: 'settings', permissionKeys: ['settings'] },
    children: [
      {
        path: '/settings',
        name: 'SettingsRoot',
        meta: { title: '组织设置', permissionKeys: ['settings'] }
      },
      {
        path: '/settings/organization',
        name: 'OrganizationManagement',
        meta: { title: '组织架构', permissionKeys: ['settings'] }
      },
      {
        path: '/settings/workflow-forms',
        name: 'WorkflowFormDesigner',
        meta: { title: '流程表单配置', permissionKeys: ['settings', 'settings-workflow-forms'] }
      },
      {
        path: '/settings/alert-rules',
        name: 'AlertRules',
        meta: { title: '告警规则', permissionKeys: ['settings'] }
      },
      {
        path: '/settings/alert-history',
        name: 'AlertHistory',
        meta: { title: '告警历史', permissionKeys: ['settings'] }
      }
    ]
  }
]

export const workbenchRoleMenuChildren = [
  { value: 'dashboard:view_welcome_banner', label: '欢迎横幅', fullLabel: '工作台：欢迎横幅' },
  { value: 'dashboard:view_metric_cards', label: '核心指标卡片', fullLabel: '工作台：核心指标卡片' },
  { value: 'dashboard:view_calendar', label: '工作日程日历', fullLabel: '工作台：工作日程日历' },
  { value: 'dashboard.quickStart', label: '快速发起', fullLabel: '工作台：快速发起' },
  { value: 'dashboard:view_tender_list', label: '标讯列表', fullLabel: '工作台：标讯列表' },
  { value: 'dashboard:view_technical_task', label: '技术任务', fullLabel: '工作台：技术任务' },
  { value: 'dashboard:view_review_list', label: '待评审列表', fullLabel: '工作台：待评审列表' },
  { value: 'dashboard:view_customer_followup', label: '客户跟进', fullLabel: '工作台：客户跟进' },
  { value: 'dashboard:view_project_list', label: '负责项目', fullLabel: '工作台：负责项目' },
  { value: 'dashboard:view_team_task', label: '团队任务', fullLabel: '工作台：团队任务' },
  { value: 'dashboard:view_global_projects', label: '项目总览', fullLabel: '工作台：项目总览' },
  { value: 'dashboard:view_active_projects', label: '进行中项目', fullLabel: '工作台：进行中项目' },
  { value: 'dashboard:view_team_performance', label: '团队绩效', fullLabel: '工作台：团队绩效' },
  { value: 'dashboard:view_approval_list', label: '待审批列表', fullLabel: '工作台：待审批列表' },
  { value: 'dashboard:view_process_timeline', label: '流程时间线', fullLabel: '工作台：流程时间线' },
  { value: 'dashboard:view_activity_list', label: '活动流', fullLabel: '工作台：活动流' },
  { value: 'dashboard:view_priority_todos', label: '优先级待办', fullLabel: '工作台：优先级待办' }
]

export const roleMenuGroups = sidebarMenuConfig
  .filter((menu) => !hiddenApiMenuNames.has(menu.name))
  .map((menu) => ({
    value: menu.meta.permissionKeys[0],
    label: menu.meta.title,
    children: menu.name === 'Dashboard' ? workbenchRoleMenuChildren : []
  }))

export const roleMenuOptions = [
  ...roleMenuGroups.map((group) => ({ value: group.value, label: group.label })),
  ...roleMenuGroups.flatMap((group) => group.children.map((child) => ({
    value: child.value,
    label: child.fullLabel || `${group.label}：${child.label}`
  })))
]
