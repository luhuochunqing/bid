<!--
  Input: candidateFacts, candidateTotalWeight, isAdoptingCandidates
  Output: 待人工复核的候选评分标准预览卡片与一键采纳动作
  Pos: src/views/Project/stages/components/ScoreCandidateReviewCard.vue
  注：从 ScoreParseDrawer.vue 拆出的候选评分项复核卡片，遵守 300 行预算。
-->
<template>
  <div class="candidate-review-card" data-test="score-parse-candidate-review">
    <div class="candidate-review-header">
      <div class="candidate-review-title">
        <span class="candidate-icon">💡</span>
        <span>检测到待人工复核的候选评分标准{{ selectedSchemeTitle ? `（${selectedSchemeTitle}）` : '' }}（本次提取 {{ candidateFacts.length }} 条记录；当前方案候选 {{ eligibleFacts.length }} 项，有效合计 {{ candidateTotalWeight }} 分）</span>
      </div>
      <div class="candidate-review-desc">
        已识别的评分项如下。请对照原文核对名称、分值和完整规则；未确认的项目不会自动生效。
      </div>
    </div>
    <div v-if="schemes.length > 1" class="scheme-selection">
      <label for="score-scheme-select">评分方案</label>
      <el-select id="score-scheme-select" :model-value="selectedSchemeId" placeholder="请选择一个评分方案"
        aria-label="选择评分方案" @change="$emit('scheme-change', $event)">
        <el-option v-for="(scheme, index) in schemes" :key="scheme.schemeId" :label="schemeLabel(scheme, index)" :value="scheme.schemeId">
          {{ schemeLabel(scheme, index) }}<template v-if="scheme.declaredTotal != null">（{{ scheme.declaredTotal }} 分）</template>
        </el-option>
      </el-select>
    </div>
    <el-alert
      v-if="adoptionStatus === 'SOURCE_ROWS_UNCOVERED' || adoptionStatus === 'CONFLICTING_TOTAL' || adoptionStatus === 'UNKNOWN_TOTAL' || schemeSelectionRequired || declaredTotalWeight == null || candidateTotalWeight !== declaredTotalWeight"
      type="warning"
      :closable="false"
      show-icon
      class="candidate-weight-alert"
      :title="adoptionStatus === 'SOURCE_ROWS_UNCOVERED'
        ? '原文评分表仍有未识别行，请核对后决定是否采纳。'
        : adoptionStatus === 'CONFLICTING_TOTAL'
        ? '所选评分方案的原文总分存在冲突，请核验后决定是否采纳。'
        : schemeSelectionRequired && !selectedSchemeId
        ? '文档包含多个评分方案，请先选择一个方案；不同方案不会合并计分。'
        : adoptionStatus === 'UNKNOWN_TOTAL'
        ? `该方案包含多个物理表格，原文未声明统一总分（当前候选合计 ${candidateTotalWeight} 分）；核对后可人工采纳。`
        : declaredTotalWeight == null
        ? '原文尚无可验证总分；请核对候选项后决定是否采纳。'
        : candidateTotalWeight > declaredTotalWeight
        ? `候选合计为 ${candidateTotalWeight} 分，超过原文声明的 ${declaredTotalWeight} 分，请核对后决定是否采纳。`
        : `候选合计为 ${candidateTotalWeight} 分，原文声明 ${declaredTotalWeight} 分，尚差 ${declaredTotalWeight - candidateTotalWeight} 分；核对后可人工采纳当前候选。`"
    />
    <el-alert v-if="adoptionStatus === 'SOURCE_ROWS_UNCOVERED' && declaredTotalWeight != null && candidateTotalWeight > declaredTotalWeight"
      type="warning" :closable="false" show-icon class="candidate-weight-alert"
      :title="`候选合计为 ${candidateTotalWeight} 分，超过原文声明的 ${declaredTotalWeight} 分，请核对后决定是否采纳。`" />
    <div v-if="schemeSelectionRequired && !selectedSchemeId" class="scheme-selection-hint">请选择方案以查看其候选总分与原文总分证据。</div>
    <div class="candidate-table-wrapper">
      <table class="candidate-table">
        <thead>
          <tr>
            <th style="width: 50px">序号</th>
            <th style="width: 180px">评分项</th>
            <th style="width: 85px">权重</th>
            <th style="width: 95px">评分类别</th>
            <th>标书原文与详情</th>
            <th style="width: 55px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(fact, idx) in eligibleFacts" :key="fact.factId || idx" :data-fact-id="fact.factId">
            <td class="col-center"><el-input v-model="fact.code" :disabled="readonly" size="small" :aria-label="`第${idx + 1}项序号`" /></td>
            <td class="col-dim">
              <el-input v-model="fact.dimension" :disabled="readonly" size="small" aria-label="评分维度" />
              <div v-if="invalidEffectiveFacts.has(fact)" class="validation-diff" data-test="invalid-dimension-hint">名称为空、纯序号或异常截断，请补全。</div>
            </td>
            <td class="col-weight"><el-input-number v-model="fact.weight" :disabled="readonly" :min="0" :precision="2" size="small" aria-label="分值" /></td>
            <td class="col-score-type">
              <span class="score-type-label">{{ scoreTypeLabel(manualScoreTypes.get(fact) || fact.scoreType) }}</span>
              <el-select :model-value="manualScoreTypes.get(fact)" @update:model-value="manualScoreTypes.set(fact, $event)"
                :disabled="readonly" clearable size="small" placeholder="按规则自动判断" class="score-type-select" aria-label="评分类别">
                <el-option label="客观项" value="OBJECTIVE" />
                <el-option label="主观项" value="SUBJECTIVE" />
              </el-select>
            </td>
            <td class="col-detail">
              <el-input v-model="fact.sourceText" :disabled="readonly" type="textarea" :autosize="{ minRows: 2, maxRows: 8 }" aria-label="完整规则" />
              <div v-if="fact.scopeStatus === 'SOURCE_UNVERIFIED' || fact.candidateEligible !== true" class="validation-diff">来源未核验，请人工核对后决定是否采纳。</div>
              <div v-if="!fact.sourceText || fact.sourceText.trim() === '—'" class="validation-diff">评分规则尚未填写，请核对后决定是否采纳。</div>
              <div v-for="evidence in readableEvidences(fact)" :key="evidence.quotedText" class="evidence-block">
                <span>{{ evidence.quotedText }}</span>
              </div>
              <div v-if="fact.validationStatus && fact.validationStatus !== 'AUTO_APPROVED'" class="validation-diff">
                <div v-for="message in validationMessages(fact)" :key="message">{{ message }}</div>
              </div>
            </td>
            <td class="col-center">
              <el-button
                type="danger"
                link
                size="small"
                :disabled="readonly"
                data-test="btn-exclude-fact"
                @click="toggleFactEligibility(fact, false)"
              >
                移出
              </el-button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <section v-if="auditFacts.length" class="audit-facts" aria-label="当前方案外或来源未验证事实">
      <h4>当前方案外或来源未验证事实（不计当前候选、不计当前总分）</h4>
      <ul>
        <li v-for="fact in auditFacts" :key="fact.factId" class="audit-fact-row">
          <div class="audit-fact-info">
            <strong>{{ fact.dimension || fact.code || '未命名评分事实' }}</strong>
            <span>{{ fact.weight > 0 ? (fact.candidateEligible === true ? '其他评分方案（未选择）' : scopeLabel(fact.scopeStatus)) : '非计分条款或分值待确认' }}</span>
            <span v-if="fact.exclusionReason && fact.exclusionReason !== 'SCORE_WEIGHT_NOT_CONFIRMED'">：{{ exclusionLabel(fact.exclusionReason) }}</span>
            <small v-if="fact.sourceText"> {{ fact.sourceText }}</small>
          </div>
          <el-button
            v-if="!readonly"
            type="primary"
            link
            size="small"
            data-test="btn-include-fact"
            @click="toggleFactEligibility(fact, true)"
          >
            + 纳入本方案
          </el-button>
        </li>
      </ul>
    </section>
    <div class="candidate-review-actions">
      <el-alert v-if="hasUnresolvedRelationship" type="warning" :closable="false"
        title="父子评分关系未确认，暂按明细计分；请核对后决定是否采纳。" />
      <el-alert v-if="hasExplicitAlternative" type="warning" :closable="false"
        title="已选择备用提取结果，请核对是否与其他候选项重复计分。" />
      <el-alert v-if="invalidEffectiveFacts.size > 0" type="error" :closable="false" data-test="invalid-dimension-alert"
        title="存在空名称、纯序号或异常截断的评分项，请补全具体名称后再采纳。" />
      <el-button
        type="primary"
        :loading="isAdoptingCandidates"
        :disabled="readonly || candidateTotalWeight <= 0 || (schemeSelectionRequired && !selectedSchemeId) || invalidEffectiveFacts.size > 0"
        data-test="btn-adopt-candidates"
        @click="emit('adopt', candidateFacts.map(fact => ({ ...fact, scoreType: manualScoreTypes.get(fact) || undefined })))"
      >
        ⚡ 一键采纳为正式评分标准（{{ candidateTotalWeight }}分）
      </el-button>
      <el-button :disabled="readonly || isAdoptingCandidates || reparseDisabled" @click="$emit('reparse')">
        🔄 重新解析
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { isManualScoreCandidate } from '@/composables/projectDetail/manualScoreCandidate.js'
import { isInvalidCandidateDimension } from './ScoreExtractionPanel.vue'
import {
  exclusionLabel,
  readableEvidences,
  schemeLabel,
  scopeLabel,
  scoreTypeLabel,
  validationMessages,
} from './scoreCandidateReviewPresentation.js'

const props = defineProps({
  readonly: { type: Boolean, default: false },
  candidateFacts: { type: Array, default: () => [] },
  schemes: { type: Array, default: () => [] },
  selectedSchemeId: { type: String, default: null },
  schemeSelectionRequired: { type: Boolean, default: false },
  candidateTotalWeight: { type: Number, default: 0 },
  declaredTotalWeight: { type: Number, default: 100 },
  adoptionStatus: { type: String, default: 'DECLARED_TOTAL' },
  isAdoptingCandidates: { type: Boolean, default: false },
  reparseDisabled: { type: Boolean, default: false },
})

const selectedSchemeTitle = computed(() => {
  const index = props.schemes.findIndex((scheme) => scheme.schemeId === props.selectedSchemeId)
  return index < 0 ? '' : schemeLabel(props.schemes[index], index)
})
const eligibleFacts = computed(() => props.candidateFacts.filter((fact) =>
  isManualScoreCandidate(fact, props.selectedSchemeId, props.schemes.length)))
const hasUnresolvedRelationship = computed(() => eligibleFacts.value.some((parent) =>
  String(parent.childAggregation || '').toUpperCase() === 'UNKNOWN'
  && eligibleFacts.value.some((child) => child.parentFactId === parent.factId)))
const hasExplicitAlternative = computed(() => eligibleFacts.value.some((fact) => fact.manualIncluded
  && fact.evidences?.some((evidence) => evidence.kind === 'ALTERNATIVE_EXTRACTION_SET_NOT_SELECTED')))
const auditFacts = computed(() => props.candidateFacts.filter((fact) =>
  !isManualScoreCandidate(fact, props.selectedSchemeId, props.schemes.length)))
const invalidEffectiveFacts = computed(() => {
  const ids = new Set(eligibleFacts.value.map(fact => fact.factId))
  const childrenByParent = new Map()
  const pending = []
  for (const fact of eligibleFacts.value) {
    const parentId = fact.parentFactId
    if (!parentId || !ids.has(parentId)) {
      pending.push(fact)
    } else {
      const children = childrenByParent.get(parentId) || []
      children.push(fact)
      childrenByParent.set(parentId, children)
    }
  }

  const invalid = new Set()
  while (pending.length) {
    const fact = pending.pop()
    const children = childrenByParent.get(fact.factId)
    const relation = String(fact.childAggregation || '').trim().toUpperCase()
    const parentIsUnit = ['SHARED_CAP', 'BEST_OF', 'MUTUALLY_EXCLUSIVE'].includes(relation)
    // Match legacy adoption: SUM and UNKNOWN traverse selected children, caps publish the parent.
    if (children?.length && !parentIsUnit) {
      pending.push(...children)
    } else if (isInvalidCandidateDimension(fact.dimension) && contributesScore(fact)) {
      invalid.add(fact)
    }
  }
  return invalid
})

function contributesScore(fact) {
  const weight = Number(fact.weight)
  return weight > 0 || (fact.weight != null && String(fact.weight).trim() !== '' && weight === 0
    && fact.candidateEligible === true && fact.scopeStatus === 'IN_SCHEME' && fact.sourceText?.trim()
    && fact.evidences?.some(evidence => evidence.kind === 'SOURCE_VERIFIED_ZERO_DEDUCTION'
      && evidence.quotedText === fact.sourceText))
}

const emit = defineEmits(['adopt', 'reparse', 'scheme-change', 'fact-change'])
const manualScoreTypes = reactive(new WeakMap())

function toggleFactEligibility(fact, eligible) {
  if (props.readonly) return
  if (eligible && props.schemeSelectionRequired && !props.selectedSchemeId) {
    ElMessage.warning('请先在上方选择一个评分方案，再纳入评分项')
    return
  }
  fact.manualExcluded = !eligible
  fact.manualIncluded = eligible
  fact.candidateEligible = eligible
  if (eligible && props.selectedSchemeId) {
    fact.schemeId = props.selectedSchemeId
  }
  emit('fact-change', props.candidateFacts)
}
</script>

<style scoped>
.candidate-review-card {
  background: var(--bg-white);
  border: 1px solid var(--status-warning-color, #fa8c16);
  border-radius: var(--radius-md);
  padding: 16px;
  margin-bottom: 12px;
}
.candidate-review-header { margin-bottom: 12px; }
.candidate-review-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--status-warning-color, #d46b08);
  display: flex;
  align-items: center;
  gap: 6px;
}
.candidate-review-desc { font-size: 12px; color: var(--text-muted); margin-top: 4px; }
.candidate-weight-alert { margin-bottom: 12px; }
.scheme-selection { display: flex; align-items: center; gap: 10px; margin: 10px 0; }
.scheme-selection .el-select { width: min(420px, 100%); }
.scheme-selection-hint { margin: 10px 0; color: var(--text-muted); font-size: 12px; }
.candidate-table-wrapper {
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--border-base, #ebeef5);
  border-radius: var(--radius-sm);
  margin-bottom: 12px;
}
.candidate-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.candidate-table th, .candidate-table td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--border-base, #ebeef5);
  text-align: left;
}
.candidate-table th { background: var(--bg-muted, #f5f7fa); color: var(--text-primary-ui, #303133); font-weight: 600; }
.candidate-table .col-center { text-align: center; }
.candidate-table .col-dim { font-weight: 600; color: var(--text-primary-ui, #303133); }
.candidate-table .col-weight { font-weight: 600; color: var(--brand-xiyu-logo, #e02424); }
.candidate-table .col-score-type { color: var(--text-primary-ui, #303133); text-align: center; }
.score-type-label { display: block; margin-bottom: 6px; font-weight: 600; }
.candidate-table .col-detail { color: var(--text-muted); max-width: 360px; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.55; }
.evidence-block, .validation-diff { margin-top: 6px; padding: 6px 8px; border-radius: var(--radius-sm); background: var(--bg-muted, #f5f7fa); }
.score-type-select { margin-top: 6px; width: 100%; }
.evidence-block small { display: block; margin-top: 3px; }
.validation-diff { color: var(--status-danger-color, #c45656); background: var(--status-danger-bg, #fef0f0); }
.audit-fact-row { display: flex; justify-content: space-between; align-items: center; padding: 4px 0; border-bottom: 1px dashed var(--border-base, #ebeef5); }
.audit-fact-info { flex: 1; }
.candidate-review-actions { display: flex; gap: 10px; align-items: center; margin-top: 12px; }
</style>
