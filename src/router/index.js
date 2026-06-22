import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { hasStoredUserHint } from '@/api/session.js'
import { registerLoginNavigator } from './sessionNavigation.js'
import { hasAllPermissions } from '@/utils/permission'
import { sidebarMenuConfig } from '@/config/sidebar-menu'

const DEFAULT_AUTHENTICATED_HOME = '/dashboard'
const HIDDEN_API_ROUTES = new Set(['CustomerOpportunityCenter', '/bidding/customer-opportunities'])

const getRequiredPermissions = (to) => {
  const permissions = to.matched.flatMap((record) => record.meta?.permissionKeys || [])
  return [...new Set(permissions)]
}

const hasRouteAccess = (to, userStore) => {
  const requiredPermissions = getRequiredPermissions(to)

  if (requiredPermissions.length > 0) {
    return hasAllPermissions(userStore.menuPermissions, requiredPermissions)
  }

  return true
}

const getFirstAccessiblePath = (userStore) => {
  // CO-210 Fix: Check if user has any permissions at all
  const perms = userStore.menuPermissions || []
  if (perms.length === 0 && !perms.includes('all')) {
    // User has no permissions - redirect to a safe fallback instead of /dashboard
    // This prevents infinite redirect loops when non-admin roles have empty menuPermissions
    return '/no-permission'
  }

  if (userStore.hasPermission('dashboard')) return DEFAULT_AUTHENTICATED_HOME
  for (const menu of sidebarMenuConfig) {
    if (hasAllPermissions(userStore.menuPermissions, menu.meta?.permissionKeys)) {
      return menu.path
    }
  }
  // CO-210 Fix: If no accessible menu found, don't default to /dashboard
  // Instead, return to login or a safe page
  return '/login'
}

const isHiddenApiRoute = (to) => {
  if (!to) return false
  if (typeof to.name === 'string' && HIDDEN_API_ROUTES.has(to.name)) {
    return true
  }
  return HIDDEN_API_ROUTES.has(to.path)
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/no-permission',
    name: 'NoPermission',
    component: () => import('@/views/Common/NoPermission.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/',
    component: () => import('@/components/layout/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/dashboard'
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard/Workbench.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'bidding',
        name: 'Bidding',
        component: () => import('@/views/Bidding/List.vue'),
        meta: { title: '标讯中心' }
      },
      {
        path: 'bidding/customer-opportunities',
        name: 'CustomerOpportunityCenter',
        redirect: '/bidding',
        meta: { title: '客户商机中心', icon: 'bidding' }
      },
      {
        path: 'bidding/:id',
        name: 'BiddingDetail',
        component: () => import('@/views/Bidding/Detail.vue'),
        meta: { title: '标讯详情', showBack: true }
      },
      {
        path: 'bidding/ai-analysis/:id',
        name: 'BiddingAIAnalysis',
        component: () => import('@/views/Bidding/AIAnalysis.vue'),
        meta: { title: 'AI分析', requiresAuth: true, showBack: true }
      },
      {
        path: 'bidding/create',
        name: 'BiddingCreate',
        component: () => import('@/views/Bidding/TenderCreatePage.vue'),
        meta: { title: '新建标讯', showBack: true }
      },
      {
        path: 'ai-center',
        name: 'AICenter',
        component: () => import('@/views/AI/Center.vue'),
        meta: { title: 'AI能力中心', icon: 'MagicStick' }
      },
      {
        path: 'ai-center/solution-reuse',
        name: 'SolutionReuse',
        component: () => import('@/views/AI/SolutionReuse.vue'),
        meta: { title: '历史方案提取与复用', showBack: true }
      },
      {
        path: 'ai-center/market-timing',
        name: 'MarketTiming',
        component: () => import('@/views/AI/MarketTiming.vue'),
        meta: { title: '商机时间预测', showBack: true }
      },
      {
        path: 'project',
        name: 'ProjectList',
        component: () => import('@/views/Project/List.vue'),
        meta: { title: '投标项目' }
      },
      {
        path: 'project/create',
        redirect: '/project'
      },
      {
        path: 'project/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/Project/Detail.vue'),
        meta: { title: '项目详情', showBack: true }
      },
      {
        path: 'knowledge',
        component: () => import('@/views/Knowledge/KbLayout.vue'),
        meta: { requiresAuth: true },
        children: [
          // 默认重定向到 qualification（行政人员等小权限用户的唯一可访问子项）。
          // 路由守卫会在 archive/personnel/case 等子项上拦截无权限用户，
          // 但默认重定向必须指向"必然有权限"的目标，否则新登录用户会被踢到 403。
          { path: '', redirect: 'qualification' },
          {
            path: 'archive',
            name: 'ProjectArchive',
            component: () => import('@/views/Knowledge/views/ProjectArchive.vue'),
            meta: { title: '项目档案', permissionKeys: ['knowledge', 'knowledge-archive'] }
          },
          {
            path: 'qualification',
            name: 'Qualification',
            alias: '/knowledge/qualifications',
            component: () => import('@/views/Knowledge/Qualification.vue'),
            meta: { title: '资质证书', permissionKeys: ['knowledge', 'knowledge-qualification'] }
          },
          {
            path: 'personnel',
            name: 'Personnel',
            component: () => import('@/views/Knowledge/Personnel.vue'),
            meta: { title: '人员证书', permissionKeys: ['knowledge', 'knowledge-personnel'] }
          },
          {
            path: 'performance',
            name: 'Performance',
            component: () => import('@/views/Knowledge/Performance.vue'),
            meta: { title: '业绩管理', permissionKeys: ['knowledge', 'knowledge-performance'] }
          },
          {
            path: 'brand-auth',
            name: 'BrandAuth',
            component: () => import('@/views/Knowledge/BrandAuth.vue'),
            meta: { title: '品牌授权', permissionKeys: ['knowledge', 'knowledge-brand-auth'] }
          },
          {
            path: 'warehouse',
            name: 'Warehouse',
            component: () => import('@/views/Knowledge/Warehouse.vue'),
            meta: { title: '仓库信息', permissionKeys: ['knowledge', 'knowledge-warehouse'] }
          },
          {
            path: 'case',
            name: 'Case',
            alias: '/knowledge/cases',
            component: () => import('@/views/Knowledge/views/CaseWrapper.vue'),
            meta: { title: '案例库', permissionKeys: ['knowledge', 'knowledge-case'] }
          },
          {
            path: 'template',
            name: 'Template',
            alias: '/knowledge/templates',
            component: () => import('@/views/Knowledge/Template.vue'),
            meta: { title: '模板库', permissionKeys: ['knowledge', 'knowledge-template'] }
          },
        ]
      },
      {
        path: 'resource',
        redirect: 'resource/margin'
      },
      {
        path: 'resource/margin',
        name: 'MarginManagement',
        component: () => import('@/views/Resource/MarginManagement.vue'),
        meta: { title: '保证金管理' }
      },
      {
        path: 'resource/expense',
        name: 'Expense',
        component: () => import('@/views/Resource/Expense.vue'),
        meta: { title: '费用管理' }
      },
      {
        path: 'resource/account',
        name: 'Account',
        component: () => import('@/views/Resource/Account.vue'),
        meta: { title: '账户管理' }
      },
      {
        path: 'resource/ca-management',
        name: 'CAManagement',
        component: () => import('@/views/Resource/CAManagement.vue'),
        meta: { title: 'CA信息管理', requiresAuth: true }
      },
      {
        path: 'resource/contract-borrow',
        name: 'ContractBorrow',
        component: () => import('@/views/Resource/ContractBorrow.vue'),
        meta: { title: '合同借阅' }
      },
      {
        path: 'resource/bid-result',
        name: 'BidResult',
        component: () => import('@/views/Resource/BidResult.vue'),
        meta: { title: '投标结果闭环' }
      },
      // BAR 投标资产台账
      {
        path: 'resource/bar',
        name: 'BAR',
        component: () => import('@/views/Resource/BAR/CheckPanel.vue'),
        meta: { title: '可投标能力检查' }
      },
      {
        path: 'resource/bar/sites',
        name: 'BAR_SiteList',
        component: () => import('@/views/Resource/BAR/SiteList.vue'),
        meta: { title: '站点台账', showBack: true }
      },
      {
        path: 'resource/bar/site/:id',
        name: 'BAR_SiteDetail',
        component: () => import('@/views/Resource/BAR/SiteDetail.vue'),
        meta: { title: '站点详情', showBack: true }
      },
      {
        path: 'resource/bar/sop/:siteId',
        name: 'BAR_SOPDetail',
        component: () => import('@/views/Resource/BAR/SOPDetail.vue'),
        meta: { title: '找回SOP', showBack: true }
      },
      {
        path: 'analytics/dashboard',
        name: 'AnalyticsDashboard',
        component: () => import('@/views/Analytics/Dashboard.vue'),
        meta: { title: '数据分析', permissionKeys: ['analytics', 'analytics-dashboard'] }
      },
      {
        path: 'analytics',
        redirect: '/analytics/dashboard'
      },
      {
        path: 'task-board',
        name: 'TaskBoard',
        component: () => import('@/views/TaskBoard/TaskBoardPage.vue'),
        meta: { title: '任务看板', permissionKeys: ['task-board'] }
      },
      {
        path: 'operation-logs',
        name: 'OperationLogs',
        component: () => import('@/views/System/OperationLogPage.vue'),
        meta: { title: '日志说明', showBack: true }
      },
      {
        path: 'audit-logs',
        name: 'AuditLogs',
        component: () => import('@/views/System/AuditLogPage.vue'),
        meta: { title: '审计日志', permissionKeys: ['audit-logs'], showBack: true }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/System/Settings.vue'),
        meta: { title: '系统设置', permissionKeys: ['settings'], requiresAuth: true }
      },
      {
        path: 'settings/organization',
        name: 'OrganizationManagement',
        component: () => import('@/views/System/organization/OrganizationManagement.vue'),
        meta: { title: '组织架构', permissionKeys: ['settings'], activeMenu: '/settings', showBack: true }
      },
      {
        path: 'settings/workflow-forms',
        name: 'WorkflowFormDesigner',
        component: () => import('@/views/System/WorkflowFormDesigner.vue'),
        meta: { title: '流程表单配置', permissionKeys: ['settings', 'settings-workflow-forms'], activeMenu: '/settings', showBack: true }
      },
      {
        path: 'settings/alert-rules',
        name: 'AlertRules',
        component: () => import('@/views/System/AlertRules.vue'),
        meta: { title: '告警规则', permissionKeys: ['settings'], showBack: true }
      },
      {
        path: 'settings/alert-history',
        name: 'AlertHistory',
        component: () => import('@/views/System/AlertHistory.vue'),
        meta: { title: '告警历史', permissionKeys: ['settings'], showBack: true }
      },
      {
        path: 'settings/ai-models',
        name: 'AiModels',
        component: () => import('@/views/System/AiModelSettings.vue'),
        meta: { title: 'AI能力模型', permissionKeys: ['settings'], showBack: true }
      },
      {
        path: 'settings/messages-tasks',
        name: 'MessagesTasks',
        component: () => import('@/views/System/MessagesTasks.vue'),
        meta: { title: '消息与任务', permissionKeys: ['settings'], showBack: true }
      },
      {
        path: 'settings/integration',
        name: 'SystemIntegration',
        component: () => import('@/views/System/SystemIntegration.vue'),
        meta: { title: '系统集成', permissionKeys: ['settings'], showBack: true }
      },
      {
        path: 'inbox',
        name: 'Inbox',
        component: () => import('@/views/NotificationInbox.vue'),
        meta: { title: '通知中心' }
      },
      {
        path: 'bidding/keyword-subscription',
        name: 'KeywordSubscription',
        component: () => import('@/views/Bidding/KeywordSubscription.vue'),
        meta: { title: '关键词订阅', permissionKeys: ['bidding', 'bidding-list'], showBack: true }
      },
      {
        path: 'document/editor/:id',
        name: 'DocumentEditor',
        component: () => import('@/views/Document/Editor.vue'),
        meta: { title: '标书编辑器', requiresAuth: true, showBack: true }
      }
    ]
  }
]

// 检查路由链中是否有 requiresAuth
const isRequiresAuthRoute = (route) => {
  return route.matched.some((r) => r.meta?.requiresAuth)
}

const router = createRouter({
  history: createWebHistory(),
  routes
})

registerLoginNavigator(async () => {
  if (router.currentRoute.value.path === '/login') {
    return
  }

  if (typeof window !== 'undefined' && Object.prototype.hasOwnProperty.call(window, 'routerPushCalled')) {
    window.routerPushCalled = true
  }

  await router.push('/login')
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  let hasAuthState = Boolean(userStore.currentUser)
  const shouldAttemptRestore = isRequiresAuthRoute(to) || hasAuthState || (to.path === '/login' && hasStoredUserHint())

  if (!userStore.hasRestoredSession && shouldAttemptRestore) {
    await userStore.restoreSession()
    hasAuthState = Boolean(userStore.currentUser)
  }

  if (isRequiresAuthRoute(to) && !hasAuthState) {
    next('/login')
  } else if (hasAuthState && isHiddenApiRoute(to)) {
    next('/bidding')
  } else if (to.path === '/login' && hasAuthState) {
    next(DEFAULT_AUTHENTICATED_HOME)
  } else if (hasAuthState && !hasRouteAccess(to, userStore)) {
    next(getFirstAccessiblePath(userStore))
  } else {
    next()
  }
})

export default router
