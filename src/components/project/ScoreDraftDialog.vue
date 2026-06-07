<template>
  <el-dialog
    :model-value="visible"
    title="评分标准拆解确认"
    width="1100px"
    top="5vh"
    @close="handleClose"
  >
    <div class="score-draft-dialog">
      <div class="toolbar">
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          :on-change="handleFileChange"
          accept=".doc,.docx,.xls,.xlsx,.pdf"
        >
          <el-button type="primary">选择评分文件</el-button>
        </el-upload>
        <span class="file-name">{{ selectedFile?.name || '未选择文件' }}</span>
        <span class="upload-tip">支持 doc、docx、xls、xlsx、文本型 pdf</span>
        <el-button type="success" :disabled="!selectedFile" :loading="parsing" @click="handleParse">
          解析评分标准
        </el-button>
        <el-button :disabled="drafts.length === 0" @click="handleRefresh">刷新</el-button>
        <el-button :disabled="drafts.length === 0" @click="handleClear">清空草稿</el-button>
      </div>

      <div class="summary" v-if="drafts.length > 0">
        <el-tag>总数 {{ drafts.length }}</el-tag>
        <el-tag type="info">草稿 {{ countByStatus('DRAFT') }}</el-tag>
        <el-tag type="warning">待生成 {{ countByStatus('READY') }}</el-tag>
        <el-tag type="danger">暂不生成 {{ countByStatus('SKIPPED') }}</el-tag>
      </div>

      <el-table
        v-loading="loading"
        :data="drafts"
        border
        height="500"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="类别" width="90">
          <template #default="{ row }">
            {{ formatCategory(row.category) }}
          </template>
        </el-table-column>
        <el-table-column label="评分项" prop="scoreItemTitle" min-width="150" />
        <el-table-column label="评分规则" min-width="260">
          <template #default="{ row }">
            <div class="rule-text">{{ row.scoreRuleText }}</div>
          </template>
        </el-table-column>
        <el-table-column label="分值" prop="scoreValueText" width="110" />
        <el-table-column label="建议交付物" min-width="180">
          <template #default="{ row }">
            <div class="deliverables-text">{{ formatDeliverables(row.suggestedDeliverables) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="来源定位" min-width="170">
          <template #default="{ row }">
            <div class="source-text">{{ formatSource(row) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="任务标题" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.generatedTaskTitle" />
          </template>
        </el-table-column>
        <el-table-column label="责任人" width="140">
          <template #default="{ row }">
            <el-input v-model="row.assigneeName" placeholder="手动指定" />
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="180">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.dueDate"
              type="date"
              value-format="YYYY-MM-DDT00:00:00"
              placeholder="选择日期"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-select v-model="row.status">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="待生成" value="READY" />
              <el-option label="暂不生成" value="SKIPPED" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleSave(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" :disabled="selectedReadyDraftIds.length === 0" @click="handleGenerate">
        生成正式任务（{{ selectedReadyDraftIds.length }}）
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectsApi } from '@/api'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  projectId: {
    type: [String, Number],
    required: true,
  },
})

const emit = defineEmits(['update:visible', 'generated'])

const loading = ref(false)
const parsing = ref(false)
const selectedFile = ref(null)
const drafts = ref([])
const selectedRows = ref([])

const readyDraftIds = computed(() => (
  drafts.value
    .filter((row) => row.status === 'READY')
    .map((row) => row.id)
))

const selectedReadyDraftIds = computed(() => {
  const manuallySelectedIds = selectedRows.value
    .filter((row) => row.status === 'READY')
    .map((row) => row.id)

  return manuallySelectedIds.length > 0 ? manuallySelectedIds : readyDraftIds.value
})

watch(() => props.visible, async (visible) => {
  if (visible) {
    await handleRefresh()
  }
})

function countByStatus(status) {
  return drafts.value.filter((draft) => draft.status === status).length
}

function formatCategory(category) {
  if (category === 'technical') return '技术'
  if (category === 'business') return '商务'
  if (category === 'price') return '价格'
  return category || '-'
}

function formatDeliverables(deliverables) {
  return Array.isArray(deliverables) && deliverables.length > 0
    ? deliverables.join('、')
    : '待补充'
}

function formatSource(row) {
  const parts = []
  if (row.sourceFileName) {
    parts.push(row.sourceFileName)
  }
  if (Number.isInteger(row.sourcePage) && row.sourcePage > 0) {
    parts.push(`第${row.sourcePage}页`)
  }
  if (Number.isInteger(row.sourceTableIndex) && row.sourceTableIndex >= 0) {
    parts.push(`表${row.sourceTableIndex + 1}`)
  }
  if (Number.isInteger(row.sourceRowIndex) && row.sourceRowIndex >= 0) {
    parts.push(`行${row.sourceRowIndex + 1}`)
  }
  return parts.length > 0 ? parts.join(' / ') : '待补充'
}

function handleFileChange(file) {
  selectedFile.value = file.raw
}

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

async function handleRefresh() {
  loading.value = true
  try {
    const result = await projectsApi.getScoreDrafts(props.projectId)
    drafts.value = Array.isArray(result?.data) ? result.data : []
  } catch (error) {
    ElMessage.error(error.message || '加载评分草稿失败')
  } finally {
    loading.value = false
  }
}

async function handleParse() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择评分文件')
    return
  }

  parsing.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    const result = await projectsApi.parseScoreDrafts(props.projectId, formData)
    const parsedDrafts = Array.isArray(result?.data?.drafts) ? result.data.drafts : []
    drafts.value = parsedDrafts
    selectedRows.value = []
    ElMessage.success(`已解析 ${parsedDrafts.length} 条评分草稿`)
  } catch (error) {
    ElMessage.error(error.message || '评分标准解析失败')
  } finally {
    parsing.value = false
  }
}

async function handleSave(row) {
  const payload = {
    assigneeId: row.assigneeId || null,
    assigneeName: row.assigneeName || '',
    dueDate: row.dueDate || null,
    generatedTaskTitle: row.generatedTaskTitle,
    generatedTaskDescription: row.generatedTaskDescription,
    status: row.status,
    skipReason: row.skipReason || '',
  }

  if (payload.status === 'DRAFT' && payload.assigneeName) {
    payload.status = 'READY'
  }

  try {
    const result = await projectsApi.updateScoreDraft(props.projectId, row.id, payload)
    if (result?.data) {
      Object.assign(row, result.data)
    }
    ElMessage.success('评分草稿已保存')
  } catch (error) {
    ElMessage.error(error.message || '评分草稿保存失败')
  }
}

async function handleGenerate() {
  if (selectedReadyDraftIds.value.length === 0) {
    ElMessage.warning('请至少选择一条 READY 状态的评分草稿')
    return
  }

  try {
    const result = await projectsApi.generateScoreDraftTasks(props.projectId, selectedReadyDraftIds.value)
    const tasks = Array.isArray(result?.data) ? result.data : []
    emit('generated', tasks)
    ElMessage.success(`已生成 ${tasks.length} 条正式任务`)
    emit('update:visible', false)
  } catch (error) {
    ElMessage.error(error.message || '正式任务生成失败')
  }
}

async function handleClear() {
  try {
    await ElMessageBox.confirm('确认清空未生成的评分草稿吗？', '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await projectsApi.clearScoreDrafts(props.projectId)
    drafts.value = []
    selectedRows.value = []
    ElMessage.success('评分草稿已清空')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '清空评分草稿失败')
    }
  }
}

function handleClose() {
  emit('update:visible', false)
}
</script>

<style scoped>
.score-draft-dialog { display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.file-name { color: var(--text-secondary-ui); font-size: 13px; }
.upload-tip { color: var(--text-muted); font-size: 12px; }
.summary { display: flex; gap: 8px; }
.rule-text { white-space: pre-wrap; line-height: 1.5; }

.deliverables-text,
.source-text {
  color: var(--text-secondary-ui);
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
}
</style>
