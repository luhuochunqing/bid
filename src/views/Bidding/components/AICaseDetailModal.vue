<template>
  <el-dialog
    v-model="dialogVisible"
    title="案例详情"
    width="720px"
    destroy-on-close
    append-to-body
    class="ai-case-detail-dialog"
  >
    <div v-if="caseDetail" class="detail-body">
      <section class="detail-module scoring-module">
        <div class="module-label">评分项</div>
        <div class="scoring-title">{{ caseDetail.scoringPointTitle || caseDetail.scoringTitle }}</div>
        <div v-if="caseDetail.matchScore != null || caseDetail.score != null" class="scoring-score">
          <el-tag :type="scoreTagType" size="small">
            {{ displayScore }}% {{ caseDetail.matchLabel || caseDetail.scoreLabel || '' }}
          </el-tag>
        </div>
      </section>

      <section class="detail-module middle-module">
        <div class="layer">
          <h4 class="layer-title">评分项详细要素原文</h4>
          <div class="layer-body">{{ caseDetail.requirementRaw || '暂无评分项详细要素原文' }}</div>
        </div>
        <div class="layer">
          <h4 class="layer-title">应答片段原文</h4>
          <div class="layer-body">{{ caseDetail.responseText || '暂无应答片段原文' }}</div>
        </div>
        <div class="layer">
          <h4 class="layer-title">案例元信息</h4>
          <dl class="meta-grid">
            <div class="meta-item">
              <dt>项目名称</dt>
              <dd>{{ displayValue(caseDetail.sourceProjectName) }}</dd>
            </div>
            <div class="meta-item">
              <dt>招标主体</dt>
              <dd>{{ displayValue(caseDetail.bidEntity) }}</dd>
            </div>
            <div class="meta-item">
              <dt>创建时间</dt>
              <dd>{{ displayValue(caseDetail.createdAt) }}</dd>
            </div>
            <div class="meta-item">
              <dt>项目状态</dt>
              <dd>{{ displayProjectStatus(caseDetail.projectStatus) }}</dd>
            </div>
            <div class="meta-item">
              <dt>客户类型</dt>
              <dd>{{ customerTypeLabel(caseDetail.customerType) }}</dd>
            </div>
            <div class="meta-item">
              <dt>项目类型</dt>
              <dd>{{ projectTypeLabel(caseDetail.projectType) }}</dd>
            </div>
          </dl>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="detail-footer">
        <el-button type="primary" class="reuse-btn" @click="handleReuse">复用</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { customerTypeLabel, projectTypeLabel } from '@/views/Project/utils/projectListFormatters.js'
import { getProjectStatusText } from '@/views/Project/project-utils.js'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  caseDetail: {
    type: Object,
    default: () => null
  }
})

const emit = defineEmits(['update:modelValue', 'reuse'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const displayScore = computed(() => {
  const detail = props.caseDetail || {}
  return detail.matchScore ?? detail.score ?? 0
})

const scoreTagType = computed(() => (displayScore.value >= 85 ? 'danger' : 'primary'))

function displayValue(value) {
  if (value == null || String(value).trim() === '') return '-'
  return value
}

function displayProjectStatus(status) {
  const text = getProjectStatusText(status)
  return text || '-'
}

const handleReuse = () => {
  emit('reuse', props.caseDetail)
}
</script>

<style scoped>
.detail-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.detail-module {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.module-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  letter-spacing: 0.4px;
}
.scoring-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.5;
}
.middle-module {
  gap: 14px;
}
.layer-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}
.layer-body {
  background: var(--el-fill-color-lighter);
  border-left: 3px solid var(--brand-xiyu-logo);
  border-radius: 4px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary-ui);
  white-space: pre-wrap;
  max-height: 180px;
  overflow-y: auto;
}
.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
  margin: 0;
}
.meta-item dt {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 4px;
}
.meta-item dd {
  margin: 0;
  font-size: 13px;
  color: var(--gray-750);
  line-height: 1.5;
}
.detail-footer {
  display: flex;
  justify-content: flex-end;
}
.reuse-btn {
  background-color: var(--brand-xiyu-logo);
  border-color: var(--brand-xiyu-logo);
}
.reuse-btn:hover {
  background-color: var(--brand-xiyu-logo-hover);
  border-color: var(--brand-xiyu-logo-hover);
}
</style>
