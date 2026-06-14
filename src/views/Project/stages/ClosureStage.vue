<template>
  <div class="closure-stage">

    <!-- 一、保证金管理 -->
    <div class="form-section">
      <div class="form-section-title">保证金管理</div>

      <!-- 是否有保证金 -->
      <div class="field-row">
        <div class="field-item">
          <label>是否有保证金 <span class="required-mark">*</span></label>
          <input class="fake-input" :value="preview?.hasDeposit ? '有' : '无'" disabled />
        </div>
      </div>

      <template v-if="preview?.hasDeposit">
        <!-- 保证金金额 + 缴纳方式 -->
        <div class="deposit-info">
          <div class="deposit-info-item">
            <span class="di-label">保证金金额（万）</span>
            <span class="di-value">{{ formatAmount(preview?.depositAmount) }}</span>
          </div>
          <div class="deposit-info-item">
            <span class="di-label">缴纳方式</span>
            <span class="di-value">{{ preview?.depositPaymentMethod || '-' }}</span>
          </div>
        </div>

        <template v-if="canEditDeposit">
          <!-- 保证金退回情况 -->
          <div class="field-row">
            <div class="field-item">
              <label>保证金退回情况 <span class="required-mark">*</span></label>
              <el-select
                v-model="form.depositReturnStatus"
                placeholder="请选择"
                style="width:320px"
                @change="onDepositStatusChange"
              >
                <el-option label="未退回" value="NOT_RETURNED" />
                <el-option label="全部退回" value="FULLY_RETURNED" />
                <el-option label="转平台服务费" value="TRANSFERRED_TO_FEE" />
                <el-option label="部分退回，部分转平台服务费" value="PARTIAL_RETURN_PARTIAL_TRANSFER" />
              </el-select>
            </div>
          </div>

          <!-- 全部退回子字段 -->
          <div v-if="form.depositReturnStatus === 'FULLY_RETURNED'" class="dynamic-fields">
            <div class="field-row">
              <div class="field-item">
                <label>退回日期 <span class="required-mark">*</span></label>
                <el-date-picker
                  v-model="form.depositReturnDate"
                  type="date"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  placeholder="选择退回日期"
                  style="width:100%"
                />
              </div>
              <div class="field-item">
                <label>退回凭证 <span class="required-mark">*</span></label>
                <el-upload
                  :action="uploadUrl"
                  :headers="uploadHeaders"
                  :data="{ tag: '保证金银行回单' }"
                  :accept="acceptedTypes"
                  :before-upload="beforeUpload"
                  :limit="1"
                  :on-success="handleEvidenceUploadSuccess"
                  :on-remove="handleEvidenceRemove"
                  :file-list="depositEvidenceFiles"
                >
                  <el-button type="primary" size="small">上传银行回单</el-button>
                  <template #tip><div class="el-upload__tip">支持 PDF/JPG/PNG，不超过 10MB</div></template>
                </el-upload>
                <div v-if="form.depositReturnEvidenceId" class="uploaded-hint">已上传</div>
              </div>
            </div>
          </div>

          <!-- 转平台服务费子字段 -->
          <div v-if="form.depositReturnStatus === 'TRANSFERRED_TO_FEE'" class="dynamic-fields">
            <div class="field-row">
              <div class="field-item">
                <label>转服务费金额（元） <span class="required-mark">*</span></label>
                <el-input-number v-model="form.transferAmount" :min="0.01" :precision="2" placeholder="请输入金额" style="width:100%" />
              </div>
              <div class="field-item">
                <label>证明文件 <span class="required-mark">*</span></label>
                <el-upload
                  :action="uploadUrl"
                  :headers="uploadHeaders"
                  :data="{ tag: '保证金银行回单' }"
                  :accept="acceptedTypes"
                  :before-upload="beforeUpload"
                  :limit="1"
                  :on-success="handleEvidenceUploadSuccess"
                  :on-remove="handleEvidenceRemove"
                  :file-list="depositEvidenceFiles"
                >
                  <el-button type="primary" size="small">上传证明文件</el-button>
                  <template #tip><div class="el-upload__tip">支持 PDF/JPG/PNG，不超过 10MB</div></template>
                </el-upload>
                <div v-if="form.depositReturnEvidenceId" class="uploaded-hint">已上传</div>
              </div>
            </div>
          </div>

          <!-- 部分退回+部分转服务费子字段 -->
          <div v-if="form.depositReturnStatus === 'PARTIAL_RETURN_PARTIAL_TRANSFER'" class="dynamic-fields">
            <div class="field-row">
              <div class="field-item">
                <label>退回金额（元） <span class="required-mark">*</span></label>
                <el-input-number v-model="form.returnedAmount" :min="0.01" :precision="2" placeholder="请输入退回金额" style="width:100%" />
              </div>
              <div class="field-item">
                <label>转服务费金额（元） <span class="required-mark">*</span></label>
                <el-input-number v-model="form.transferAmount" :min="0.01" :precision="2" placeholder="请输入转服务费金额" style="width:100%" />
              </div>
            </div>
            <div class="field-row">
              <div class="field-item full-width">
                <label>证明文件 <span class="required-mark">*</span></label>
                <el-upload
                  :action="uploadUrl"
                  :headers="uploadHeaders"
                  :data="{ tag: '保证金银行回单' }"
                  :accept="acceptedTypes"
                  :before-upload="beforeUpload"
                  :limit="1"
                  :on-success="handleEvidenceUploadSuccess"
                  :on-remove="handleEvidenceRemove"
                  :file-list="depositEvidenceFiles"
                >
                  <el-button type="primary" size="small">上传证明文件</el-button>
                  <template #tip><div class="el-upload__tip">支持 PDF/JPG/PNG，不超过 10MB</div></template>
                </el-upload>
                <div v-if="form.depositReturnEvidenceId" class="uploaded-hint">已上传</div>
              </div>
            </div>
          </div>

          <!-- 未退回提示 -->
          <el-alert
            v-if="form.depositReturnStatus === 'NOT_RETURNED'"
            type="warning" :closable="false" show-icon
            title="保证金未退回时，无法提交结项申请。请先完成保证金退回。"
          />
        </template>

        <!-- 只读：已提交的退回情况 -->
        <template v-if="!canEditDeposit && preview?.depositReturnStatus && preview?.depositReturnStatus !== 'NA'">
          <div class="readonly-block">
            <div class="readonly-row"><span class="readonly-label">保证金退回情况</span><span class="readonly-value">{{ depositStatusLabel(preview?.depositReturnStatus) }}</span></div>
            <template v-if="preview?.depositReturnStatus === 'FULLY_RETURNED'">
              <div class="readonly-row"><span class="readonly-label">退回日期</span><span class="readonly-value">{{ preview?.depositReturnDate || '-' }}</span></div>
              <div class="readonly-row"><span class="readonly-label">退回凭证</span><span class="readonly-value" v-if="preview?.depositReturnEvidenceId">已上传</span><span class="readonly-value" v-else>-</span></div>
            </template>
            <template v-if="preview?.depositReturnStatus === 'TRANSFERRED_TO_FEE'">
              <div class="readonly-row"><span class="readonly-label">转服务费金额</span><span class="readonly-value">{{ formatAmount(preview?.transferAmount) }} 元</span></div>
              <div class="readonly-row"><span class="readonly-label">证明文件</span><span class="readonly-value" v-if="preview?.depositReturnEvidenceId">已上传</span><span class="readonly-value" v-else>-</span></div>
            </template>
            <template v-if="preview?.depositReturnStatus === 'PARTIAL_RETURN_PARTIAL_TRANSFER'">
              <div class="readonly-row"><span class="readonly-label">退回金额</span><span class="readonly-value">{{ formatAmount(preview?.returnedAmount) }} 元</span></div>
              <div class="readonly-row"><span class="readonly-label">转服务费金额</span><span class="readonly-value">{{ formatAmount(preview?.transferAmount) }} 元</span></div>
              <div class="readonly-row"><span class="readonly-label">证明文件</span><span class="readonly-value" v-if="preview?.depositReturnEvidenceId">已上传</span><span class="readonly-value" v-else>-</span></div>
            </template>
          </div>
        </template>
      </template>
    </div>

    <!-- 二、项目总结 -->
    <div class="form-section">
      <div class="form-section-title">
        <span>项目总结</span>
        <div class="section-actions">
          <el-button v-if="isBidManager || preview?.reviewStatus === 'APPROVED'" size="small" :loading="exporting" @click="handleExportDocs">📥 文档导出</el-button>
          <el-tooltip
            v-if="preview?.reviewStatus === 'APPROVED' && isBidManager"
            :content="precipitateTooltip"
            :disabled="precipitateReady"
            raw-content
            placement="top"
            effect="dark"
          >
            <el-button size="small" type="primary" :disabled="!precipitateReady || precipitating" :loading="precipitating" @click="triggerPrecipitation">🤖 AI自动生成案例</el-button>
          </el-tooltip>
        </div>
      </div>
      <div class="field-item full-width">
        <template v-if="canEditSummary">
          <div style="border:1px solid var(--el-border-color);border-radius:4px;">
            <Toolbar style="border-bottom:1px solid var(--el-border-color)" :editor="richEditorRef" :defaultConfig="toolbarConfig" mode="default" />
            <Editor style="height:300px;overflow-y:hidden" v-model="form.projectSummary" :defaultConfig="editorConfig" mode="default" @onCreated="richEditorCreated" />
          </div>
        </template>
        <div v-else class="summary-readonly rich-text-content" v-html="safeHtml(form.projectSummary) || '<span class=\'text-gray\'>(暂无项目总结)</span>'"></div>
      </div>
    </div>

    <!-- 三、操作按钮 -->
    <div class="btn-container">
      <el-button v-if="canSubmitClosure" type="primary" :disabled="!canSubmit || preview?.alreadyClosed" :loading="submitting" @click="submitClosure">提交结项</el-button>
      <el-button v-if="canApprove" type="success" :loading="approving" @click="doApprove">通过</el-button>
      <el-button v-if="canApprove" type="danger" plain :loading="rejecting" @click="showRejectDialog = true">驳回</el-button>

      <template v-if="preview?.reviewStatus === 'APPROVED'">
        <el-button type="success" disabled style="opacity:0.6;cursor:not-allowed;">已结项</el-button>
        <el-button v-if="isProjectLeader || isBidManager" :loading="rebidLoading" @click="handleRebid">二次招标</el-button>
      </template>
    </div>

    <!-- 驳回对话框 -->
    <el-dialog v-model="showRejectDialog" title="驳回结项申请" width="400px">
      <el-form>
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRejectDialog = false">取消</el-button>
        <el-button type="danger" :disabled="!rejectReason.trim()" :loading="rejecting" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, shallowRef, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { getApiUrl } from '@/api/config.js'
import { casesApi } from '@/api/modules/knowledge.js'
import { useUserStore } from '@/stores/user'
import { safeHtml } from '@/utils/safeHtml.js'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import { useRouter } from 'vue-router'
import { readinessToTooltip } from './readinessTooltip.js'

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const emit = defineEmits(['closed'])
const router = useRouter()

const userStore = useUserStore()
const userRole = computed(() => userStore.userRole)

const preview = ref(null)
const submitting = ref(false)
const approving = ref(false)
const rejecting = ref(false)
const showRejectDialog = ref(false)
const rejectReason = ref('')

// 富文本编辑器
const richEditorRef = shallowRef(null)
const toolbarConfig = {}
const editorConfig = { placeholder: '请输入项目整体总结、遗留问题、后续跟进事项等（非必填）' }
let editorInstance = null
function richEditorCreated(editor) { editorInstance = editor; richEditorRef.value = editor }
onBeforeUnmount(() => { if (editorInstance) { editorInstance.destroy(); editorInstance = null } })

// 文档导出
const exporting = ref(false)
async function handleExportDocs() {
  exporting.value = true
  try {
    const response = await projectLifecycleApi.exportProjectArchive(props.projectId)
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a'); link.href = url
    link.setAttribute('download', '项目资料_' + props.projectId + '.zip')
    document.body.appendChild(link); link.click(); document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('项目资料导出成功')
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '导出失败') }
  finally { exporting.value = false }
}

// 文件上传配置
const uploadUrl = computed(() => getApiUrl(`/api/projects/${props.projectId}/documents`))
const acceptedTypes = '.pdf,.jpg,.jpeg,.png'
const MAX_FILE_SIZE_MB = 10
const ALLOWED_MIMES = ['application/pdf', 'image/jpeg', 'image/jpg', 'image/png']
const depositEvidenceFiles = ref([])
const uploadHeaders = computed(() => { const t = userStore?.token; return t ? { Authorization: 'Bearer ' + t } : {} })

const form = reactive({
  depositReturnStatus: '', depositReturnDate: '', depositReturnEvidenceId: null,
  transferAmount: null, returnedAmount: null, projectSummary: '', notes: '', archiveLocation: '',
})

const isProjectLeader = computed(() => userRole.value === 'sales')
const isBidManager = computed(() => userRole.value === 'bid_admin' || userRole.value === 'bid_lead' || userRole.value === 'bid_staff')

const canEditDeposit = computed(() => {
  if (!isProjectLeader.value && !isBidManager.value) return false
  if (preview.value?.alreadyClosed) return false
  return preview.value?.reviewStatus !== 'APPROVED'
})

const canEditSummary = computed(() => {
  if (!isProjectLeader.value && !isBidManager.value) return false
  if (preview.value?.alreadyClosed) return false
  return preview.value?.reviewStatus !== 'APPROVED'
})

const canSubmitClosure = computed(() => {
  if (!isProjectLeader.value && !isBidManager.value) return false
  if (preview.value?.alreadyClosed) return false
  if (preview.value?.reviewStatus === 'APPROVED') return false
  if (preview.value?.reviewStatus === 'PENDING') return false
  return true
})

const canApprove = computed(() => {
  if (!isBidManager.value) return false
  return preview.value?.reviewStatus === 'PENDING'
})

const canSubmit = computed(() => {
  if (!preview.value?.hasDeposit) return true
  const s = form.depositReturnStatus
  if (!s) return false
  if (s === 'NOT_RETURNED') return false
  if (s === 'FULLY_RETURNED') return !!form.depositReturnDate && !!form.depositReturnEvidenceId
  if (s === 'TRANSFERRED_TO_FEE') return form.transferAmount > 0 && !!form.depositReturnEvidenceId
  if (s === 'PARTIAL_RETURN_PARTIAL_TRANSFER') return form.returnedAmount > 0 && form.transferAmount > 0 && !!form.depositReturnEvidenceId
  return false
})

function onDepositStatusChange() {
  form.depositReturnDate = ''; form.depositReturnEvidenceId = null
  form.transferAmount = null; form.returnedAmount = null; depositEvidenceFiles.value = []
}

function formatAmount(v) {
  if (v == null || v === '') return '-'
  const n = Number(v); return Number.isFinite(n) ? n.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) : String(v)
}

function depositStatusLabel(s) {
  return ({ NOT_RETURNED: '未退回', FULLY_RETURNED: '全部退回', TRANSFERRED_TO_FEE: '转平台服务费', PARTIAL_RETURN_PARTIAL_TRANSFER: '部分退回，部分转平台服务费' })[s] || s || '-'
}

function beforeUpload(file) {
  if (!ALLOWED_MIMES.includes(file.type)) { ElMessage.error('不支持的文件类型'); return false }
  if (file.size > MAX_FILE_SIZE_MB * 1024 * 1024) { ElMessage.error('文件不能超过 10MB'); return false }
  return true
}

function handleEvidenceUploadSuccess(r) {
  if (r?.data?.id) { form.depositReturnEvidenceId = r.data.id; ElMessage.success('文件上传成功') }
  else ElMessage.warning('文件上传成功但未返回 ID')
}

function handleEvidenceRemove() { form.depositReturnEvidenceId = null; depositEvidenceFiles.value = [] }

async function loadPreview() {
  try {
    const r = await projectLifecycleApi.getClosurePreview(props.projectId)
    preview.value = r?.data || r
    const p = preview.value
    if (p?.depositReturnStatus && p.depositReturnStatus !== 'NA') {
      form.depositReturnStatus = p.depositReturnStatus; form.depositReturnDate = p.depositReturnDate || ''
      form.depositReturnEvidenceId = p.depositReturnEvidenceId || null
      form.transferAmount = p.transferAmount || null; form.returnedAmount = p.returnedAmount || null
    }
    if (p?.projectSummary) form.projectSummary = p.projectSummary
  } catch (e) { if (e?.response?.status !== 404) console.warn('loadPreview error:', e) }
}

async function submitClosure() {
  submitting.value = true
  try {
    await projectLifecycleApi.submitClosure(props.projectId, {
      depositReturnStatus: form.depositReturnStatus || undefined,
      depositReturnDate: form.depositReturnDate || undefined,
      depositReturnEvidenceId: form.depositReturnEvidenceId || undefined,
      transferAmount: form.transferAmount || undefined,
      returnedAmount: form.returnedAmount || undefined,
      projectSummary: form.projectSummary || undefined,
    })
    ElMessage.success('结项申请已提交，等待审核'); await loadPreview()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '提交失败') }
  finally { submitting.value = false }
}

async function doApprove() {
  approving.value = true
  try { await projectLifecycleApi.approveClosure(props.projectId); ElMessage.success('项目结项审核通过'); emit('closed'); await loadPreview() }
  catch (e) { ElMessage.error(e?.response?.data?.msg || '审核失败') }
  finally { approving.value = false }
}

async function doReject() {
  if (!rejectReason.value.trim()) return ElMessage.warning('请填写驳回原因')
  rejecting.value = true
  try {
    await projectLifecycleApi.rejectClosure(props.projectId, { comment: rejectReason.value })
    ElMessage.success('结项申请已驳回'); showRejectDialog.value = false; rejectReason.value = ''; await loadPreview()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '驳回失败') }
  finally { rejecting.value = false }
}

defineExpose({ canSubmit, form, preview })

const rebidLoading = ref(false)
async function handleRebid() {
  rebidLoading.value = true
  try {
    const res = await projectLifecycleApi.rebidProject(props.projectId)
    const newProjectId = res?.data?.projectId || res?.projectId
    if (newProjectId) { ElMessage.success('二次招标项目已创建，即将跳转'); router.push({ name: 'ProjectDetail', params: { id: String(newProjectId) } }) }
    else ElMessage.success('二次招标项目已创建')
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '创建二次招标项目失败') }
  finally { rebidLoading.value = false }
}

const precipitating = ref(false); const precipitateReady = ref(false); const precipitateTooltip = ref('')

onMounted(async () => {
  await loadPreview()
  if (props.projectId) checkPrecipitationReadiness()
})

async function checkPrecipitationReadiness() {
  try {
    const resp = await casesApi.checkPrecipitationReadiness(props.projectId)
    const data = resp?.data || resp
    precipitateReady.value = data.canPrecipitate
    // 蓝图 4.1.1.2.1 异常处理：把后端 missingItems 翻译为面向用户的中文场景化提示
    precipitateTooltip.value = readinessToTooltip(data.missingItems)
  } catch (e) {
    precipitateReady.value = false
    precipitateTooltip.value = '无法检查前置条件，请稍后重试'
  }
}

async function triggerPrecipitation() {
  precipitating.value = true
  try {
    const resp = await casesApi.precipitateCases(props.projectId)
    ElMessage.success(resp?.message || '案例沉淀任务已触发，完成后将通过消息通知')
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '触发案例沉淀失败') }
  finally { precipitating.value = false }
}
</script>

<style scoped>
.closure-stage { display: flex; flex-direction: column; gap: 16px; }

/* 区块容器 — 对齐 MVP form-section 样式 */
.form-section {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
}
.form-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #2E7659;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e0e0e0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 字段行 */
.field-row { display: flex; gap: 16px; margin-bottom: 16px; }
.field-item { display: flex; flex-direction: column; gap: 6px; flex: 1; }
.field-item.full-width { flex: 100%; }
.field-item label { font-size: 13px; color: #555; font-weight: 500; }
.required-mark { color: #e65100; margin-left: 2px; }

/* 禁用输入框 */
.fake-input {
  padding: 10px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 5px;
  font-size: 13px;
  background: #f5f5f5;
  color: #999;
  max-width: 200px;
}

/* 保证金信息 2列网格 */
.deposit-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  padding: 16px 20px;
  margin-bottom: 16px;
}
.deposit-info-item { display: flex; flex-direction: column; gap: 4px; }
.di-label { font-size: 12px; color: #888; }
.di-value { font-size: 14px; color: #333; font-weight: 500; }

/* 动态子字段 */
.dynamic-fields {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  padding: 16px;
  margin-top: 16px;
}

/* 只读摘要 */
.readonly-block { border-top: 1px solid #e8e8e8; padding-top: 16px; margin-top: 8px; }
.readonly-row { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.readonly-label { font-size: 13px; font-weight: 500; color: #555; min-width: 100px; }
.readonly-value { font-size: 13px; color: var(--el-text-color-primary); }

/* 操作按钮 */
.btn-container {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 8px 0;
  flex-wrap: wrap;
}

.section-actions { display: flex; gap: 8px; align-items: center; }
.summary-readonly { padding: 12px 0; min-height: 60px; line-height: 1.8; }
.text-gray { color: var(--el-text-color-secondary); }
.uploaded-hint { color: var(--el-color-success); font-size: 12px; margin-top: 4px; }

/* 富文本只读样式 — wangEditor HTML 在 v-html 中正确渲染 */
.rich-text-content { line-height: 1.8; color: #333; }
.rich-text-content :deep(h1) { font-size: 22px; font-weight: 600; margin: 16px 0 8px; }
.rich-text-content :deep(h2) { font-size: 18px; font-weight: 600; margin: 14px 0 6px; }
.rich-text-content :deep(h3) { font-size: 16px; font-weight: 600; margin: 12px 0 4px; }
.rich-text-content :deep(h4) { font-size: 14px; font-weight: 600; margin: 10px 0 4px; }
.rich-text-content :deep(p) { margin: 6px 0; }
.rich-text-content :deep(ul),
.rich-text-content :deep(ol) { padding-left: 24px; margin: 6px 0; }
.rich-text-content :deep(li) { margin: 3px 0; }
.rich-text-content :deep(blockquote) {
  border-left: 3px solid var(--el-color-primary);
  padding: 8px 16px;
  margin: 8px 0;
  background: #f8f9fa;
  color: #555;
  border-radius: 0 4px 4px 0;
}
.rich-text-content :deep(pre) {
  background: #f5f5f5;
  padding: 12px 16px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 8px 0;
}
.rich-text-content :deep(code) { font-family: 'SFMono-Regular', Consolas, monospace; font-size: 13px; }
.rich-text-content :deep(a) { color: var(--el-color-primary); text-decoration: underline; }
.rich-text-content :deep(table) { border-collapse: collapse; width: 100%; margin: 8px 0; }
.rich-text-content :deep(th),
.rich-text-content :deep(td) { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }
.rich-text-content :deep(th) { background: #f8f9fa; font-weight: 600; }
.rich-text-content :deep(img) { max-width: 100%; height: auto; border-radius: 4px; margin: 8px 0; }
</style>
