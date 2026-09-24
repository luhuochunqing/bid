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
        <span>检测到待人工复核的候选评分标准（共 {{ candidateFacts.length }} 项，合计 {{ candidateTotalWeight }} 分）</span>
      </div>
      <div class="candidate-review-desc">
        文档中包含多个表格或参考模板，系统未自动入库。请核对下方候选细则列表，确认无误后可一键采纳：
      </div>
    </div>
    <el-alert
      v-if="adoptionStatus === 'CONFLICTING_TOTAL' || declaredTotalWeight == null || candidateTotalWeight !== declaredTotalWeight"
      type="warning"
      :closable="false"
      show-icon
      class="candidate-weight-alert"
      :title="adoptionStatus === 'CONFLICTING_TOTAL'
        ? '招标文件存在多个冲突的评分表，请人工核对分表后再采纳。'
        : declaredTotalWeight == null
        ? '原文未提供单表总分，确认候选项无误后将以候选合计作为正式总分。'
        : `候选评分项合计为 ${candidateTotalWeight} 分，应与原文声明的 ${declaredTotalWeight} 分闭合。`"
    />
    <div class="candidate-table-wrapper">
      <table class="candidate-table">
        <thead>
          <tr>
            <th style="width: 60px">序号</th>
            <th style="width: 200px">评分项</th>
            <th style="width: 90px">权重</th>
            <th style="width: 100px">评分类别</th>
            <th>标书原文与详情</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(fact, idx) in candidateFacts" :key="fact.factId || idx">
            <td class="col-center"><el-input v-model="fact.code" size="small" :aria-label="`第${idx + 1}项序号`" /></td>
            <td class="col-dim">
              <el-input v-model="fact.dimension" size="small" aria-label="评分维度" />
            </td>
            <td class="col-weight"><el-input-number v-model="fact.weight" :min="0" :precision="2" size="small" aria-label="分值" /></td>
            <td class="col-score-type">
              <span class="score-type-label">{{ scoreTypeLabel(fact.scoreType) }}</span>
              <el-select v-model="fact.scoreType" size="small" placeholder="主客观" class="score-type-select" aria-label="评分类别">
                <el-option label="客观项" value="OBJECTIVE" />
                <el-option label="主观项" value="SUBJECTIVE" />
              </el-select>
            </td>
            <td class="col-detail">
              <el-input v-model="fact.sourceText" type="textarea" :autosize="{ minRows: 3, maxRows: 12 }" aria-label="完整规则" />
              <el-input v-model="fact.parentFactId" size="small" placeholder="父项 factId（无则留空）" aria-label="父项" class="parent-input" />
              <div v-if="sourceCellIdsOf(fact).length" class="evidence-location">
                单元格：{{ sourceCellIdsOf(fact).join(', ') }}
              </div>
              <div v-for="evidence in (fact.evidences || [])" :key="evidence.id || evidence.cellId" class="evidence-block">
                <span>{{ evidence.quotedText }}</span>
                <small>{{ evidence.explanation }}</small>
              </div>
              <div v-if="fact.validationStatus && fact.validationStatus !== 'AUTO_APPROVED'" class="validation-diff">
                {{ fact.validationStatus }}：{{ fact.validationMessage || fact.fallbackReason || '请按原文修正后重新校验' }}
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="candidate-review-actions">
      <el-button
        type="primary"
        :loading="isAdoptingCandidates"
        :disabled="candidateTotalWeight <= 0 || adoptionStatus === 'CONFLICTING_TOTAL' || (declaredTotalWeight != null && candidateTotalWeight !== declaredTotalWeight)"
        data-test="btn-adopt-candidates"
        @click="$emit('adopt', candidateFacts)"
      >
        ⚡ 一键采纳为正式评分标准（{{ candidateTotalWeight }}分）
      </el-button>
      <el-button :disabled="isAdoptingCandidates || reparseDisabled" @click="$emit('reparse')">
        🔄 重新解析
      </el-button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  candidateFacts: {
    type: Array,
    default: () => [],
  },
  candidateTotalWeight: {
    type: Number,
    default: 0,
  },
  declaredTotalWeight: {
    type: Number,
    default: 100,
  },
  adoptionStatus: {
    type: String,
    default: 'DECLARED_TOTAL',
  },
  isAdoptingCandidates: {
    type: Boolean,
    default: false,
  },
  reparseDisabled: {
    type: Boolean,
    default: false,
  },
})

defineEmits(['adopt', 'reparse'])

function sourceCellIdsOf(fact) {
  if (Array.isArray(fact?.sourceCellIds)) return fact.sourceCellIds
  if (typeof fact?.sourceCellIds !== 'string' || !fact.sourceCellIds.trim()) return []
  try {
    const parsed = JSON.parse(fact.sourceCellIds)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return [fact.sourceCellIds]
  }
}

function scoreTypeLabel(scoreType) {
  if (scoreType === 'OBJECTIVE' || scoreType === '客观项') return '客观项'
  if (scoreType === 'SUBJECTIVE' || scoreType === '主观项') return '主观项'
  return '待确认'
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
.candidate-review-header {
  margin-bottom: 12px;
}
.candidate-review-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--status-warning-color, #d46b08);
  display: flex;
  align-items: center;
  gap: 6px;
}
.candidate-review-desc {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}
.candidate-weight-alert {
  margin-bottom: 12px;
}
.candidate-table-wrapper {
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid var(--border-base, #ebeef5);
  border-radius: var(--radius-sm);
  margin-bottom: 12px;
}
.candidate-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.candidate-table th,
.candidate-table td {
  padding: 8px 10px;
  border-bottom: 1px solid var(--border-base, #ebeef5);
  text-align: left;
}
.candidate-table th {
  background: var(--bg-muted, #f5f7fa);
  color: var(--text-primary-ui, #303133);
  font-weight: 600;
}
.candidate-table .col-center {
  text-align: center;
}
.candidate-table .col-dim {
  font-weight: 600;
  color: var(--text-primary-ui, #303133);
}
.candidate-table .col-weight {
  font-weight: 600;
  color: var(--brand-xiyu-logo, #e02424);
}
.candidate-table .col-score-type { color: var(--text-primary-ui, #303133); text-align: center; }
.score-type-label { display: block; margin-bottom: 6px; font-weight: 600; }
.candidate-table .col-detail {
  color: var(--text-muted);
  max-width: 360px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.55;
}
.evidence-location,
.evidence-block,
.validation-diff {
  margin-top: 6px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  background: var(--bg-muted, #f5f7fa);
}
.score-type-select,
.parent-input {
  margin-top: 6px;
  width: 100%;
}
.evidence-block small {
  display: block;
  margin-top: 3px;
}
.validation-diff {
  color: var(--status-danger-color, #c45656);
  background: var(--status-danger-bg, #fef0f0);
}
.candidate-review-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
</style>
