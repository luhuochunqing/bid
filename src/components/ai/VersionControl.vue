<template>
  <el-dialog
    v-model="dialogVisible"
    title="版本管理"
    width="1000px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="version-control">
      <!-- 左侧版本时间线 -->
      <div class="version-timeline">
        <div class="timeline-header">
          <h3>版本历史</h3>
          <el-button type="primary" size="small" @click="handleCompareMode" v-if="!compareMode">
            版本对比
          </el-button>
          <el-button size="small" @click="compareMode = false" v-else>
            返回
          </el-button>
        </div>

        <el-timeline class="version-list">
          <el-timeline-item
            v-for="version in versions"
            :key="version.id"
            :timestamp="version.timestamp"
            placement="top"
            :color="version.isCurrent ? '#409eff' : '#dcdfe6'"
            :size="version.isCurrent ? 'large' : 'normal'"
            :class="{ 'current-version': version.isCurrent }"
          >
            <div class="version-item" @click="selectVersion(version)">
              <div class="version-header">
                <div class="version-info">
                  <span class="version-number">版本 {{ version.version }}</span>
                  <el-tag
                    v-if="version.isCurrent"
                    type="primary"
                    size="small"
                    effect="dark"
                  >
                    当前版本
                  </el-tag>
                </div>
                <div class="version-author">
                  <el-avatar :size="32" class="author-avatar">
                    {{ version.avatar }}
                  </el-avatar>
                  <span class="author-name">{{ version.author }}</span>
                </div>
              </div>

              <div class="version-changes">
                <div class="changes-title">变更内容:</div>
                <ul class="changes-list">
                  <li v-for="(change, index) in version.changes" :key="index">
                    {{ change }}
                  </li>
                </ul>
              </div>

              <!-- 版本操作按钮 -->
              <div class="version-actions">
                <el-button
                  type="primary"
                  size="small"
                  plain
                  @click.stop="handleRestore(version)"
                  v-if="!version.isCurrent"
                >
                  恢复此版本
                </el-button>
                <el-button
                  size="small"
                  @click.stop="viewVersionDetail(version)"
                >
                  查看详情
                </el-button>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>

      <!-- 右侧版本对比 -->
      <div class="version-compare" v-if="compareMode">
        <div class="compare-header">
          <h3>版本对比</h3>
        </div>

        <div class="compare-selector">
          <div class="selector-item">
            <label>旧版本:</label>
            <el-select v-model="compareOld" placeholder="选择旧版本" style="width: 180px">
              <el-option
                v-for="v in versions.filter(v => !v.isCurrent)"
                :key="v.id"
                :label="`版本 ${v.version}`"
                :value="v.id"
              />
            </el-select>
          </div>
          <div class="selector-item">
            <label>新版本:</label>
            <el-select v-model="compareNew" placeholder="选择新版本" style="width: 180px">
              <el-option
                v-for="v in versions"
                :key="v.id"
                :label="`版本 ${v.version}`"
                :value="v.id"
              />
            </el-select>
          </div>
        </div>

        <div class="compare-content" v-if="oldVersion && newVersion">
          <div class="compare-panel">
            <div class="panel-header old">
              版本 {{ oldVersion.version }}
            </div>
            <div class="panel-body">
              <div v-for="(change, idx) in (compareResult?.version1?.changes || oldVersion.changes)" :key="idx" class="change-item removed">
                - {{ change }}
              </div>
            </div>
          </div>

          <div class="compare-panel">
            <div class="panel-header new">
              版本 {{ newVersion.version }}
            </div>
            <div class="panel-body">
              <div v-for="(change, idx) in (compareResult?.version2?.changes || newVersion.changes)" :key="idx" class="change-item added">
                + {{ change }}
              </div>
            </div>
          </div>
        </div>

        <el-empty v-else description="请选择两个版本进行对比" :image-size="100" />
      </div>

      <!-- 右侧版本详情 (非对比模式) -->
      <div class="version-detail" v-else>
        <div class="detail-header">
          <h3>版本详情</h3>
        </div>

        <div v-if="selectedVersion" class="detail-content">
          <div class="detail-item">
            <span class="detail-label">版本号:</span>
            <span class="detail-value">{{ selectedVersion.version }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">创建时间:</span>
            <span class="detail-value">{{ selectedVersion.timestamp }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">创建人:</span>
            <span class="detail-value">{{ selectedVersion.author }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">状态:</span>
            <el-tag :type="selectedVersion.isCurrent ? 'success' : 'info'" size="small">
              {{ selectedVersion.isCurrent ? '当前版本' : '历史版本' }}
            </el-tag>
          </div>

          <div class="detail-changes">
            <div class="detail-label">变更内容:</div>
            <ul class="detail-changes-list">
              <li v-for="(change, index) in selectedVersion.changes" :key="index">
                {{ change }}
              </li>
            </ul>
          </div>
        </div>

        <el-empty v-else description="请选择一个版本查看详情" :image-size="100" />
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" @click="handleCreateVersion">创建新版本</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const emit = defineEmits(['update:modelValue', 'restore', 'create-version'])
const userStore = useUserStore()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const compareMode = ref(false)
const compareOld = ref('')
const compareNew = ref('')
const selectedVersion = ref(null)
const versions = ref([])
const compareResult = ref(null)
const isApiMode = computed(() => true)
const currentUserId = computed(() => userStore.currentUser?.id ?? null)

const oldVersion = computed(() => {
  return versions.value.find(v => String(v.id) === String(compareOld.value))
})

const newVersion = computed(() => {
  return versions.value.find(v => String(v.id) === String(compareNew.value))
})

// 初始化对比版本
const initCompareVersions = () => {
  if (versions.value.length >= 2) {
    compareNew.value = versions.value[0].id
    compareOld.value = versions.value[1].id
  }
}

const loadVersions = async () => {
  const response = await collaborationApi.versions.getVersions(props.projectId)
  if (!response?.success) {
    versions.value = []
    selectedVersion.value = null
    ElMessage.info(response?.msg || '当前项目暂无版本数据')
    return
  }

  versions.value = Array.isArray(response.data) ? response.data : []
  selectedVersion.value = versions.value[0] || null
}

const selectVersion = (version) => {
  selectedVersion.value = version
}

const viewVersionDetail = (version) => {
  selectedVersion.value = version
  compareMode.value = false
}

const handleCompareMode = () => {
  initCompareVersions()
  compareResult.value = null
  compareMode.value = true
}

const handleRestore = (version) => {
  ElMessageBox.confirm(
    `确定要恢复到版本 ${version.version} 吗？当前版本将被保存为新版本。`,
    '恢复版本',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    if (isApiMode.value && !currentUserId.value) {
      ElMessage.warning('当前用户缺少真实用户 ID，无法回滚版本')
      return
    }

    const response = await collaborationApi.versions.rollback(props.projectId, version.id, currentUserId.value)
    if (!response?.success) {
      ElMessage.error(response?.msg || '版本回滚失败')
      return
    }
    emit('restore', version)
    ElMessage.success(`已恢复到版本 ${version.version}`)
    await loadVersions()
  }).catch(() => {
    // 用户取消
  })
}

const handleCreateVersion = () => {
  ElMessageBox.prompt('请输入新版本说明', '创建新版本', {
    confirmButtonText: '创建',
    cancelButtonText: '取消',
    inputPlaceholder: '请描述本次主要变更...'
  }).then(async ({ value }) => {
    if (isApiMode.value && !currentUserId.value) {
      ElMessage.warning('当前用户缺少真实用户 ID，无法创建版本')
      return
    }

    const response = await collaborationApi.versions.createVersion(props.projectId, {
      changeSummary: value,
      content: value,
      createdBy: currentUserId.value || 1 })
    if (!response?.success) {
      ElMessage.error(response?.msg || '创建版本失败')
      return
    }
    emit('create-version', { description: value })
    ElMessage.success('新版本创建成功')
    await loadVersions()
  }).catch(() => {
    // 用户取消
  })
}

const handleClose = () => {
  dialogVisible.value = false
  compareMode.value = false
  selectedVersion.value = null
  compareResult.value = null
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      loadVersions()
    }
  }
)

watch(
  () => [compareOld.value, compareNew.value, compareMode.value],
  async ([oldId, newId, isCompare]) => {
    if (!isCompare || !oldId || !newId) return
    const response = await collaborationApi.versions.compare(props.projectId, oldId, newId)
    compareResult.value = response?.success ? response.data : null
    if (!response?.success && isApiMode.value) {
      ElMessage.error(response?.msg || '版本对比失败')
    }
  }
)
</script>

<style scoped>
.version-control {
  display: flex;
  gap: 20px;
  min-height: 450px;
}

.version-timeline {
  flex: 1;
  min-width: 400px;
  border-right: 1px solid var(--gray-250);
  padding-right: 20px;
}

.version-compare,
.version-detail {
  flex: 1;
  min-width: 380px;
}

.timeline-header,
.compare-header,
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--gray-250);
}

.timeline-header h3,
.compare-header h3,
.detail-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--gray-750);
}

.version-list {
  padding-left: 10px;
}

.version-item {
  padding: 12px;
  background: var(--bg-subtle);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.version-item:hover {
  background: #ecf5ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}

.version-item.current-version {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
}

.version-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.version-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.version-number {
  font-weight: 600;
  font-size: 15px;
  color: var(--gray-750);
}

.version-author {
  display: flex;
  align-items: center;
  gap: 8px;
}

.author-name {
  font-size: 13px;
  color: var(--text-secondary-ui);
}

.version-changes {
  margin-bottom: 12px;
}

.changes-title {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.changes-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  color: var(--text-secondary-ui);
}

.changes-list li {
  margin-bottom: 4px;
  line-height: 1.5;
}

.version-actions {
  display: flex;
  gap: 8px;
}

/* 版本对比样式 */
.compare-selector {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
  padding: 16px;
  background: var(--bg-subtle);
  border-radius: 6px;
}

.selector-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.selector-item label {
  font-size: 14px;
  color: var(--text-secondary-ui);
  min-width: 60px;
}

.compare-content {
  display: flex;
  gap: 16px;
}

.compare-panel {
  flex: 1;
  border: 1px solid var(--gray-250);
  border-radius: 6px;
  overflow: hidden;
}

.panel-header {
  padding: 10px 16px;
  font-weight: 500;
  color: white;
}

.panel-header.old {
  background: #f56c6c;
}

.panel-header.new {
  background: #67c23a;
}

.panel-body {
  padding: 16px;
  max-height: 350px;
  overflow-y: auto;
  background: #fafafa;
}

.change-item {
  padding: 6px 10px;
  margin-bottom: 6px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.5;
}

.change-item.removed {
  background: #fee;
  color: #f56c6c;
  text-decoration: line-through;
}

.change-item.added {
  background: #e1f9e8;
  color: #67c23a;
}

/* 版本详情样式 */
.detail-content {
  padding: 16px;
  background: var(--bg-subtle);
  border-radius: 6px;
}

.detail-item {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed var(--gray-250);
}

.detail-item:last-child {
  border-bottom: none;
}

.detail-label {
  font-size: 14px;
  color: var(--text-muted);
  min-width: 80px;
}

.detail-value {
  font-size: 14px;
  color: var(--gray-750);
}

.detail-changes {
  margin-top: 16px;
}

.detail-changes .detail-label {
  margin-bottom: 8px;
  display: block;
}

.detail-changes-list {
  margin: 0;
  padding-left: 20px;
}

.detail-changes-list li {
  padding: 6px 0;
  font-size: 13px;
  color: var(--text-secondary-ui);
}

/* 时间线样式覆盖 */
:deep(.el-timeline-item__timestamp) {
  font-size: 12px;
  color: var(--text-muted);
}

:deep(.el-timeline-item__wrapper) {
  padding-left: 28px;
}

/* 移动端响应式 */
@media (max-width: 768px) {
  .version-control {
    flex-direction: column;
  }

  .version-timeline {
    border-right: none;
    border-bottom: 1px solid var(--gray-250);
    padding-right: 0;
    padding-bottom: 20px;
    min-width: 100%;
  }

  .version-compare,
  .version-detail {
    min-width: 100%;
  }

  .compare-selector {
    flex-direction: column;
    gap: 12px;
  }

  .compare-content {
    flex-direction: column;
  }

  :deep(.el-dialog) {
    width: 95% !important;
  }
}
</style>
