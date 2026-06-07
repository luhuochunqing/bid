<template>
  <div class="task-activity-panel">
    <div v-if="taskId && !readonly" class="comment-composer">
      <el-input
        v-model="commentText"
        type="textarea"
        :rows="3"
        maxlength="5000"
        show-word-limit
        placeholder="写评论，使用 @[姓名](用户ID) 提及同事"
      />
      <div class="composer-actions">
        <el-button
          type="primary"
          :icon="Position"
          :loading="submitting"
          :disabled="!canSubmit"
          @click="submitComment"
        >
          发送
        </el-button>
      </div>
    </div>

    <el-alert v-if="errorMessage" type="warning" :closable="false" :title="errorMessage" />

    <el-empty v-if="!taskId" description="保存任务后查看动态" :image-size="72" />
    <el-empty v-else-if="!loading && activities.length === 0" description="暂无动态" :image-size="72" />
    <el-timeline v-else class="activity-timeline">
      <el-timeline-item
        v-for="item in activities"
        :key="`${item.type}-${item.id}`"
        :timestamp="formatActivityTime(item.createdAt)"
      >
        <div class="activity-row">
          <div class="activity-meta">
            <el-tag size="small" :type="item.type === 'COMMENT' ? 'success' : 'info'">
              {{ item.type === 'COMMENT' ? '评论' : '历史' }}
            </el-tag>
            <strong>{{ item.actorName || '系统' }}</strong>
          </div>
          <p v-if="item.type === 'COMMENT'" class="comment-content">
            {{ renderMentionText(item.content) }}
          </p>
          <div v-else class="history-viewer">
            <div class="history-title">{{ item.action || 'UPDATE' }}</div>
            <dl>
              <template v-for="field in visibleSnapshotFields(item.snapshot)" :key="field.key">
                <dt>{{ field.label }}</dt>
                <dd>{{ field.value }}</dd>
              </template>
            </dl>
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Position } from '@element-plus/icons-vue'
import { taskActivityApi } from '@/api/modules/taskActivity.js'
import { parseMentionContent } from '@/utils/notificationHelpers.js'

const props = defineProps({
  taskId: { type: [Number, String], default: null },
  readonly: { type: Boolean, default: false },
})

const activities = ref([])
const commentText = ref('')
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')

const canSubmit = computed(() => commentText.value.trim().length > 0 && !submitting.value)

watch(() => props.taskId, () => {
  loadActivity()
}, { immediate: true })

async function loadActivity() {
  if (!props.taskId) {
    activities.value = []
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await taskActivityApi.getActivity(props.taskId)
    activities.value = Array.isArray(result?.data) ? result.data : []
  } catch (error) {
    errorMessage.value = error?.message || '任务动态加载失败'
  } finally {
    loading.value = false
  }
}

async function submitComment() {
  const content = commentText.value.trim()
  if (!content) return
  submitting.value = true
  errorMessage.value = ''
  try {
    await taskActivityApi.createComment(props.taskId, { content })
    commentText.value = ''
    await loadActivity()
  } catch (error) {
    errorMessage.value = error?.message || '评论发送失败'
  } finally {
    submitting.value = false
  }
}

function renderMentionText(content) {
  return parseMentionContent(content).plainText
}

function visibleSnapshotFields(snapshot = {}) {
  const fields = [
    ['title', '任务名称'],
    ['status', '状态'],
    ['priority', '优先级'],
    ['assigneeId', '负责人ID'],
    ['dueDate', '截止日期'],
  ]
  return fields
    .filter(([key]) => snapshot?.[key] !== undefined && snapshot?.[key] !== null && snapshot?.[key] !== '')
    .map(([key, label]) => ({ key, label, value: String(snapshot[key]) }))
}

function formatActivityTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('zh-CN', { hour12: false })
}

defineExpose({ loadActivity, submitComment })
</script>

<style scoped>
.task-activity-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.comment-composer {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
}

.activity-timeline {
  padding: 4px 0 0;
}

.activity-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.activity-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-content {
  margin: 0;
  white-space: pre-wrap;
  color: var(--gray-750);
  line-height: 1.6;
}

.history-viewer {
  border-left: 3px solid #dcdfe6;
  padding-left: 10px;
  color: var(--text-secondary-ui);
}

.history-title {
  margin-bottom: 6px;
  font-weight: 600;
  color: var(--gray-750);
}

.history-viewer dl {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 4px 10px;
  margin: 0;
}

.history-viewer dt {
  color: var(--text-muted);
}

.history-viewer dd {
  min-width: 0;
  margin: 0;
  word-break: break-word;
}
</style>
