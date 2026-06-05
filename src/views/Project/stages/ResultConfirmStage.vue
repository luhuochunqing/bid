<template>
  <el-card class="stage-view" shadow="never">
    <template #header>结果确认 (Result)</template>
    <div class="result-form">
      <div class="form-section">
        <div class="section-title">结果类型</div>
        <el-radio-group v-model="form.resultType" class="result-type-cards" :disabled="!canOperate">
          <el-radio-button value="WON">中标</el-radio-button>
          <el-radio-button value="LOST">未中标</el-radio-button>
          <el-radio-button value="FAILED">流标</el-radio-button>
          <el-radio-button value="ABANDONED">弃标</el-radio-button>
        </el-radio-group>
      </div>
      <template v-if="form.resultType === 'WON'">
        <div class="form-section">
          <div class="section-title">合同信息</div>
          <div class="contract-row">
            <div class="contract-field">
              <span class="field-label">中标金额(万元)</span>
              <el-input-number v-model="form.awardAmount" :min="0" :precision="2" />
            </div>
            <div class="contract-field">
              <span class="field-label">合同开始日期</span>
              <el-date-picker v-model="form.contractStartDate" type="date" value-format="YYYY-MM-DD" />
            </div>
            <div class="contract-field">
              <span class="field-label">合同结束日期</span>
              <el-date-picker v-model="form.contractEndDate" type="date" value-format="YYYY-MM-DD" />
            </div>
          </div>
        </div>
      </template>
      <div class="form-section" v-if="form.resultType === 'FAILED' || form.resultType === 'ABANDONED'">
        <div class="section-title">结果摘要</div>
        <el-input v-model="form.summary" type="textarea" :rows="3" placeholder="请填写流标/弃标原因摘要..." :disabled="!canOperate" />
      </div>
      <div class="form-section">
        <div class="section-title">凭证文件</div>
        <el-upload
          v-model:file-list="evidenceFiles"
          :action="uploadUrl"
          :headers="uploadHeaders"
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
        </el-upload>
      </div>
      <div class="form-section">
        <div class="section-title">竞争对手情况</div>
        <div class="competitor-table">
          <el-table :data="form.competitors" border size="small" style="width: 100%">
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
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ $index }">
                <el-button type="danger" size="small" :icon="Delete" circle :disabled="!canOperate" @click="removeCompetitor($index)" />
              </template>
            </el-table-column>
          </el-table>
          <el-button class="add-row-btn" type="primary" size="small" :icon="Plus" :disabled="!canOperate" @click="addCompetitor">添加一行</el-button>
        </div>
      </div>
      <div class="form-section">
        <div class="section-title">备注</div>
        <el-input v-model="form.notes" type="textarea" :rows="3" placeholder="其他备注信息（选填）" :disabled="!canOperate" />
      </div>
      <div class="form-actions">
        <el-button v-if="resultDone" type="primary" disabled>已登记</el-button>
        <el-button v-else type="primary" :loading="submitting" :disabled="!canOperate" @click="submit">登记结果</el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus, UploadFilled } from '@element-plus/icons-vue'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { getApiUrl } from '@/api/config.js'
import { useUserStore } from '@/stores/user.js'

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const emit = defineEmits(['registered', 'switch-tab'])
const userStore = useUserStore()

const OPERABLE_ROLES = ['admin', 'admin_staff', 'auditor', 'bid_admin', 'bid_lead', 'bid_specialist', 'manager', 'sales', 'staff', 'task_executor']
const currentRoleCode = computed(() => userStore?.currentUser?.roleCode || userStore?.currentUser?.role || '')
const canOperate = computed(() => OPERABLE_ROLES.includes(currentRoleCode.value))

const DEFAULT_COMPETITOR = () => ({ name: '', discount: '', paymentTerm: '', notes: '' })
const DEFAULT_COMPETITORS = () => [DEFAULT_COMPETITOR(), DEFAULT_COMPETITOR(), DEFAULT_COMPETITOR()]

const form = reactive({
  resultType: 'WON', awardAmount: 0, contractStartDate: '', contractEndDate: '',
  notes: '', summary: '', evidenceFileIds: [], competitors: DEFAULT_COMPETITORS(),
})
const evidenceFiles = ref([])
const existing = ref(null)
const submitting = ref(false)
const resultDone = ref(false)
const resultNextTab = computed(() => (form.resultType === 'WON' || form.resultType === 'LOST') ? 'RETROSPECTIVE' : 'CLOSED')

const uploadUrl = computed(() => getApiUrl(`/api/projects/${props.projectId}/documents`))
const acceptedTypes = '.pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx'
const MAX_FILE_SIZE_MB = 10
const ALLOWED_MIMES = ['application/pdf', 'image/jpeg', 'image/jpg', 'image/png', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document']

const evidenceTip = computed(() => {
  const tips = { WON: '中标通知书', LOST: '未中标说明或官方结果公告', FAILED: '流标公告', ABANDONED: '弃标说明' }
  return `${tips[form.resultType] || ''}，支持 PDF/图片/Word，单文件≤10MB，最多5个`
})

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

function addCompetitor() { form.competitors.push(DEFAULT_COMPETITOR()) }
function removeCompetitor(index) {
  if (form.competitors.length <= 1) { ElMessage.info('至少保留一行'); return }
  form.competitors.splice(index, 1)
}

async function load() {
  try {
    const r = await projectLifecycleApi.getResult(props.projectId)
    existing.value = r?.data || r
    if (existing.value?.competitors?.length) form.competitors = existing.value.competitors.map(c => ({ ...c }))
  } catch (e) { if (e?.response?.status !== 404) console.warn(e) }
}

async function submit() {
  if (!form.resultType) return ElMessage.warning('请选择结果类型')
  if ((form.resultType === 'FAILED' || form.resultType === 'ABANDONED') && !form.summary?.trim()) return ElMessage.warning('流标/弃标结果需填写摘要')
  if (!form.evidenceFileIds.length) return ElMessage.warning('请上传凭证文件')
  submitting.value = true
  try {
    const payload = {
      resultType: form.resultType,
      awardAmount: form.resultType === 'WON' ? form.awardAmount : null,
      contractStartDate: form.resultType === 'WON' ? form.contractStartDate || null : null,
      contractEndDate: form.resultType === 'WON' ? form.contractEndDate || null : null,
      notes: form.notes, summary: form.summary,
      evidenceFileIds: form.evidenceFileIds, competitors: form.competitors,
    }
    await projectLifecycleApi.registerResult(props.projectId, payload)
    resultDone.value = true
    ElMessage.success('结果已登记，已推进至下一阶段')
    emit('switch-tab', resultNextTab.value)
    emit('registered')
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '登记失败') }
  finally { submitting.value = false }
}

onMounted(load)
</script>

<style scoped>
.result-form { display: flex; flex-direction: column; gap: 20px; }
.form-section { display: flex; flex-direction: column; gap: 8px; }
.section-title { font-weight: 600; font-size: 14px; color: #303133; }
.form-actions { display: flex; justify-content: flex-end; margin-top: 8px; }
.contract-row { display: flex; gap: 24px; flex-wrap: wrap; }
.contract-field { display: flex; flex-direction: column; gap: 4px; }
.field-label { font-size: 13px; color: #606266; }
.competitor-table { width: 100%; }
.add-row-btn { margin-top: 8px; }
.result-type-cards { display: inline-flex; gap: 12px; flex-wrap: wrap; }
</style>
