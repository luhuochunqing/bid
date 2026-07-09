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
      ref="basicInfoTabRef"
      :active-tab="activeTab"
      :form="form"
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
      @file-remove="handleFileRemove"
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
      :parsing-document="parsingDocument"
      :submitting-eval="submittingEval"
      @save="handleSave"
      @cancel="handleCancel"
      @cancel-edit="handleCancelEdit"
      @assign="openAssignDialog"
      @next-step="handleNextStep"
      @submit-eval="handleSubmitEvaluation"
    />

    <AssignDialog v-model="showAssignDialog" v-model:form="assignForm" :loading="assigning" @reset="assignForm.assignee = null" @submit="doAssign" />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tendersApi } from '@/api/modules/tenders.js'
import { batchTendersApi } from '@/api/modules/tenders/batch.js'
import { useUserStore } from '@/stores/user'
import { isBidManager } from '@/utils/permission'
import { buildManualTenderPayload } from './list/helpers.js'
import TenderEvaluationForm from './detail/TenderEvaluationForm.vue'
import AssignDialog from './list/components/AssignDialog.vue'
import TenderBasicInfoTab from './list/components/TenderBasicInfoTab.vue'
import TenderActionBar from './list/components/TenderActionBar.vue'
import { useTenderCreateForm } from './list/composables/useTenderCreateForm.js'
import { useTenderAiParse } from './list/composables/useTenderAiParse.js'

const ACCEPT_FILE_TYPES = '.pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const { form, rules, regions, customerTypes, projectTypes, priorities, canSave, populateForm, validateBeforeSave } = useTenderCreateForm()
const { parsingDocument, handleFileChange, handleFileRemove, handlePastedTextParse } = useTenderAiParse(form)

const editTenderId = computed(() => { const id = route.query.edit; return id ? Number(id) : null })
const isEditMode = computed(() => !!editTenderId.value)

const basicInfoTabRef = ref(null)
const activeTab = ref('basic')
const saving = ref(false)
const isReadOnly = ref(false)
const evaluation = ref({})
const createdTenderId = ref(null)
const hasUnsavedChanges = ref(false)

const tenderDetail = ref(null)
const tenderStatus = computed(() => tenderDetail.value?.status || null)
const isAdminOrLead = computed(() => isBidManager(userStore.userRole))
const currentUserId = computed(() => userStore.currentUser?.id)
const canProceedToNext = computed(() => tenderStatus.value === 'TRACKING' && tenderDetail.value?.projectManagerId === currentUserId.value)
const canFillEvaluation = computed(() => Boolean(createdTenderId.value))

const showAssignDialog = ref(false)
const assignForm = ref({ tenderTitle: '', assignee: null, priority: 'medium', remark: '' })
const assigning = ref(false)

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
  if (parsingDocument.value) {
    ElMessage.warning('标讯附件仍在上传或识别中，请稍后再保存')
    return
  }
  if (!(await validateBeforeSave(basicInfoTabRef))) return
  saving.value = true
  try {
    const payload = buildManualTenderPayload(form.value)
    const response = isEditMode.value
      ? await tendersApi.update(editTenderId.value, payload)
      : await tendersApi.create(payload)
    if (!response?.success) {
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
function handleCancel() { router.push('/bidding') }
function handleCancelEdit() { router.push(editTenderId.value ? `/bidding/${editTenderId.value}` : '/bidding') }

async function fetchTenderDetail() {
  if (!createdTenderId.value) return
  try { const res = await tendersApi.getDetail(createdTenderId.value); if (res?.success) tenderDetail.value = res.data } catch {}
}

async function openAssignDialog() {
  assignForm.value = { tenderTitle: tenderDetail.value?.title || '', assignee: null, priority: 'medium', remark: '' }
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
function handleNextStep() { activeTab.value = 'evaluation' }
</script>

<style scoped>
.bidding-create-page { padding: 24px; }
.create-header-card { background: var(--el-bg-color); border: 1px solid var(--el-border-color-extra-light); border-radius: 8px; padding: 20px 24px; margin-bottom: 16px; }
.create-title { font-size: 20px; font-weight: 600; margin: 0; color: var(--el-text-color-primary); }
.detail-tabs { margin-bottom: 16px; }
.tab-content { margin-bottom: 80px; }
</style>
