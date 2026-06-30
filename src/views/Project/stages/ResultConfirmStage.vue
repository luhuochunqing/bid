<template>
  <div class="result-stage">
    <!-- 投标结果 -->
    <el-card shadow="never" class="stage-section">
      <template #header><span class="section-title">投标结果</span></template>
      <div class="result-cards">
        <div
          v-for="opt in resultOptions"
          :key="opt.value"
          class="result-chip"
          :class="{ 'result-chip--active': form.resultType === opt.value, 'result-chip--clickable': canOperate }"
          @click="selectResult(opt.value)"
        >
          <span class="result-chip-label">{{ opt.label }}</span>
        </div>
      </div>

      <!-- 未中标/流标/弃标 → 原因（CO-322）；CO-320: 中标金额/合同日期字段已移除 -->
      <div v-if="NON_WON_TYPES.includes(form.resultType)" class="summary-section">
        <label class="field-label">{{ summaryLabel }}<span class="required-mark">*</span></label>
        <el-input v-model="form.summary" type="textarea" :rows="3" :placeholder="summaryPlaceholder" :disabled="!canOperate" />
      </div>
    </el-card>

    <!-- 凭证文件 -->
    <el-card shadow="never" class="stage-section">
      <template #header><span class="section-title">凭证文件<span class="required-mark">*</span></span></template>
      <el-upload :with-credentials="true"
        v-model:file-list="evidenceFiles"
        :action="uploadUrl"
        :headers="uploadHeaders"
        :data="{ documentCategory: 'WIN_NOTICE' }"
        :accept="acceptedTypes"
        :before-upload="beforeUpload"
        drag
        multiple
        :limit="5"
        name="file"
        :on-success="handleUploadSuccess"
        :on-error="handleUploadError"
        :on-remove="handleUploadRemove"
        :disabled="!canOperate"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">{{ evidenceTip }}</div>
        </template>
        <template #file="{ file }">
          <div class="evidence-file-row">
            <a href="javascript:void(0)" class="upload-file-link" @click.prevent="handleDownloadFile(file)">{{ file.name }}</a>
            <el-button
              v-if="canOperate"
              link
              type="danger"
              size="small"
              @click.prevent="handleEvidenceFileRemove(file)"
            >删除</el-button>
          </div>
        </template>
      </el-upload>
    </el-card>

    <!-- 竞争对手情况 -->
    <el-card shadow="never" class="stage-section">
      <template #header><span class="section-title">竞争对手情况</span></template>
      <el-table :data="form.competitors" border size="small" class="competitor-table">
        <el-table-column prop="name" label="竞争对手名称" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.name" placeholder="输入名称" size="small" :disabled="!canOperate" />
          </template>
        </el-table-column>
        <el-table-column prop="discount" label="折扣" width="120">
          <template #default="{ row }">
            <el-input v-model="row.discount" placeholder="如：95折" size="small" :disabled="!canOperate" />
          </template>
        </el-table-column>
        <el-table-column prop="paymentTerm" label="账期" width="140">
          <template #default="{ row }">
            <el-input v-model="row.paymentTerm" placeholder="如：月结60天" size="small" :disabled="!canOperate" />
          </template>
        </el-table-column>
        <el-table-column prop="notes" label="其他说明" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.notes" placeholder="补充信息" size="small" :disabled="!canOperate" />
          </template>
        </el-table-column>
        <el-table-column v-if="canOperate" label="操作" width="90" align="center">
          <template #default="{ $index }">
            <el-button type="danger" size="small" :icon="Delete" circle @click="removeCompetitor($index)" />
          </template>
        </el-table-column>
      </el-table>
      <el-button v-if="canOperate" class="add-row-btn" type="primary" plain size="small" :icon="Plus" @click="addCompetitor">添加一行</el-button>
    </el-card>


    <!-- 操作按钮 -->
    <div v-if="canOperate" class="btn-container">
      <el-button v-if="resultDone" type="primary" size="large" disabled>已登记</el-button>
      <el-button v-else type="primary" size="large" :loading="submitting" @click="submit">登记结果</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus, UploadFilled } from '@element-plus/icons-vue'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { getResultConfirmNextTab } from '@/constants/projectStages.js'
import { getApiUrl } from '@/api/config.js'
import { useUserStore } from '@/stores/user.js'
import { downloadWithFilename } from '@/utils/download.js'
import { getDocuments } from '@/api/modules/projectDocuments.js'

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const emit = defineEmits(['registered', 'switch-tab'])
const userStore = useUserStore()

const OPERABLE_ROLES = ['admin', 'bid-administration', 'auditor', '/bidAdmin', 'bid-TeamLeader', 'bid-Team', 'manager', 'bid-projectLeader']
const currentRoleCode = computed(() => userStore?.currentUser?.roleCode || userStore?.currentUser?.role || '')
const resultDone = ref(false)
const canOperate = computed(() => !resultDone.value && OPERABLE_ROLES.includes(currentRoleCode.value))

const resultOptions = [
  { value: 'WON', label: '中标' },
  { value: 'LOST', label: '未中标' },
  { value: 'FAILED', label: '流标' },
  { value: 'ABANDONED', label: '弃标' },
]

const DEFAULT_COMPETITOR = () => ({ name: '', discount: '', paymentTerm: '', notes: '' })
const DEFAULT_COMPETITORS = () => [DEFAULT_COMPETITOR(), DEFAULT_COMPETITOR(), DEFAULT_COMPETITOR()]

const form = reactive({
  resultType: 'WON',
  notes: '', summary: '', evidenceFileIds: [], competitors: DEFAULT_COMPETITORS(),
})

function selectResult(value) {
  if (!canOperate.value) return
  form.resultType = value
}
const evidenceFiles = ref([])
const existing = ref(null)
const submitting = ref(false)
const resultNextTab = computed(() => getResultConfirmNextTab(form.resultType))

const uploadUrl = computed(() => getApiUrl(`/api/projects/${props.projectId}/documents`))
const acceptedTypes = '.pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx'
const MAX_FILE_SIZE_MB = 10
const ALLOWED_MIMES = ['application/pdf', 'image/jpeg', 'image/jpg', 'image/png', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document']

const evidenceTip = computed(() => {
  const tips = { WON: '中标通知书', LOST: '未中标说明或官方结果公告', FAILED: '流标公告', ABANDONED: '弃标说明' }
  return `${tips[form.resultType] || ''}，支持 PDF/图片/Word，单文件≤10MB，最多5个`
})

const SUMMARY_META = {
  LOST: { label: '丢标原因', placeholder: '请填写丢标原因...' },
  FAILED: { label: '流标原因', placeholder: '请填写流标原因...' },
  ABANDONED: { label: '弃标原因', placeholder: '请填写弃标原因...' },
}
// CO-322: 非 WON 结果类型单一来源（template v-if + submit 校验共用），避免新增类型时分歧
const NON_WON_TYPES = Object.keys(SUMMARY_META)
const summaryLabel = computed(() => (SUMMARY_META[form.resultType] || {}).label || '原因')
const summaryPlaceholder = computed(() => (SUMMARY_META[form.resultType] || {}).placeholder || '')

const uploadHeaders = computed(() => {
  const token = userStore?.token
  return token ? { Authorization: `Bearer ${token}` } : {}
})

function beforeUpload(file) {
  if (file.type && !ALLOWED_MIMES.includes(file.type)) {
    ElMessage.error(`不支持的文件类型: ${file.type || '未知'}`)
    return false
  }
  if (file.size > MAX_FILE_SIZE_MB * 1024 * 1024) { ElMessage.error(`文件不能超过 ${MAX_FILE_SIZE_MB}MB`); return false }
  return true
}

function handleUploadSuccess(response) {
  if (response?.data?.id) form.evidenceFileIds.push(response.data.id)
  else ElMessage.warning('上传响应异常，缺少文件ID')
}

function handleUploadError(err) {
  const msg = err?.response?.data?.msg || err?.message || '上传失败'
  ElMessage.error('凭证上传失败: ' + msg)
}

function handleUploadRemove(uploadFile) {
  const idx = form.evidenceFileIds.indexOf(uploadFile.response?.data?.id)
  if (idx > -1) form.evidenceFileIds.splice(idx, 1)
}

function handleEvidenceFileRemove(file) { handleUploadRemove(file); evidenceFiles.value = evidenceFiles.value.filter((item) => item !== file) }

// CO-375: 凭证文件下载
function handleDownloadFile(file) {
  const documentId = file.response?.data?.id
  if (!documentId) { ElMessage.warning('文件信息缺失，无法下载'); return }
  const url = `/api/projects/${props.projectId}/documents/${documentId}/download`
  downloadWithFilename(url, file.name || '凭证文件')
}

function addCompetitor() { form.competitors.push(DEFAULT_COMPETITOR()) }
function removeCompetitor(index) {
  if (form.competitors.length <= 1) { ElMessage.info('至少保留一行'); return }
  form.competitors.splice(index, 1)
}

async function load() {
  try {
    const r = await projectLifecycleApi.getResult(props.projectId)
    const data = r?.data || r
    existing.value = data
    if (data) {
      // 恢复所有表单字段
      if (data.resultType) form.resultType = data.resultType
      if (data.notes != null) form.notes = data.notes
      if (data.summary != null) form.summary = data.summary
      if (data.evidenceFileIds?.length) {
        form.evidenceFileIds = [...data.evidenceFileIds]
        // CO-408: 根据 evidenceFileIds 回填 evidenceFiles（el-upload file-list），避免再次进入页面时文件名丢失
        await backfillEvidenceFiles(data.evidenceFileIds)
      }
      if (data.competitors?.length) form.competitors = data.competitors.map(c => ({ ...c }))
      // 已登记的结果不可再编辑
      if (data.registeredAt) resultDone.value = true
    }
  } catch (e) { if (e?.response?.status !== 404) console.warn(e) }
}

// CO-408: 按 ids 拉取项目文档并回填 evidenceFiles
async function backfillEvidenceFiles(ids) {
  try {
    const r = await getDocuments(props.projectId)
    const docs = r?.data || r || []
    const docMap = new Map(docs.map(d => [Number(d.id), d]))
    evidenceFiles.value = ids
      .map(id => docMap.get(Number(id)))
      .filter(Boolean)
      .map(doc => ({
        name: doc.name || '凭证文件',
        url: doc.fileUrl || '',
        response: { data: doc },
        status: 'success',
      }))
  } catch (e) { console.error('回填凭证文件失败:', e) }
}

async function submit() {
  if (!form.resultType) return ElMessage.warning('请选择结果类型')
  if (NON_WON_TYPES.includes(form.resultType) && !form.summary?.trim()) return ElMessage.warning('请填写原因')
  if (!form.evidenceFileIds.length) return ElMessage.warning('请上传凭证文件')
  submitting.value = true
  try {
    const payload = {
      resultType: form.resultType,
      notes: form.notes, summary: form.summary,
      evidenceFileIds: form.evidenceFileIds, competitors: form.competitors,
    }
    await projectLifecycleApi.registerResult(props.projectId, payload)
    resultDone.value = true
    ElMessage.success('结果已登记，已推进至下一阶段')
    emit('registered')
    emit('switch-tab', resultNextTab.value)
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '登记失败') }
  finally { submitting.value = false }
}

onMounted(load)

defineExpose({ load })
</script>

<style scoped>
.result-stage { display: flex; flex-direction: column; gap: 16px; }
.section-title { font-size: 15px; font-weight: 600; color: #2E7659; }
.upload-file-link { color: var(--el-color-primary); text-decoration: none; }
.upload-file-link:hover { text-decoration: underline; }
.evidence-file-row { display: flex; align-items: center; gap: 8px; }

/* 结果类型卡片 */
.result-cards { display: flex; flex-wrap: wrap; gap: 16px; margin-bottom: 4px; }
.result-chip {
  display: flex; align-items: center; justify-content: center;
  flex: 1; min-width: 100px; padding: 14px 16px; border-radius: 8px;
  border: 2px solid var(--el-border-color); background: var(--el-fill-color-light);
  user-select: none; transition: all 0.2s;
}
.result-chip--clickable { cursor: pointer; }
.result-chip--clickable:hover { border-color: var(--el-color-primary); }
.result-chip--active {
  background: var(--el-color-primary); color: #fff; border-color: var(--el-color-primary);
}
.result-chip--active .result-chip-label { color: #fff; }
.result-chip-label { font-size: 15px; font-weight: normal; color: #333; }

/* 中标合同信息 */
.contract-row { display: flex; gap: 20px; flex-wrap: wrap; margin-top: 16px; }
.contract-field { display: flex; flex-direction: column; gap: 6px; flex: 1; min-width: 160px; }
.field-label { font-size: 13px; font-weight: 500; color: #555; }

/* 流标/弃标摘要 */
.summary-section { margin-top: 16px; }
.summary-section .field-label { display: block; margin-bottom: 8px; }

/* 必填标记 */
.required-mark { color: #e65100; margin-left: 2px; }

/* 竞争对手 */
.competitor-table { width: 100%; }
.competitor-table :deep(.el-table__body td) { padding: 2px 0; }
.competitor-table :deep(.el-table__body .el-input__inner) { height: 28px; }
.add-row-btn { margin-top: 12px; }

/* 操作按钮 */
.btn-container { display: flex; justify-content: flex-end; }
</style>
