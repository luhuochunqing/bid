<template>
  <div class="score-item-select">
    <el-select
      v-model="model"
      :placeholder="placeholder"
      clearable
      filterable
      :filter-method="filterScoreItems"
      :no-data-text="noDataText"
      @change="$emit('change', $event)"
      @visible-change="onScoreSelectVisible"
    >
      <el-option
        v-for="item in visibleScoreItems"
        :key="item.id"
        :label="item.title"
        :value="item.id"
      >
        <el-tooltip
          :content="item.tooltip"
          :disabled="!item.tooltip"
          placement="right"
          :fallback-placements="['left', 'top', 'bottom']"
          :show-after="150"
          teleported
          popper-class="score-item-option-tooltip"
        >
          <div class="score-option-line">{{ item.title }}</div>
        </el-tooltip>
      </el-option>
    </el-select>
    <div v-if="selectedScoreItemDetail" class="score-item-detail-preview" aria-live="polite">
      <div class="score-item-detail-label">详细要素（标书原文）</div>
      <p class="score-item-detail-text">{{ selectedScoreItemDetail }}</p>
    </div>
  </div>
</template>

<script setup>
// Input: scoring items from AI 评分标准解析 { id, code, dim, detail }
// Output: short-label select with original-text tooltip + selected preview
// Pos: src/components/common/ - shared score-item picker
import { computed, ref } from 'vue'
import {
  scoreItemMatchesQuery,
  scoreItemOptionTitle,
  scoreItemOptionTooltip
} from '@/utils/scoreItemOptionDisplay.js'

const props = defineProps({
  items: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择评分项' }
})

defineEmits(['change'])
const model = defineModel({ type: [Number, String], default: null })

const scoreItemQuery = ref('')

const visibleScoreItems = computed(() =>
  props.items
    .filter((item) => scoreItemMatchesQuery(item, scoreItemQuery.value))
    .map((item) => ({
      id: item.id,
      title: scoreItemOptionTitle(item),
      tooltip: scoreItemOptionTooltip(item)
    }))
)

const selectedScoreItemDetail = computed(() => {
  const item = props.items.find((row) => row.id === model.value)
  return scoreItemOptionTooltip(item)
})

const noDataText = computed(() => (scoreItemQuery.value.trim() ? '无匹配评分项' : '暂无评分项'))

function filterScoreItems(query) {
  scoreItemQuery.value = query
}

function onScoreSelectVisible(open) {
  if (!open) scoreItemQuery.value = ''
}
</script>

<style scoped>
.score-item-select { width: 100%; }
.score-item-select :deep(.el-select) { width: 100%; }
.score-option-line { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.score-item-detail-preview {
  margin-top: 8px;
  padding: 8px 10px;
  background: var(--bg-subtle);
  border: 1px solid var(--gray-150);
  border-radius: 6px;
  max-height: 120px;
  overflow-y: auto;
}
.score-item-detail-label { font-size: 12px; color: var(--text-muted); margin-bottom: 4px; }
.score-item-detail-text { margin: 0; font-size: 12px; line-height: 1.6; color: var(--text-primary); white-space: pre-wrap; word-break: break-word; }
</style>

<style>
.score-item-option-tooltip {
  max-width: 400px;
  max-height: 240px;
  overflow-y: auto;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
