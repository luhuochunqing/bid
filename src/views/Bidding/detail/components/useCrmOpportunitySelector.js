// Input: props, emit
// Output: crm opportunity search state/actions
// Pos: src/views/Bidding/detail/components/ - CRM opportunity selector composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { crmApi } from '@/api/modules/crm.js'
import { CRM_POSITION_TO_ROLE } from './customerInfoMatrixConfig.js'

/**
 * 解析 CRM 商机 gapFile 字段为评估表 projectPlanGapFiles。
 * CRM 返回的 gapFile 是 JSON 数组字符串（可多文件），元素形如 {url, name}：
 *   '[{"url":"https://...","name":"xxx.xlsx"}]'
 * 此前直接把整个字符串当单个 URL，导致 fileUrl 存了一坨 JSON 文本、附件打不开。
 */
function parseGapFiles(raw) {
  if (!raw) return []
  // CRM gapFile 通常是 JSON 数组字符串 '[{"url":"...","name":"..."}]'（可多文件），
  // 也可能退化成 JSON 对象字符串（单文件）或单个 URL 字符串（旧数据）。
  // 统一解析，避免把 JSON 文本当 URL 落库导致附件打不开。
  const fromObj = o => ({ fileName: o.name || o.fileName || 'GAP附件', fileUrl: o.url || o.fileUrl || '' })
  const pick = list => list.map(fromObj).filter(f => f.fileUrl)
  if (typeof raw === 'string') {
    const trimmed = raw.trim()
    try {
      const parsed = JSON.parse(trimmed)
      if (Array.isArray(parsed)) return pick(parsed)
      if (parsed && typeof parsed === 'object') {
        const f = fromObj(parsed)
        return f.fileUrl ? [f] : []
      }
    } catch { /* 非合法 JSON，按单个 URL 兜底 */ }
    return [{ fileName: 'GAP附件', fileUrl: trimmed }]
  }
  if (Array.isArray(raw)) return pick(raw)
  if (raw && typeof raw === 'object') {
    const f = fromObj(raw)
    return f.fileUrl ? [f] : []
  }
  return []
}

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

  const searchForm = reactive({ name: '', code: '', projectStatus: [] })

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

  async function confirmLink() {
    if (!selectedChance.value) {
      ElMessage.warning('请先选择商机')
      return
    }
    // CRM选择模式
    const chance = selectedChance.value
    let customerInfos = []
    if (chance?.id) {
      try {
        const contactRes = await crmApi.getContactPersons(chance.id)
        const contacts = Array.isArray(contactRes) ? contactRes : (contactRes?.data || [])
        // CO-277: 对接人查询成功但为空时阻断关联，提示用户到 CRM 系统添加对接人。
        // 生产案例：CRM 推送标讯时项目负责人未在 CRM 系统录入对接人，导致商机关联后
        // 评估表客户信息矩阵为空，后续 CRM token 获取和商机反查全部失败。
        if (contacts.length === 0) {
          ElMessage.error('请到 CRM 系统添加项目对接人')
          return
        }
        // CO-431: CRM 对接人 position 是数字字符串（生产日志实锤，见 CO-329 PR #1124 评论
        // 与 docs/references/crm-integration-lessons.md §13），必须按 position 字段映射到对应角色行，
        // 不能按数组下标 idx % 14 强行分配（否则 position=8 招标文件制作人会被错配到第 1 行项目最高决策人）。
        // position 缺失或超出字典范围时落到 EXTERNAL_ROLE_N（外部对接人），不抢占 14 个固定坑位，
        // 避免与按 position 映射的对接人撞车丢人。EXTERNAL_ROLE_N 是后端
        // TenderEvaluationCustomerInfoPolicy.isValidRoleKey 认可的合法 roleKey，前端
        // getCustomerInfoRoleLabel 也支持渲染为"外部对接人N"。
        // CO-526: 同一个已知 position 出现多个对接人时，第一个保留标准 roleKey，后续重复者落到
        // EXTERNAL_ROLE_N，但 POSITION 字段仍记录原始 position，与后端 CrmEvaluationMapper 保持同步，
        // 避免 tender_evaluation_customer_info 表的 uk_eval_role_info 唯一约束冲突。
        let externalRoleSeq = 0
        const seenPositions = new Set()
        customerInfos = contacts.map((c) => {
          const standardRole = CRM_POSITION_TO_ROLE[c.position]
          const isKnownPosition = standardRole !== undefined
          let roleKey = standardRole
          if (isKnownPosition) {
            if (seenPositions.has(c.position)) {
              roleKey = `EXTERNAL_ROLE_${++externalRoleSeq}`
            } else {
              seenPositions.add(c.position)
            }
          }
          if (!roleKey) {
            roleKey = `EXTERNAL_ROLE_${++externalRoleSeq}`
          }
          return {
            roleKey,
            NAME: c.name || '',
            CONTACT_INFO: c.phone || c.email || '',
            POSITION: isKnownPosition ? c.position : null,
          XIYU_CONTACT: c.ehsyProjectManager || '',
          CONTACT_METHOD: c.contactMethod || '',
          INFO_TENDENCY_BASIS: c.preferenceBasis || '',
          CONTACTED: c.contacted != null ? (c.contacted ? '是' : '否') : null,
          GUIDED_BID: c.guidedBidDocument != null ? (c.guidedBidDocument ? '是' : '否') : null,
          CAN_GET_KEY_INFO: c.getKeyInfo != null ? (c.getKeyInfo ? '是' : '否') : null,
          CAN_REMOVE_ADVERSE: c.deleteDisadvantage != null ? (c.deleteDisadvantage ? '是' : '否') : null,
          CAN_SYNC_EVAL: c.syncInfo != null ? (c.syncInfo ? '是' : '否') : null,
          TENDENCY: c.preferenceLevel || null,
          INFO_CLEAR_WINNER_BID: c.guaranteeWin ? 'true' : 'false',
          INFO_WIN_RATE_IMPACT: c.impactRate || null,
        }
        })
      } catch {
        ElMessage.error('请到 CRM 系统添加项目对接人')
        return
      }
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
      chanceGroupName: chance.groupName || '',          // CO-501: 商机集团名称（二次本地校验用）
      chanceTenderSubject: chance.tenderSubject || '',  // CO-501: 商机招标主体名称（二次本地校验用）
      evaluationData: {
        opportunityId: chance.code,
        basic: {
          riskAssessment: chance.riskPrediction || '',
          unfavorableItems: chance.bidDocumentDisadvantage || '',
          contractPeriodStart: chance.evaluationTime || '',
          contractPeriodEnd: '',
          plannedShortlistedCount: chance.planSupplierCount || null,
          mroOfficeFlowAmount: chance.ecommerceMroAmount || null,
          contingencyPlan: chance.backupPlan != null ? (chance.backupPlan ? '是' : '否') : '',
          processKnowledge: chance.managerUnderstandProcess || '',
          supportNotes: chance.remark || '',
          projectPlanGapFiles: parseGapFiles(chance.gapFile),
          customerRevenue: chance.customerRevenue || null,
        },
        customerInfos,
        // CO-312: 是否投标/弃标原因由项目负责人手动填写，不再从 CRM 商机详情带入。
        // 因此 evaluationData 不含 recommendation 段；后端 validator 在该段为 null 时
        // 跳过 shouldBid 校验，mapper 将 recommendation 置空（待决策），评估表 UI 显示
        // "请选择"占位，由项目负责人手动补填后走正常提交流程。
      },
    })
    showDialog.value = false
  }

  function resetSearch() { /* 手动输入模式已移除，无需重置 */ }

  return {
    showDialog, searching, loading, results, selectedId,
    selectedChance, totalCount, currentPage, pageSize, searchForm, linkedOpportunity,
    openSearch, doSearch, onSelect, confirmLink, resetSearch,
  }
}
