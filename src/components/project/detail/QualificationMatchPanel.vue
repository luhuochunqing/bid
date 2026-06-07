<template>
  <section class="agent-section">
    <header>
      知识库匹配
      <el-badge :value="totalBadgeCount" :hidden="!totalBadgeCount" type="primary" class="panel-badge">
        <el-button size="small" text :loading="loading" @click="$emit('match')">一键解析</el-button>
      </el-badge>
    </header>

    <el-tabs v-if="hasData" v-model="activeTab" class="kb-tabs">
      <el-tab-pane name="qualification">
        <template #label>
          资质库
          <span v-if="qualificationItems?.length" class="tab-count">{{ qualificationItems.length }}</span>
        </template>
        <div v-if="qualificationItems?.length" class="qual-match-list">
          <div v-for="(item, idx) in qualificationItems" :key="idx" :class="['qual-match-item', qualClass(item.status)]">
            <span class="qual-status-badge">{{ qualLabel(item.status) }}</span>
            <div class="qual-content">
              <p class="qual-req-text">{{ item.requirementText }}</p>
              <p v-if="item.status === 'SATISFIED' || item.status === 'ATTENTION'" class="qual-matched-name">
                匹配资质：{{ item.matchedQualificationName }}
              </p>
              <p v-if="item.remainingDays !== null && item.remainingDays !== undefined" class="qual-expiry">
                剩余 {{ item.remainingDays }} 天
              </p>
              <p class="qual-reason">{{ item.reason }}</p>
            </div>
          </div>
        </div>
        <el-empty v-else description="无资质要求" :image-size="48" />
      </el-tab-pane>

      <el-tab-pane name="personnel">
        <template #label>
          人员库
          <span v-if="personnelItems?.length" class="tab-count">{{ personnelItems.length }}</span>
        </template>
        <div v-if="personnelItems?.length" class="qual-match-list">
          <div v-for="(item, idx) in personnelItems" :key="idx" :class="['qual-match-item', qualClass(item.status)]">
            <span class="qual-status-badge">{{ qualLabel(item.status) }}</span>
            <div class="qual-content">
              <p class="qual-req-text">{{ item.requirementText }}</p>
              <p v-if="item.matchedPersonnelName" class="qual-matched-name">
                匹配：{{ item.matchedPersonnelName }} - {{ item.matchedCertName }}
              </p>
              <p v-if="item.remainingDays !== null && item.remainingDays !== undefined" class="qual-expiry">
                剩余 {{ item.remainingDays }} 天
              </p>
              <p class="qual-reason">{{ item.reason }}</p>
            </div>
          </div>
        </div>
        <el-empty v-else description="无人员匹配项" :image-size="48" />
      </el-tab-pane>

      <el-tab-pane name="brandAuth">
        <template #label>
          品牌授权
          <span v-if="brandAuthItems?.length" class="tab-count">{{ brandAuthItems.length }}</span>
        </template>
        <div v-if="brandAuthItems?.length" class="qual-match-list">
          <div v-for="(item, idx) in brandAuthItems" :key="idx" :class="['qual-match-item', qualClass(item.status)]">
            <span class="qual-status-badge">{{ qualLabel(item.status) }}</span>
            <div class="qual-content">
              <p class="qual-req-text">{{ item.requirementText }}</p>
              <p v-if="item.matchedBrandName" class="qual-matched-name">
                匹配：{{ item.matchedBrandName }} - {{ item.matchedProductLine }}
              </p>
              <p v-if="item.remainingDays !== null && item.remainingDays !== undefined" class="qual-expiry">
                剩余 {{ item.remainingDays }} 天
              </p>
              <p class="qual-reason">{{ item.reason }}</p>
            </div>
          </div>
        </div>
        <el-empty v-else description="无品牌授权匹配项" :image-size="48" />
      </el-tab-pane>

      <el-tab-pane name="performance">
        <template #label>
          业绩库
          <span v-if="performanceItems?.length" class="tab-count">{{ performanceItems.length }}</span>
        </template>
        <div v-if="performanceItems?.length" class="qual-match-list">
          <div v-for="(item, idx) in performanceItems" :key="idx" :class="['qual-match-item', qualClass(item.status)]">
            <span class="qual-status-badge">{{ qualLabel(item.status) }}</span>
            <div class="qual-content">
              <p class="qual-req-text">{{ item.requirementText }}</p>
              <p v-if="item.matchedContractName" class="qual-matched-name">
                匹配：{{ item.matchedContractName }}
              </p>
              <p v-if="item.remainingDays !== null && item.remainingDays !== undefined" class="qual-expiry">
                剩余 {{ item.remainingDays }} 天
              </p>
              <p class="qual-reason">{{ item.reason }}</p>
            </div>
          </div>
        </div>
        <el-empty v-else description="无业绩匹配项" :image-size="48" />
      </el-tab-pane>
    </el-tabs>

    <div v-else-if="matched" class="qual-empty">
      <el-empty description="未解析出知识库匹配要求" :image-size="48" />
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  knowledgeBaseMatch: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  matched: { type: Boolean, default: false },
})
defineEmits(['match'])

const activeTab = ref('qualification')

const qualificationItems = computed(() => props.knowledgeBaseMatch?.qualificationMatch?.items)
const personnelItems = computed(() => props.knowledgeBaseMatch?.personnelMatch?.items)
const brandAuthItems = computed(() => props.knowledgeBaseMatch?.brandAuthMatch?.items)
const performanceItems = computed(() => props.knowledgeBaseMatch?.performanceMatch?.items)

const hasData = computed(() =>
  qualificationItems.value?.length
  || personnelItems.value?.length
  || brandAuthItems.value?.length
  || performanceItems.value?.length
)

const totalBadgeCount = computed(() => {
  const s = props.knowledgeBaseMatch?.summary
  if (!s) return 0
  return s.totalUnsatisfied + s.totalAttention
})

const qualLabel = (status) => ({
  SATISFIED: '已满足',
  ATTENTION: '需关注',
  UNSATISFIED: '不满足',
}[status] || status)

const qualClass = (status) => ({
  SATISFIED: 'qual-satisfied',
  ATTENTION: 'qual-attention',
  UNSATISFIED: 'qual-unsatisfied',
}[status] || '')
</script>

<style scoped>
.qual-match-list { display: grid; gap: 10px; }
.qual-match-item {
  display: grid; grid-template-columns: auto 1fr; gap: 10px;
  padding: 12px; border-radius: 12px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}
.qual-status-badge {
  display: inline-flex; align-items: center; height: fit-content;
  padding: 2px 8px; border-radius: 6px;
  font-size: 12px; font-weight: 600; white-space: nowrap; margin-top: 2px;
}
.qual-satisfied .qual-status-badge {
  background: var(--el-color-success-light-9); color: var(--el-color-success);
}
.qual-attention .qual-status-badge {
  background: var(--el-color-warning-light-9); color: var(--el-color-warning);
}
.qual-unsatisfied .qual-status-badge {
  background: var(--el-color-danger-light-9); color: var(--el-color-danger);
}
.qual-content p { margin: 0 0 4px; line-height: 1.5; }
.qual-req-text { font-weight: 600; color: var(--el-text-color-primary); }
.qual-matched-name { color: var(--el-color-success); font-size: 13px; }
.qual-expiry { color: var(--el-color-warning); font-size: 12px; font-weight: 500; }
.qual-reason { color: var(--el-text-color-secondary); font-size: 12px; }
.qual-empty { padding: 8px 0; }
.panel-badge { display: inline-flex; vertical-align: middle; }
.tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  margin-left: 4px;
  border-radius: 9px;
  background: var(--el-fill-color);
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}
</style>
