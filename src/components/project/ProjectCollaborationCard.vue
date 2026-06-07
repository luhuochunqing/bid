<template>
  <el-card class="collaboration-card">
    <template #header>
      <div class="card-title">
        <div class="title-main">
          <el-icon><ChatDotRound /></el-icon>
          <span>协作讨论</span>
        </div>
        <div class="actions">
          <el-button
            link
            type="primary"
            :icon="Plus"
            :disabled="!projectId"
            @click="openCreateDialog"
          >
            新建讨论
          </el-button>
        </div>
      </div>
    </template>

    <div v-if="loading" class="collab-empty">加载中...</div>
    <el-empty v-else-if="threads.length === 0" description="暂无讨论，点击右上角新建" :image-size="64" />
    <div v-else class="collab-body">
      <div class="thread-list">
        <div
          v-for="thread in threads"
          :key="thread.id"
          class="thread-item"
          :class="{ 'thread-item--active': selectedThread?.id === thread.id }"
          @click="selectThread(thread)"
        >
          <div class="thread-row">
            <span class="thread-title">{{ thread.title }}</span>
            <el-tag :type="statusType(thread.status)" size="small">{{ thread.status }}</el-tag>
          </div>
          <div class="thread-meta">
            <span>{{ thread.commentCount || 0 }} 条评论</span>
            <span>{{ formatTime(thread.updatedAt || thread.createdAt) }}</span>
          </div>
        </div>
      </div>

      <div v-if="selectedThread" class="thread-detail">
        <div class="thread-detail-header">
          <h4>{{ selectedThread.title }}</h4>
        </div>
        <div class="comments-section">
          <div v-if="comments.length === 0" class="collab-empty-inline">暂无评论，来发表第一条吧</div>
          <div v-for="comment in comments" :key="comment.id" class="comment-item">
            <div class="comment-header">
              <strong>{{ comment.createdByName || ('用户#' + comment.createdBy) }}</strong>
              <span>{{ formatTime(comment.createdAt) }}</span>
            </div>
            <div class="comment-content">{{ comment.content }}</div>
          </div>
        </div>
        <MentionInput v-model="newComment" :rows="3" placeholder="添加评论，输入 @ 提及同事" class="comment-input" />
        <el-button type="primary" :loading="sending" class="send-button" @click="addComment">发送评论</el-button>
      </div>
    </div>

    <el-dialog
      v-model="createDialogVisible"
      title="新建讨论"
      width="420px"
      :close-on-click-modal="false"
      @close="resetCreateForm"
    >
      <el-form :model="createForm" label-position="top">
        <el-form-item label="标题" required>
          <el-input v-model="createForm.title" maxlength="80" show-word-limit placeholder="例如：标书第二章思路讨论" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Plus } from '@element-plus/icons-vue'
import { collaborationApi } from '@/api/modules/collaboration.js'
import { mentionsApi } from '@/api/modules/mentions.js'
import MentionInput from '@/components/common/MentionInput.vue'
import { parseMentionContent } from '@/utils/notificationHelpers.js'

const props = defineProps({
  projectId: { type: [String, Number], default: null },
})

const threads = ref([])
const selectedThread = ref(null)
const comments = ref([])
const newComment = ref('')
const loading = ref(false)
const sending = ref(false)

const createDialogVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ title: '' })

watch(
  () => props.projectId,
  (id) => {
    if (id) loadThreads()
    else {
      threads.value = []
      selectedThread.value = null
      comments.value = []
    }
  },
  { immediate: true }
)

async function loadThreads() {
  if (!props.projectId) return
  loading.value = true
  try {
    const res = await collaborationApi.getThreads({ projectId: props.projectId })
    threads.value = Array.isArray(res?.data) ? res.data : []
  } catch (e) {
    ElMessage.error('加载讨论失败')
    threads.value = []
  } finally {
    loading.value = false
  }
}

async function selectThread(thread) {
  selectedThread.value = thread
  comments.value = []
  try {
    const res = await collaborationApi.getThread(thread.id)
    comments.value = res?.data?.comments || []
  } catch (e) {
    comments.value = []
  }
}

async function addComment() {
  const raw = newComment.value
  if (!raw.trim()) return
  const thread = selectedThread.value
  if (!thread) return
  const { plainText, mentionedUserIds } = parseMentionContent(raw)
  sending.value = true
  try {
    await collaborationApi.addComment(thread.id, { content: plainText })
  } catch (e) {
    sending.value = false
    ElMessage.error('发送失败')
    return
  }
  newComment.value = ''
  if (mentionedUserIds.length > 0) {
    try {
      await mentionsApi.create({
        content: raw,
        sourceEntityType: 'COMMENT',
        sourceEntityId: thread.id,
        title: thread.title,
      })
      ElMessage.success('评论已发送')
    } catch (e) {
      ElMessage.warning('评论已发送，但 @ 通知发送失败')
    }
  } else {
    ElMessage.success('评论已发送')
  }
  sending.value = false
  await selectThread(thread)
}

function openCreateDialog() {
  if (!props.projectId) {
    ElMessage.warning('项目未就绪')
    return
  }
  createDialogVisible.value = true
}

function resetCreateForm() {
  createForm.title = ''
}

async function submitCreate() {
  const title = createForm.title.trim()
  if (!title) {
    ElMessage.warning('请输入讨论标题')
    return
  }
  creating.value = true
  try {
    const res = await collaborationApi.createThread({ projectId: props.projectId, title })
    if (res?.success === false) {
      ElMessage.error(res?.message || '创建讨论失败')
      return
    }
    createDialogVisible.value = false
    resetCreateForm()
    ElMessage.success('讨论已创建')
    await loadThreads()
    const created = threads.value.find((t) => t.id === res?.data?.id) || threads.value[0]
    if (created) await selectThread(created)
  } catch (e) {
    ElMessage.error('创建讨论失败')
  } finally {
    creating.value = false
  }
}

function statusType(s) {
  const map = { OPEN: 'primary', RESOLVED: 'success', CLOSED: 'info' }
  return map[String(s || '').toUpperCase()] || 'info'
}

function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return String(t)
  return d.toLocaleString('zh-CN', { hour12: false })
}
</script>

<style scoped>
.collaboration-card {
  margin-top: 16px;
}
.card-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.title-main {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.actions {
  display: flex;
  gap: 8px;
}
.collab-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-muted);
}
.collab-empty-inline {
  padding: 12px;
  color: var(--text-muted);
  font-size: 13px;
}
.collab-body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.thread-list {
  flex: 0 0 260px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  max-height: 420px;
  overflow-y: auto;
}
.thread-item {
  padding: 10px 12px;
  border-bottom: 1px solid #f2f6fc;
  cursor: pointer;
  transition: background 150ms ease;
}
.thread-item:hover {
  background: var(--bg-subtle);
}
.thread-item--active {
  background: #ecf5ff;
}
.thread-item:last-child {
  border-bottom: none;
}
.thread-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.thread-title {
  font-weight: 500;
  color: var(--gray-750);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.thread-meta {
  font-size: 12px;
  color: var(--text-muted);
  display: flex;
  gap: 12px;
}
.thread-detail {
  flex: 1 1 auto;
  min-width: 0;
}
.thread-detail-header h4 {
  margin: 0 0 12px 0;
  color: var(--gray-750);
}
.comments-section {
  margin-bottom: 12px;
  max-height: 320px;
  overflow-y: auto;
}
.comment-item {
  padding: 10px 0;
  border-bottom: 1px solid #f2f6fc;
}
.comment-item:last-child {
  border-bottom: none;
}
.comment-header {
  display: flex;
  gap: 12px;
  font-size: 13px;
  margin-bottom: 4px;
  color: var(--text-secondary-ui);
}
.comment-content {
  color: var(--gray-750);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.comment-input {
  width: 100%;
}
.send-button {
  margin-top: 8px;
}
@media (max-width: 768px) {
  .collab-body {
    flex-direction: column;
  }
  .thread-list {
    flex: none;
    width: 100%;
    max-height: 220px;
  }
}
</style>
