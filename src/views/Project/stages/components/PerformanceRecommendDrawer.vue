<template>
  <el-drawer
    v-model="visible"
    title="业绩智能推荐"
    size="600px"
    append-to-body
    @open="handleOpen"
  >
    <div class="perf-recommend-drawer">
      <div v-if="loading" class="loading-wrap">
        <el-skeleton :rows="6" animated />
      </div>
      <div v-else-if="matchedItems.length > 0" class="drawer-section">
        <label class="section-label">
          匹配业绩（{{ matchedItems.length }} 条）
        </label>
        <div class="perf-list">
          <div
            v-for="(item, idx) in matchedItems"
            :key="idx"
            :class="['perf-card', statusClass(item.status)]"
          >
            <div class="perf-card-header">
              <span class="perf-badge">{{ statusLabel(item.status) }}</span>
              <span class="perf-contract">{{ item.matchedContractName || '未匹配' }}</span>
            </div>
            <div class="perf-card-body">
              <p class="perf-req">招标要求：{{ item.requirementText }}</p>
              <p v-if="item.matchedSigningEntity" class="perf-entity">
                签约单位：{{ item.matchedSigningEntity }}
              </p>
              <p v-if="item.remainingDays != null" class="perf-days">
                剩余有效期：{{ item.remainingDays }} 天
              </p>
              <p v-if="item.reason" class="perf-reason">{{ item.reason }}</p>
            </div>
            <div class="perf-card-footer">
              <el-button
                v-if="item.matchedContractName"
                type="primary"
                link
                size="small"
                :icon="CopyDocument"
                @click="handleCopy(item)"
              >
                复制业绩信息
              </el-button>
              <el-button
                v-if="item.matchedContractName"
                type="success"
                link
                size="small"
                :icon="Document"
                @click="insertToDocument(item)"
              >
                插入标书
              </el-button>
              <el-button
                v-if="item.matchedContractName"
                type="info"
                link
                size="small"
                @click="jumpToDetail"
              >
                查看详情
              </el-button>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无匹配业绩" :image-size="80">
        <template #description>
          <p>暂无匹配业绩</p>
          <p class="empty-tip">请确保招标文件已解析，且业绩库中有相关记录</p>
        </template>
      </el-empty>
      <div v-if="summary" class="perf-summary">
        <el-tag type="success" size="small">已满足 {{ summary.totalSatisfied }}</el-tag>
        <el-tag type="warning" size="small">需关注 {{ summary.totalAttention }}</el-tag>
        <el-tag type="danger" size="small">不满足 {{ summary.totalUnsatisfied }}</el-tag>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Document } from '@element-plus/icons-vue'
import bidAgentApi from '@/api/modules/bidAgent.js'

const props = defineProps({
  modelValue: Boolean,
  projectId: { type: [String, Number], required: true }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const matchData = ref(null)

const matchedItems = computed(() => matchData.value?.performanceMatch?.items || [])
const summary = computed(() => matchData.value?.summary || null)

const statusLabel = (s) => ({
  SATISFIED: '已满足', ATTENTION: '需关注', UNSATISFIED: '不满足'
}[s] || s)

const statusClass = (s) => ({
  SATISFIED: 'perf-satisfied', ATTENTION: 'perf-attention', UNSATISFIED: 'perf-unsatisfied'
}[s] || '')

async function handleOpen() {
  loading.value = true
  matchData.value = null
  try {
    const res = await bidAgentApi.getKnowledgeBaseMatch(props.projectId)
    matchData.value = res?.data || res || null
  } catch (e) {
    ElMessage.error('加载业绩推荐失败：' + (e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

async function handleCopy(item) {
  const text = formatPerformanceText(item)
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success({ message: '已复制业绩信息到剪贴板', duration: 3000 })
  } catch {
    ElMessage.error('复制到剪贴板失败')
  }
}

function formatPerformanceText(item) {
  const lines = []
  if (item.matchedContractName) lines.push(`合同名称：${item.matchedContractName}`)
  if (item.matchedSigningEntity) lines.push(`签约单位：${item.matchedSigningEntity}`)
  if (item.remainingDays != null) lines.push(`剩余有效期：${item.remainingDays} 天`)
  if (item.reason) lines.push(`备注：${item.reason}`)
  return lines.join('\n')
}

function insertToDocument(item) {
  const text = formatPerformanceText(item)
  // 将业绩信息存入 sessionStorage，供文档编辑器读取
  sessionStorage.setItem('pendingPerformanceInsert', JSON.stringify({
    contractName: item.matchedContractName,
    signingEntity: item.matchedSigningEntity,
    remainingDays: item.remainingDays,
    reason: item.reason,
    fullText: text,
    timestamp: Date.now()
  }))
  // 打开文档编辑器（新标签页）
  const editorUrl = `/document/editor/${props.projectId}`
  window.open(editorUrl, '_blank')
  ElMessage.success({ message: '已打开文档编辑器，请在编辑器中粘贴业绩内容', duration: 4000 })
}

function jumpToDetail() {
  window.open('/knowledge/performance', '_blank')
}
</script>

<style scoped>
.perf-recommend-drawer { display: flex; flex-direction: column; gap: 16px; min-height: 300px; }
.loading-wrap { padding: 20px 0; }
.drawer-section { display: flex; flex-direction: column; gap: 12px; }
.section-label { font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }
.perf-list { display: flex; flex-direction: column; gap: 12px; }
.perf-card { background: var(--el-bg-color); border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 14px 16px; transition: box-shadow 0.3s; }
.perf-card:hover { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08); }
.perf-satisfied { border-left: 3px solid var(--el-color-success); }
.perf-attention { border-left: 3px solid var(--el-color-warning); }
.perf-unsatisfied { border-left: 3px solid var(--el-color-danger); }
.perf-card-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.perf-badge { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 6px; font-size: 12px; font-weight: 600; white-space: nowrap; }
.perf-satisfied .perf-badge { background: var(--el-color-success-light-9); color: var(--el-color-success); }
.perf-attention .perf-badge { background: var(--el-color-warning-light-9); color: var(--el-color-warning); }
.perf-unsatisfied .perf-badge { background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.perf-contract { font-weight: 600; font-size: 14px; color: var(--el-text-color-primary); }
.perf-card-body p { margin: 0 0 4px; line-height: 1.5; font-size: 13px; }
.perf-req { color: var(--el-text-color-secondary); background: var(--el-fill-color-lighter); padding: 4px 8px; border-radius: 4px; margin-bottom: 8px !important; }
.perf-entity { color: var(--el-text-color-regular); }
.perf-days { color: var(--el-color-warning); }
.perf-reason { color: var(--el-text-color-secondary); font-size: 12px; }
.perf-card-footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--el-border-color-lighter); }
.perf-summary { display: flex; gap: 8px; flex-wrap: wrap; padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
.empty-tip { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px; }
</style>
