<template>
  <div class="bidding-create-page">
    <div class="create-header-card">
      <h2 class="create-title">{{ isEditMode ? '编辑标讯' : '新建标讯' }}</h2>
    </div>

    <el-tabs v-model="activeTab" class="detail-tabs" type="border-card">
      <el-tab-pane label="基本信息" name="basic" />
      <el-tab-pane label="项目评估表" name="evaluation" />
    </el-tabs>

    <TenderBasicInfoTab
      :active-tab="activeTab"
      :form="form"
      :form-ref="formRef"
      :rules="rules"
      :regions="regions"
      :customer-types="customerTypes"
      :project-types="projectTypes"
      :priorities="priorities"
      :saving="saving"
      :is-read-only="isReadOnly"
      :parsing-document="parsingDocument"
      :accept-file-types="ACCEPT_FILE_TYPES"
      @parse-paste="handlePastedTextParse"
      @file-change="handleFileChange"
    />

    <div v-show="activeTab === 'evaluation'" class="tab-content">
      <el-alert title="请在CRM商机中心编辑项目评估表后，关联到该标讯" type="info" :closable="false" show-icon style="margin-bottom: 16px" />
      <el-skeleton v-if="!canFillEvaluation" :rows="6" animated />
      <template v-else>
        <TenderEvaluationForm :evaluation="evaluation" :can-fill="false" :can-decide="false" :tender-id="null" @submit="handleSubmitEvaluation" @save-draft="handleEvaluationSaveDraft" @dirty-changed="onFormDirtyChanged" />
      </template>
    </div>

    <TenderActionBar
      :is-edit-mode="isEditMode"
      :created-tender-id="createdTenderId"
      :tender-status="tenderStatus"
      :is-admin-or-lead="isAdminOrLead"
      :can-proceed-to-next="canProceedToNext"
      :active-tab="activeTab"
      :can-save="canSave"
      :saving="saving"
      :submitting-eval="submittingEval"
      @save="handleSave"
      @cancel="handleCancel"
      @cancel-edit="handleCancelEdit"
      @assign="openAssignDialog"
      @next-step="handleNextStep"
      @submit-eval="handleSubmitEvaluation"
    />

    <AssignDialog v-model="showAssignDialog" v-model:form="assignForm" :candidates="assignCandidates" :loading="assigning" :loading-candidates="loadingCandidates" @reset="assignForm.assignee = null" @submit="doAssign" />
    <DuplicateWarningDialog v-model="showDuplicateDialog" :duplicates="duplicateList" :current-tender="currentTenderForDuplicate" @notify-admin="handleNotifyAdmin" />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tendersApi } from '@/api/modules/tenders.js'
import { batchTendersApi } from '@/api/modules/tenders/batch.js'
import { useUserStore } from '@/stores/user'
import { buildManualTenderPayload } from './list/helpers.js'
import TenderEvaluationForm from './detail/TenderEvaluationForm.vue'
import AssignDialog from './list/components/AssignDialog.vue'
import DuplicateWarningDialog from './list/components/DuplicateWarningDialog.vue'
import TenderBasicInfoTab from './list/components/TenderBasicInfoTab.vue'
import TenderActionBar from './list/components/TenderActionBar.vue'
import { useTenderCreateForm } from './list/composables/useTenderCreateForm.js'
import { useTenderAiParse } from './list/composables/useTenderAiParse.js'

const ACCEPT_FILE_TYPES = '.pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const { formRef, form, rules, regions, customerTypes, projectTypes, priorities, canSave, populateForm, validateBeforeSave } = useTenderCreateForm()
const { parsingDocument, handleFileChange, handlePastedTextParse } = useTenderAiParse(form)

const editTenderId = computed(() => { const id = route.query.edit; return id ? Number(id) : null })
const isEditMode = computed(() => !!editTenderId.value)

const activeTab = ref('basic')
const saving = ref(false)
const isReadOnly = ref(false)
const evaluation = ref({})
const createdTenderId = ref(null)
const hasUnsavedChanges = ref(false)

const showDuplicateDialog = ref(false)
const duplicateList = ref([])
const currentTenderForDuplicate = ref({})

const tenderDetail = ref(null)
const tenderStatus = computed(() => tenderDetail.value?.status || null)
const isAdminOrLead = computed(() => {
  const role = (userStore.userRole || '').toLowerCase().replace(/^role_/, '')
  return role === 'bid_admin' || role === 'bid_lead' || role === 'admin'
})
const currentUserId = computed(() => userStore.currentUser?.id)
const canProceedToNext = computed(() => tenderStatus.value === 'TRACKING' && tenderDetail.value?.projectManagerId === currentUserId.value)
const canFillEvaluation = computed(() => Boolean(createdTenderId.value))

const showAssignDialog = ref(false)
const assignForm = ref({ tenderTitle: '', assignee: null, priority: 'medium', remark: '' })
const assignCandidates = ref([])
const assigning = ref(false)
const loadingCandidates = ref(false)

const submittingEval = ref(false)

onMounted(async () => {
  if (editTenderId.value) {
    try {
      const res = await tendersApi.getDetail(editTenderId.value)
      if (res?.success && res.data) {
        populateForm(res.data)
        createdTenderId.value = editTenderId.value
      } else {
        ElMessage.error('加载标讯数据失败')
        router.replace('/bidding/create')
      }
    } catch {
      ElMessage.error('加载标讯数据失败')
      router.replace('/bidding/create')
    }
  }
})

async function handleSave() {
  if (!(await validateBeforeSave())) return
  saving.value = true
  try {
    const payload = buildManualTenderPayload(form.value)
    const response = isEditMode.value
      ? await tendersApi.update(editTenderId.value, payload)
      : await tendersApi.create(payload)
    if (!response?.success) {
      if (response?.code === 409 || response?.status === 409) {
        if (handleCreateConflict(response)) return
      }
      throw new Error(response?.msg || (isEditMode.value ? '标讯更新失败' : '标讯入库失败'))
    }
    if (!isEditMode.value) createdTenderId.value = response.data?.id
    isReadOnly.value = true
    ElMessage.success(isEditMode.value ? '标讯已更新' : '标讯已成功入库')
    hasUnsavedChanges.value = false
    if (!isEditMode.value) await fetchTenderDetail()
    router.push(`/bidding/${isEditMode.value ? editTenderId.value : createdTenderId.value}`)
  } catch (error) {
    if (!error?.isAxiosError && !error?.response) {
      ElMessage.error(error.message || (isEditMode.value ? '标讯更新失败' : '标讯入库失败'))
    }
  } finally { saving.value = false }
}

function handleCreateConflict(response) {
  const dup = response?.data
  if (Array.isArray(dup) && dup.length > 0) {
    duplicateList.value = dup
    currentTenderForDuplicate.value = { title: form.value.title, purchaserName: form.value.purchaser, registrationDeadline: form.value.deadline, bidOpeningTime: form.value.bidOpeningTime }
    showDuplicateDialog.value = true
    return true
  }
  ElMessage.error(response?.msg || '标讯入库失败')
  return false
}

async function handleNotifyAdmin() {
  showDuplicateDialog.value = false
  ElMessage.success('已通知管理员复核')
  router.push('/bidding')
}

function handleNextStep() { activeTab.value = 'evaluation' }
function handleCancel() { router.push('/bidding') }
function handleCancelEdit() { router.push(editTenderId.value ? `/bidding/${editTenderId.value}` : '/bidding') }

async function fetchTenderDetail() {
  if (!createdTenderId.value) return
  try { const res = await tendersApi.getDetail(createdTenderId.value); if (res?.success) tenderDetail.value = res.data } catch {}
}

async function openAssignDialog() {
  assignForm.value = { tenderTitle: tenderDetail.value?.title || '', assignee: null, priority: 'medium', remark: '' }
  loadingCandidates.value = true
  try { const res = await batchTendersApi.getAssignmentCandidates(); assignCandidates.value = res?.data || [] }
  catch { ElMessage.error('获取候选人列表失败') }
  finally { loadingCandidates.value = false }
  showAssignDialog.value = true
}

async function doAssign(payload) {
  const assignee = payload?.assignee ?? assignForm.value.assignee
  if (!assignee) { ElMessage.warning('请选择项目负责人'); return }
  assigning.value = true
  try {
    const res = await batchTendersApi.batchAssign([createdTenderId.value], assignee, payload?.remark ?? assignForm.value.remark)
    if (res?.success) { ElMessage.success('分配成功'); showAssignDialog.value = false; await fetchTenderDetail() }
    else throw new Error(res?.message || '分配失败')
  } catch (error) { ElMessage.error(error?.message || '分配失败') }
  finally { assigning.value = false }
}

async function handleSubmitEvaluation() {
  if (!createdTenderId.value) return
  submittingEval.value = true
  try { await tendersApi.submitEvaluationFinal(createdTenderId.value, evaluation.value); ElMessage.success('评估表已提交'); router.push(`/bidding/${createdTenderId.value}`) }
  catch { ElMessage.error('评估表提交失败') }
  finally { submittingEval.value = false }
}

async function handleEvaluationSaveDraft() {
  if (!createdTenderId.value) return
  try { await tendersApi.saveEvaluationDraft(createdTenderId.value, evaluation.value); ElMessage.success('评估表草稿已保存') }
  catch { ElMessage.error('评估表草稿保存失败') }
}

function onFormDirtyChanged(dirty) { hasUnsavedChanges.value = dirty }
</script>

<style scoped>
.bidding-create-page { padding: 24px; }
.create-header-card { background: var(--el-bg-color); border: 1px solid var(--el-border-color-extra-light); border-radius: 8px; padding: 20px 24px; margin-bottom: 16px; }
.create-title { font-size: 20px; font-weight: 600; margin: 0; color: var(--el-text-color-primary); }
.detail-tabs { margin-bottom: 16px; }
.tab-content { margin-bottom: 80px; }
</style>
