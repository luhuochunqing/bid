<template>
  <section class="match-score-panel" :class="{ compact }">
    <div class="match-score-head">
      <div>
        <p class="match-score-kicker">Bid Match Scoring</p>
        <h3>投标匹配评分</h3>
      </div>
      <el-button
        v-if="showAction && summary.actionText"
        :loading="generating"
        :type="summary.state === 'ready' ? 'default' : 'primary'"
        @click="handlePrimaryAction"
      >
        <el-icon><component :is="primaryIcon" /></el-icon>
        {{ summary.actionText }}
      </el-button>
    </div>

    <div v-if="isReady" class="score-ready">
      <div class="score-overview">
        <div class="score-circle" :class="`tone-${scoreForView.tone}`">
          <span class="score-number">{{ scoreForView.totalScore }}</span>
          <span class="score-unit">分</span>
        </div>
        <div class="score-meta">
          <div class="score-title-row">
            <strong>{{ summary.text }}</strong>
            <el-tag v-if="scoreForView.modelVersion" effect="plain" size="small">
              {{ scoreForView.modelVersion }}
            </el-tag>
          </div>
          <p v-if="scoreForView.modelName">{{ scoreForView.modelName }}</p>
          <p v-if="scoreForView.summary">{{ scoreForView.summary }}</p>
        </div>
      </div>

      <div class="dimension-stack">
        <div
          v-for="dimension in scoreForView.dimensionSummaries"
          :key="dimension.key || dimension.name"
          class="dimension-row"
        >
          <div class="dimension-topline">
            <div class="dimension-name">
              <span>{{ dimension.name }}</span>
              <el-tag size="small" effect="plain">{{ dimension.weightText }}</el-tag>
            </div>
            <div class="dimension-score">
              <el-tag :type="dimension.tagType" size="small">{{ dimension.score }}分</el-tag>
              <el-button
                link
                type="primary"
                :disabled="dimension.evidence.length === 0"
                @click="openEvidence(dimension)"
              >
                证据
              </el-button>
            </div>
          </div>
          <el-progress
            :percentage="dimension.percentage"
            :stroke-width="10"
            :show-text="false"
            :status="dimension.tagType === 'danger' ? 'exception' : undefined"
          />
          <p v-if="dimension.description" class="dimension-desc">{{ dimension.description }}</p>
        </div>
      </div>
    </div>

    <el-empty
      v-else
      :description="summary.text"
      class="score-empty"
    >
      <p class="empty-description">{{ summary.description }}</p>
    </el-empty>

    <el-drawer
      v-model="evidenceVisible"
      :title="selectedDimension ? `${selectedDimension.name}证据` : '评分证据'"
      size="420px"
    >
      <div v-if="selectedDimension" class="evidence-list">
        <article
          v-for="(item, index) in selectedDimension.evidence"
          :key="`${selectedDimension.key}-${index}`"
          class="evidence-item"
        >
          <h4>{{ item.title }}</h4>
          <p v-if="item.content">{{ item.content }}</p>
          <span v-if="item.source">{{ item.source }}</span>
        </article>
      </div>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { DocumentChecked, Refresh, Setting, View } from '@element-plus/icons-vue'
import { getScoreTone, normalizeMatchScoreForView, summarizeScoreState } from './normalizers.js'

const props = defineProps({
  score: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  generating: { type: Boolean, default: false },
  error: { type: String, default: '' },
  compact: { type: Boolean, default: false },
  showAction: { type: Boolean, default: true },
})

const emit = defineEmits(['generate', 'reload', 'configure'])

const selectedDimension = ref(null)
const evidenceVisible = ref(false)

const scoreForView = computed(() => {
  const normalized = normalizeMatchScoreForView(props.score)
  if (!normalized) return null
  return {
    ...normalized,
    tone: getScoreTone(normalized.totalScore),
  }
})

const summary = computed(() => summarizeScoreState({
  loading: props.loading,
  generating: props.generating,
  error: props.error,
  score: scoreForView.value,
}))

const isReady = computed(() => summary.value.state === 'ready' && scoreForView.value)

const primaryIcon = computed(() => {
  if (summary.value.state === 'not-configured') return Setting
  if (summary.value.state === 'ready' || summary.value.state === 'failed') return Refresh
  if (summary.value.state === 'error') return View
  return DocumentChecked
})

const handlePrimaryAction = () => {
  if (summary.value.state === 'error') {
    emit('reload')
    return
  }
  if (summary.value.state === 'not-configured') {
    emit('configure')
    return
  }
  emit('generate')
}

const openEvidence = (dimension) => {
  selectedDimension.value = dimension
  evidenceVisible.value = true
}
</script>

<style scoped>
.match-score-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.match-score-head,
.score-overview,
.dimension-topline,
.dimension-name,
.dimension-score,
.score-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.match-score-head {
  justify-content: space-between;
}
.match-score-kicker {
  margin: 0 0 4px;
  color: #6d7d5d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.match-score-head h3 {
  margin: 0;
  color: #1f2d1d;
}
.score-overview {
  align-items: flex-start;
}
.score-circle {
  display: grid;
  flex: 0 0 96px;
  width: 96px;
  height: 96px;
  place-items: center;
  border: 6px solid #d8e6d3;
  border-radius: 50%;
  color: #2f5f34;
}
.score-number {
  font-size: 30px;
  font-weight: 800;
  line-height: 1;
}
.score-unit {
  margin-top: -20px;
  font-size: 13px;
}
.tone-excellent,
.tone-good {
  border-color: #8bc07f;
  color: #2f6f3a;
}
.tone-warning {
  border-color: #e6a23c;
  color: #9a650f;
}
.tone-danger {
  border-color: #f56c6c;
  color: #b93a3a;
}
.score-meta {
  min-width: 0;
  color: #52624c;
  line-height: 1.7;
}
.score-meta p {
  margin: 4px 0 0;
}
.dimension-stack {
  display: grid;
  gap: 14px;
}
.dimension-row {
  padding: 12px;
  border: 1px solid rgba(67, 89, 55, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}
.dimension-topline {
  justify-content: space-between;
  margin-bottom: 8px;
}
.dimension-name {
  min-width: 0;
  color: #1f2d1d;
  font-weight: 700;
}
.dimension-desc,
.empty-description {
  margin: 8px 0 0;
  color: #66705f;
  line-height: 1.6;
}
.score-empty {
  padding: 16px 0;
}
.evidence-list {
  display: grid;
  gap: 12px;
}
.evidence-item {
  padding: 12px;
  border: 1px solid rgba(67, 89, 55, 0.12);
  border-radius: 8px;
}
.evidence-item h4 {
  margin: 0 0 8px;
  color: #1f2d1d;
}
.evidence-item p,
.evidence-item span {
  margin: 0;
  color: #52624c;
  line-height: 1.6;
}
@media (max-width: 720px) {
  .match-score-head,
  .score-overview,
  .dimension-topline {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
