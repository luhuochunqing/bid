import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CUSTOMER_INFO_COLUMNS, getCustomerInfoRoleLabel } from './components/customerInfoMatrixConfig.js'

// ---- Default factories ----
function makeEmptyBasic() {
  return {
    plannedShortlistedCount: null,
    mroOfficeFlowAmount: null,
    customerRevenue: null,
    unfavorableItems: '',
    riskAssessment: '',
    contingencyPlan: '',
    processKnowledge: '',
    supportNotes: '',
    projectPlanGap: '',
    projectPlanGapFiles: [],
  }
}
function makeEmptyRecommendation() { return { shouldBid: null, reason: '' } }
function makeEmptyForm() { return { basic: makeEmptyBasic(), customerInfo: [], recommendation: makeEmptyRecommendation() } }

// ---- Mapping helpers ----
/** Map column key → backend valueType */
export const VT = {
  NAME: 'TEXT', CONTACT_INFO: 'TEXT', POSITION: 'ENUM14', XIYU_CONTACT: 'TEXT', CONTACT_METHOD: 'ENUM7',
  INFO_TENDENCY_BASIS: 'TEXT', INFO_CLEAR_WINNER_BID: 'SWITCH', INFO_WIN_RATE_IMPACT: 'DROPDOWN6',
  CONTACTED: 'DROPDOWN', GUIDED_BID: 'DROPDOWN',
  CAN_GET_KEY_INFO: 'DROPDOWN', CAN_REMOVE_ADVERSE: 'DROPDOWN',
  CAN_SYNC_EVAL: 'DROPDOWN', TENDENCY: 'DROPDOWN',
}

const BOOLEAN_INFO_KEYS = new Set([
  'INFO_CLEAR_WINNER_BID',
  'CONTACTED',
  'GUIDED_BID',
  'CAN_GET_KEY_INFO',
  'CAN_REMOVE_ADVERSE',
  'CAN_SYNC_EVAL',
])

function toBooleanFormValue(value) {
  if (value === true || value === false) return value
  if (value == null) return value
  const text = String(value).trim()
  if (text === 'true' || text === '是') return true
  if (text === 'false' || text === '否') return false
  return value
}

function toBackendCellValue(infoKey, value) {
  if (!BOOLEAN_INFO_KEYS.has(infoKey)) return String(value)
  const normalized = toBooleanFormValue(value)
  if (normalized === true || normalized === false) return String(normalized)
  return String(value)
}

/**
 * Map a backend evaluation payload into the 3-section form shape.
 */
export function evaluationToForm(evaluation) {
  if (!evaluation) return makeEmptyForm()
  const blank = makeEmptyForm()
  const basicDTO = evaluation.evaluationBasic || evaluation.basic || {}
  const customers = evaluation.evaluationCustomerInfos || evaluation.customerInfo || []
  const recDTO = evaluation.evaluationRecommendation || evaluation.recommendation || {}
  return {
    basic: {
      plannedShortlistedCount: basicDTO.plannedShortlistedCount ?? blank.basic.plannedShortlistedCount,
      mroOfficeFlowAmount: basicDTO.mroOfficeFlowAmount ?? blank.basic.mroOfficeFlowAmount,
      customerRevenue: basicDTO.customerRevenue ?? blank.basic.customerRevenue,
      unfavorableItems: basicDTO.unfavorableItems ?? blank.basic.unfavorableItems,
      riskAssessment: basicDTO.riskAssessment ?? blank.basic.riskAssessment,
      contingencyPlan: basicDTO.contingencyPlan ?? blank.basic.contingencyPlan,
      processKnowledge: basicDTO.processKnowledge ?? blank.basic.processKnowledge,
      supportNotes: basicDTO.supportNotes ?? blank.basic.supportNotes,
      projectPlanGap: basicDTO.projectPlanGap ?? blank.basic.projectPlanGap,
      projectPlanGapFiles: Array.isArray(basicDTO.projectPlanGapFiles) ? basicDTO.projectPlanGapFiles : blank.basic.projectPlanGapFiles,
    },
    customerInfo: eavToFlat(customers),
    recommendation: {
      shouldBid: recDTO.shouldBid != null ? recDTO.shouldBid : (evaluation.bidRecommendation != null ? (evaluation.bidRecommendation === 'RECOMMEND') : blank.recommendation.shouldBid),
      reason: recDTO.reason ?? blank.recommendation.reason,
    },
  }
}

/** Convert backend EAV format to frontend flat row format. */
function eavToFlat(eavRows) {
  if (!Array.isArray(eavRows) || eavRows.length === 0) return []
  const byRole = new Map()
  for (const row of eavRows) {
    if (!row.roleKey) continue
    if (!byRole.has(row.roleKey)) {
      byRole.set(row.roleKey, { roleKey: row.roleKey, roleLabel: getCustomerInfoRoleLabel(row.roleKey) })
    }
    byRole.get(row.roleKey)[row.infoKey] = BOOLEAN_INFO_KEYS.has(row.infoKey) ? toBooleanFormValue(row.value) : row.value
  }
  return Array.from(byRole.values())
}

/**
 * Build the API payload from form state (3-section structure).
 * Backend expects: TenderEvaluationSubmitRequest with legacy bidRecommendation + evaluationBasic/evaluationCustomerInfos/evaluationRecommendation
 *
 * CO-262: 透传 projectPlanGapFiles（CRM 回填的 GAP 附件外部 URL 引用），
 * 由后端 SubmissionService 在保存评估表时原子性持久化到 project_documents 表。
 * 用户手动上传的附件已通过 uploadEvaluationDocument 接口落库，
 * 此处仅传递 CRM 回填的外部 URL 引用，后端会先清空再重建。
 */
export function buildApiPayload(form) {
  const b = form.basic
  return {
    bidRecommendation: form.recommendation.shouldBid != null ? (form.recommendation.shouldBid ? 'RECOMMEND' : 'NOT_RECOMMEND') : null,
    evaluationBasic: {
      plannedShortlistedCount: b.plannedShortlistedCount ?? null,
      mroOfficeFlowAmount: b.mroOfficeFlowAmount ?? null,
      customerRevenue: b.customerRevenue ?? null,
      unfavorableItems: b.unfavorableItems || null,
      riskAssessment: b.riskAssessment || null,
      contingencyPlan: b.contingencyPlan || null,
      processKnowledge: b.processKnowledge || null,
      supportNotes: b.supportNotes || null,
      projectPlanGap: b.projectPlanGap || null,
      projectPlanGapFiles: Array.isArray(b.projectPlanGapFiles) ? b.projectPlanGapFiles : [],
    },
    evaluationCustomerInfos: Array.isArray(form.customerInfo) ? form.customerInfo.flatMap((row) =>
      CUSTOMER_INFO_COLUMNS
        .filter(col => col.key in row && row[col.key] != null && String(row[col.key]).trim() !== '')
        .map(col => ({
          roleKey: row.roleKey, infoKey: col.key, value: toBackendCellValue(col.key, row[col.key]), valueType: VT[col.key] || 'TEXT',
        }))
    ) : [],
    evaluationRecommendation: { shouldBid: form.recommendation.shouldBid ?? null, reason: form.recommendation.reason || null },
  }
}

// ---- Validation ----

export function validateBasicSection(basic) {
  // V1026: 所有基础字段均为 CRM 自动带入，不做强制必填校验
  // 仅做格式校验
  if (basic.plannedShortlistedCount != null && basic.plannedShortlistedCount < 1) {
    return '计划入围供应商数量不能小于 1'
  }
  if (basic.mroOfficeFlowAmount != null && Number(basic.mroOfficeFlowAmount) < 0) {
    return '电商MRO+办公流水金额不能为负数'
  }
  if (basic.customerRevenue != null && Number(basic.customerRevenue) < 0) {
    return '客户营收不能为负数'
  }
  return null
}

export function validateRecommendation(recommendation) {
  if (recommendation.shouldBid === null || recommendation.shouldBid === undefined) {
    return '请选择是否投标'
  }
  // When choosing NOT to bid (shouldBid = false), reason is required
  if (recommendation.shouldBid === false && (!recommendation.reason || !String(recommendation.reason).trim())) {
    return '选择不投标时，请填写理由'
  }
  return null
}

export function validateAll(form) {
  const basicErr = validateBasicSection(form.basic)
  if (basicErr) return { valid: false, section: 'basic', message: basicErr }

  const recErr = validateRecommendation(form.recommendation)
  if (recErr) return { valid: false, section: 'recommendation', message: recErr }

  return { valid: true }
}

// ---- Composable ----

export function useTenderEvaluationForm(props, emit) {
  const form = reactive(makeEmptyForm())

  // Deep snapshot for unsaved-changes detection
  const initialFormSnapshot = ref(null)

  // Sync incoming evaluation prop -> form
  watch(
    () => props.evaluation,
    (next) => {
      const mapped = evaluationToForm(next)
      // Save a deep clone as baseline for dirty detection
      initialFormSnapshot.value = JSON.parse(JSON.stringify(mapped))
      Object.assign(form.basic, mapped.basic)
      form.customerInfo = mapped.customerInfo
      Object.assign(form.recommendation, mapped.recommendation)
    },
    { immediate: true, deep: true }
  )

  // True when the form differs from the last server-provided evaluation
  const hasUnsavedChanges = computed(() => {
    if (!initialFormSnapshot.value) return false
    return JSON.stringify(buildApiPayload(form)) !== JSON.stringify(initialFormSnapshot.value)
  })

  // ---- visibility (instance-level, no role enum) --------------------------
  const evaluationStatus = computed(() => props.evaluation?.evaluationStatus || null)
  const isSubmitted = computed(() => evaluationStatus.value === 'SUBMITTED')

  const isEditable = computed(() => props.canFill && !isSubmitted.value)
  const isReadOnly = computed(() => !isEditable.value)

  const isRecommendationEditable = computed(() => (props.canFill || props.canFillRecommendation) && !isSubmitted.value)
  const isRecommendationReadOnly = computed(() => !isRecommendationEditable.value)

  const showDraftSubmitButtons = computed(() => (props.canFill || props.canFillRecommendation) && !isSubmitted.value)
  const showDecisionButtons = computed(() => (props.canDecide || props.canFillRecommendation) && isSubmitted.value)

  // ---- active section / collapsible panel state ----
  // CO-310 两步流程：默认展开第三部分（项目负责人建议），一/二部分（CRM 只读数据）收起可手动展开
  const activeSection = ref(['recommendation'])

  // ---- handlers -----------------------------------------------------------
  function handleSubmit() {
    const result = validateAll(form)
    if (!result.valid) {
      if (!activeSection.value.includes(result.section)) {
        activeSection.value = [...activeSection.value, result.section]
      }
      ElMessage.warning(result.message)
      return
    }
    emit('submit', buildApiPayload(form))
  }

  function handleSaveDraft() {
    emit('save-draft', buildApiPayload(form))
  }

  function handleBid() {
    emit('bid')
  }

  async function handleAbandon() {
    try {
      const { value: reason } = await ElMessageBox.prompt(
        '请填写弃标原因（必填）',
        '弃标确认',
        {
          confirmButtonText: '确认弃标',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputPlaceholder: '请输入弃标原因...',
          inputErrorMessage: '弃标原因不能为空',
          inputValidator: (v) => Boolean(v && v.trim()) || '弃标原因不能为空',
        }
      )
      const trimmed = (reason || '').trim()
      if (!trimmed) {
        ElMessage.warning('弃标原因不能为空')
        return
      }
      emit('abandon', { reason: trimmed })
    } catch {
      // user cancelled the dialog — no emit
    }
  }

  return {
    form,
    activeSection,
    isReadOnly,
    isRecommendationReadOnly,
    isEditable,
    showDraftSubmitButtons,
    showDecisionButtons,
    hasUnsavedChanges,
    handleSubmit,
    handleSaveDraft,
    handleBid,
    handleAbandon,
    validateBasicSection,
    validateRecommendation,
  }
}
