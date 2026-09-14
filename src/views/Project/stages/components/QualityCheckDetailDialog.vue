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

      <!-- CA 证书供需比对看板（招标文件要求 vs 企业 CA 台账比对） -->
      <div v-if="isCaItem && caComparisonData" class="dt-section ca-comparison-section">
        <div class="dt-section-header">
          <div class="dt-label">CA 证书供需比对</div>
          <el-tag size="small" :type="caComparisonData.tagType" effect="dark">
            {{ caComparisonData.statusText }}
          </el-tag>
        </div>
        <div class="ca-compare-grid">
          <!-- 左侧：招标文件要求 -->
          <div class="ca-card ca-card-req">
            <div class="ca-card-title">
              <span><span class="ca-icon">📑</span> 招标文件要求</span>
            </div>
            <div class="ca-card-content">
              <div class="ca-req-text">{{ caComparisonData.requirementText }}</div>
              <div v-if="caComparisonData.tenderQuote" class="ca-quote-text">
                “{{ caComparisonData.tenderQuote }}”
              </div>
            </div>
          </div>

          <!-- 右侧：企业 CA 台账现状 -->
          <div class="ca-card ca-card-stock" :class="'ca-border-' + caComparisonData.statusClass">
            <div class="ca-card-title">
              <span><span class="ca-icon">🔐</span> 企业 CA 台账比对</span>
              <span class="ca-badge" :class="'badge-' + caComparisonData.statusClass">
                {{ caComparisonData.statusText }}
              </span>
            </div>
            <div class="ca-card-content">
              <div class="ca-stock-desc">{{ caComparisonData.stockText }}</div>
              <div v-if="caComparisonData.matchedCertDetail" class="ca-cert-detail">
                {{ caComparisonData.matchedCertDetail }}
              </div>
              <div class="ca-jump-link">
                <a @click="goKnowledge('/resource/ca-management?tab=ca')">前往【CA证书管理】查看台账 ↗</a>
              </div>
            </div>
          </div>
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
                  <el-tag size="small" :type="getQualDetailStatusMeta(detail.status).tagType" effect="plain">
                    {{ getQualDetailStatusMeta(detail.status).text }}
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

      <!-- 联动数据来源：知识库与资源库跳转链接 -->
      <div v-if="(showLocationRows || isCaItem) && kbLinks.length > 0" class="dt-row">
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
  isQualificationMatchItem,
  isCaCertificateItem,
  parseCaComparison
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
const isCaItem = computed(() => isCaCertificateItem(props.item?.itemCode))

const qualificationDetails = computed(() => {
  return Array.isArray(props.item?.details) ? props.item.details : []
})

const caComparisonData = computed(() =>
  isCaItem.value ? parseCaComparison(props.item?.conclusion, props.item?.evidences) : null
)

const dialogWidth = computed(() => {
  if (isQualificationMatch.value && qualificationDetails.value.length > 0) return '720px'
  if (isCaItem.value && caComparisonData.value) return '680px'
  return '560px'
})

const statusTag = computed(() => {
  const meta = getStatusMeta(props.item?.status, props.item?.conclusion)
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
const kbLinks = computed(() => {
  const links = extractKbLinks(props.item?.evidences)
  if (isCaItem.value && !links.some(l => l.source === 'ca-certificate')) {
    links.push({ source: 'ca-certificate', label: 'CA证书管理', route: '/resource/ca-management' })
  }
  return links
})

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
@import './qualityCheckDetailDialog.css';
</style>
