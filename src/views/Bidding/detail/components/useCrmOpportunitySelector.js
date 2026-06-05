// Input: props, emit
// Output: crm opportunity search + manual fallback state/actions
// Pos: src/views/Bidding/detail/components/ - CRM opportunity selector composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { crmApi } from '@/api/modules/crm.js'

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

  async function openSearch() {
    showDialog.value = true
    if (!searchPerformed.value) await doSearch(1)
  }

  async function doSearch(page) {
    if (page) currentPage.value = page
    searching.value = true
    try {
      const params = {
        pageIndex: currentPage.value,
        pageSize: pageSize.value,
        body: {},
      }
      if (searchForm.name) params.body.name = searchForm.name
      if (searchForm.code) params.body.code = searchForm.code
      if (searchForm.projectStatus.length > 0) params.body.projectStatus = searchForm.projectStatus
      if (props.tenderer && !searchForm.name) params.body.name = props.tenderer

      const res = await crmApi.searchOpportunities(params)
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

  function confirmLink() {
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
          opportunityId: null, basic: { projectBackground: mf.remark || '', competitorAnalysis: '',
            contractPeriodStart: mf.evaluationTime || '', contractPeriodEnd: '',
            shortlistedCount: 0, platformServiceFee: 0 },
          customerInfos: [], recommendation: { shouldBid: true, reason: '' },
        },
      })
      showDialog.value = false
      ElMessage.success('已手动关联商机')
      return
    }
    // CRM选择模式
    const chance = selectedChance.value
    linkedOpportunity.value = { name: chance.name, code: chance.code, id: chance.id }
    emit('linked', {
      opportunityId: chance.id,
      opportunityName: chance.name,
      evaluationData: {
        opportunityId: chance.id,
        basic: {
          projectBackground: chance.remark || '',
          competitorAnalysis: chance.bidDocumentDisadvantage || '',
          contractPeriodStart: chance.evaluationTime || '',
          contractPeriodEnd: '',
          shortlistedCount: chance.planSupplierCount || 0,
          platformServiceFee: chance.ecommerceMroAmount || 0,
        },
        customerInfos: [],
        recommendation: { shouldBid: !chance.backupPlan, reason: chance.riskPrediction || '' },
      },
    })
    showDialog.value = false
    ElMessage.success('CRM商机已关联，评估表已回填')
  }

  function resetSearch() { showManualForm.value = false; manualConfirmed.value = false }

  return {
    showDialog, searching, loading, searchPerformed, results, selectedId,
    selectedChance, totalCount, currentPage, pageSize, showManualForm,
    manualConfirmed, searchForm, manualForm, linkedOpportunity,
    openSearch, doSearch, onSelect, confirmManual, confirmLink, resetSearch,
  }
}
