<template>
  <el-dialog
    v-model="dialogVisible"
    title="检查项详情"
    :width="dialogWidth"
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

      <!-- 结构化资质比对清单（招标要求 vs 我司证书 vs 标书页码） -->
      <div v-if="isQualificationMatch && qualificationDetails.length > 0" class="dt-section">
        <div class="dt-section-header">
          <div class="dt-label">资质比对清单</div>
          <span class="dt-section-count">共 {{ qualificationDetails.length }} 项</span>
        </div>
        <div class="qual-table-wrapper">
          <table class="qual-compare-table">
            <thead>
              <tr>
                <th style="width: 32%">招标资质要求</th>
                <th style="width: 28%">我司持有资质</th>
                <th style="width: 16%">标书位置</th>
                <th style="width: 24%">核查说明</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(detail, idx) in qualificationDetails" :key="idx" class="qual-row">
                <td class="col-req">
                  <div class="qual-req-text">{{ detail.requirement }}</div>
                </td>
                <td class="col-cert">
                  <div v-if="detail.matchedCertName" class="qual-cert-name">
                    📜 {{ detail.matchedCertName }}
                  </div>
                  <div v-if="detail.remainingDays !== null && detail.remainingDays !== undefined" class="qual-days-tag" :class="{ 'days-warning': detail.remainingDays <= 60 }">
                    剩余 {{ detail.remainingDays }} 天
                  </div>
                  <div v-else-if="!detail.matchedCertName" class="qual-cert-empty">
                    （资质库未匹配）
                  </div>
                </td>
                <td class="col-page">
                  <span v-if="detail.bidPageNumber" class="qual-page-badge">第 {{ detail.bidPageNumber }} 页</span>
                  <span v-else class="qual-page-none">未定位</span>
                </td>
                <td class="col-status">
                  <el-tag size="small" :type="qualStatusBadge(detail.status).tagType" effect="plain">
                    {{ qualStatusBadge(detail.status).text }}
                  </el-tag>
                  <div v-if="detail.remark" class="qual-remark">{{ detail.remark }}</div>
                </td>
              </tr>
            </tbody>
          </table>
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
import {
  stripReasonCode,
  extractBidPages,
  extractBidQuote,
  extractKbLinks,
  getStatusMeta,
  getQualDetailStatusMeta,
  isQualificationMatchItem
} from '../composables/qualityCheckDetail.js'

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

const isQualificationMatch = computed(() => isQualificationMatchItem(props.item?.itemCode))

const qualificationDetails = computed(() => {
  return Array.isArray(props.item?.details) ? props.item.details : []
})

function qualStatusBadge(status) {
  return getQualDetailStatusMeta(status)
}

const dialogWidth = computed(() =>
  isQualificationMatch.value && qualificationDetails.value.length > 0 ? '720px' : '560px'
)

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
.detail-content { padding: 0 4px; }
.dt-row { margin-bottom: 14px; }
.dt-label { font-size: 11px; color: var(--gray-350); font-weight: 600; margin-bottom: 4px; letter-spacing: 0.5px; }
.dt-val { font-size: 13px; color: var(--text-primary-ui); line-height: 1.6; }
.dt-quote { background: var(--gray-50); border-left: 3px solid var(--color-danger); padding: 8px 12px; border-radius: 4px; font-size: 12px; color: var(--text-secondary-ui); line-height: 1.6; }
.dt-location { display: inline-flex; align-items: center; gap: 4px; background: var(--color-danger-bg); border: 1px solid var(--color-danger-dark); color: var(--color-danger-dark); padding: 4px 10px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.dt-source-list { display: flex; flex-wrap: wrap; gap: 8px; }
.dt-source { display: inline-flex; align-items: center; gap: 4px; background: var(--brand-xiyu-logo-light); color: var(--brand-xiyu-logo); padding: 4px 10px; border-radius: 4px; font-size: 12px; cursor: pointer; font-weight: 500; border: 1px solid var(--brand-xiyu-logo-soft); transition: background-color 0.16s; }
.dt-source:hover { background: var(--brand-xiyu-logo-soft); }
.suggestion-fail { color: var(--color-danger-dark); font-weight: 600; }
.suggestion-manual { color: var(--color-warning); }
.score-link { display: inline-flex; align-items: center; gap: 2px; color: var(--brand-xiyu-logo); font-weight: 600; cursor: pointer; border-bottom: 1px dashed var(--brand-xiyu-logo); padding-bottom: 1px; margin-right: 8px; }
.score-link:hover { color: var(--brand-xiyu-logo-hover); border-bottom-color: var(--brand-xiyu-logo-hover); }
.dt-section { margin-bottom: 16px; }
.dt-section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.dt-section-count { font-size: 12px; color: var(--gray-350); }
.qual-table-wrapper { border: 1px solid var(--gray-150); border-radius: 6px; overflow: hidden; }
.qual-compare-table { width: 100%; border-collapse: collapse; font-size: 12px; line-height: 1.5; }
.qual-compare-table th { background: var(--gray-50); color: var(--text-secondary-ui); font-weight: 600; text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--gray-150); }
.qual-compare-table td { padding: 8px 10px; border-bottom: 1px solid var(--gray-100); vertical-align: top; }
.qual-row:last-child td { border-bottom: none; }
.qual-row:hover { background: var(--gray-50); }
.qual-req-text { color: var(--text-primary-ui); font-weight: 500; word-break: break-word; }
.qual-cert-name { color: var(--text-primary); font-weight: 500; margin-bottom: 2px; word-break: break-word; }
.qual-cert-empty { color: var(--text-placeholder); font-style: italic; }
.qual-days-tag { display: inline-block; font-size: 11px; color: var(--brand-xiyu-logo); background: var(--color-success-bg); border: 1px solid var(--brand-xiyu-logo-soft); border-radius: 3px; padding: 1px 6px; margin-top: 2px; }
.qual-days-tag.days-warning { color: var(--color-warning); background: var(--color-warning-bg); border-color: var(--color-warning); }
.qual-page-badge { display: inline-block; background: var(--brand-xiyu-logo-light); color: var(--brand-xiyu-logo); border: 1px solid var(--brand-xiyu-logo-soft); border-radius: 3px; padding: 2px 6px; font-size: 11px; font-weight: 500; }
.qual-page-none { color: var(--text-placeholder); font-size: 11px; }
.qual-remark { font-size: 11px; color: var(--text-secondary-ui); margin-top: 4px; line-height: 1.4; }
</style>
