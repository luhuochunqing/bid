export const hiddenApiMenuNames = new Set(['CustomerOpportunityCenter', 'AuditLogs', 'BAR', 'BAR_SiteList', 'Expense', 'ContractBorrow', 'BidResult', 'AlertRules', 'AlertHistory', 'Template'])

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
        meta: { title: '项目列表', permissionKeys: ['project-list'] }
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
        meta: { title: '项目档案', permissionKeys: ['knowledge-archive'] }
      },
      {
        path: '/knowledge/case',
        name: 'Case',
        meta: { title: '案例库', permissionKeys: ['knowledge-case'] }
      },
      {
        path: '/knowledge/qualification',
        name: 'Qualification',
        meta: { title: '资质证书', permissionKeys: ['knowledge-qualification'] }
      },
      {
        path: '/knowledge/personnel',
        name: 'Personnel',
        meta: { title: '人员证书', permissionKeys: ['knowledge-personnel'] }
      },
      {
        path: '/knowledge/warehouse',
        name: 'Warehouse',
        meta: { title: '仓库信息', permissionKeys: ['knowledge-warehouse'] }
      },
      {
        path: '/knowledge/performance',
        name: 'Performance',
        meta: { title: '业绩管理', permissionKeys: ['knowledge-performance'] }
      },
      {
        path: '/knowledge/brand-auth',
        name: 'BrandAuth',
        meta: { title: '品牌授权', permissionKeys: ['knowledge-brand-auth'] }
      }
    ]
  },
  {
    path: '/resource',
    name: 'Resource',
    meta: { title: '资源管理', icon: 'resource', permissionKeys: ['resource'] },
    children: [
      {
        path: '/resource/margin',
        name: 'MarginManagement',
        meta: { title: '保证金管理', permissionKeys: ['resource-expense'] }
      },
      {
        path: '/resource/account',
        name: 'Account',
        meta: { title: '账户管理', permissionKeys: ['resource-account'] }
      },
      {
        path: '/resource/ca-management',
        name: 'CAManagement',
        meta: { title: 'CA信息管理', permissionKeys: ['resource-ca'] }
      }
    ]
  },
  {
    path: '/analytics/dashboard',
    name: 'AnalyticsDashboard',
    meta: { title: '数据分析', icon: 'analytics', permissionKeys: ['analytics-dashboard'] }
  },
  {
    path: '/task-board',
    name: 'TaskBoard',
    meta: { title: '任务看板', icon: 'task', permissionKeys: ['task-board'] }
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
        meta: { title: '流程表单配置', permissionKeys: ['settings-workflow-forms'] }
      },
      {
        path: '/settings/messages-tasks',
        name: 'MessagesTasks',
        meta: { title: '消息与任务', permissionKeys: ['settings-messages-tasks'] }
      },
      {
        path: '/settings/ai-models',
        name: 'AiModels',
        meta: { title: 'AI能力模型', permissionKeys: ['settings-ai-models'] }
      },
      {
        path: '/ai-center',
        name: 'AICenter',
        meta: { title: 'AI能力中心', permissionKeys: ['ai-center'] }
      },
      {
        path: '/settings/integration',
        name: 'SystemIntegration',
        meta: { title: '系统集成', permissionKeys: ['settings-integration'] }
      },
      {
        path: '/operation-logs',
        name: 'OperationLogs',
        meta: { title: '日志说明', permissionKeys: ['operation-logs'] }
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
    children: menu.children && menu.children.length
      ? menu.children
          .filter((child) => !hiddenApiMenuNames.has(child.name))
          .map((child) => ({
            value: child.meta.permissionKeys.length > 1
              ? child.meta.permissionKeys[child.meta.permissionKeys.length - 1]
              : child.name !== menu.name
                ? child.name.replace(/([A-Z])/g, '-$1').replace(/^-/, '').toLowerCase()
                : menu.meta.permissionKeys[0],
            label: child.meta.title,
            fullLabel: `${menu.meta.title}：${child.meta.title}`
          }))
      : menu.name === 'Dashboard'
        ? workbenchRoleMenuChildren
        : []
  }))

export const roleMenuOptions = [
  ...roleMenuGroups.map((group) => ({ value: group.value, label: group.label })),
  ...roleMenuGroups.flatMap((group) => group.children.map((child) => ({
    value: child.value,
    label: child.fullLabel || `${group.label}：${child.label}`
  })))
]
