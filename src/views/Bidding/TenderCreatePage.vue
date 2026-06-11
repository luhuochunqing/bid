<template>
  <div class="bidding-create-page">
    <div class="create-header-card">
      <h2 class="create-title">{{ isEditMode ? '编辑标讯' : '新建标讯' }}</h2>
    </div>

    <!-- Tabs -->
    <el-tabs v-model="activeTab" class="detail-tabs" type="border-card">
      <el-tab-pane label="基本信息" name="basic" />
      <el-tab-pane label="项目评估表" name="evaluation" />
    </el-tabs>

    <!-- Tab content -->
    <div v-show="activeTab === 'basic'" class="tab-content">
      <el-card shadow="never">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="110px"
          :disabled="saving || isReadOnly"
        >
          <el-row :gutter="16">
            <el-col :span="24">
              <el-form-item label="项目名称" prop="title">
                <el-input v-model="form.title" placeholder="请输入项目名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="总部所在地" prop="region">
                <el-select v-model="form.region" placeholder="选择总部所在地" class="full-width">
                  <el-option v-for="r in regions" :key="r" :label="r" :value="r" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="招标主体" prop="purchaser">
                <el-input v-model="form.purchaser" placeholder="请输入招标主体" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="报名截止时间" prop="deadline">
                <el-date-picker
                  v-model="form.deadline"
                  type="datetime"
                  format="YYYY-MM-DD HH:mm"
                  value-format="YYYY-MM-DD HH:mm"
                  placeholder="选择报名截止时间"
                  class="full-width"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="开标时间" prop="bidOpeningTime">
                <el-date-picker
                  v-model="form.bidOpeningTime"
                  type="datetime"
                  format="YYYY-MM-DD HH:mm"
                  value-format="YYYY-MM-DD HH:mm"
                  placeholder="选择开标时间"
                  class="full-width"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="客户类型" prop="customerType">
                <el-select v-model="form.customerType" placeholder="选择客户类型" class="full-width">
                  <el-option v-for="t in customerTypes" :key="t" :label="t" :value="t" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="优先级" prop="priority">
                <el-select v-model="form.priority" placeholder="选择优先级" class="full-width">
                  <el-option
                    v-for="item in priorities"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  >
                    <div class="priority-option">
                      <span>{{ item.label }} · {{ item.desc }}</span>
                      <small>{{ item.standard }}</small>
                    </div>
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="项目类型" prop="projectType">
                <el-select v-model="form.projectType" placeholder="选择项目类型（选填）" clearable class="full-width">
                  <el-option v-for="t in projectTypes" :key="t" :label="t" :value="t" />
                </el-select>
              </el-form-item>
            </el-col>
            <!-- 联系人1 -->
            <el-col :span="24">
              <div class="contact-group-title">联系人1</div>
            </el-col>
            <el-col :span="4">
              <el-form-item label="姓名" prop="contact" label-width="56px">
                <el-input v-model="form.contact" placeholder="联系人姓名" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="手机号" prop="phone" label-width="64px">
                <el-input v-model="form.phone" placeholder="手机号" />
              </el-form-item>
            </el-col>
            <el-col :span="7">
              <el-form-item label="座机" prop="landline" label-width="56px">
                <el-input v-model="form.landline" placeholder="座机（如 010-12345678）" />
              </el-form-item>
            </el-col>
            <el-col :span="7">
              <el-form-item label="邮箱" prop="mail" label-width="56px">
                <el-input v-model="form.mail" placeholder="邮箱" />
              </el-form-item>
            </el-col>
            <!-- 联系人2 -->
            <el-col :span="24">
              <div class="contact-group-title">联系人2 <span class="optional-tag">选填</span></div>
            </el-col>
            <el-col :span="4">
              <el-form-item label="姓名" label-width="56px">
                <el-input v-model="form.contact2" placeholder="联系人姓名" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="手机号" prop="phone2" label-width="64px">
                <el-input v-model="form.phone2" placeholder="手机号" />
              </el-form-item>
            </el-col>
            <el-col :span="7">
              <el-form-item label="座机" prop="landline2" label-width="56px">
                <el-input v-model="form.landline2" placeholder="座机" />
              </el-form-item>
            </el-col>
            <el-col :span="7">
              <el-form-item label="邮箱" prop="mail2" label-width="56px">
                <el-input v-model="form.mail2" placeholder="邮箱" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="标讯描述">
                <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入标讯描述" maxlength="5000" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="标讯信息">
                <el-input v-model="form.tenderInfo" type="textarea" :rows="3" placeholder="请输入标讯信息（选填）" maxlength="5000" show-word-limit />
              </el-form-item>
            </el-col>
            <!-- AI 解析区域 -->
            <el-col :span="24">
              <el-form-item label="粘贴识别">
                <div class="paste-hint">[粘贴识别] 或文字输入，系统将智能拆分回填标讯信息</div>
                <el-input
                  v-model="form.pastedText"
                  type="textarea"
                  :rows="4"
                  maxlength="500000"
                  show-word-limit
                  placeholder="直接粘贴招标公告正文，系统将自动识别并回填字段"
                  :disabled="parsingDocument"
                />
                <div class="paste-actions">
                  <el-button
                    type="primary"
                    :icon="DocumentCopy"
                    :loading="parsingDocument"
                    @click="handlePastedTextParse"
                  >
                    识别粘贴文字
                  </el-button>
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="附件上传">
                <div class="upload-hint">支持 PDF/Word 文件上传（≤50MB），上传后自动 AI 解析并回填表单字段</div>
                <el-upload
                  class="manual-tender-upload"
                  :auto-upload="false"
                  :on-change="handleFileChange"
                  :file-list="form.attachments"
                  :limit="5"
                  :accept="acceptFileTypes"
                  multiple
                  drag
                >
                  <el-icon class="el-icon--upload"><Upload /></el-icon>
                  <div class="el-upload__text">
                    {{ parsingDocument ? 'DeepSeek/AI 解析中...' : '将文件拖到此处，或点击选择附件（PDF/Word ≤50MB）' }}
                  </div>
                </el-upload>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>
    </div>

    <div v-show="activeTab === 'evaluation'" class="tab-content">
      <el-alert
        title="请在CRM商机中心编辑项目评估表后，关联到该标讯"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-skeleton v-if="!canFillEvaluation" :rows="6" animated />
      <template v-else>
        <TenderEvaluationForm
          :evaluation="evaluation"
          :can-fill="false"
          :can-decide="false"
          :tender-id="null"
          @submit="handleEvaluationSubmit"
          @save-draft="handleEvaluationSaveDraft"
          @dirty-changed="onFormDirtyChanged"
        />
      </template>
    </div>

    <!-- 底部操作栏 — 状态机驱动按钮 -->
    <div class="form-action-bar">
      <div class="form-action-bar-inner">
        <div class="action-bar-right">
          <!-- 编辑模式：取消(返回详情) + 保存 -->
          <template v-if="isEditMode && createdTenderId">
            <el-button size="large" @click="handleCancelEdit">取消</el-button>
            <el-button type="primary" size="large" :loading="saving" :disabled="!canSave" @click="handleSave">保存</el-button>
          </template>
          <!-- 未保存：取消 + 保存 -->
          <template v-else-if="!createdTenderId">
            <el-button size="large" @click="handleCancel">取消</el-button>
            <el-button type="primary" size="large" :loading="saving" :disabled="!canSave" @click="handleSave">保存</el-button>
          </template>
          <!-- 待分配 + 管理员/组长：返回 + 分配 -->
          <template v-else-if="tenderStatus === 'PENDING_ASSIGNMENT' && isAdminOrLead">
            <el-button size="large" @click="handleCancel">返回列表</el-button>
            <el-button type="primary" size="large" @click="openAssignDialog">分配</el-button>
          </template>
          <!-- 跟踪中（已分配）+ 授权角色：下一步 / 提交 -->
          <template v-else-if="tenderStatus === 'TRACKING' && canProceedToNext">
            <template v-if="activeTab === 'basic'">
              <el-button size="large" @click="handleCancel">返回列表</el-button>
              <el-button type="success" size="large" @click="handleNextStep">下一步</el-button>
            </template>
            <template v-else-if="activeTab === 'evaluation'">
              <el-button size="large" @click="handleCancel">返回列表</el-button>
              <el-button type="primary" size="large" :loading="submittingEval" @click="handleSubmitEvaluation">提交</el-button>
            </template>
          </template>
          <!-- 无操作权限时：返回列表 -->
          <template v-else>
            <el-button size="large" @click="handleCancel">返回列表</el-button>
          </template>
        </div>
      </div>
    </div>

    <!-- 分配对话框 -->
    <AssignDialog
      v-model="showAssignDialog"
      v-model:form="assignForm"
      :candidates="assignCandidates"
      :loading="assigning"
      :loading-candidates="loadingCandidates"
      @reset="assignForm.assignee = null"
      @submit="doAssign"
    />

    <!-- 去重警告弹窗 -->
    <DuplicateWarningDialog
      v-model="showDuplicateDialog"
      :duplicates="duplicateList"
      :current-tender="currentTenderForDuplicate"
      @notify-admin="handleNotifyAdmin"
    />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DocumentCopy, Upload } from '@element-plus/icons-vue'
import { tendersApi } from '@/api/modules/tenders.js'
import { batchTendersApi } from '@/api/modules/tenders/batch.js'
import { useUserStore } from '@/stores/user'
import { buildManualTenderPayload } from './list/helpers.js'
import {
  REGION_OPTIONS,
  CUSTOMER_TYPE_OPTIONS,
  PROJECT_TYPE_OPTIONS,
  PRIORITY_OPTIONS,
  MANUAL_FORM_RULES,
} from './list/constants.js'
import TenderEvaluationForm from './detail/TenderEvaluationForm.vue'
import AssignDialog from './list/components/AssignDialog.vue'
import DuplicateWarningDialog from './list/components/DuplicateWarningDialog.vue'

const ACCEPT_FILE_TYPES = '.pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document'
const PASTED_TEXT_MAX_LENGTH = 500000
const MAX_FILE_SIZE = 50 * 1024 * 1024

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// --- edit mode ---
const editTenderId = computed(() => {
  const id = route.query.edit
  return id ? Number(id) : null
})
const isEditMode = computed(() => !!editTenderId.value)

function mapTenderToForm(tender) {
  return {
    title: tender.title || '',
    purchaser: tender.purchaserName || '',
    region: tender.region || '',
    deadline: tender.registrationDeadline || tender.deadline || null,
    bidOpeningTime: tender.bidOpeningTime || null,
    customerType: tender.customerType || '',
    priority: tender.priority || '',
    projectType: tender.projectType || '',
    sourcePlatform: tender.source || '人工录入',
    contact: tender.contactName || '',
    phone: tender.contactPhone || '',
    landline: tender.contactTel || '',
    mail: tender.contactMail || '',
    contact2: tender.contactName2 || '',
    phone2: tender.contactPhone2 || '',
    landline2: tender.contactTel2 || '',
    mail2: tender.contactMail2 || '',
    description: tender.description || '',
    tenderInfo: tender.tenderInfo || '',
    attachments: [],
    sourceDocumentName: tender.sourceDocumentName || '',
    sourceDocumentFileType: tender.sourceDocumentFileType || '',
    sourceDocumentFileUrl: tender.sourceDocumentFileUrl || '',
    pastedText: '',
  }
}

// --- form state ---
const formRef = ref(null)
const form = ref(createForm())
const saving = ref(false)
const parsingDocument = ref(false)
const activeTab = ref('basic')
const evaluation = ref({})
const createdTenderId = ref(null)
const hasUnsavedChanges = ref(false)
const showDuplicateDialog = ref(false)
const duplicateList = ref([])
const currentTenderForDuplicate = ref({})

async function loadTenderForEdit(id) {
  try {
    const res = await tendersApi.getDetail(id)
    if (res?.success && res.data) {
      form.value = mapTenderToForm(res.data)
      createdTenderId.value = id
    } else {
      ElMessage.error('加载标讯数据失败')
      router.replace('/bidding/create')
    }
  } catch (e) {
    ElMessage.error('加载标讯数据失败')
    router.replace('/bidding/create')
  }
}

if (editTenderId.value) {
  loadTenderForEdit(editTenderId.value)
}

// --- role helpers ---
const isAdminOrLead = computed(() => {
  const role = (userStore.userRole || '').toLowerCase().replace(/^role_/, '')
  return role === 'bid_admin' || role === 'bid_lead' || role === 'admin'
})
const currentUserId = computed(() => userStore.currentUser?.id)

// --- tender detail (fetched after save) ---
const tenderDetail = ref(null)
const tenderStatus = computed(() => tenderDetail.value?.status || null)
const projectLeaderId = computed(() => tenderDetail.value?.projectManagerId || null)
const canProceedToNext = computed(() => {
  if (tenderStatus.value !== 'TRACKING') return false
  if (projectLeaderId.value && currentUserId.value === projectLeaderId.value) return true
  return false
})

// --- assignment state ---
const showAssignDialog = ref(false)
const assignForm = ref({ tenderTitle: '', assignee: null, priority: 'medium', remark: '' })
const assignCandidates = ref([])
const assigning = ref(false)
const loadingCandidates = ref(false)

// --- evaluation submit state ---
const submittingEval = ref(false)
const isReadOnly = ref(false)

// --- constants ---
const regions = REGION_OPTIONS
const customerTypes = CUSTOMER_TYPE_OPTIONS
const projectTypes = PROJECT_TYPE_OPTIONS
const priorities = PRIORITY_OPTIONS
const rules = MANUAL_FORM_RULES
const acceptFileTypes = ACCEPT_FILE_TYPES

function createForm() {
  return {
    title: '',
    purchaser: '',             // 招标主体（必填）
    region: '',
    deadline: null,
    bidOpeningTime: null,
    customerType: '',
    priority: '',
    projectType: '',           // 项目类型（选填）
    sourcePlatform: '人工录入', // 来源平台（后端控制，人工录入默认此值）
    // 联系人1
    contact: '',
    phone: '',
    landline: '',
    mail: '',
    // 联系人2
    contact2: '',
    phone2: '',
    landline2: '',
    mail2: '',
    // 其他
    description: '',           // 标讯描述
    tenderInfo: '',            // 标讯信息
    attachments: [],
    sourceDocumentName: '',
    sourceDocumentFileType: '',
    sourceDocumentFileUrl: '',
    pastedText: '',
  }
}

const canSave = computed(() => {
  return (
    form.value.title?.trim() &&
    form.value.purchaser?.trim() &&
    form.value.region?.trim() &&
    form.value.deadline &&
    form.value.bidOpeningTime &&
    form.value.customerType?.trim() &&
    form.value.priority?.trim()
  )
})

const canFillEvaluation = computed(() => Boolean(createdTenderId.value))

// Guard: pastedText maxlength
watch(
  () => form.value.pastedText,
  (val) => {
    if (val && val.length > PASTED_TEXT_MAX_LENGTH) {
      form.value.pastedText = val.substring(0, PASTED_TEXT_MAX_LENGTH)
    }
  },
)

function resolveUploadFile(file) {
  if (file instanceof File || file instanceof Blob) return file
  if (file?.raw instanceof File || file?.raw instanceof Blob) return file.raw
  return null
}

function isSupportedParseFile(file) {
  const name = String(file?.name || '').toLowerCase()
  return name.endsWith('.pdf') || name.endsWith('.doc') || name.endsWith('.docx')
}

async function handleFileChange(file, fileList) {
  form.value.attachments = fileList
  const uploadFile = resolveUploadFile(file)

  if (!uploadFile) return
  if (uploadFile.size > MAX_FILE_SIZE) {
    ElMessage.warning(`文件 "${uploadFile.name}" 超过 50MB 限制`)
    form.value.attachments = fileList.filter(f => {
      const fFile = resolveUploadFile(f)
      return fFile && fFile.size <= MAX_FILE_SIZE
    })
    return
  }
  if (!isSupportedParseFile(uploadFile)) {
    ElMessage.warning(`文件 "${uploadFile.name}" 格式不支持，仅支持 PDF/Word 文件`)
    form.value.attachments = fileList.filter(f => isSupportedParseFile(resolveUploadFile(f)))
    return
  }

  await runAiParse(async () => {
    const response = await tendersApi.parseTenderIntakeDocument(uploadFile, {
      entityId: 'create-tender',
    })
    if (!response?.success) throw new Error(response?.msg || '文档自动识别失败')
    applyParsedFields(response.data)
    applySourceDocumentMetadata(uploadFile, response.data)
    return 'DeepSeek/AI 已识别附件内容，可继续编辑后保存'
  })
}

async function handlePastedTextParse() {
  const text = form.value.pastedText?.trim()
  if (!text) {
    ElMessage.warning('请先粘贴标讯正文')
    return
  }

  await runAiParse(async () => {
    const response = await tendersApi.parseTenderIntakeText(text, {
      entityId: 'create-tender',
    })
    if (!response?.success) throw new Error(response?.msg || '粘贴文本识别失败')
    applyParsedFields(response.data)
    return 'DeepSeek/AI 已识别粘贴文本，可继续编辑后保存'
  })
}

/**
 * 统一处理 AI 解析的加载状态、成功提示和错误提示。
 * 类似 CRM 部分的 load/search 拆分，此处提取公共解析流程，减少重复代码。
 */
async function runAiParse(parseFn) {
  parsingDocument.value = true
  try {
    const successMessage = await parseFn()
    if (successMessage) {
      ElMessage.success(successMessage)
    }
  } catch (error) {
    const timedOut = error?.code === 'ECONNABORTED'
    ElMessage.warning(timedOut ? 'AI 解析超时，可继续手动填写' : '自动识别失败，可继续手动填写')
  } finally {
    parsingDocument.value = false
  }
}

function applyParsedFields(data) {
  if (!data) return

  const extracted = data?.extractedData && typeof data.extractedData === 'object' ? data.extractedData : null

  const mappings = [
    // 1) 已经是前端扁平结构（未来后端可能直接给到）
    {
      title: 'title',
      region: 'region',
      tenderAgency: 'purchaser',
      deadline: 'deadline',
      bidOpeningTime: 'bidOpeningTime',
      customerType: 'customerType',
      priority: 'priority',
      contact: 'contact',
      phone: 'phone',
      landline: 'landline',
      mail: 'mail',
      description: 'description',
      tenderInfo: 'tenderInfo',
      projectType: 'projectType',
    },
    // 2) 当前后端 doc-insight extractedData 结构
    {
      tenderTitle: 'title',
      projectName: 'title',
      tenderAgency: 'purchaser',
      deadline: 'deadline',
      bidOpeningTime: 'bidOpeningTime',
      region: 'region',
      customerType: 'customerType',
      priority: 'priority',
      contactName: 'contact',
      contactPhone: 'phone',
      contactEmail: 'mail',
      tenderScope: 'description',
    },
  ]

  const sources = [data, extracted].filter(Boolean)

  for (const src of sources) {
    for (const mapping of mappings) {
      for (const [from, to] of Object.entries(mapping)) {
        const value = src[from]
        if (value === undefined || value === null || value === '') continue
        if (form.value[to] === undefined || form.value[to] === null || form.value[to] === '') {
          form.value[to] = value
        }
      }
    }
  }
}

function applySourceDocumentMetadata(file, parsedResult) {
  const fileUrl = parsedResult?.documentId || parsedResult?.document?.fileUrl || ''
  if (!fileUrl) return
  form.value.sourceDocumentName = file?.name || parsedResult?.documentName || '招标文件'
  form.value.sourceDocumentFileType = file?.type || parsedResult?.contentType || ''
  form.value.sourceDocumentFileUrl = fileUrl
}

/**
 * 保存前的基础表单 + 业务规则校验。
 * 提取出来让 handleSave 保持线性流程，职责更清晰。
 */
async function validateBeforeSave() {
  try {
    await formRef.value?.validate()
  } catch {
    ElMessage.warning('请填写必填项后再保存')
    return false
  }

  if (!validateContacts()) return false

  // 日期业务规则校验
  if (form.value.deadline) {
    const now = new Date()
    const deadline = new Date(form.value.deadline)
    if (deadline <= now) {
      ElMessage.warning('报名截止时间必须晚于当前时间')
      return false
    }
  }

  if (form.value.deadline && form.value.bidOpeningTime) {
    const deadline = new Date(form.value.deadline)
    const bidOpening = new Date(form.value.bidOpeningTime)
    if (bidOpening <= deadline) {
      ElMessage.warning('开标时间必须晚于报名截止时间')
      return false
    }
  }

  return true
}

/**
 * 统一处理创建标讯时的重复冲突（409）。
 * 避免在 handleSave 里用魔法属性（_isConflict / _response）污染 Error 对象。
 */
function handleCreateConflict(response) {
  const dup = response?.data
  if (Array.isArray(dup) && dup.length > 0) {
    duplicateList.value = dup
    currentTenderForDuplicate.value = buildCurrentTenderForDuplicate()
    showDuplicateDialog.value = true
    return true
  }
  ElMessage.error(response?.msg || '标讯入库失败')
  return false
}

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

    if (!isEditMode.value) {
      createdTenderId.value = response.data?.id
    }
    isReadOnly.value = true
    ElMessage.success(isEditMode.value ? '标讯已更新' : '标讯已成功入库')
    hasUnsavedChanges.value = false
    if (!isEditMode.value) {
      await fetchTenderDetail()
    }
    router.push(`/bidding/${isEditMode.value ? editTenderId.value : createdTenderId.value}`)
  } catch (error) {
    if (!error?.isAxiosError && !error?.response) {
      ElMessage.error(error.message || (isEditMode.value ? '标讯更新失败' : '标讯入库失败'))
    }
  } finally {
    saving.value = false
  }
}

function buildCurrentTenderForDuplicate() {
  return {
    title: form.value.title,
    purchaserName: form.value.purchaser,
    registrationDeadline: form.value.deadline,
    bidOpeningTime: form.value.bidOpeningTime,
  }
}

async function handleNotifyAdmin() {
  showDuplicateDialog.value = false
  // TODO: 未来接入真实「通知管理员复核」后端接口（当前为占位成功提示）
  ElMessage.success('已通知管理员复核')
  router.push('/bidding')
}

function handleNextStep() {
  activeTab.value = 'evaluation'
}

function handleCancel() {
  router.push('/bidding')
}

function handleCancelEdit() {
  if (editTenderId.value) {
    router.push(`/bidding/${editTenderId.value}`)
  } else {
    router.push('/bidding')
  }
}

// --- 拉取标讯详情 ---
async function fetchTenderDetail() {
  if (!createdTenderId.value) return
  try {
    const res = await tendersApi.getDetail(createdTenderId.value)
    if (res?.success && res.data) {
      tenderDetail.value = res.data
    }
  } catch {
    // 静默失败，不影响主流程
  }
}

// --- 分配 ---
async function openAssignDialog() {
  assignForm.value = { tenderTitle: tenderDetail.value?.title || '', assignee: null, priority: 'medium', remark: '' }
  loadingCandidates.value = true
  try {
    const res = await batchTendersApi.getAssignmentCandidates()
    assignCandidates.value = res?.data || []
  } catch {
    ElMessage.error('获取候选人列表失败')
  } finally {
    loadingCandidates.value = false
  }
  showAssignDialog.value = true
}

async function doAssign(payload) {
  const assignee = payload?.assignee ?? assignForm.value.assignee
  const remark = payload?.remark ?? assignForm.value.remark
  if (!assignee) {
    ElMessage.warning('请选择项目负责人')
    return
  }
  assigning.value = true
  try {
    const res = await batchTendersApi.batchAssign(
      [createdTenderId.value], assignee, remark)
    if (res?.success) {
      ElMessage.success('分配成功')
      showAssignDialog.value = false
      await fetchTenderDetail()
    } else {
      throw new Error(res?.message || '分配失败')
    }
  } catch (error) {
    ElMessage.error(error?.message || '分配失败')
  } finally {
    assigning.value = false
  }
}

// --- 提交评估表 ---
async function handleSubmitEvaluation() {
  if (!createdTenderId.value) return
  submittingEval.value = true
  try {
    await tendersApi.submitEvaluationFinal(createdTenderId.value, evaluation.value)
    ElMessage.success('评估表已提交')
    router.push(`/bidding/${createdTenderId.value}`)
  } catch {
    ElMessage.error('评估表提交失败')
  } finally {
    submittingEval.value = false
  }
}

function onFormDirtyChanged(dirty) {
  hasUnsavedChanges.value = dirty
}

/**
 * 联系人交叉校验：
 * - 若填写了联系人姓名，则至少填写一种联系方式（手机/座机/邮箱之一）
 */
function validateContacts() {
  const f = form.value
  // 联系人1
  if (f.contact?.trim()) {
    const hasContact1 = f.phone?.trim() || f.landline?.trim() || f.mail?.trim()
    if (!hasContact1) {
      ElMessage.warning('请填写联系人1的至少一种联系方式（手机/座机/邮箱）')
      return false
    }
  }
  // 联系人2
  if (f.contact2?.trim()) {
    const hasContact2 = f.phone2?.trim() || f.landline2?.trim() || f.mail2?.trim()
    if (!hasContact2) {
      ElMessage.warning('请填写联系人2的至少一种联系方式（手机/座机/邮箱）')
      return false
    }
  }
  // 两个联系人都没填 -> 校验失败
  if (!f.contact?.trim() && !f.contact2?.trim()) {
    ElMessage.warning('请至少填写一个联系人的联系方式')
    return false
  }
  return true
}

async function handleEvaluationSubmit() {
  await handleSubmitEvaluation()
}

async function handleEvaluationSaveDraft() {
  if (!createdTenderId.value) return
  try {
    await tendersApi.saveEvaluationDraft(createdTenderId.value, evaluation.value)
    ElMessage.success('评估表草稿已保存')
  } catch {
    ElMessage.error('评估表草稿保存失败')
  }
}
</script>

<style scoped>
.bidding-create-page {
  padding: 24px;
}

.create-header-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 8px;
  padding: 20px 24px;
  margin-bottom: 16px;
}

.create-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: var(--el-text-color-primary);
}

.detail-tabs {
  margin-bottom: 16px;
}

.tab-content {
  margin-bottom: 80px;
}

.full-width {
  width: 100%;
}

.field-tip {
  margin-top: 4px;
  color: var(--gray-650, var(--el-text-color-secondary));
  font-size: 12px;
  line-height: 1.4;
}

.priority-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.25;
}

.priority-option small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.paste-hint,
.upload-hint {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.4;
}

.paste-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.manual-tender-upload {
  width: 100%;
}

.manual-tender-upload :deep(.el-upload) {
  display: block;
  width: 100%;
}

.manual-tender-upload :deep(.el-upload-dragger) {
  width: 100%;
  box-sizing: border-box;
}

.manual-tender-upload :deep(.el-upload-list) {
  width: 100%;
}

.form-action-bar {
  position: sticky;
  bottom: 0;
  z-index: 10;
  margin-top: 24px;
  padding: 20px 24px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  border: 1px solid var(--gray-150);
  border-radius: var(--radius-md);
}

.form-action-bar-inner {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}
</style>
