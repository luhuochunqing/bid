<template>
  <div class="check-result">
    <div class="score-section">
      <el-progress type="circle" :percentage="Number(detail.aiResult.value.compliance?.score || 0)" :color="detail.getProgressColor(detail.aiResult.value.compliance?.score || 0)" :width="100">
        <template #default="{ percentage }"><span class="score-text">{{ detail.formatScore(percentage) }}分</span></template>
      </el-progress>
      <p class="score-level">{{ detail.getScoreLevel(detail.aiResult.value.compliance?.score || 0) }}</p>
    </div>
    <div v-if="detail.aiResult.value.compliance?.issues?.length" class="issues-section">
      <h4 class="section-title">检查结果</h4>
      <el-table :data="detail.aiResult.value.compliance.issues" size="small" max-height="300">
        <el-table-column prop="category" label="类别" width="80" />
        <el-table-column prop="item" label="检查项" min-width="120" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="70"><template #default="{ row }"><el-tag :type="row.status === 'pass' ? 'success' : 'danger'" size="small">{{ row.status === 'pass' ? '通过' : '不通过' }}</el-tag></template></el-table-column>
        <el-table-column prop="suggestion" label="建议" min-width="100" show-overflow-tooltip />
      </el-table>
    </div>
    <el-empty v-else description="点击开始检查进行AI分析" :image-size="80" />
  </div>
</template>

<script setup>
import { inject } from 'vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const detail = inject(projectDetailKey)
</script>
