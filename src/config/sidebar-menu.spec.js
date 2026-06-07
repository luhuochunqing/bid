import { hiddenApiMenuNames, roleMenuGroups, roleMenuOptions, sidebarMenuConfig } from './sidebar-menu'

describe('sidebar-menu config', () => {
  it('keeps role menu options aligned with visible top-level sidebar menus', () => {
    const visibleMenus = sidebarMenuConfig.filter((menu) => !hiddenApiMenuNames.has(menu.name))
    const topLevelOptions = roleMenuOptions.slice(0, visibleMenus.length)
    const workbenchOptions = roleMenuOptions.slice(visibleMenus.length)

    expect(topLevelOptions.map((item) => item.label)).toEqual([
      '工作台',
      '标讯中心',
      '投标项目',
      '知识库',
      '资源管理',
      'AI 智能中心',
      '数据分析',
      '系统设置'
    ])
    expect(topLevelOptions.map((item) => item.label)).not.toEqual(
      expect.arrayContaining(['操作日志', '审计日志'])
    )
    expect(workbenchOptions.map((item) => item.value)).toEqual([
      'dashboard:view_welcome_banner',
      'dashboard:view_metric_cards',
      'dashboard:view_calendar',
      'dashboard.quickStart',
      'dashboard:view_tender_list',
      'dashboard:view_technical_task',
      'dashboard:view_review_list',
      'dashboard:view_customer_followup',
      'dashboard:view_project_list',
      'dashboard:view_team_task',
      'dashboard:view_global_projects',
      'dashboard:view_active_projects',
      'dashboard:view_team_performance',
      'dashboard:view_approval_list',
      'dashboard:view_process_timeline',
      'dashboard:view_activity_list',
      'dashboard:view_priority_todos'
    ])
  })

  it('uses the same primary permission key as the top-level sidebar menu', () => {
    const visibleMenus = sidebarMenuConfig.filter((menu) => !hiddenApiMenuNames.has(menu.name))
    const topLevelOptions = roleMenuOptions.slice(0, visibleMenus.length)

    expect(topLevelOptions).toEqual(
      visibleMenus.map((menu) => ({
        value: menu.meta.permissionKeys[0],
        label: menu.meta.title
      }))
    )
    expect(new Set(roleMenuOptions.map((item) => item.value)).size).toBe(roleMenuOptions.length)
  })

  it('groups workbench secondary permissions under the dashboard menu', () => {
    const dashboardGroup = roleMenuGroups.find((group) => group.value === 'dashboard')

    expect(roleMenuGroups.map((group) => group.label)).toEqual([
      '工作台',
      '标讯中心',
      '投标项目',
      '知识库',
      '资源管理',
      'AI 智能中心',
      '数据分析',
      '系统设置'
    ])
    expect(dashboardGroup.children.map((item) => item.label)).toEqual([
      '欢迎横幅',
      '核心指标卡片',
      '工作日程日历',
      '快速发起',
      '标讯列表',
      '技术任务',
      '待评审列表',
      '客户跟进',
      '负责项目',
      '团队任务',
      '项目总览',
      '进行中项目',
      '团队绩效',
      '待审批列表',
      '流程时间线',
      '活动流',
      '优先级待办'
    ])
  })
})
