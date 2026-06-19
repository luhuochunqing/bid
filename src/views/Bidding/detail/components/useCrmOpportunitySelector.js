// Input: props, emit
// Output: crm opportunity search + manual fallback state/actions
// Pos: src/views/Bidding/detail/components/ - CRM opportunity selector composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { crmApi } from '@/api/modules/crm.js'
import { CUSTOMER_INFO_ROWS } from './customerInfoMatrixConfig.js'

export function useCrmOpportunitySelector(props, emit) {
  const showDialog = ref(false)
  const searching = ref(false)
  const loading = ref(false)
  const searchPerformed = ref(false)
  const results = ref([])
  const selectedId = ref(null)
  const selectedChance = ref(null)
  const totalCount = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(10)
  const showManualForm = ref(false)
  const manualConfirmed = ref(false)

  const searchForm = reactive({ name: '', code: '', projectStatus: [] })
  const manualForm = reactive({
    name: '', code: '', projectLeaderName: '', evaluationTime: '',
    projectStatusText: '', projectRiskText: '', remark: '',
  })

  const linkedOpportunity = ref(
    props.alreadyLinkedName ? { name: props.alreadyLinkedName } : null
  )

  // 同步父组件传入的 alreadyLinkedName 变化（如 loadTenderDetail 完成后）
  watch(() => props.alreadyLinkedName, (newName) => {
    if (newName && (!linkedOpportunity.value || linkedOpportunity.value.name !== newName)) {
      linkedOpportunity.value = { name: newName }
    }
  })

  function hasBlueprintCriteria() {
    return Boolean(props.tenderer?.trim())
  }

  function hasManualCriteria() {
    return Boolean(searchForm.name?.trim() || searchForm.code?.trim() || searchForm.projectStatus.length > 0)
  }

  async function openSearch() {
    showDialog.value = true
    if (!searchPerformed.value) await doSearch(1)
  }

  async function doSearch(page) {
    if (page) currentPage.value = page
    searching.value = true
    try {
      let res
      if (hasManualCriteria()) {
        // 用户输入了手动查询条件：按商机名称/编号/状态走通用分页查询
        const params = {
          pageIndex: currentPage.value,
          pageSize: pageSize.value,
          body: {},
        }
        if (searchForm.name) params.body.name = searchForm.name.trim()
        if (searchForm.code) params.body.code = searchForm.code.trim()
        if (searchForm.projectStatus.length > 0) params.body.projectStatus = searchForm.projectStatus
        res = await crmApi.searchOpportunities(params)
      } else if (hasBlueprintCriteria()) {
        // 优先按招标主体（CRM groupName）查同集团商机；CRM groupName 与标讯招标主体可能不完全一致，
        // 若查不到再兜底全量，避免产品蓝图要求的 evaluationTime 精确匹配导致经常性空结果。
        res = await crmApi.searchOpportunities({
          pageIndex: currentPage.value,
          pageSize: pageSize.value,
          body: { groupName: [props.tenderer.trim()] },
        })
        if ((res?.data?.list?.length || 0) === 0) {
          res = await crmApi.searchOpportunities({
            pageIndex: currentPage.value,
            pageSize: pageSize.value,
            body: { selectAll: true },
          })
        }
      } else {
        // 标讯缺少招标主体时兜底：拉取全量商机供用户手动选择
        res = await crmApi.searchOpportunities({
          pageIndex: currentPage.value,
          pageSize: pageSize.value,
          body: { selectAll: true },
        })
      }

      const data = res?.data
      results.value = data?.list || []
      totalCount.value = data?.totalCount || 0
      searchPerformed.value = true
      if (results.value.length === 0) ElMessage.info('未找到匹配的CRM商机')
    } catch (e) {
      ElMessage.error('商机查询失败：' + (e?.message || '未知错误'))
    } finally {
      searching.value = false
    }
  }

  function onSelect(row) {
    if (!row?.id) return
    selectedId.value = row.id
    selectedChance.value = row
  }

  function confirmManual() {
    if (!manualForm.name?.trim()) { ElMessage.warning('请输入商机名称'); return }
    manualConfirmed.value = true
    ElMessage.success('商机信息已确认')
  }

  async function confirmLink() {
    if (!selectedChance.value && !manualConfirmed.value) {
      ElMessage.warning('请先选择或输入商机')
      return
    }
    // 手动输入模式
    if (manualConfirmed.value && !selectedChance.value) {
      const mf = manualForm
      linkedOpportunity.value = { name: mf.name, code: mf.code || '', id: null }
      emit('linked', {
        opportunityId: null,
        opportunityName: mf.name,
        evaluationData: {
          opportunityId: null, basic: {
            riskAssessment: mf.projectRiskText || '', unfavorableItems: '',
            contractPeriodStart: mf.evaluationTime || '', contractPeriodEnd: '',
            plannedShortlistedCount: 0, mroOfficeFlowAmount: 0,
            contingencyPlan: '', processKnowledge: '',
            supportNotes: mf.remark || '', projectPlanGap: '',
            projectPlanGapFiles: [],
            customerRevenue: null,
          },
          customerInfos: [], recommendation: { shouldBid: true, reason: '' },
        },
      })
      showDialog.value = false
      ElMessage.success('已手动关联商机')
      return
    }
    // CRM选择模式
    const chance = selectedChance.value
    let customerInfos = []
    if (chance?.id) {
      try {
        const contactRes = await crmApi.getContactPersons(chance.id)
        const contacts = contactRes?.data || []
        const roleKeys = CUSTOMER_INFO_ROWS.map(r => r.roleKey)
        customerInfos = contacts.map((c, idx) => ({
          roleKey: roleKeys[idx % roleKeys.length],
          NAME: c.name || '',
          CONTACT_INFO: c.phone || c.email || '',
          POSITION: '',
          XIYU_CONTACT: c.ehsyProjectManager || '',
          CONTACT_METHOD: c.contactMethod || '',
          INFO_TENDENCY_BASIS: c.preferenceBasis || '',
          CONTACTED: c.contacted != null ? (c.contacted ? '是' : '否') : null,
          GUIDED_BID: c.guidedBidDocument != null ? (c.guidedBidDocument ? '是' : '否') : null,
          CAN_GET_KEY_INFO: c.getKeyInfo != null ? (c.getKeyInfo ? '是' : '否') : null,
          CAN_REMOVE_ADVERSE: c.deleteDisadvantage != null ? (c.deleteDisadvantage ? '是' : '否') : null,
          CAN_SYNC_EVAL: c.syncInfo != null ? (c.syncInfo ? '是' : '否') : null,
          TENDENCY: c.preferenceLevel || null,
          INFO_CLEAR_WINNER_BID: c.guaranteeWin || false,
          INFO_WIN_RATE_IMPACT: c.impactRate || null,
        }))
      } catch { /* ignore */ }
    }
    linkedOpportunity.value = { name: chance.name, code: chance.code, id: chance.id }
    // opportunityId 传商机编号（code，CC... 格式），非商机数字 id。
    // 后端 crm_opportunity_id 字段存商机编号（V118 迁移注释明确"存商机编号如 CC20260610180"），
    // webhook bidInfoSync 回传时 code 字段也取此值匹配 CRM 商机。若传数字 id 会导致 CRM 匹配失败。
    // 注：CRM 自动推送路径（非手动关联）实测传的是 id（CO-277），后端 applyCrmLinkAndAssignment
    // 会自动按 id 反查 code 落库；但前端手动关联路径直接传 code，无需反查。
    emit('linked', {
      opportunityId: chance.code,
      opportunityName: chance.name,
      evaluationData: {
        opportunityId: chance.code,
        basic: {
          riskAssessment: chance.riskPrediction || '',
          unfavorableItems: chance.bidDocumentDisadvantage || '',
          contractPeriodStart: chance.evaluationTime || '',
          contractPeriodEnd: '',
          plannedShortlistedCount: chance.planSupplierCount || 0,
          mroOfficeFlowAmount: chance.ecommerceMroAmount || 0,
          contingencyPlan: chance.backupPlan != null ? (chance.backupPlan ? '是' : '否') : '',
          processKnowledge: chance.managerUnderstandProcess || '',
          supportNotes: chance.remark || '',
          projectPlanGap: chance.projectGap || '',
          projectPlanGapFiles: chance.gapFile ? [{ fileName: 'GAP附件', fileUrl: chance.gapFile }] : [],
          customerRevenue: chance.customerRevenue || null,
        },
        customerInfos,
        recommendation: { shouldBid: !chance.backupPlan, reason: '' },
      },
    })
    showDialog.value = false
  }

  function resetSearch() { showManualForm.value = false; manualConfirmed.value = false }

  return {
    showDialog, searching, loading, searchPerformed, results, selectedId,
    selectedChance, totalCount, currentPage, pageSize, showManualForm,
    manualConfirmed, searchForm, manualForm, linkedOpportunity,
    openSearch, doSearch, onSelect, confirmManual, confirmLink, resetSearch,
  }
}
