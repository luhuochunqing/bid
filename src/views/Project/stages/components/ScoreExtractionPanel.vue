<!-- Input: frozen snapshot and coherent active items; Output: all candidates, source details and explicit replacement selection -->
<template>
  <section class="extraction-panel" aria-label="最新提取结果">
    <h3>最新提取结果 · {{ extractionTaskLabel(snapshot.taskStatus) }}</h3>
    <p>任务 {{ snapshot.taskId }} · 提取时间 {{ formatTime(snapshot.extractedAt) || '尚未完成' }}</p>
    <p>采纳与原文核验分别记录。未采纳、未核验、不可计算的候选均保留。</p>
    <p v-if="snapshot.payloadVersion === 0">历史提取缺少完整版本记录，来源未核验。需手动重新解析才能补齐完整来源和候选状态；当前生效规则仍保留。</p>
    <p v-for="(reason, index) in snapshot.sourceDiagnostics || []" :key="index" class="diagnostic">{{ extractionReason(reason) }}</p>
    <label v-if="snapshot.schemes?.length" class="scheme-select">评分方案
      <select v-model="schemeId" aria-label="评分方案">
        <option value="" disabled>请选择一个评分方案</option>
        <option v-for="scheme in snapshot.schemes" :key="scheme.schemeId" :value="scheme.schemeId">{{ scheme.title || '评分方案' }}</option>
      </select>
    </label>
    <div v-if="scheme" class="metrics">
      <span>提取：{{ scheme.extractedCount }} 项 / {{ scoreNumber(scheme.extractedKnownTotal) }} 分{{ scheme.extractedTotalComplete ? '' : '（已知部分）' }}</span>
      <span>可计算：{{ scheme.computableCount }} 项 / {{ scoreNumber(scheme.computableTotal) }} 分</span>
      <span>声明总分：{{ scheme.declaredTotalStatus === 'KNOWN' && scheme.declaredTotal != null ? scheme.declaredTotal + ' 分' : scheme.declaredTotalStatus === 'CONFLICT' ? '存在冲突' : '未知' }}</span>
    </div>
    <div class="active-identity">
      <strong>当前生效：{{ activeItems.length }} 项 / {{ activeTotal == null ? '待确认' : activeTotal + ' 分' }}</strong>
      <p>来源任务 {{ snapshot.active?.taskId || '历史版本，来源待确认' }} · 生效时间 {{ formatTime(snapshot.active?.activatedAt) || '未知' }}</p>
      <p v-if="snapshot.active?.hasManualEdits">当前版本有人工选择或修改保护；切换将完整替换当前计分集合。</p>
    </div>
    <article v-for="item in displayedItems" :key="item.itemId" class="candidate" :data-item-id="item.itemId">
      <label class="candidate-title">
        <input v-model="selectedIds" type="checkbox" :value="item.itemId" :disabled="blocked || busy || !selectable(item)" :aria-label="'选择' + item.name" />
        {{ item.category }} · {{ item.name }}
      </label>
      <p>{{ item.computable ? '可计算' : '不可计算' }} · {{ adopted.has(item.itemId) ? '已采纳' : '未采纳' }} · {{ item.validationStatus === 'AUTO_APPROVED' ? '原文已核验' : '原文未核验' }}</p>
      <p>{{ item.fieldSources?.rawMaximum === 'SOURCE_EVIDENCE' ? '原文依据最大分' : '候选最大分（模型提取，未核验）' }}：{{ scoreNumber(item.rawMaximum) }}{{ item.scoreUnit === 'PERCENTAGE' ? '%' : ' 分' }} · 有效分：{{ scoreNumber(item.effectiveMaximum) }} · 方案比例：{{ item.schemeRatio == null ? '未提供' : item.schemeRatio }}</p>
      <p v-if="item.schemeId !== schemeId">其他方案或方案归属待确认，本次不计分</p>
      <p v-for="(reason, index) in item.diagnostics || []" :key="index" class="diagnostic">{{ extractionReason(reason) }}</p>
      <details>
        <summary>完整规则、材料要求与来源</summary>
        <h4>{{ item.fieldSources?.ruleText === 'MODEL_CLAIM' ? '模型规则说明（本项完整原文边界待核对）' : '本项规则原文' }}</h4>
        <p class="full-text">{{ item.ruleText || '规则待确认' }}</p>
        <h4>材料要求</h4>
        <p v-if="!item.materialRequirements?.length">材料要求未单独结构化，请查看完整规则和来源片段中的材料条款。</p>
        <p v-for="(material, index) in item.materialRequirements || []" :key="index" class="full-text">{{ material }}</p>
        <h4>来源片段（可能包含共享内容）</h4>
        <div v-for="(span, index) in item.fact?.sourceMetadata?.sourceSpans || []" :key="index">
          <p>{{ span.documentId || item.fileId }} · {{ span.pageNumber != null ? '第 ' + span.pageNumber + ' 页' : '' }} {{ span.locator || span.sourceId }}</p>
          <blockquote class="full-text">{{ span.rawText }}</blockquote>
        </div>
        <p v-if="!item.fact?.sourceMetadata?.sourceSpans?.length">尚无已定位原文片段</p>
        <h4>模型解释（独立于原文）</h4>
        <p class="full-text">{{ item.fact?.sourceMetadata?.modelRuleText || '未提供模型说明' }}</p>
        <p v-if="item.fact?.sourceMetadata?.modelQuotedText" class="full-text">模型引用：{{ item.fact.sourceMetadata.modelQuotedText }}</p>
      </details>
    </article>
    <p v-if="!snapshot.items?.length">本次尚无候选项；当前生效结果仍保留。</p>
    <div class="replacement">
      <h4>切换差异 · 完整替换</h4>
      <p>将当前 {{ activeItems.length }} 项 / {{ scoreNumber(activeTotal) }} 分替换为所选 {{ selected.length }} 项 / {{ selected.length ? selectedTotal : '待确认' }} 分。未选择的当前项将移出计分集合，已有实际评分将失效。</p>
      <details><summary>核对替换前后的全部规则</summary>
        <h4>当前生效</h4>
        <p v-for="item in activeItems" :key="item.id" class="full-text">{{ item.dim }} · {{ scoreNumber(item.weight) }} 分\n{{ item.detail || item.req }}</p>
        <h4>切换后</h4>
        <p v-for="item in selected" :key="item.itemId" class="full-text">{{ item.name }} · {{ scoreNumber(item.effectiveMaximum) }} 分\n{{ item.ruleText }}</p>
      </details>
      <label>人工切换原因（仅记录选择理由，不修改原文）
        <textarea v-model="reason" rows="2" aria-label="人工切换原因" :disabled="busy || blocked" />
      </label>
      <button type="button" data-test="activate-extraction" :disabled="busy || blocked || !selected.length || !reason.trim()" @click="$emit('activate', { schemeId, selectedItemIds: [...selectedIds], reason })">{{ busy ? '正在切换…' : '确认完整替换为所选项' }}</button>
    </div>
  </section>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { formatTime } from '@/composables/projectDetail/scoreParseTask.js'
import { scoreNumber, extractionReason, extractionTaskLabel } from '@/composables/projectDetail/scoreExtractionPresentation.js'
const props = defineProps({ snapshot: { type: Object, required: true }, activeItems: { type: Array, default: () => [] }, activeTotal: { type: Number, default: null }, blocked: Boolean, busy: Boolean })
defineEmits(['activate'])
const schemeId = ref(''), selectedIds = ref([]), reason = ref('')
watch(() => props.snapshot, snapshot => {
  schemeId.value = snapshot.schemes?.length === 1 ? snapshot.schemes[0].schemeId : ''
  selectedIds.value = []; reason.value = ''
}, { immediate: true })
watch(schemeId, () => { selectedIds.value = [] })
const scheme = computed(() => props.snapshot.schemes?.find(s => s.schemeId === schemeId.value))
const adopted = computed(() => new Set(props.snapshot.adoption?.adoptedItemIds || []))
const displayedItems = computed(() => [...(props.snapshot.items || [])].sort((a, b) => Number(b.schemeId === schemeId.value) - Number(a.schemeId === schemeId.value)))
const selectable = item => Boolean(schemeId.value) && item.schemeId === schemeId.value && item.computable === true
const selected = computed(() => displayedItems.value.filter(item => selectedIds.value.includes(item.itemId) && selectable(item)))
const selectedTotal = computed(() => Math.round(selected.value.reduce((sum, item) => sum + item.effectiveMaximum, 0) * 100) / 100)
</script>
<style scoped>
.extraction-panel { margin-bottom: var(--space-lg); color: var(--text-primary); }
h3, h4 { margin: var(--space-sm) 0; }
p { margin: var(--space-xs) 0; line-height: 1.6; }
.metrics { display: flex; gap: var(--space-md); flex-wrap: wrap; padding: var(--space-md); background: var(--bg-muted); }
.active-identity, .replacement { margin-top: var(--space-md); padding: var(--space-md); border: 1px solid var(--border-base); border-radius: var(--radius-sm); }
.candidate { margin-top: var(--space-sm); padding: var(--space-md); border: 1px solid var(--border-base); border-radius: var(--radius-sm); }
.candidate-title { font-weight: 600; }
.diagnostic { color: var(--status-warning-color); }
.full-text { white-space: pre-wrap; overflow-wrap: anywhere; }
summary, button, select, input { cursor: pointer; }
blockquote { border-left: 3px solid var(--border-base); margin: var(--space-sm); padding-left: var(--space-sm); }
textarea { display: block; width: 100%; box-sizing: border-box; margin: var(--space-sm) 0; padding: var(--space-sm); border: 1px solid var(--border-base); color: var(--text-primary); background: var(--bg-white); }
button, select { border: 1px solid var(--border-base); border-radius: var(--radius-sm); padding: var(--space-sm); background: var(--bg-white); color: var(--brand-xiyu-logo); }
button:disabled, input:disabled { cursor: not-allowed; opacity: 0.6; }
</style>
