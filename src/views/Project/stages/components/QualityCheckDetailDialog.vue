<template>
  <el-dialog
    v-model="dialogVisible"
    title="检查项详情"
    width="560px"
    align-center
    append-to-body
    class="quality-check-detail-dialog"
  >
    <div v-if="item" class="detail-content">
      <!-- 检查状态：固定弹窗顶部 -->
      <div class="dt-row">
        <div class="dt-label">检查状态</div>
        <el-tag :type="statusTag.type" effect="dark">{{ statusTag.text }}</el-tag>
      </div>

      <!-- 检查项说明（PRD 2.3 检查内容；旧快照无 description 时回退名称） -->
      <div class="dt-row">
        <div class="dt-label">检查项说明</div>
        <div class="dt-val">{{ descriptionText }}</div>
      </div>

      <!-- AI 检查结论：评分项响应额外前置客观项得分链接 -->
      <div class="dt-row">
        <div class="dt-label">AI 检查结论</div>
        <div class="dt-val">
          <a
            v-if="showScoreLink"
            class="score-link"
            @click="emit('go-to-score-parse')"
          >客观项得分：{{ objectiveScore }}分 ↗</a>
          {{ conclusionText || 'AI 已完成该项检查' }}
        </div>
      </div>

      <!-- 问题定位：页码（仅非通过且可定位时显示） -->
      <div v-if="showLocationRows && pagesText" class="dt-row">
        <div class="dt-label">问题定位</div>
        <span class="dt-location">{{ pagesText }}</span>
      </div>

      <!-- 原文摘录：投标文件段落原文（不高亮） -->
      <div v-if="showLocationRows && quoteText" class="dt-row">
        <div class="dt-label">原文摘录</div>
        <div class="dt-quote">{{ quoteText }}</div>
      </div>

      <!-- 联动数据来源：四知识库跳转链接 -->
      <div v-if="showLocationRows && kbLinks.length > 0" class="dt-row">
        <div class="dt-label">联动数据来源</div>
        <div class="dt-source-list">
          <a
            v-for="kb in kbLinks"
            :key="kb.source"
            class="dt-source"
            @click="goKnowledge(kb.route)"
          >{{ kb.label }}</a>
        </div>
      </div>

      <!-- 建议操作：按状态固定文案 -->
      <div v-if="suggestionText" class="dt-row">
        <div class="dt-label">建议操作</div>
        <div class="dt-val" :class="item.status === 'FAIL' ? 'suggestion-fail' : 'suggestion-manual'">
          {{ suggestionText }}
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { stripReasonCode, extractBidPages, extractBidQuote, extractKbLinks, getStatusMeta } from '../composables/qualityCheckDetail.js'

const props = defineProps({
  item: {
    type: Object,
    default: null
  },
  objectiveScore: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['go-to-score-parse'])

const dialogVisible = defineModel({ type: Boolean, default: false })

const router = useRouter()

const statusTag = computed(() => {
  const meta = getStatusMeta(props.item?.status)
  const prefix = props.item?.status ? meta.icon : ''
  return { type: meta.tagType, text: prefix ? `${prefix} ${meta.text}` : meta.text }
})

const conclusionText = computed(() => stripReasonCode(props.item?.conclusion))

// 检查项说明优先用检查内容（description），旧快照缺失时回退检查项名称
const descriptionText = computed(() => props.item?.description || props.item?.title || '')

// pass 项不展示问题定位/原文摘录/联动数据来源
const showLocationRows = computed(() => props.item?.status !== 'PASS')

const pagesText = computed(() => extractBidPages(props.item?.evidences))
const quoteText = computed(() => extractBidQuote(props.item?.evidences))
const kbLinks = computed(() => extractKbLinks(props.item?.evidences))

// 评分项响应（SCORING_RESPONSE）：结论最前展示客观项得分链接（未打分时不显示）
const showScoreLink = computed(() =>
  props.item?.itemCode === 'SCORING_RESPONSE' && typeof props.objectiveScore === 'number')

const suggestionText = computed(() => {
  switch (props.item?.status) {
    case 'FAIL': return '高风险项，建议立即修订后重新上传标书并再次执行检查。'
    case 'MANUAL_REVIEW': return '请投标负责人复核后确认是否需要修订。'
    default: return ''
  }
})

function goKnowledge(route) {
  router.push(route)
}
</script>

<style scoped>
.detail-content {
  padding: 0 4px;
}
.dt-row {
  margin-bottom: 14px;
}
.dt-label {
  font-size: 11px;
  color: #909399;
  font-weight: 600;
  margin-bottom: 4px;
  letter-spacing: 0.5px;
}
.dt-val {
  font-size: 13px;
  color: #303133;
  line-height: 1.6;
}
.dt-quote {
  background: #fafbfc;
  border-left: 3px solid #dc2626;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
}
.dt-location {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #fef9f9;
  border: 1px solid #fbc4c4;
  color: #991b1b;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}
.dt-source-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.dt-source {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #f0f7f4;
  color: #2E7659;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  font-weight: 500;
  border: 1px solid #c8e0d5;
  transition: background-color 0.16s;
}
.dt-source:hover {
  background: #e0efe7;
}
.suggestion-fail {
  color: #991b1b;
  font-weight: 600;
}
.suggestion-manual {
  color: #92400e;
}
.score-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: #2E7659;
  font-weight: 600;
  cursor: pointer;
  border-bottom: 1px dashed #2E7659;
  padding-bottom: 1px;
  margin-right: 8px;
}
.score-link:hover {
  color: #23785d;
  border-bottom-color: #23785d;
}
</style>
