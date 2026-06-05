<template>
  <div class="ai-score">
    <div class="score-header">
      <h4>AI模拟专家评审</h4>
      <el-tag :type="detail.getOverallScoreType(detail.aiResult.value.score?.total || 0)" size="large">综合评分: {{ detail.aiResult.value.score?.total || 0 }}分</el-tag>
    </div>
    <el-descriptions :column="1" border size="small" class="score-descriptions">
      <el-descriptions-item label="技术方案"><div class="score-item"><el-rate :model-value="detail.aiResult.value.score?.tech || 0" disabled show-score /></div></el-descriptions-item>
      <el-descriptions-item label="商务响应"><div class="score-item"><el-rate :model-value="detail.aiResult.value.score?.business || 0" disabled show-score /></div></el-descriptions-item>
      <el-descriptions-item label="价格竞争力"><div class="score-item"><el-rate :model-value="detail.aiResult.value.score?.price || 0" disabled show-score /></div></el-descriptions-item>
      <el-descriptions-item label="企业资质"><div class="score-item"><el-rate :model-value="detail.aiResult.value.score?.qualification || 0" disabled show-score /></div></el-descriptions-item>
    </el-descriptions>
    <div v-if="detail.aiResult.value.score?.comment" class="ai-comment"><h4 class="section-title">AI评语</h4><div class="comment-content"><p v-for="(paragraph, idx) in detail.aiResult.value.score.comment.split('\n')" :key="idx">{{ paragraph }}</p></div></div>
    <div v-if="detail.aiResult.value.score?.suggestions?.length" class="ai-suggestions"><h4 class="section-title">改进建议</h4><ul class="suggestions-list"><li v-for="(suggestion, idx) in detail.aiResult.value.score.suggestions" :key="idx"><el-icon color="#409eff"><ArrowRight /></el-icon><span>{{ suggestion }}</span></li></ul></div>
    <el-empty v-if="!detail.aiResult.value.score" description="点击开始检查进行AI智能评分" :image-size="80" />
  </div>
</template>

<script setup>
import { inject } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const detail = inject(projectDetailKey)
</script>
