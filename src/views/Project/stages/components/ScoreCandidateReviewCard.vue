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
    <div class="candidate-table-wrapper">
      <table class="candidate-table">
        <thead>
          <tr>
            <th style="width: 60px">序号</th>
            <th style="width: 200px">评分维度</th>
            <th style="width: 90px">分值</th>
            <th>原文依据 / 规则描述</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(fact, idx) in candidateFacts" :key="fact.factId || idx">
            <td class="col-center">{{ fact.code || (idx + 1) }}</td>
            <td class="col-dim">{{ fact.dimension || '—' }}</td>
            <td class="col-weight">{{ fact.weight }} 分</td>
            <td class="col-detail" :title="fact.sourceText">{{ fact.sourceText || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="candidate-review-actions">
      <el-button
        type="primary"
        :loading="isAdoptingCandidates"
        data-test="btn-adopt-candidates"
        @click="$emit('adopt')"
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
.candidate-table .col-detail {
  color: var(--text-muted);
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.candidate-review-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
</style>
