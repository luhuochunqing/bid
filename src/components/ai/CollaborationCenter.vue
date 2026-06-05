<template>
  <el-dialog
    v-model="dialogVisible"
    title="协作中心"
    width="900px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div v-if="loading" class="loading-state">
      正在加载协作数据...
    </div>
    <el-tabs v-else v-model="activeTab" class="collaboration-tabs">
      <!-- Tab1: 章节分配 -->
      <el-tab-pane label="章节分配" name="chapters">
        <div class="chapters-content">
          <el-table
            :data="chapters"
            stripe
            style="width: 100%"
            :row-class-name="getRowClassName"
          >
            <el-table-column prop="chapter" label="章节" width="180" />
            <el-table-column label="负责人" width="160">
              <template #default="{ row }">
                <el-select
                  v-model="row.owner"
                  size="small"
                  placeholder="选择负责人"
                  :disabled="row.locked"
                  @change="handleOwnerChange(row)"
                >
                  <el-option
                    v-for="user in users"
                    :key="user.value"
                    :label="user.label"
                    :value="user.value"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="dueDate" label="截止时间" width="140" />
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button
                    type="primary"
                    size="small"
                    plain
                    :icon="Bell"
                    @click="handleRemind(row)"
                  >
                    提醒
                  </el-button>
                  <el-button
                    :type="row.locked ? 'warning' : 'info'"
                    size="small"
                    plain
                    @click="handleToggleLock(row)"
                  >
                    {{ row.locked ? '解锁' : '锁定' }}
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="chapters-summary">
            <div class="summary-item">
              <span class="summary-label">总章节:</span>
              <span class="summary-value">{{ chapters.length }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">已完成:</span>
              <span class="summary-value success">{{ getCompletedCount() }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">进行中:</span>
              <span class="summary-value warning">{{ getEditingCount() }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">未开始:</span>
              <span class="summary-value info">{{ getPendingCount() }}</span>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab2: 变更记录 -->
      <el-tab-pane label="变更记录" name="history">
        <div class="history-content">
          <el-timeline class="change-timeline">
            <el-timeline-item
              v-for="(item, index) in changeHistory"
              :key="index"
              :timestamp="item.timestamp"
              placement="top"
              :color="getChangeTypeColor(item.type)"
              :size="index === 0 ? 'large' : 'normal'"
            >
              <div class="change-item">
                <div class="change-header">
                  <div class="change-author">
                    <el-avatar :size="32" class="author-avatar">
                      {{ item.avatar }}
                    </el-avatar>
                    <span class="author-name">{{ item.author }}</span>
                  </div>
                  <el-tag :type="getChangeTypeTag(item.type)" size="small">
                    {{ getChangeTypeText(item.type) }}
                  </el-tag>
                </div>
                <div class="change-description">
                  {{ item.description }}
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>

          <el-empty
            v-if="changeHistory.length === 0"
            description="暂无变更记录"
            :image-size="80"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" @click="handleSave" v-if="activeTab === 'chapters'">
        保存分配
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import { collaborationApi } from '@/api'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  projectId: {
    type: String,
    default: 'P001'
  }
})

const emit = defineEmits(['update:modelValue', 'save', 'remind', 'toggle-lock', 'owner-change'])
const userStore = useUserStore()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const activeTab = ref('chapters')
const loading = ref(false)
const isApiMode = computed(() => true)

const users = computed(() =>
  (userStore.users || []).map((user) => ({
    value: user.name,
    label: user.name }))
)

const chapters = ref([])
const changeHistory = ref([])

const flattenSections = (sections = []) => sections.flatMap((section) => {
  const normalized = {
    ...section,
    chapter: section.chapter || section.title || section.name || '未命名章节',
    owner: section.owner || '',
    dueDate: section.dueDate || '',
    locked: Boolean(section.locked),
    status: section.status || (section.owner ? 'editing' : 'pending') }
  return [normalized, ...flattenSections(section.children || [])]
})

const resolveUserIdByName = (name) => {
  const matchedUser = (userStore.users || []).find((user) => user.name === name)
  return matchedUser?.id ?? userStore.currentUser?.id ?? null
}

const loadData = async () => {
  if (!props.projectId) return

  loading.value = true

  try {
    const [treeResponse, threadResponse] = await Promise.all([
      collaborationApi.editor.getTree(props.projectId),
      collaborationApi.collaboration.getThreads({ projectId: props.projectId }),
    ])

    chapters.value = treeResponse?.success && Array.isArray(treeResponse.data)
      ? flattenSections(treeResponse.data).map((item, index) => ({
          ...item,
          chapter: item.chapter || `${index + 1}. ${item.title || item.name || '未命名章节'}` }))
      : []

    changeHistory.value = threadResponse?.success && Array.isArray(threadResponse.data)
      ? threadResponse.data.map((item) => ({
          type: item.type === 'thread' ? 'edit' : item.type || 'comment',
          author: item.author || '未知用户',
          avatar: item.avatar || '协',
          timestamp: item.timestamp || '',
          description: item.content || item.title || '暂无内容' }))
      : []

    if (!treeResponse?.success && !threadResponse?.success) {
      ElMessage.info(treeResponse?.message || threadResponse?.message || '当前项目暂无协作数据')
    }
  } finally {
    loading.value = false
  }
}

const getRowClassName = ({ row }) => {
  if (row.status === 'completed') return 'row-completed'
  if (row.status === 'editing') return 'row-editing'
  return ''
}

const getStatusType = (status) => {
  const typeMap = {
    completed: 'success',
    editing: 'warning',
    pending: 'info'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    completed: '已完成',
    editing: '编辑中',
    pending: '未开始'
  }
  return textMap[status] || status
}

const getChangeTypeColor = (type) => {
  const colorMap = {
    edit: '#409eff',
    comment: 'var(--text-muted)',
    conflict: '#f56c6c'
  }
  return colorMap[type] || '#dcdfe6'
}

const getChangeTypeTag = (type) => {
  const typeMap = {
    edit: 'primary',
    comment: 'info',
    conflict: 'danger'
  }
  return typeMap[type] || 'info'
}

const getChangeTypeText = (type) => {
  const textMap = {
    edit: '编辑',
    comment: '评论',
    conflict: '冲突'
  }
  return textMap[type] || type
}

const getCompletedCount = () => {
  return chapters.value.filter(c => c.status === 'completed').length
}

const getEditingCount = () => {
  return chapters.value.filter(c => c.status === 'editing').length
}

const getPendingCount = () => {
  return chapters.value.filter(c => c.status === 'pending').length
}

const handleOwnerChange = async (row) => {
  const assignedBy = resolveUserIdByName(userStore.userName)

  if (isApiMode.value && !assignedBy) {
    ElMessage.warning('当前用户缺少真实用户 ID，无法提交章节分配')
    await loadData()
    return
  }

  const response = await collaborationApi.editor.assignSection(props.projectId, {
    sectionId: row.id,
    owner: row.owner,
    assignedBy,
    dueDate: row.dueDate || null })

  if (!response?.success) {
    await loadData()
    ElMessage.error(response?.msg || '章节分配失败')
    return
  }

  row.owner = response?.data?.owner || row.owner
  row.dueDate = response?.data?.dueDate || row.dueDate
  row.status = row.owner ? 'editing' : 'pending'
  emit('owner-change', { ...row, response: response.data })
  ElMessage.success(`已将 ${row.chapter} 分配给 ${row.owner}`)
}

const handleRemind = async (row) => {
  try {
    await ElMessageBox.confirm(
    `确定要提醒 ${row.owner} 完成章节「${row.chapter}」吗？`,
    '发送提醒',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })
  } catch {
    return
  }

  const remindedBy = resolveUserIdByName(userStore.userName)
  if (isApiMode.value && !remindedBy) {
    ElMessage.warning('当前用户缺少真实用户 ID，无法发送提醒')
    return
  }

  const response = await collaborationApi.editor.createReminder(props.projectId, {
    sectionId: row.id,
    recipient: row.owner,
    remindedBy,
    message: `请尽快完成章节「${row.chapter}」` })

  if (!response?.success) {
    ElMessage.error(response?.msg || '发送提醒失败')
    return
  }

  emit('remind', { ...row, reminder: response.data })
  ElMessage.success(`已向 ${row.owner} 发送提醒`)
}

const handleToggleLock = async (row) => {
  const previousLocked = row.locked
  const userId = resolveUserIdByName(userStore.userName)
  if (isApiMode.value && !userId) {
    ElMessage.warning('当前用户缺少真实用户 ID，无法变更锁定状态')
    return
  }

  row.locked = !row.locked
  const response = await collaborationApi.editor.updateLock(props.projectId, {
    sectionId: row.id,
    locked: row.locked,
    userId })

  if (!response?.success) {
    row.locked = previousLocked
    ElMessage.error(response?.msg || '锁定状态更新失败')
    return
  }

  row.locked = Boolean(response?.data?.locked)
  emit('toggle-lock', { ...row, response: response.data })
  ElMessage.success(`${row.chapter} 已${row.locked ? '锁定' : '解锁'}`)
}

const handleSave = async () => {
  await loadData()
  emit('save', chapters.value)
  ElMessage.success('章节分配保存成功')
}

const handleClose = () => {
  dialogVisible.value = false
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      loadData()
    }
  }
)
</script>

<style scoped>
.loading-state {
  padding: 48px 0;
  text-align: center;
  color: var(--text-muted);
}

.collaboration-tabs {
  min-height: 400px;
}

/* 章节分配样式 */
.chapters-content {
  padding: 10px 0;
}

.table-actions {
  display: flex;
  gap: 8px;
}

.readonly-owner {
  color: var(--text-secondary-ui);
}

:deep(.el-table .row-completed) {
  background-color: #f0f9ff;
}

:deep(.el-table .row-editing) {
  background-color: #fef9e7;
}

.chapters-summary {
  display: flex;
  justify-content: space-around;
  padding: 20px;
  margin-top: 20px;
  background: var(--bg-subtle);
  border-radius: 6px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.summary-label {
  font-size: 13px;
  color: var(--text-muted);
}

.summary-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--gray-750);
}

.summary-value.success {
  color: #67c23a;
}

.summary-value.warning {
  color: #e6a23c;
}

.summary-value.info {
  color: var(--text-muted);
}

/* 变更记录样式 */
.history-content {
  padding: 10px 0;
}

.change-timeline {
  padding-left: 10px;
  max-height: 450px;
  overflow-y: auto;
}

.change-item {
  padding: 12px;
  background: var(--bg-subtle);
  border-radius: 6px;
  transition: all 0.2s;
}

.change-item:hover {
  background: #ecf5ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.change-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.change-author {
  display: flex;
  align-items: center;
  gap: 10px;
}

.author-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--gray-750);
}

.change-description {
  font-size: 13px;
  color: var(--text-secondary-ui);
  line-height: 1.6;
  padding-left: 42px;
}

/* 时间线样式覆盖 */
:deep(.el-timeline-item__timestamp) {
  font-size: 12px;
  color: var(--text-muted);
}

:deep(.el-timeline-item__wrapper) {
  padding-left: 28px;
}

/* Tabs 样式优化 */
:deep(.el-tabs__content) {
  padding-top: 10px;
}

/* 移动端响应式 */
@media (max-width: 768px) {
  :deep(.el-dialog) {
    width: 95% !important;
  }

  :deep(.el-table) {
    font-size: 12px;
  }

  :deep(.el-table .cell) {
    padding: 8px 4px;
  }

  .chapters-summary {
    flex-wrap: wrap;
    gap: 16px;
  }

  .summary-item {
    min-width: 45%;
  }

  .table-actions {
    flex-direction: column;
    gap: 4px;
  }

  .change-description {
    padding-left: 0;
    margin-top: 8px;
  }
}

/* 触摸设备优化 */
@media (hover: none) and (pointer: coarse) {
  .el-button {
    min-height: 40px;
  }

  :deep(.el-select .el-input__inner) {
    min-height: 40px;
  }
}
</style>
