<template>
  <div class="settings-page">
    <div class="settings-hero">
      <div>
        <p class="eyebrow">Organization Settings</p>
        <h1>系统设置</h1>
        <p class="hero-copy">
          统一维护部门树、角色权限和用户组织归属，确保任务分配、Dashboard 团队负载和数据权限使用同一份组织主数据。
        </p>
      </div>
      <el-button :loading="pageLoading" type="primary" @click="loadAll">刷新配置</el-button>
    </div>

    <el-alert
      v-if="deptTree.length === 0"
      title="当前未配置部门树，团队任务分配会显示未配置组织关系。"
      type="warning"
      show-icon
      :closable="false"
      class="page-alert"
    />

    <el-tabs v-model="activeTab" class="settings-tabs">
      <el-tab-pane label="数据权限" name="departments">
        <DepartmentTreePanel
          v-loading="loading"
          :dept-tree="deptTree"
          :users="users"
          :save-handler="saveDepartments"
        />
      </el-tab-pane>
      <el-tab-pane label="角色权限" name="roles">
        <RoleManagementPanel
          v-loading="loading"
          :roles="roles"
          :dept-options="deptOptions"
          :save-handler="saveRole"
          :toggle-handler="toggleRole"
          :reset-handler="resetRole"
        />
      </el-tab-pane>
      <el-tab-pane label="接口权限矩阵" name="interface-permissions">
        <InterfacePermissionMatrixPanel />
      </el-tab-pane>
      <el-tab-pane
        v-if="isAdmin"
        label="任务状态字典"
        name="task-status-dict"
      >
        <TaskStatusDictPanel />
      </el-tab-pane>
      <el-tab-pane
        v-if="isAdmin"
        label="任务扩展字段"
        name="task-extended-fields"
      >
        <TaskExtendedFieldPanel />
      </el-tab-pane>
      <el-tab-pane label="用户组织归属" name="users">
        <UserOrganizationPanel
          v-loading="loading"
          :users="users"
          :dept-options="deptOptions"
          :roles="enabledRoles"
          :save-handler="saveUserOrganization"
        />
      </el-tab-pane>
      <el-tab-pane label="投标匹配评分" name="bid-match-scoring">
        <BidMatchScoringSettingsPanel
          :loading="bidScoringLoading"
          :saving="bidScoringSaving"
          :activating="bidScoringActivating"
          v-model:current-model="bidScoringCurrentModel"
          :weight-validation="bidScoringWeightValidation"
          :enabled-dimension-count="enabledBidScoringDimensionCount"
          :save="saveBidScoringSettings"
          :activate-current-model="activateBidScoringModel"
          :add-dimension="addBidScoringDimension"
          :remove-dimension="removeBidScoringDimension"
          :add-rule="addBidScoringRule"
          :remove-rule="removeBidScoringRule"
          :evidence-key-options="evidenceKeyOptions"
          :rule-type-options="ruleTypeOptions"
        />
      </el-tab-pane>
      <el-tab-pane v-if="canViewAuditLogs" label="审计日志" name="audit">
        <AuditLogPanel mode="audit" />
      </el-tab-pane>
      <el-tab-pane v-if="isAdmin" label="系统信息" name="system-info">
        <SystemInfoPanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import BidMatchScoringSettingsPanel from './settings/BidMatchScoringSettingsPanel.vue'
import DepartmentTreePanel from './settings/DepartmentTreePanel.vue'
import InterfacePermissionMatrixPanel from './settings/InterfacePermissionMatrixPanel.vue'
import RoleManagementPanel from './settings/RoleManagementPanel.vue'
import UserOrganizationPanel from './settings/UserOrganizationPanel.vue'
import AuditLogPanel from './settings/AuditLogPanel.vue'
import TaskStatusDictPanel from './settings/TaskStatusDictPanel.vue'
import TaskExtendedFieldPanel from './settings/TaskExtendedFieldPanel.vue'
import SystemInfoPanel from './settings/SystemInfoPanel.vue'
import { useOrganizationSettings } from './settings/useOrganizationSettings'
import {
  EVIDENCE_KEY_OPTIONS,
  RULE_TYPE_OPTIONS,
  useBidMatchScoringSettings
} from './settings/useBidMatchScoringSettings'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const canViewAuditLogs = computed(() => userStore.hasPermission('audit-logs') || userStore.hasPermission('all'))
const isAdmin = computed(() => userStore.hasPermission('all'))
const settingsTabNames = new Set([
  'departments',
  'roles',
  'interface-permissions',
  'task-status-dict',
  'task-extended-fields',
  'users',
  'bid-match-scoring',
  'audit',
  'system-info'
])
const getRouteTab = () => {
  const tab = typeof route.query.tab === 'string' ? route.query.tab : ''
  if (tab === 'audit' && !canViewAuditLogs.value) return 'departments'
  if (tab === 'task-status-dict' && !isAdmin.value) return 'departments'
  if (tab === 'task-extended-fields' && !isAdmin.value) return 'departments'
  return settingsTabNames.has(tab) ? tab : 'departments'
}
const activeTab = ref(getRouteTab())

const {
  loading,
  deptTree,
  deptOptions,
  users,
  roles,
  enabledRoles,
  load: loadOrganizationSettings,
  saveDepartments,
  saveUserOrganization,
  saveRole,
  toggleRole,
  resetRole
} = useOrganizationSettings()

const {
  loading: bidScoringLoading,
  saving: bidScoringSaving,
  activating: bidScoringActivating,
  currentModel: bidScoringCurrentModel,
  weightValidation: bidScoringWeightValidation,
  enabledDimensionCount: enabledBidScoringDimensionCount,
  load: loadBidScoringSettings,
  save: saveBidScoringSettings,
  activateCurrentModel: activateBidScoringModel,
  addDimension: addBidScoringDimension,
  removeDimension: removeBidScoringDimension,
  addRule: addBidScoringRule,
  removeRule: removeBidScoringRule,
} = useBidMatchScoringSettings()

const pageLoading = computed(() => loading.value || bidScoringLoading.value)
const evidenceKeyOptions = EVIDENCE_KEY_OPTIONS
const ruleTypeOptions = RULE_TYPE_OPTIONS

const loadAll = async () => {
  const results = await Promise.allSettled([
    loadOrganizationSettings(),
    loadBidScoringSettings()
  ])
  for (const result of results) {
    if (result.status === 'rejected') {
      console.warn('Settings page: a data source failed to load', result.reason)
    }
  }
}

watch(canViewAuditLogs, (allowed) => {
  if (!allowed && activeTab.value === 'audit') {
    activeTab.value = 'departments'
  }
}, { immediate: true })

watch(isAdmin, (allowed) => {
  if (!allowed && activeTab.value === 'task-status-dict') {
    activeTab.value = 'departments'
  }
  if (!allowed && activeTab.value === 'task-extended-fields') {
    activeTab.value = 'departments'
  }
  if (!allowed && activeTab.value === 'system-info') {
    activeTab.value = 'departments'
  }
}, { immediate: true })

onMounted(loadAll)

const TAB_REDIRECT_MAP = { 'ai-models': '/settings/ai-models', 'integration': '/settings/integration' }

watch(() => route.query.tab, (tab) => {
  if (typeof tab === 'string' && TAB_REDIRECT_MAP[tab]) {
    router.replace(TAB_REDIRECT_MAP[tab])
    return
  }
  activeTab.value = getRouteTab()
}, { immediate: true })
</script>

<style scoped>
.settings-page {
  min-height: 100vh;
  padding: 24px;
  background: var(--bg-page);
}

.settings-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: center;
  padding: 28px;
  margin-bottom: 18px;
  border: 1px solid rgba(67, 89, 55, 0.14);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 18px 45px rgba(48, 64, 37, 0.08);
}

.eyebrow {
  margin: 0 0 8px;
  color: #597044;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.settings-hero h1 {
  margin: 0;
  color: #1f2d1d;
  font-size: 32px;
}

.hero-copy {
  max-width: 760px;
  margin: 10px 0 0;
  color: #52624c;
  line-height: 1.7;
}

.page-alert {
  margin-bottom: 16px;
}

.settings-tabs {
  padding: 16px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.78);
}

@media (max-width: 768px) {
  .settings-page {
    padding: 14px;
  }

  .settings-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
