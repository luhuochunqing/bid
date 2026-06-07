<template>
  <div class="quality-result">
    <div v-if="detail.qualityResult.value" class="quality-summary">
      <el-statistic title="发现问题" :value="detail.qualityResult.value.errors?.length || 0" suffix="个"><template #prefix><el-icon color="#f56c6c"><WarningFilled /></el-icon></template></el-statistic>
      <el-statistic title="建议修改" :value="detail.qualityResult.value.suggestions?.length || 0" suffix="条"><template #prefix><el-icon color="#e6a23c"><QuestionFilled /></el-icon></template></el-statistic>
    </div>

    <h4 v-if="detail.qualityResult.value?.errors?.length" class="section-title">问题列表</h4>
    <el-table :data="detail.qualityResult.value?.errors || []" size="small" max-height="280">
      <el-table-column prop="type" label="类型" width="70">
        <template #default="{ row }"><el-tag v-if="row.type === 'typo'" type="danger" size="small">错别字</el-tag><el-tag v-else-if="row.type === 'grammar'" type="warning" size="small">语法</el-tag><el-tag v-else type="info" size="small">格式</el-tag></template>
      </el-table-column>
      <el-table-column prop="original" label="原文" min-width="80" show-overflow-tooltip />
      <el-table-column prop="suggestion" label="建议修改" min-width="80" show-overflow-tooltip />
      <el-table-column prop="location" label="位置" width="100" show-overflow-tooltip />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row, $index }"><el-button link type="primary" size="small" @click="detail.handleAdoptSuggestion(row, $index)">采纳</el-button><el-button link type="info" size="small" @click="detail.handleIgnoreSuggestion($index)">忽略</el-button></template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="!detail.qualityResult.value || !detail.qualityResult.value.errors?.length"
      :description="detail.qualityResult.value?.empty ? '当前项目暂无可检查文档，请先上传或关联项目文档' : '点击开始检查进行文书质量分析'"
      :image-size="80"
    />
  </div>
</template>

<script setup>
import { inject } from 'vue'
import { QuestionFilled, WarningFilled } from '@element-plus/icons-vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const detail = inject(projectDetailKey)
</script>
