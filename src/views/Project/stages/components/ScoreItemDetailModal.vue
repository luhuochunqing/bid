<!--
  Input: item (评分项基础信息), result (实际打分结果), mode ('est' | 'actual')
  Output: 评分标准详细要素、依据、标书引用、缺失说明与优化建议弹窗
  Pos: src/views/Project/stages/components/ScoreItemDetailModal.vue
-->
<template>
  <div v-if="visible" class="detail-mask" :class="{ open: visible }" @click="handleClose">
    <div class="detail-modal" :class="{ open: visible }" @click.stop>
      <div class="detail-modal-header">
        <div class="detail-modal-title">{{ modalTitle }}</div>
        <button class="detail-modal-close" @click="handleClose">×</button>
      </div>
      <div class="detail-modal-body">
        <div class="detail-row">
          <div class="detail-label">详细评分项要素</div>
          <div class="detail-value">{{ item?.detail || '-' }}</div>
        </div>

        <div class="detail-row">
          <div class="detail-label">权重 / 评分类别</div>
          <div class="detail-value">
            权重 {{ item?.weight ?? 0 }} 分 ｜
            <span class="pill" :class="item?.scoreType === '客观项' ? 'info' : 'neutral'">{{ item?.scoreType || '客观项' }}</span>
          </div>
        </div>

        <div class="detail-row">
          <div class="detail-label">满足状态</div>
          <div class="detail-value status-cell" :class="item?.status || 'neutral'">
            {{ item?.statusText || '待确认' }}
          </div>
        </div>

        <div class="detail-row">
          <div class="detail-label">{{ mode === 'actual' ? '实际得分' : '预计得分' }}</div>
          <div class="detail-value" :class="scoreClass">{{ scoreDisplay }}</div>
        </div>

        <div class="detail-row">
          <div class="detail-label">{{ mode === 'actual' ? '评分依据' : '得分依据' }}</div>
          <div class="detail-quote">{{ basisText }}</div>
        </div>

        <div v-if="mode === 'actual' && result?.quote" class="detail-row">
          <div class="detail-label">标书引用</div>
          <div class="detail-quote">{{ result.quote }}</div>
        </div>

        <div v-if="mode === 'actual' && result?.missedReason" class="detail-row">
          <div class="detail-label">缺失说明</div>
          <div class="detail-miss">{{ result.missedReason }}</div>
        </div>

        <div v-if="suggestionText" class="detail-row">
          <div class="detail-label">修改建议</div>
          <div class="detail-suggestion">
            <div class="suggestion-title">
              💡 {{ item?.status === 'danger' ? '不满足项改进建议' : '待确认项补充建议' }}
            </div>
            {{ suggestionText }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  mode: { type: String, default: 'est' },
  item: { type: Object, default: () => ({}) },
  result: { type: Object, default: () => null },
})

const emit = defineEmits(['update:visible', 'close'])

function handleClose() {
  emit('update:visible', false)
  emit('close')
}

const modalTitle = computed(() => {
  const code = props.item?.code || ''
  const dim = props.item?.dim || ''
  const suffix = props.mode === 'actual' ? '实际评分详情' : '预计评分详情'
  return `${code} · ${dim} — ${suffix}`
})

const scoreDisplay = computed(() => {
  const weight = props.item?.weight ?? 0
  if (props.mode === 'actual') {
    if (!props.result) return '—'
    if (props.result.status === 'subjective' || props.result.actualScore === null || props.result.actualScore === undefined) return '待评审'
    return `${props.result.actualScore} / ${weight}`
  }
  return typeof props.item?.estScore === 'number' ? `${props.item.estScore} / ${weight}` : props.item?.estScore || '待评审'
})

const scoreClass = computed(() => {
  if (props.mode === 'actual') {
    if (!props.result) return 'na'
    if (props.result.status === 'subjective' || props.result.actualScore === null || props.result.actualScore === undefined) return 'subjective'
    if (props.result.actualScore === props.item?.weight) return 'full'
    return props.result.actualScore === 0 ? 'zero' : 'partial'
  }
  if (typeof props.item?.estScore === 'number') {
    if (props.item.estScore === props.item?.weight) return 'full'
    return props.item.estScore === 0 ? 'zero' : 'partial'
  }
  return 'subjective'
})

const basisText = computed(() => {
  if (props.mode === 'actual') return props.result?.evidence || props.item?.estBasis || '暂无评分依据'
  return props.item?.estBasis || '需评标专家根据标书描述人工评审'
})

const defaultSuggestions = {
  A1: '建议在标书中补充完整的总体架构设计图，明确组件划分和技术选型依据，确保架构方案与招标要求逐条对齐',
  A2: '建议补充微服务架构图、服务治理方案（注册中心、配置中心、网关）及高可用容灾设计说明',
  A3: '建议详细描述数据加密方案（含国密算法支持）、备份策略（全量+增量）及灾难恢复流程',
  A4: '建议补充 API 接口规范文档，提供第三方系统集成示例及开放能力清单',
  B1: '建议补充报价明细构成说明，确保报价完整覆盖软硬件、实施、运维全部费用，避免漏项',
  B2: '建议在标书中逐条明确响应招标文件规定的付款方式条款，避免模糊表述',
  C1: '建议补充项目实施计划甘特图，明确各里程碑节点、交付物及责任人',
  C3: '建议补充培训方案详细计划，包括培训内容、课时安排、考核方式及知识转移保障措施',
  D2: '建议尽快启动 CMMI 5 级认证评估流程，或在标书中提供更充分的替代方案说明（如 CMMI 3 级 + 研发管理体系证明）',
  E1: '建议在标书中补充本地化服务承诺，包括本地团队派驻计划、办公场地租赁证明或合作伙伴协议',
}

const suggestionText = computed(() => {
  if (props.result?.suggestion) return props.result.suggestion
  if (props.item?.suggestion) return props.item.suggestion
  if (['danger', 'warn', 'neutral'].includes(props.item?.status)) {
    return defaultSuggestions[props.item?.code] || (props.item?.status === 'danger' ? '建议针对此项要求补充证明材料或替代响应方案' : '建议在标书中详细补充相关阐述，降低评审不确定性')
  }
  return ''
})
</script>

<style scoped>
.detail-mask { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.45); opacity: 0; visibility: hidden; transition: opacity 0.25s, visibility 0.25s; z-index: 4000; }
.detail-mask.open { opacity: 1; visibility: visible; }
.detail-modal { position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%) scale(0.95); width: 560px; max-width: calc(100vw - 32px); max-height: 75vh; background: var(--bg-white); border-radius: var(--radius-md); box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2); z-index: 4001; opacity: 0; visibility: hidden; transition: all 0.25s; display: flex; flex-direction: column; }
.detail-modal.open { opacity: 1; visibility: visible; transform: translate(-50%, -50%) scale(1); }
.detail-modal-header { padding: 14px 20px; border-bottom: 1px solid var(--gray-100); display: flex; align-items: center; justify-content: space-between; flex-shrink: 0; }
.detail-modal-title { font-size: 15px; font-weight: 600; color: var(--text-primary); }
.detail-modal-close { width: 28px; height: 28px; border-radius: var(--radius-sm); color: var(--text-muted); font-size: 18px; display: inline-flex; align-items: center; justify-content: center; background: none; border: none; cursor: pointer; }
.detail-modal-close:hover { background: var(--bg-muted); color: var(--text-primary); }
.detail-modal-body { flex: 1; overflow-y: auto; padding: 16px 20px; font-size: 13px; color: var(--text-primary-ui); line-height: 1.7; }
.detail-row { margin-bottom: 14px; }
.detail-label { font-size: 12px; font-weight: 600; color: var(--brand-xiyu-logo-active); margin-bottom: 4px; padding-left: 8px; border-left: 3px solid var(--brand-xiyu-logo); line-height: 1.4; }
.detail-value { font-size: 13px; color: var(--text-primary-ui); }
.pill { font-size: 11px; padding: 1px 6px; border-radius: 3px; font-weight: 600; display: inline-block; }
.pill.info { background: var(--status-info-bg); color: var(--status-info-color); }
.pill.neutral { background: var(--status-neutral-bg); color: var(--status-neutral-color); }
.status-cell { font-size: 12px; font-weight: 500; }
.status-cell.ok { color: var(--status-success-color); }
.status-cell.warn { color: var(--status-warning-color); }
.status-cell.danger { color: var(--status-danger-color); }
.status-cell.neutral { color: var(--text-muted); }
.detail-value.full { color: var(--status-success-color); font-weight: 700; }
.detail-value.partial { color: var(--status-warning-color); font-weight: 700; }
.detail-value.zero { color: var(--status-danger-color); font-weight: 700; }
.detail-value.subjective { color: var(--text-muted); font-size: 12px; }
.detail-value.na { color: var(--text-lighter); }
.detail-quote { background: var(--status-success-bg-soft); border-left: 3px solid var(--status-success-border); padding: 8px 12px; margin-top: 6px; border-radius: 0 4px 4px 0; color: var(--gray-700); font-size: 12px; }
.detail-miss { background: var(--status-danger-bg-soft); border-left: 3px solid var(--status-danger-border); padding: 8px 12px; margin-top: 6px; border-radius: 0 4px 4px 0; color: var(--status-danger-color); font-size: 12px; }
.detail-suggestion { background: var(--status-info-bg); border-left: 3px solid var(--status-info-color); padding: 10px 12px; margin-top: 6px; border-radius: 0 4px 4px 0; color: var(--status-info-color); font-size: 12px; line-height: 1.7; }
.suggestion-title { font-weight: 600; margin-bottom: 4px; display: flex; align-items: center; gap: 4px; }
</style>
