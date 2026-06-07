<!-- Input: organization integration operations composable + YAPI directory config form -->
<!-- Output: compact API-only organization operations card with YAPI directory configuration collapse panel -->
<!-- Pos: src/views/System/settings/integration/ -->
<!-- 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->

<template>
  <div class="organization-card">
    <div class="card-toolbar">
      <div>
        <p class="card-kicker">Organization</p>
        <h3 class="card-title">组织架构系统</h3>
      </div>
      <div class="toolbar-actions">
        <el-button :loading="loading" @click="load">刷新状态</el-button>
        <el-button type="primary" :loading="syncing" :disabled="!canOperate" @click="startSyncRun">窗口对账</el-button>
      </div>
    </div>

    <el-alert
      v-if="errorText"
      class="status-alert"
      type="error"
      :title="errorText"
      :closable="false"
      show-icon
    />

    <div v-loading="loading" class="status-grid">
      <div class="status-cell">
        <span class="status-label">接入开关</span>
        <el-tag :type="enabledTagType" effect="plain">
          {{ enabledText }}
        </el-tag>
      </div>
      <div class="status-cell">
        <span class="status-label">SDK 订阅</span>
        <el-tag :type="status?.eventSdkEnabled ? 'success' : 'warning'" effect="plain">
          {{ loaded ? (status?.eventSdkEnabled ? '已启用' : '待接入') : '--' }}
        </el-tag>
      </div>
      <div class="status-cell">
        <span class="status-label">待重试</span>
        <strong>{{ countText(status?.pendingRetryCount) }}</strong>
      </div>
      <div class="status-cell">
        <span class="status-label">死信</span>
        <strong>{{ countText(status?.deadLetterCount) }}</strong>
      </div>
    </div>

    <div class="last-run">
      <span class="status-label">最近同步</span>
      <span>{{ lastRunText }}</span>
    </div>

    <el-collapse v-model="activeCollapse" class="yapi-config-collapse">
      <el-collapse-item title="YAPI 目录配置" name="yapi">
        <YapiDirectoryConfigForm />
      </el-collapse-item>
    </el-collapse>

    <div class="resync-grid">
      <div class="resync-operation">
        <el-input v-model="userId" placeholder="userId" clearable />
        <el-button :loading="resyncingUser" :disabled="!canOperate" @click="resyncUser">重同步用户</el-button>
      </div>
      <div class="resync-operation">
        <el-input v-model="deptId" placeholder="deptId" clearable />
        <el-button :loading="resyncingDepartment" :disabled="!canOperate" @click="resyncDepartment">重同步部门</el-button>
      </div>
      <div class="resync-operation">
        <el-input v-model="deadLetterEventKey" placeholder="eventKey" clearable />
        <el-button :loading="replayingDeadLetter" :disabled="!canOperate" @click="replayDeadLetter">重放死信</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useOrganizationIntegrationOperations } from '../useOrganizationIntegrationOperations.js'
import YapiDirectoryConfigForm from './YapiDirectoryConfigForm.vue'

const {
  loading,
  syncing,
  resyncingUser,
  resyncingDepartment,
  replayingDeadLetter,
  status,
  loaded,
  errorText,
  canOperate,
  userId,
  deptId,
  deadLetterEventKey,
  load,
  startSyncRun,
  resyncUser,
  resyncDepartment,
  replayDeadLetter,
} = useOrganizationIntegrationOperations()

const activeCollapse = ref([])

const enabledText = computed(() => {
  if (errorText.value) return '状态未知'
  if (!loaded.value) return '--'
  return status.value?.enabled ? '已启用' : '未启用'
})

const enabledTagType = computed(() => {
  if (errorText.value) return 'danger'
  if (!loaded.value) return 'info'
  return status.value?.enabled ? 'success' : 'info'
})

const lastRunText = computed(() => {
  if (!loaded.value || errorText.value) return '--'
  const run = status.value?.lastRun
  if (!run) return '暂无记录'
  return `${run.runType} / ${run.status} / ${run.successCount ?? 0}/${run.totalCount ?? 0}`
})

const countText = (value) => (loaded.value && !errorText.value ? value ?? 0 : '--')

onMounted(load)
</script>

<style scoped>
.organization-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 24px;
  border: 1px solid rgba(67, 89, 55, 0.14);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
}

.card-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(67, 89, 55, 0.1);
}

.toolbar-actions {
  display: flex;
  gap: 10px;
}

.toolbar-actions :deep(.el-button + .el-button),
.resync-operation :deep(.el-button + .el-button) {
  margin-left: 0;
}

.card-kicker {
  margin: 0 0 4px;
  color: #6d7d5d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.card-title {
  margin: 0;
  color: #1f2d1d;
  font-size: 18px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.resync-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.resync-operation {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.status-cell,
.last-run {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 40px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(67, 89, 55, 0.05);
}

.status-label {
  color: #52624c;
  font-size: 13px;
}

.last-run {
  justify-content: flex-start;
}

.status-alert {
  --el-alert-border-radius-base: 8px;
}

.yapi-config-collapse {
  border: none;
  background: transparent;
}

.yapi-config-collapse :deep(.el-collapse-item__header) {
  height: 40px;
  padding: 0 12px;
  border-radius: 8px;
  background: rgba(67, 89, 55, 0.05);
  color: #1f2d1d;
  font-weight: 600;
  font-size: 14px;
  border: none;
}

.yapi-config-collapse :deep(.el-collapse-item__wrap) {
  border: none;
  background: transparent;
}

.yapi-config-collapse :deep(.el-collapse-item__content) {
  padding: 12px 0 4px;
}

@media (max-width: 920px) {
  .status-grid {
    grid-template-columns: 1fr 1fr;
  }

  .resync-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .card-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolbar-actions,
  .status-grid,
  .resync-grid {
    width: 100%;
  }

  .toolbar-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-actions :deep(.el-button) {
    width: 100%;
  }

  .status-grid,
  .resync-grid {
    grid-template-columns: 1fr;
  }

  .resync-operation {
    grid-template-columns: 1fr;
  }

  .resync-operation :deep(.el-button) {
    width: 100%;
  }
}
</style>
