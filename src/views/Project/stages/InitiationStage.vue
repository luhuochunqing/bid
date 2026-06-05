<template>
<!-- M4.2: Dynamic Form Engine — project.initiation scope -->
<div class="initiation-stage">
  <!-- AdaptiveFormPage wraps the stage, enabling dynamic schema overrides -->
  <AdaptiveFormPage
    ref="adaptiveForm"
    scope="project.initiation"
    :model-value="form"
    :disabled="locked || submitting || saving"
    @update:model-value="handleDynamicUpdate"
    @submit="handleDynamicSubmit"
  >
    <!-- #fallback-form: entire existing InitiationStage form -->
    <template #fallback-form>
      <div class="initiation-stage-fallback">
<el-card class="section-card" shadow="never"><template #header><span>投标信息</span></template>
<el-form :model="form" label-width="200px" :disabled="locked">
<!-- 是否需要保证金：保留字段，放第一个 -->
<el-form-item label="是否需要保证金" required>
  <el-select v-model="form.needDeposit" @change="onDepositChange">
    <el-option label="是" value="YES" />
    <el-option label="否" value="NO" />
  </el-select>
</el-form-item>
<template v-if="form.needDeposit === 'YES'">
  <div class="grid-2">
    <el-form-item label="保证金金额" required><el-input-number v-model="form.depositAmount" :min="0" :precision="2" /></el-form-item>
    <el-form-item label="保证金缴纳方式" required>
      <el-select v-model="form.depositPaymentMethod">
        <el-option label="电汇" value="WIRE" />
        <el-option label="保险/保函" value="GUARANTEE" />
      </el-select>
    </el-form-item>
  </div>
</template>
<!-- 以下字段与标讯评估表「一、基础信息」完全对齐 -->
<el-divider />
<div class="grid-2">
  <el-form-item label="计划入围供应商数量"><el-input-number v-model="form.expectedBidders" :min="1" :precision="0" /></el-form-item>
  <el-form-item label="电商MRO+办公流水金额（万）"><el-input-number v-model="form.annualEcommerceAmount" :min="0" :precision="2" /></el-form-item>
</div>
<div class="grid-2">
  <el-form-item label="客户营收（万）"><el-input-number v-model="form.customerRevenue" :min="0" :precision="2" /></el-form-item>
  <el-form-item label=" " /> <!-- spacer -->
</div>
<el-form-item label="招标文件不利项"><el-input v-model="form.tenderAdverseItems" type="textarea" :rows="3" maxlength="5000" /></el-form-item>
<el-form-item label="风险预判"><el-input v-model="form.riskAssessment" type="textarea" :rows="3" maxlength="5000" /></el-form-item>
<el-form-item label="项目经理综合评估是否有兜底方案"><el-input v-model="form.riskMitigationPlan" type="textarea" :rows="3" maxlength="5000" /></el-form-item>
<el-form-item label="项目经理是否了解评标全流程"><el-input v-model="form.pmUnderstandsProcess" type="textarea" :rows="3" maxlength="5000" /></el-form-item>
<el-form-item label="需要的支持及其他关键信息备注"><el-input v-model="form.supportNeeded" type="textarea" :rows="3" maxlength="5000" /></el-form-item>
<el-form-item label="项目计划GAP"><el-input v-model="form.projectPlanGap" type="textarea" :rows="3" maxlength="5000" /></el-form-item>
</el-form></el-card>
<el-card class="section-card" shadow="never">
<template #header><span>客户信息</span></template>
<div class="customer-table-wrapper">
<el-table :data="custFixedRows" border style="min-width:3200px" height="500">
<!-- 列顺序、标签、控件类型对齐 customerInfoMatrixConfig.js -->
<el-table-column label="客户信息（角色名）" width="170" fixed="left"><template #default="{row}"><span class="role-label">{{ row.role }}</span></template></el-table-column>
<el-table-column label="姓名" width="120"><template #default="{row}"><el-input v-model="row.name" size="small" placeholder="请输入姓名" /></template></el-table-column>
<el-table-column label="职位" width="140"><template #default="{row}"><el-select v-model="row.position" size="small" placeholder="请选择"><el-option v-for="o in POSITION_OPTIONS" :key="o" :label="o" :value="o" /></el-select></template></el-table-column>
<el-table-column label="西域项目负责人" width="130"><template #default="{row}"><el-input v-model="row.xiyuContact" size="small" placeholder="请输入负责人" /></template></el-table-column>
<el-table-column label="触达方式" width="120"><template #default="{row}"><el-select v-model="row.reachMethod" size="small" placeholder="请选择"><el-option v-for="o in CONTACT_METHOD_OPTIONS" :key="o" :label="o" :value="o" /></el-select></template></el-table-column>
<el-table-column label="倾向性评估依据" width="180"><template #default="{row}"><el-input v-model="row.preferenceBasis" size="small" placeholder="请输入依据" /></template></el-table-column>
<el-table-column label="是否触达" width="110"><template #default="{row}"><el-select v-model="row.reached" size="small"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select></template></el-table-column>
<el-table-column label="是否有正式高层交流" width="150"><template #default="{row}"><el-select v-model="row.hasHighLevelMeeting" size="small"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select></template></el-table-column>
<el-table-column label="是否向此人引导标书" width="150"><template #default="{row}"><el-select v-model="row.guideBid" size="small"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select></template></el-table-column>
<el-table-column label="是否可获取关键信息" width="150"><template #default="{row}"><el-select v-model="row.canGetKeyInfo" size="small"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select></template></el-table-column>
<el-table-column label="是否可删除不利项" width="150"><template #default="{row}"><el-select v-model="row.canRemoveAdverse" size="small"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select></template></el-table-column>
<el-table-column label="是否为重点攻克对象" width="150"><template #default="{row}"><el-select v-model="row.isKeyTarget" size="small"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select></template></el-table-column>
<el-table-column label="是否可同步评标信息" width="150"><template #default="{row}"><el-select v-model="row.canSyncEval" size="small"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select></template></el-table-column>
<el-table-column label="对我司的倾向性" width="150"><template #default="{row}"><el-select v-model="row.preference" size="small"><el-option label="支持" value="SUPPORT" /><el-option label="中立" value="NEUTRAL" /><el-option label="反对" value="OPPOSE" /></el-select></template></el-table-column>
<el-table-column label="是否给出明确中标信息" width="160"><template #default="{row}"><el-select v-model="row.canConfirmWin" size="small"><el-option label="是" value="YES" /><el-option label="否" value="NO" /></el-select></template></el-table-column>
<el-table-column label="对中标影响率" width="130"><template #default="{row}"><el-select v-model="row.winRateImpact" size="small" placeholder="请选择"><el-option v-for="o in IMPACT_OPTIONS" :key="o.value" :label="o.label" :value="o.value" /></el-select></template></el-table-column>
</el-table></div></el-card>
<el-card class="section-card" shadow="never">
<template #header>
  <div class="section-header">
    <span>招标文件与 AI 风险评估</span>
    <div class="ai-risk-corner">
      <el-button type="primary" size="small" :loading="aiAssessing" :disabled="!form.tenderDocumentId || locked" @click="runAIAssessment">
        {{ aiAssessing ? '评估中...' : 'AI 风险评估' }}
      </el-button>
      <el-tag :type="riskTagType" class="risk-tag">{{ riskTagText || '中风险' }}</el-tag>
    </div>
  </div>
</template>
<div class="bid-doc-section">
  <div class="bid-doc-upload-area">
    <el-upload
      v-model:file-list="bidDocFiles"
      :action="uploadUrl"
      :headers="uploadHeaders"
      :before-upload="beforeUploadDoc"
      :on-success="handleDocUploadSuccess"
      :limit="1"
      accept=".pdf,.doc,.docx"
      drag
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div class="upload-text">将招标文件拖到此处，或<em>点击上传</em></div>
      <template #tip><div class="upload-tip">支持 PDF、Word 格式，单个文件</div></template>
    </el-upload>
  </div>
  <el-alert v-if="form.aiRiskAssessmentNotes" :title="form.aiRiskAssessmentNotes" :type="aiAlertType" :closable="false" show-icon class="ai-result-alert" />
  <div class="bid-doc-actions">
    <template v-if="!isApprovalMode">
      <el-button :loading="saving" :disabled="locked" @click="saveDraft">保存草稿</el-button>
      <el-button type="primary" :loading="submitting" :disabled="locked" @click="submit">提交立项</el-button>
    </template>
    <el-tag v-if="errorMsg" type="danger" class="error-tag">{{ errorMsg }}</el-tag>
  </div>
</div>
</el-card>
      <!-- 标书制作人员分配：仅投标管理员/组长在待审核状态可见 -->
      <el-card v-if="isApprovalMode" class="section-card" shadow="never">
        <template #header><span>标书制作人员分配</span></template>
        <el-form :model="approvalForm" label-width="140px">
          <div class="grid-2">
            <el-form-item label="投标负责人" required>
              <el-select v-model="approvalForm.biddingLeaderId" filterable remote :remote-method="searchLeader" :loading="leaderSearching" placeholder="搜索人员" style="width:100%" @change="(id) => { const o = leaderOptions.find(u => u.id === id); approvalForm.biddingLeaderLabel = o ? o._label : '' }">
                <el-option v-for="u in leaderOptions" :key="u.id" :label="u._label" :value="u.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="投标辅助人员">
              <el-select v-model="approvalForm.biddingAssistantId" filterable remote :remote-method="searchAssistant" :loading="assistantSearching" placeholder="搜索人员" style="width:100%" clearable @change="(id) => { const o = assistantOptions.find(u => u.id === id); approvalForm.biddingAssistantLabel = o ? o._label : '' }">
                <el-option v-for="u in assistantOptions" :key="u.id" :label="u._label" :value="u.id" />
              </el-select>
            </el-form-item>
          </div>
        </el-form>
        <div class="bid-doc-actions">
          <el-button type="danger" :loading="rejecting" @click="handleReject">驳回</el-button>
          <el-button type="success" :loading="approving" @click="handleApprove">同意</el-button>
        </div>
      </el-card>
      </div><!-- end .initiation-stage-fallback -->
    </template><!-- end #fallback-form -->
  </AdaptiveFormPage>
</div><!-- end .initiation-stage -->
</template>
<script setup>
import { ref, reactive, computed, onMounted, shallowRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { usersApi } from '@/api/modules/users.js'
import { tendersApi } from '@/api/modules/tenders.js'
import { projectsApi } from '@/api/modules/projects.js'
import { tenderInitApi } from '@/api/modules/tenderInitMapping.js'

import { useUserStore } from '@/stores/user.js'
import AdaptiveFormPage from '@/components/common/AdaptiveFormPage.vue'

// 标讯→立项枚举映射（惰加载，从后端数据字典 API 获取）
let _mappingPromise = null
async function _ensureMapping() {
  if (!_mappingPromise) _mappingPromise = tenderInitApi.getMapping().then(r => (r?.data || {}), () => ({}))
  const d = await _mappingPromise
  return { projectType: d?.projectType || {}, customerType: d?.customerType || {} }
}
const normType = (r, m) => r ? (m[r] || r) : ''

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const emit = defineEmits(['updated'])
const userStore = useUserStore()
const adaptiveForm = shallowRef(null)
const form = reactive({ projectName: '', ownerUnit: '', createTime: new Date().toISOString().slice(0, 16).replace('T', ' '), projectType: '', customerType: '', priorityLevel: 'B', headquartersLocation: '', projectLeaderName: '', projectLeaderUserId: null, leaderDepartment: '', contactName: '', contactPhone: '', contactTel: '', contactMail: '', contactName2: '', contactPhone2: '', contactTel2: '', contactMail2: '', tenderId: null, expectedBidders: 0, annualEcommerceAmount: 0, annualRevenue: 0, customerRevenue: 0, bidOpenTime: '', bidMonth: '', biddingPlatform: '', needDeposit: 'NO', depositAmount: 0, depositPaymentMethod: '', tenderAdverseItems: '', riskAssessment: '', riskMitigationPlan: '', pmUnderstandsProcess: '', supportNeeded: '', projectPlanGap: '', tenderDocumentId: null, aiRiskLevel: null, aiRiskAssessmentNotes: '', biddingLeaderName: '', biddingAssistantName: '' })
// 与 customerInfoMatrixConfig.js CUSTOMER_INFO_ROWS 对齐（14 行）
const CUST_ROLES = ["项目最高决策人","物资公司董事长","物资公司分管电商领导","电商公司董事长","电商公司总经理","电商公司副总经理","电商公司运营负责人","招标文件制作人","其他关键决策人1","其他关键决策人2","其他关键决策人3","专家1","专家2","专家3"]
function emptyCustRow(role) { return { role, name: '', position: '', xiyuContact: '', reached: '', reachMethod: '', preference: '', preferenceBasis: '', hasHighLevelMeeting: '', guideBid: '', canGetKeyInfo: '', canRemoveAdverse: '', isKeyTarget: '', canSyncEval: '', canConfirmWin: '', winRateImpact: '' } }
const POSITION_OPTIONS = ['董事长','总经理','副总经理','部门负责人','项目负责人','采购负责人','技术负责人','财务负责人','法务负责人','评标专家','经办人','外部顾问','其他决策人','其他']
const CONTACT_METHOD_OPTIONS = ['电话','微信','邮件','拜访','会议','第三方引荐','未触达']
const IMPACT_OPTIONS = [{ label: '极高', value: 'VERY_HIGH' },{ label: '高', value: 'HIGH' },{ label: '中', value: 'MEDIUM' },{ label: '低', value: 'LOW' },{ label: '极低', value: 'VERY_LOW' },{ label: '无影响', value: 'NONE' }]
const custFixedRows = ref(CUST_ROLES.map(emptyCustRow)); const bidDocFiles = ref([]); const existing = ref(false); const locked = ref(false); const fieldLocked = ref(false); const submitting = ref(false); const saving = ref(false); const approving = ref(false); const rejecting = ref(false); const aiAssessing = ref(false); const errorMsg = ref(''); const reviewStatus = ref('')
// 审批模式：投标管理员/组长 查看 PENDING_REVIEW 的立项
const userRole = computed(() => userStore.currentUser?.role || '')
const isApprovalMode = computed(() => (userRole.value === 'bid_admin' || userRole.value === 'bid_lead') && reviewStatus.value === 'PENDING_REVIEW')
// 人员搜索
const leaderOptions = ref([]); const leaderSearching = ref(false)
async function searchLeader(q) { if (!q || q.length < 1) return; leaderSearching.value = true; try { const r = await usersApi.search(q, 15); leaderOptions.value = (Array.isArray(r) ? r : []).map(u => ({ ...u, _label: u.name + '（' + (u.employeeId || '') + '）- ' + (u.departmentName || u.deptName || '') })) } catch { leaderOptions.value = [] } finally { leaderSearching.value = false } }
const assistantOptions = ref([]); const assistantSearching = ref(false)
async function searchAssistant(q) { if (!q || q.length < 1) return; assistantSearching.value = true; try { const r = await usersApi.search(q, 15); assistantOptions.value = (Array.isArray(r) ? r : []).map(u => ({ ...u, _label: u.name + '（' + (u.employeeId || '') + '）- ' + (u.departmentName || u.deptName || '') })) } catch { assistantOptions.value = [] } finally { assistantSearching.value = false } }
const approvalForm = reactive({ biddingLeaderId: null, biddingLeaderLabel: '', biddingAssistantId: null, biddingAssistantLabel: '' })
const uploadUrl = computed(() => `/api/projects/${props.projectId}/documents`)
const uploadHeaders = computed(() => { const t = userStore?.token; return t ? { Authorization: 'Bearer ' + t } : {} })
const riskTagType = computed(() => form.aiRiskLevel === 'HIGH' ? 'danger' : form.aiRiskLevel === 'MEDIUM' ? 'warning' : form.aiRiskLevel === 'LOW' ? 'success' : 'info')
const riskTagText = computed(() => form.aiRiskLevel === 'HIGH' ? '高风险' : form.aiRiskLevel === 'MEDIUM' ? '中风险' : form.aiRiskLevel === 'LOW' ? '低风险' : '')
const aiAlertType = computed(() => form.aiRiskLevel === 'HIGH' ? 'error' : form.aiRiskLevel === 'MEDIUM' ? 'warning' : 'success')
function beforeUploadDoc(f) { const valid = ['.pdf', '.doc', '.docx'].some(e => f.name.toLowerCase().endsWith(e)); if (!valid) { ElMessage.error('仅支持 PDF/Word 格式'); return false } return true }
function onDepositChange(val) { if (val === 'NO') { form.depositAmount = 0; form.depositPaymentMethod = '' } }
function handleDocUploadSuccess(r) { if (r?.data?.id) form.tenderDocumentId = r.data.id }
function buildPayload() { return { ...form, customerInfoRows: custFixedRows.value } }
async function autoFillFromTender() {
  try {
    const projResp = await projectsApi.getDetail(props.projectId)
    const project = projResp?.data || projResp
    const tenderId = project?.tenderId
    if (!tenderId) return
    const tenderResp = await tendersApi.getDetail(tenderId)
    const t = tenderResp?.data || tenderResp
    if (!t) return
    const bidTime = t.bidOpeningTime || t.bidOpenTime
    const mapping = await _ensureMapping()
    Object.assign(form, {
      bidOpenTime: bidTime || '',
      bidMonth: bidTime ? new Date(bidTime).toISOString().slice(0, 7).replace('-', '/') : '',
      projectType: normType(t.projectType, mapping.projectType),
      customerType: normType(t.customerType, mapping.customerType),
      ownerUnit: t.purchaserName || '',
      projectLeaderName: t.projectManagerName || '',
      leaderDepartment: t.department || '',
      biddingLeaderName: t.biddingPersonName || '',
      biddingPlatform: t.platform || t.biddingPlatform || '',
      headquartersLocation: t.region || '',
      contactName: t.contactName || '',
      contactPhone: t.contactPhone || '',
      contactTel: t.contactTel || '',
      contactMail: t.contactMail || '',
      contactName2: t.contactName2 || '',
      contactPhone2: t.contactPhone2 || '',
      contactTel2: t.contactTel2 || '',
      contactMail2: t.contactMail2 || '',
      annualEcommerceAmount: t.budget ?? form.annualEcommerceAmount,
      expectedBidders: t.expectedBidders ?? form.expectedBidders,
      riskAssessment: t.riskAssessment || '',
      riskMitigationPlan: t.riskMitigationPlan || '',
      tenderAdverseItems: t.tenderAdverseItems || t.unfavorableItems || '',
      supportNeeded: t.supportNeeded || '',
      projectPlanGap: t.projectPlanGap || '',
      projectName: t.projectName || t.name || '',
      tenderId: tenderId,
      createTime: project.createdAt ? new Date(project.createdAt).toLocaleString('zh-CN') : '',
    })
    // 尝试从评估表带入投标信息字段
    try {
      const evalResp = await tendersApi.getEvaluation(tenderId)
      const ev = evalResp?.data || evalResp
      const basic = ev?.evaluationBasic
      if (basic) {
        if (basic.plannedShortlistedCount != null) form.expectedBidders = basic.plannedShortlistedCount
        if (basic.mroOfficeFlowAmount != null) form.annualEcommerceAmount = basic.mroOfficeFlowAmount
        if (basic.customerRevenue != null) { form.customerRevenue = basic.customerRevenue; form.annualRevenue = basic.customerRevenue }
        if (basic.unfavorableItems) form.tenderAdverseItems = basic.unfavorableItems
        if (basic.riskAssessment) form.riskAssessment = basic.riskAssessment
        if (basic.contingencyPlan) form.riskMitigationPlan = basic.contingencyPlan
        if (basic.processKnowledge) form.pmUnderstandsProcess = basic.processKnowledge
        if (basic.supportNotes) form.supportNeeded = basic.supportNotes
        if (basic.projectPlanGap) form.projectPlanGap = basic.projectPlanGap
      }
    } catch (_) { /* 评估表可能不存在，忽略 */ }
  } catch (e) {
    console.warn('[InitiationStage] auto-fill from tender failed', e)
  }
}
async function load() { try { const r = await projectLifecycleApi.getInitiation(props.projectId); const d = r?.data || r; if (d) { Object.assign(form, d); if (d.customerInfoRows) custFixedRows.value = d.customerInfoRows; else if (d.customerInfoJson) { try { custFixedRows.value = JSON.parse(d.customerInfoJson) } catch (_) { /* ignore malformed JSON */ } } existing.value = true; reviewStatus.value = d.reviewStatus || ''; fieldLocked.value = !!d.bidOpenTime && !!d.ownerUnit; if (d.locked) locked.value = !!d.locked } } catch (e) { if (e?.response?.status === 404) { await autoFillFromTender() } else { console.warn(e) } } }
async function handleApprove() { if (!approvalForm.biddingLeaderId) return ElMessage.warning('请选择投标负责人'); approving.value = true; errorMsg.value = ''; try { await projectLifecycleApi.approveInitiation(props.projectId, { primaryLeadUserId: approvalForm.biddingLeaderId, secondaryLeadUserId: approvalForm.biddingAssistantId || null }); ElMessage.success('已通过，项目进入标书制作阶段'); emit('updated'); await load() } catch (e) { errorMsg.value = e?.response?.data?.msg || '审批失败' } finally { approving.value = false } }
async function handleReject() { try { const { value: reason } = await ElMessageBox.prompt('请填写驳回原因', '驳回立项', { confirmButtonText: '确认驳回', cancelButtonText: '取消', inputType: 'textarea', inputErrorMessage: '驳回原因不能为空', inputValidator: (v) => Boolean(v && v.trim()) }); if (!reason || !reason.trim()) return; rejecting.value = true; errorMsg.value = ''; try { await projectLifecycleApi.rejectInitiation(props.projectId, { rejectionReason: reason.trim() }); ElMessage.success('已驳回'); emit('updated'); await load() } catch (e) { errorMsg.value = e?.response?.data?.msg || '驳回失败' } finally { rejecting.value = false } } catch { /* user cancelled */ } }
async function saveDraft() { saving.value = true; errorMsg.value = ''; try { const m = existing.value ? 'updateInitiation' : 'submitInitiation'; await projectLifecycleApi[m](props.projectId, buildPayload()); ElMessage.success('草稿已保存'); existing.value = true; await load() } catch (e) { errorMsg.value = e?.response?.data?.msg || '保存失败' } finally { saving.value = false } }
async function submit() { if (form.needDeposit === 'YES' && !form.depositPaymentMethod) return ElMessage.warning('请选择保证金缴纳方式'); submitting.value = true; errorMsg.value = ''; try { const m = existing.value ? 'updateInitiation' : 'submitInitiation'; await projectLifecycleApi[m](props.projectId, buildPayload()); ElMessage.success(existing.value ? '立项信息已更新' : '立项已提交'); fieldLocked.value = true; existing.value = true; emit('updated'); await load() } catch (e) { errorMsg.value = e?.response?.data?.msg || '提交失败' } finally { submitting.value = false } }
async function runAIAssessment() { if (!form.tenderDocumentId) return ElMessage.warning('请先上传招标文件'); aiAssessing.value = true; try { const { scoreAnalysisApi } = await import('@/api/modules/ai.js'); const r = await scoreAnalysisApi.generatePreview({ documentId: form.tenderDocumentId }); form.aiRiskLevel = r?.data?.riskLevel || 'MEDIUM'; form.aiRiskAssessmentNotes = r?.data?.summary || 'AI 评估已完成'; ElMessage.success('AI 风险评估完成') } catch (e) { if (e?.response?.status === 503 || e?.response?.status === 502) { ElMessage.warning('AI 服务暂不可用，请稍后重试') } else if (e?.response?.status === 401 || e?.response?.status === 403) { ElMessage.error('AI 评估权限不足') } else { ElMessage.error('AI 评估失败：' + (e?.message || '未知错误')) } } finally { aiAssessing.value = false } }

/**
 * Sync updates from DynamicFormRenderer back into the reactive form.
 */
function handleDynamicUpdate(value) {
  Object.assign(form, value)
}

/**
 * Forward dynamic form submit to the stage's submit handler.
 */
async function handleDynamicSubmit(formData) {
  if (formData) Object.assign(form, formData)
  await submit()
}
onMounted(load)
</script>
<style scoped>
.initiation-stage { display: flex; flex-direction: column; gap: 16px; }
.section-card { border: 1px solid var(--el-border-color-light); }
.section-header { display: flex; justify-content: space-between; align-items: center; }
.grid-3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0 24px; }
.grid-2 { display: grid; grid-template-columns: repeat(2, 1fr); gap: 0 24px; }
.grid-1 { display: grid; grid-template-columns: 1fr; gap: 0; margin-top: 8px; }
.customer-table-wrapper { overflow-x: auto; }
.ai-risk-corner { display: flex; align-items: center; gap: 10px; }
.risk-tag { font-size: 14px; }
.bid-doc-section { display: flex; flex-direction: column; gap: 16px; }
.bid-doc-upload-area { display: flex; justify-content: center; }
.bid-doc-upload-area .upload-icon { font-size: 40px; color: #c0c4cc; }
.bid-doc-upload-area .upload-text { font-size: 14px; color: #606266; margin-top: 8px; }
.bid-doc-upload-area .upload-text em { color: #2E7659; font-style: normal; }
.bid-doc-upload-area .upload-tip { font-size: 12px; color: #909399; margin-top: 4px; }
.bid-doc-actions { display: flex; gap: 12px; align-items: center; justify-content: flex-end; padding-top: 8px; }
.error-tag { font-size: 13px; }
.ai-result-alert { margin-top: 0; }
</style>
