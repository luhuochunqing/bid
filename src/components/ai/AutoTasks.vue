<template>
  <el-dialog
    v-model="dialogVisible"
    title="自动化节点"
    width="800px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-tabs v-model="activeTab" class="auto-tasks-tabs">
      <!-- 自动化规则配置 -->
      <el-tab-pane label="自动化规则" name="rules">
        <div class="rules-section">
          <div class="section-header">
            <span class="section-title">已配置的自动化规则</span>
            <el-button type="primary" size="small" @click="handleAddRule">
              <el-icon><Plus /></el-icon>
              新增规则
            </el-button>
          </div>

          <el-table :data="rulesData" border stripe>
            <el-table-column prop="trigger" label="触发条件" width="180" />
            <el-table-column prop="action" label="自动动作" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-switch
                  v-model="row.enabled"
                  @change="handleToggleRule(row)"
                  :loading="row.loading"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click="handleEditRule(row)"
                >
                  编辑
                </el-button>
                <el-button
                  type="danger"
                  link
                  size="small"
                  @click="handleDeleteRule(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty
            v-if="rulesData.length === 0"
            description="暂无自动化规则"
          />

          <div class="rules-tip">
            <el-icon><InfoFilled /></el-icon>
            <span>自动化规则将在满足触发条件时自动执行相应的提醒动作</span>
          </div>
        </div>
      </el-tab-pane>

      <!-- 待处理提醒 -->
      <el-tab-pane name="reminders">
        <template #label>
          <span class="tab-label">
            待处理提醒
            <el-badge v-if="pendingReminders.length > 0" :value="pendingReminders.length" class="badge" />
          </span>
        </template>

        <div class="reminders-section">
          <div class="section-header">
            <span class="section-title">待处理的自动化提醒</span>
            <div class="filter-group">
              <el-radio-group v-model="urgencyFilter" size="small">
                <el-radio-button value="all">全部</el-radio-button>
                <el-radio-button value="high">紧急</el-radio-button>
                <el-radio-button value="medium">一般</el-radio-button>
                <el-radio-button value="low">低</el-radio-button>
              </el-radio-group>
            </div>
          </div>

          <el-table :data="filteredReminders" border stripe>
            <el-table-column label="优先级" width="80" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="getUrgencyType(row.urgency)"
                  size="small"
                >
                  {{ getUrgencyLabel(row.urgency) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="task" label="提醒事项" />
            <el-table-column label="触发时间" width="170">
              <template #default="{ row }">
                <div class="due-time">
                  <el-icon><Clock /></el-icon>
                  {{ formatDueTime(row.dueTime) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" align="center">
              <template #default="{ row }">
                <el-button
                  type="success"
                  size="small"
                  @click="handleComplete(row)"
                  :loading="row.loading"
                >
                  处理
                </el-button>
                <el-button
                  type="warning"
                  size="small"
                  @click="handleSnooze(row)"
                >
                  延期
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="filteredReminders.length === 0" description="暂无待处理提醒" />
        </div>
      </el-tab-pane>

      <!-- 执行历史 -->
      <el-tab-pane label="执行历史" name="history">
        <div class="history-section">
          <el-timeline>
            <el-timeline-item
              v-for="item in executionHistory"
              :key="item.id"
              :timestamp="item.timestamp"
              :type="item.status === 'success' ? 'success' : 'warning'"
              placement="top"
            >
              <div class="history-item">
                <div class="history-title">{{ item.action }}</div>
                <div class="history-detail">{{ item.detail }}</div>
                <div class="history-result">
                  <el-tag :type="item.status === 'success' ? 'success' : 'danger'" size="small">
                    {{ item.status === 'success' ? '执行成功' : '执行失败' }}
                  </el-tag>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>

          <el-empty v-if="executionHistory.length === 0" description="暂无执行历史" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" @click="handleRefresh">刷新</el-button>
    </template>

    <!-- 新增/编辑规则对话框 -->
    <el-dialog
      v-model="ruleDialogVisible"
      :title="editingRule ? '编辑规则' : '新增规则'"
      width="500px"
      append-to-body
    >
      <el-form :model="ruleForm" label-width="100px">
        <el-form-item label="触发条件">
          <el-input v-model="ruleForm.trigger" placeholder="如: 开标前3天" />
        </el-form-item>
        <el-form-item label="自动动作">
          <el-input v-model="ruleForm.action" placeholder="如: 提醒确认投标保证金" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="ruleForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRule">确定</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Plus, InfoFilled, Clock } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  projectId: {
    type: String,
    default: ''
  },
  data: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:modelValue', 'refresh'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const activeTab = ref('rules')
const urgencyFilter = ref('all')
const ruleDialogVisible = ref(false)
const editingRule = ref(null)

const getSectionData = (key) => {
  const provided = props.data?.[key]

  if (Array.isArray(provided)) {
    return provided
  }

  return []
}

const rulesData = ref([])
const pendingReminders = ref([])
const executionHistory = ref([])

const syncAutoTaskData = () => {
  rulesData.value = getSectionData('rules')
  pendingReminders.value = getSectionData('pendingReminders')
  executionHistory.value = getSectionData('executionHistory')
}

watch(
  () => props.data,
  () => {
    syncAutoTaskData()
  },
  { immediate: true, deep: true }
)

const ruleForm = ref({
  trigger: '',
  action: '',
  enabled: true
})

const filteredReminders = computed(() => {
  if (urgencyFilter.value === 'all') {
    return pendingReminders.value
  }
  return pendingReminders.value.filter(r => r.urgency === urgencyFilter.value)
})

const getUrgencyType = (urgency) => {
  const types = {
    high: 'danger',
    medium: 'warning',
    low: 'info'
  }
  return types[urgency] || 'info'
}

const getUrgencyLabel = (urgency) => {
  const labels = {
    high: '紧急',
    medium: '一般',
    low: '低'
  }
  return labels[urgency] || '未知'
}

const formatDueTime = (time) => {
  return time
}

const handleAddRule = () => {
  editingRule.value = null
  ruleForm.value = { trigger: '', action: '', enabled: true }
  ruleDialogVisible.value = true
}

const handleEditRule = (row) => {
  editingRule.value = row
  ruleForm.value = { ...row }
  ruleDialogVisible.value = true
}

const handleSaveRule = () => {
  if (editingRule.value) {
    Object.assign(editingRule.value, ruleForm.value)
  } else {
    rulesData.value.push({
      id: Date.now(),
      ...ruleForm.value
    })
  }
  ruleDialogVisible.value = false
}

const handleDeleteRule = (row) => {
  const index = rulesData.value.findIndex(r => r.id === row.id)
  if (index > -1) {
    rulesData.value.splice(index, 1)
  }
}

const handleToggleRule = (row) => {
  row.loading = true
  setTimeout(() => {
    row.loading = false
  }, 500)
}

const handleComplete = (row) => {
  row.loading = true
  setTimeout(() => {
    const index = pendingReminders.value.findIndex(r => r.id === row.id)
    if (index > -1) {
      pendingReminders.value.splice(index, 1)
    }
  }, 500)
}

const handleSnooze = (_row) => {
  // 延期逻辑
}

const handleClose = () => {
  dialogVisible.value = false
}

const handleRefresh = () => {
  emit('refresh')
}
</script>

<style scoped>
.auto-tasks-tabs {
  min-height: 400px;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-750);
}

.filter-group {
  display: flex;
  gap: 8px;
  align-items: center;
}

.rules-section,
.reminders-section,
.history-section {
  padding: 10px 0;
}

.rules-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 12px;
  background-color: #f4f4f5;
  border-radius: 8px;
  color: var(--text-muted);
  font-size: 14px;
}

.due-time {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary-ui);
  font-size: 13px;
}

.history-item {
  padding: 12px;
  background-color: var(--bg-subtle);
  border-radius: 8px;
}

.history-title {
  font-weight: 600;
  color: var(--gray-750);
  margin-bottom: 4px;
}

.history-detail {
  color: var(--text-secondary-ui);
  font-size: 13px;
  margin-bottom: 8px;
}

.history-result {
  display: flex;
  justify-content: flex-end;
}

:deep(.el-timeline-item__timestamp) {
  font-size: 13px;
  color: var(--text-muted);
}

:deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-badge__content) {
  transform: translateY(-50%) translateX(100%);
}
</style>
