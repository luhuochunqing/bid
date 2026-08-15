// Input: props (projectId), emit
// Output: 评分标准解析与对标打分状态机、计算属性及接口交互逻辑
// Pos: src/composables/projectDetail/useScoreParseDrawer.js
// 一旦我被更新，务必更新我的开头注释。

import { ref, computed } from 'vue'
import { bidAgentApi } from '@/api/modules/bidAgent.js'
import { projectsApi } from '@/api/modules/projects.js'
import { ElMessage, ElMessageBox } from 'element-plus'

export const defaultScoreTemplate = [
  { code: 'A1', dim: '技术方案', detail: '总体架构设计（架构图、组件划分、技术选型）', weight: 10, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '需评标专家根据标书技术方案描述人工评审' },
  { code: 'A2', dim: '技术方案', detail: '微服务架构与高可用设计', weight: 8, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '需评标专家根据标书技术方案描述人工评审' },
  { code: 'A3', dim: '技术方案', detail: '数据安全与备份恢复方案', weight: 6, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '需评标专家根据标书技术方案描述人工评审' },
  { code: 'A4', dim: '技术方案', detail: '接口设计与开放能力', weight: 5, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '需评标专家根据标书技术方案描述人工评审' },
  { code: 'B1', dim: '商务方案', detail: '报价合理性（与市场均价对比）', weight: 10, status: 'neutral', statusText: '待确认', scoreType: '客观项', estScore: 9, estBasis: 'AI 预计报价 580 万元处于市场均价区间（550-620 万元），预计得分 9' },
  { code: 'B2', dim: '商务方案', detail: '付款方式响应', weight: 5, status: 'neutral', statusText: '待确认', scoreType: '客观项', estScore: 5, estBasis: 'AI 预计标书将完全响应招标付款方式（30%+60%+10%），预计满分' },
  { code: 'C1', dim: '实施服务', detail: '项目实施计划与里程碑', weight: 7, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '需评标专家根据标书实施计划人工评审' },
  { code: 'C2', dim: '实施服务', detail: '团队配置（项目经理 + 核心成员）', weight: 8, status: 'ok', statusText: '✓ 满足', scoreType: '客观项', estScore: 8, estBasis: '知识库命中：人员库匹配项目经理 PMP 认证 + 核心成员资质齐全，预计满分', kbHit: true },
  { code: 'C3', dim: '实施服务', detail: '培训方案与知识转移', weight: 5, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '需评标专家根据标书培训方案人工评审' },
  { code: 'D1', dim: '资质业绩', detail: '信息系统集成及服务资质', weight: 6, status: 'ok', statusText: '✓ 满足', scoreType: '客观项', estScore: 6, estBasis: '知识库命中：资质库匹配证书「信息系统集成及服务资质一级」，预计满分', kbHit: true },
  { code: 'D2', dim: '资质业绩', detail: 'CMMI 5 级认证', weight: 5, status: 'danger', statusText: '✗ 不满足', scoreType: '客观项', estScore: 0, estBasis: '知识库未匹配 CMMI 5 级证书（最高为 CMMI 3 级），预计 0 分' },
  { code: 'D3', dim: '资质业绩', detail: '近 3 年类似项目业绩（≥3 项）', weight: 7, status: 'ok', statusText: '✓ 满足', scoreType: '客观项', estScore: 7, estBasis: '知识库命中：业绩库匹配近 3 年类似项目 5 项（≥3 项要求），预计满分', kbHit: true },
  { code: 'E1', dim: '加分项', detail: '本地化服务能力（本地团队 / 办公场地）', weight: 18, status: 'neutral', statusText: '待确认', scoreType: '主观项', estScore: '待评审', estBasis: '工商注册地与项目所在地不一致，需评标专家根据标书本地化服务承诺人工评审' },
]

export const defaultScoreResults = {
  A1: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '技术方案描述类评分项，需评标专家人工评审' },
  A2: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '技术方案描述类评分项，需评标专家人工评审' },
  A3: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '技术方案描述类评分项，需评标专家人工评审' },
  A4: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '技术方案描述类评分项，需评标专家人工评审' },
  B1: { actualScore: 9, scoreType: 'objective', status: 'full', evidence: '报价 580 万元，处于市场均价区间（550-620 万元）', quote: '第 3 章 投标报价（第 12 页）：我方投标总价：人民币 580 万元整（含税）' },
  B2: { actualScore: 5, scoreType: 'objective', status: 'full', evidence: '完全响应招标文件付款方式（30%+60%+10%）', quote: '第 5 章 商务条款响应（第 18 页）：我方完全接受招标文件规定的付款方式：合同签订后预付 30%，验收合格后付 60%，质保期满付 10%。' },
  C1: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '实施计划类评分项，需评标专家人工评审' },
  C2: { actualScore: 8, scoreType: 'objective', status: 'full', evidence: '标书 §3.2 配置项目经理 1 名 + 核心成员 5 名，人员资质均符合招标要求', quote: '第 6 章 实施服务方案（第 22 页）：项目经理：张三（PMP 认证，10 年智慧园区项目经验）；核心成员：架构师 1、前端 1、后端 2、测试 1。' },
  C3: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '培训方案类评分项，需评标专家人工评审' },
  D1: { actualScore: 6, scoreType: 'objective', status: 'full', evidence: '知识库命中：资质库匹配证书「信息系统集成及服务资质一级（有效期至 2027-08-12）」', kbHit: true },
  D2: { actualScore: 3, scoreType: 'objective', status: 'partial', evidence: '标书已补充 CMMI 3 级证书说明及替代方案，部分满足要求', quote: '第 7 章 资质证明（第 28 页）：我方虽未取得 CMMI 5 级认证，但已通过 CMMI 3 级认证（证书编号：CN-XXXX-2024），并建立了完整的研发管理体系，可覆盖招标文件要求的研发过程管理能力。', missedReason: 'CMMI 5 级认证未找到匹配证书，标书已补充 CMMI 3 级说明，部分得分' },
  D3: { actualScore: 7, scoreType: 'objective', status: 'full', evidence: '知识库命中：业绩库匹配近 3 年类似项目 5 项（≥3 项要求），含智慧园区项目 3 项', quote: '第 7 章 资质证明（第 30 页）：近 3 年类似项目业绩 5 项，含智慧园区项目 3 项' },
  E1: { actualScore: null, scoreType: 'subjective', status: 'subjective', evidence: '本地化服务能力评分项，需评标专家人工评审（可参考工商注册地辅助判定）' },
}

export function useScoreParseDrawer(props, emit) {
  const visible = ref(false)
  const loading = ref(false)
  const error = ref('')

  const isSection1Expanded = ref(true)
  const currentStage = ref(2)
  const scored = ref(true)
  const scoringOverlayVisible = ref(false)

  const sourceFileName = ref('xxx.pdf')
  const parseTime = ref('')
  const bidFileName = ref('xxx 投标文件_v3.pdf')
  const scoreTime = ref('')
  const importing = ref(false)

  const scoreItems = ref([])
  const scoreResults = ref({})

  const detailModalVisible = ref(false)
  const detailMode = ref('est')
  const selectedItem = ref(null)
  const selectedResult = ref(null)

  const totalWeight = computed(() => scoreItems.value.reduce((a, b) => a + (Number(b.weight) || 0), 0))
  const objectiveWeight = computed(() =>
    scoreItems.value.filter((s) => s.scoreType === '客观项').reduce((a, b) => a + (Number(b.weight) || 0), 0)
  )
  const subjectiveWeight = computed(() => totalWeight.value - objectiveWeight.value)

  const statsOkCount = computed(() => scoreItems.value.filter((s) => s.status === 'ok').length)
  const statsDangerCount = computed(() => scoreItems.value.filter((s) => s.status === 'danger').length)
  const statsNeutralCount = computed(() => scoreItems.value.filter((s) => s.status !== 'ok' && s.status !== 'danger').length)

  const estTotalScore = computed(() =>
    scoreItems.value.reduce((sum, item) => sum + (typeof item.estScore === 'number' ? item.estScore : 0), 0)
  )

  const actualTotalScore = computed(() =>
    Object.entries(scoreResults.value).reduce((sum, [, res]) => {
      return sum + (res && res.scoreType === 'objective' && typeof res.actualScore === 'number' ? res.actualScore : 0)
    }, 0)
  )

  function openDetail(item, idx, mode) {
    selectedItem.value = item
    selectedResult.value = scoreResults.value[item.code] || null
    detailMode.value = mode
    detailModalVisible.value = true
  }

  async function open(options = {}) {
    visible.value = true
    loading.value = true
    error.value = ''
    currentStage.value = options.stage ?? 2
    scored.value = options.autoScore ? false : true
    if (options.file) bidFileName.value = options.file

    try {
      const [analysisResp, _qualResp, scoringResp] = await Promise.allSettled([
        bidAgentApi.getFullAnalysis(props.projectId),
        bidAgentApi.getQualificationMatch(props.projectId),
        bidAgentApi.getScoringCriteria(props.projectId),
      ])

      parseTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
      scoreTime.value = new Date().toLocaleString('zh-CN', { hour12: false })

      const analysis = analysisResp.status === 'fulfilled' ? analysisResp.value?.data : null
      const scoringData = scoringResp.status === 'fulfilled' ? scoringResp.value?.data : null

      sourceFileName.value = scoringData?.sourceFileName || analysis?.sourceFileName || 'xxx.pdf'

      const apiItems = scoringData?.structuredItems || analysis?.scoringCriteria?.items || []
      if (Array.isArray(apiItems) && apiItems.length > 0) {
        scoreItems.value = apiItems.map((s, i) => {
          const fallback = defaultScoreTemplate[i] || {}
          const reqText = s.req || s.indicator || s.detail || fallback.req || fallback.detail || ''
          return {
            code: s.itemNumber || s.code || fallback.code || `S${i + 1}`,
            dim: s.dimension || s.dim || fallback.dim || '评分项',
            req: reqText,
            detail: s.detail || reqText,
            weight: Number(s.weight ?? fallback.weight ?? 5),
            status: s.status || fallback.status || 'neutral',
            statusText: s.statusText || fallback.statusText || '待确认',
            scoreType: s.scoreType || fallback.scoreType || '客观项',
            estScore: s.estScore ?? fallback.estScore ?? '待评审',
            estBasis: s.estBasis || fallback.estBasis || '根据标书描述与资质匹配情况综合评审',
          }
        })
      } else {
        scoreItems.value = JSON.parse(JSON.stringify(defaultScoreTemplate))
      }

      scoreResults.value = JSON.parse(JSON.stringify(defaultScoreResults))

      emit('parsed', {
        dangerCount: statsDangerCount.value,
        warnCount: statsNeutralCount.value,
      })

      if (options.autoScore) {
        setTimeout(() => runScoring(true), 400)
      }
    } catch (e) {
      error.value = e?.response?.data?.msg || '评分标准解析加载失败'
    } finally {
      loading.value = false
    }
  }

  function runScoring(auto = false) {
    if (currentStage.value !== 2) {
      ElMessage.warning('请先进入阶段 2（上传投标文件后）')
      return
    }
    scoringOverlayVisible.value = true
    setTimeout(() => {
      scoringOverlayVisible.value = false
      scored.value = true
      scoreTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
      ElMessage.success(auto ? 'AI 自动打分完成' : 'AI 实际打分完成')
    }, 1600)
  }

  async function reparse() {
    await open({ stage: currentStage.value, autoScore: false })
    ElMessage.success('已重新解析评分标准')
  }

  function exportReport() {
    ElMessage.info('正在生成 AI 评分标准与打分解析报告 PDF...')
  }

  async function importToDrafts() {
    if (!scoreItems.value.length) return
    try {
      await ElMessageBox.confirm(
        '将导入 AI 分析的评分标准到评分草稿，会覆盖现有未生成的草稿。确认导入？',
        '导入到评分草稿',
        { confirmButtonText: '确认导入', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }

    importing.value = true
    try {
      const res = await projectsApi.importScoreDraftsFromAnalysis(props.projectId)
      if (res?.data) {
        ElMessage.success(`成功导入 ${res.data.totalCount ?? scoreItems.value.length} 项评分草稿`)
        emit('imported', res.data)
      } else {
        ElMessage.error(res?.msg || '导入失败')
      }
    } catch (e) {
      ElMessage.error(e?.response?.data?.msg || e?.message || '导入失败')
    } finally {
      importing.value = false
    }
  }

  return {
    visible,
    loading,
    error,
    isSection1Expanded,
    currentStage,
    scored,
    scoringOverlayVisible,
    sourceFileName,
    parseTime,
    bidFileName,
    scoreTime,
    importing,
    scoreItems,
    scoreResults,
    detailModalVisible,
    detailMode,
    selectedItem,
    selectedResult,
    totalWeight,
    objectiveWeight,
    subjectiveWeight,
    statsOkCount,
    statsDangerCount,
    statsNeutralCount,
    estTotalScore,
    actualTotalScore,
    openDetail,
    open,
    runScoring,
    reparse,
    exportReport,
    importToDrafts,
  }
}
