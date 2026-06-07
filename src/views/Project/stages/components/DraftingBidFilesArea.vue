<template>
  <el-card class="bid-files-area" shadow="never">
    <template #header>
      <div class="area-header">
        <span class="area-title">投标文件</span>
        <div class="area-actions">
          <el-button v-if="canAIRecommendCase" type="primary" link :icon="Search" @click="openAiRecommend">
            AI智能推荐案例
          </el-button>
          <el-button v-if="canAIComplianceCheck" type="warning" link :icon="CircleCheckFilled" @click="handleOpenComplianceCheck">
            AI合规性检查
          </el-button>
          <el-button v-if="canAIBidDocumentQualityCheck" type="danger" link :icon="DocumentChecked" @click="handleOpenBidDocumentQualityCheck">
            AI标书质量核查
            <el-badge v-if="bidQualityIssueCount > 0" :value="bidQualityIssueCount" class="quality-badge" />
          </el-button>
        </div>
      </div>
    </template>

    <div class="bid-agent-entry">
      <div class="bid-agent-copy">
        <p class="bid-agent-eyebrow">标书生成 Agent</p>
        <p class="bid-agent-desc">上传招标文件，AI 自动拆解要求、匹配资料并生成标书初稿，写入文档编辑器。</p>
      </div>
      <el-button type="success" @click="openBidAgentDrawer">
        <el-icon><MagicStick /></el-icon>
        启动 AI 生成初稿
      </el-button>
    </div>

    <DraftingBidPanel
      :project-id="projectId"
      :can-submit-bid-for-review="canSubmitBidForReview"
      :can-review-bid="canReviewBid"
      :can-submit-bid="canSubmitBid"
      :advance-error="advanceError"
      @update:advance-error="advanceError = $event"
      @advanced="emit('advanced')"
      @open-reviewer-dialog="handleReviewBid"
    />
  </el-card>
</template>

<script setup>
import { CircleCheckFilled, DocumentChecked, MagicStick, Search } from '@element-plus/icons-vue'
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'
import DraftingBidPanel from './DraftingBidPanel.vue'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  canAIRecommendCase: Boolean,
  canAIComplianceCheck: Boolean,
  canAIBidDocumentQualityCheck: Boolean,
  canSubmitBidForReview: Boolean,
  canReviewBid: Boolean,
  canSubmitBid: Boolean,
  advanceError: { type: String, default: '' },
  bidQualityIssueCount: { type: Number, default: 0 },
})
const emit = defineEmits(['advanced', 'openAiRecommend'])

const ctx = useProjectDetailContext()

function openBidAgentDrawer() { ctx.bidAgent?.openDrawer?.() }
function openAiRecommend() { emit('openAiRecommend') }
function handleOpenComplianceCheck() { ctx.runAICheck?.() }
function handleOpenBidDocumentQualityCheck() { ctx.runBidDocumentQualityCheck?.() }
function handleReviewBid() { ctx.reviewerDialogVisible.value = true }
</script>
