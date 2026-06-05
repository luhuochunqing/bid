<template>
  <section v-if="data" class="kb-match-panel">
    <header class="kb-header">
      <span class="kb-title">四库联动匹配</span>
      <el-button size="small" text :loading="loading" @click="$emit('fetch')">刷新匹配</el-button>
    </header>
    <el-tabs v-model="activeTab" class="kb-tabs">
      <el-tab-pane v-for="tab in tabs" :key="tab.key" :label="tab.label" :name="tab.key">
        <div v-if="tab.items?.length" class="kb-item-list">
          <div v-for="(item, idx) in tab.items" :key="idx" :class="['kb-item', statusClass(item.status)]">
            <span class="kb-badge">{{ statusLabel(item.status) }}</span>
            <div class="kb-item-content">
              <p class="kb-req">{{ item.requirementText }}</p>
              <p v-if="matchDetail(item)" class="kb-matched">匹配：{{ matchDetail(item) }}</p>
              <p v-if="item.remainingDays != null" class="kb-days">剩余 {{ item.remainingDays }} 天</p>
              <p v-if="item.reason" class="kb-reason">{{ item.reason }}</p>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无匹配数据" :image-size="40" />
      </el-tab-pane>
    </el-tabs>
    <div v-if="data.summary" class="kb-summary">
      <el-tag type="success" size="small">已满足 {{ data.summary.totalSatisfied }}</el-tag>
      <el-tag type="warning" size="small">需关注 {{ data.summary.totalAttention }}</el-tag>
      <el-tag type="danger" size="small">不满足 {{ data.summary.totalUnsatisfied }}</el-tag>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  data: { type: Object, default: null },
  loading: { type: Boolean, default: false },
})
defineEmits(['fetch'])

const activeTab = ref('qualification')

const tabs = computed(() => [
  { key: 'qualification', label: '资质库', items: props.data?.qualificationMatch?.items },
  { key: 'personnel', label: '人员库', items: props.data?.personnelMatch?.items },
  { key: 'brandAuth', label: '品牌授权', items: props.data?.brandAuthMatch?.items },
  { key: 'performance', label: '业绩库', items: props.data?.performanceMatch?.items },
])

const statusLabel = (s) => ({
  SATISFIED: '已满足', ATTENTION: '需关注', UNSATISFIED: '不满足',
}[s] || s)

const statusClass = (s) => ({
  SATISFIED: 'kb-satisfied', ATTENTION: 'kb-attention', UNSATISFIED: 'kb-unsatisfied',
}[s] || '')

const matchDetail = (item) =>
  item.matchedQualificationName || item.matchedPersonnelName
  || item.matchedBrandName || item.matchedContractName || ''
</script>

<style scoped>
.kb-match-panel { display: grid; gap: 12px; }
.kb-header { display: flex; justify-content: space-between; align-items: center; }
.kb-title { font-weight: 700; color: var(--el-text-color-primary); font-size: 14px; }
.kb-summary { display: flex; gap: 8px; flex-wrap: wrap; }
.kb-item-list { display: grid; gap: 8px; }
.kb-item {
  display: grid; grid-template-columns: auto 1fr; gap: 8px;
  padding: 10px 12px; border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter); background: var(--el-bg-color);
}
.kb-badge {
  display: inline-flex; align-items: center; height: fit-content;
  padding: 2px 8px; border-radius: 6px; font-size: 12px; font-weight: 600;
  white-space: nowrap; margin-top: 2px;
}
.kb-satisfied .kb-badge { background: var(--el-color-success-light-9); color: var(--el-color-success); }
.kb-attention .kb-badge { background: var(--el-color-warning-light-9); color: var(--el-color-warning); }
.kb-unsatisfied .kb-badge { background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.kb-item-content p { margin: 0 0 3px; line-height: 1.5; }
.kb-req { font-weight: 600; color: var(--el-text-color-primary); font-size: 13px; }
.kb-matched { color: var(--el-color-success); font-size: 12px; }
.kb-days { color: var(--el-color-warning); font-size: 12px; }
.kb-reason { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
