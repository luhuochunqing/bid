<!--
  Input: projectId, open options (stage, autoScore, file)
  Output: AI 评分标准解析 V3 抽屉（折叠外预判更新及本次统计/原因 + 阶段 2 投标文件实际打分）
  Pos: src/views/Project/stages/components/ScoreParseDrawer.vue
  注：保留解析重试与 E2E 钩子；抽屉遮罩锁定内容滚动，候选支持复核采纳。
      方案资格按原文评分方案判定，清楚标识方案内、方案外与来源未验证事实；多方案需显式选择（2026-09-24）。
-->
<template>
  <el-drawer
    v-model="visible"
    title="AI 评分标准解析"
    size="960px"
    :close-on-click-modal="false"
    class="score-parse-drawer"
  >
    <div v-if="loading && !scoreItems.length && !latestExtraction" class="loading-state">
      <div class="loading-icon">🤖</div>
      <div class="loading-text">AI 正在解析评分标准，联动知识库做智能比对<span class="dots"></span></div>
    </div>
    <div v-if="error" class="error-state">
      <el-alert :title="errorPresentation.title" :description="errorPresentation.message" type="error" show-icon :closable="false" class="ai-document-error-alert" />
      <div v-if="errorPresentation.detail && errorPresentation.scanned" class="error-detail">原始错误：{{ errorPresentation.detail }}</div>
      <div class="error-hint">扫描大 PDF 可压缩后在立项未提交前替换文件；也可以先进行 OCR，再重新解析。</div>
      <el-button size="small" style="margin-top: 12px" :disabled="loading || scoringOverlayVisible" @click="reparse">重新解析</el-button>
    </div>

    <div v-if="!loading || scoreItems.length || latestExtraction" class="drawer-content-wrapper" :class="{ 'scroll-locked': scoringOverlayVisible }" data-test="score-parse-content">
      <div class="scoring-overlay" :class="{ show: scoringOverlayVisible }">
        <div class="spinner"></div>
        <div class="scoring-text" data-test="score-parse-overlay-text">AI 正在对标已上传标书进行实际打分...</div>
        <div class="scoring-sub">读取评分项 → 解析标书内容 → 比对知识库资质证书、仓库信息、品牌授权资料 → 计算客观项得分</div>
      </div>

      <div class="toolbar estimate-refresh-toolbar">
        <span v-if="scoreItems.length" class="doc-time">证书新鲜度：{{ staleEstimateCount }} 项待核验 · 证书核验时间 {{ estimateCheckedAt }}（仅反映证书变化）</span>
        <span v-else class="doc-time">暂无生效评分项，无法更新知识库预判</span>
        <button class="btn-tool" data-test="refresh-estimates" :disabled="!scoreItems.length || estimatesRefreshing || loading || scoringOverlayVisible" @click="refreshEstimates">{{ estimatesRefreshing ? '正在更新知识库预判…' : '更新知识库预判' }}</button>
      </div>
      <p v-if="estimateRefreshError" role="alert" data-test="estimate-refresh-error">{{ estimateRefreshError }}</p>
      <section v-if="estimateRefreshResult" role="status" data-test="estimate-refresh-result" aria-label="本次知识库预判刷新结果">
        <p>本次处理 {{ estimateRefreshResult.processedCount }} 项 · 有数值得分 {{ estimateRefreshResult.scoredCount }} 项（含 0 分）· 无分值 {{ estimateRefreshResult.pendingCount }} 项</p>
        <ul v-if="estimateRefreshResult.pendingItems.length">
          <li v-for="item in estimateRefreshResult.pendingItems" :key="item.id">{{ item.dim }}：{{ item.reason }}</li>
        </ul>
      </section>

      <!-- 第一部分：招标文件解析 -->
      <div class="section-divider">
        <div class="section-title-bar">
          <span class="section-num">1</span>
          招标文件解析
          <div class="section-actions">
            <button v-if="allowExtractionActions" class="btn-primary-sm" data-test="score-parse-reparse" :disabled="loading || scoringOverlayVisible" @click="reparse">🔄 重新解析</button>
          </div>
        </div>

        <p v-if="showExtractionReview && extractionWarning" role="alert">{{ extractionWarning }}</p>
        <button v-if="showExtractionReview && (latestExtraction || extractionWarning || error)" class="btn-tool" :disabled="loading" @click="refreshExtraction">刷新并重新核对版本</button>
        <div class="toolbar">
          <div class="toolbar-info">
            <span class="doc-tag">📄 招标文件：{{ sourceFileName }}</span>
            <span v-if="decisionBadgeVisible" :class="['doc-tag', 'decision-tag', decisionBadgeType]" data-test="score-parse-decision-status">
              {{ decisionBadgeText }}
            </span>
            <span v-if="reviewReason && !latestExtraction" class="doc-tag review-reason-tag" :title="reviewReason">
              理由: {{ reviewReason }}
            </span>
            <span v-if="showExtractionReview" class="doc-time">生效规则来源时间 {{ activeParseTime }}</span>
          </div>
        </div>

        <div class="collapse-item expanded">
          <div class="collapse-header" @click="isSection1Expanded = !isSection1Expanded">
            <div class="collapse-title">📋 {{ showExtractionReview && candidateFacts.length > 0 && scoreItems.length > 0 ? '已生效评分项 / 最新候选' : '当前生效评分项' }}</div>
            <div class="collapse-meta">
              <span class="pill ok">{{ scoreItems.length }} 项</span>
              <span class="collapse-arrow">{{ isSection1Expanded ? '▴' : '▾' }}</span>
            </div>
          </div>
          <div v-show="isSection1Expanded" class="collapse-body">
            <section v-if="sourceDiagnostics.length && (!latestExtraction || latestExtraction.payloadVersion === 0)"
              aria-label="本次提取需核对的原因" data-test="parse-source-diagnostics">
              <p v-for="diagnostic in sourceDiagnostics" :key="diagnostic">{{ extractionReason(diagnostic) }}</p>
            </section>
            <!-- 最新任务的候选与既有正式结果可以并存；新解析待复核时必须仍可见。 -->
            <ScoreExtractionPanel
              v-if="latestExtraction && showExtractionReview"
              :snapshot="latestExtraction"
              :active-items="scoreItems"
              :active-total="totalWeight"
              :blocked="!allowExtractionActions || activationBlocked || loading || scoringOverlayVisible"
              :busy="activating"
              @activate="allowExtractionActions && activateExtraction($event)"
            />
            <ScoreCandidateReviewCard
              v-if="showExtractionReview && !latestExtraction && candidateFacts.length > 0"
              :candidate-facts="candidateFacts"
              :readonly="!allowExtractionActions"
              :schemes="schemes"
              :selected-scheme-id="selectedSchemeId"
              :scheme-selection-required="schemeSelectionRequired"
              :candidate-total-weight="candidateTotalWeight"
              :declared-total-weight="declaredTotalWeight"
              :adoption-status="adoptionStatus"
              :is-adopting-candidates="isAdoptingCandidates"
              :reparse-disabled="!allowExtractionActions || loading || scoringOverlayVisible"
              @adopt="allowExtractionActions && adoptCandidateFacts($event)"
              @scheme-change="selectedSchemeId = $event"
              @reparse="reparse"
            />

            <div v-if="scoreItems.length === 0 && !latestExtraction && !candidateFacts.length" class="scoring-placeholder">
              <div class="placeholder-icon">📋</div>
              <div class="placeholder-text">尚未解析到评分标准，将使用立项招标文件解析。若立项尚未上传，请先在立项阶段上传招标文件。</div>
              <div class="placeholder-sub">
                <button class="btn-tool" :disabled="loading || scoringOverlayVisible" @click="reparse">重新解析</button>
              </div>
            </div>
            <h3 v-if="scoreItems.length">当前生效评分项</h3>
            <ScoreParseTable
              v-if="scoreItems.length"
              mode="est"
              :items="scoreItems"
              :results="scoreResults"
              :total-weight="totalWeight"
              :declared-total="activeDeclaredTotal"
              :versioned="Boolean(latestExtraction)"
              :stats-ok-count="statsOkCount"
              :stats-danger-count="statsDangerCount"
              :stats-neutral-count="statsNeutralCount"
              :objective-weight="objectiveWeight"
              :subjective-weight="subjectiveWeight"
              :highlight-score="estTotalScore"
              @open-detail="openDetail"
            />
          </div>
        </div>
      </div>

      <!-- 第二部分：投标文件评分 -->
      <div class="section-divider">
        <div class="section-title-bar">
          <span class="section-num">2</span>
          投标文件评分
          <div class="section-actions">
            <button class="btn-primary-sm" :disabled="loading || scoringOverlayVisible || currentStage === 1 || scoreItems.length === 0" @click="runScoring({ auto: false })">
              {{ currentStage === 1 ? '⚡ AI 实际打分（需先上传标书）' : scored ? '⚡ 重新打分' : '⚡ AI 实际打分' }}
            </button>
          </div>
        </div>

        <div class="toolbar">
          <div class="toolbar-info">
            <span class="doc-tag">📦 投标文件：{{ bidFileName }}</span>
            <span v-if="scoringHint" class="doc-tag">{{ scoringHint }}</span>
            <span v-if="circuitHint" class="doc-tag">{{ circuitHint }}</span>
            <span class="doc-time">评分时间 {{ scoreTime }}</span>
          </div>
        </div>
        <div v-if="normalizationWarning" class="normalization-warning">
          <span class="warning-icon">⚠</span>
          <span class="warning-text">{{ normalizationWarning }}</span>
        </div>
        <div v-if="scoreItems.length === 0" class="scoring-placeholder">
          <div class="placeholder-icon">📋</div>
          <div class="placeholder-text">尚未解析到评分标准，无法进行投标文件对标打分</div>
          <div class="placeholder-sub">请先在阶段 1 完成评分标准提取与解析</div>
        </div>

        <div v-else-if="currentStage === 1" class="scoring-placeholder">
          <div class="placeholder-icon">📦</div>
          <div class="placeholder-text">尚未上传投标文件</div>
          <div class="placeholder-sub">在标书制作中上传投标文件后，AI 将自动对标评分标准进行实际打分</div>
        </div>

        <div v-else-if="!scored && !hasScoreResults" class="scoring-placeholder">
          <div class="placeholder-icon">⚡</div>
          <div class="placeholder-text">已检测到投标文件，尚未打分</div>
          <div class="placeholder-sub">点击上方「AI 实际打分」按钮开始对标打分</div>
        </div>

        <div v-else class="scoring-table-container">
          <ScoreParseTable
            mode="actual"
            :items="scoreItems"
            :results="scoreResults"
            :total-weight="totalWeight"
              :declared-total="activeDeclaredTotal"
              :versioned="Boolean(latestExtraction)"
            :stats-ok-count="statsOkCount"
            :stats-danger-count="statsDangerCount"
            :stats-neutral-count="statsNeutralCount"
            :objective-weight="objectiveWeight"
            :subjective-weight="subjectiveWeight"
            :highlight-score="actualTotalScore"
            @open-detail="openDetail"
          />
        </div>
      </div>

      <!-- 底部图例 -->
      <div class="legend">
        <template v-if="currentStage === 2 && scored">
          <b class="legend-title">说明：</b>
          <span class="pill info">客观项</span> AI 基于标书内容 + 知识库证书自动判定，计入总分；
          <span class="pill neutral">主观项</span> 需评标专家人工评审，AI 不计分。
        </template>
        <template v-else-if="currentStage === 2">
          <b class="legend-title">说明：</b> 阶段 2 已检测到投标文件，点击「AI 实际打分」按钮开始对标打分。
        </template>
        <template v-else>
          <b class="legend-title">阶段 1 说明：</b> 预计得分为<b>知识库粗估，最终评审另算</b>：有则满分、无则零分、部分按比例。上传投标文件后进入阶段 2，AI 将自动对标打分。<br />
          <span class="status-cell ok">✓ 满足</span> 可用资料全部满足 ｜
          <span class="status-cell danger">✗ 不满足</span> 知识库未匹配 ｜
          <span class="status-cell neutral">待确认</span> 部分满足（仍显示数值）、主观项或无对应资料源
        </template>
      </div>
    </div>

    <!-- 评分项详情与建议弹窗 -->
    <ScoreItemDetailModal
      v-model:visible="detailModalVisible"
      :mode="detailMode"
      :item="selectedItem"
      :result="selectedResult"
    />
  </el-drawer>
</template>

<script setup>
import { extractionReason } from '@/composables/projectDetail/scoreExtractionPresentation.js'
import { useScoreParseDrawer } from '@/composables/projectDetail/useScoreParseDrawer.js'
import ScoreExtractionPanel from './ScoreExtractionPanel.vue'
import ScoreCandidateReviewCard from './ScoreCandidateReviewCard.vue'
import ScoreItemDetailModal from './ScoreItemDetailModal.vue'
import ScoreParseTable from './ScoreParseTable.vue'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
  showExtractionReview: { type: Boolean, default: true },
  allowExtractionActions: { type: Boolean, default: true },
})

const emit = defineEmits(['parsed'])

const {
  latestExtraction, activeParseTime, activeDeclaredTotal, extractionWarning, activationBlocked, activating, activateExtraction, refreshExtraction, hasScoreResults,
  visible, loading, error, errorPresentation, isSection1Expanded, currentStage, scored, scoringOverlayVisible,
  sourceFileName, bidFileName, scoreTime, scoringHint, circuitHint, normalizationWarning,
  scoreItems, scoreResults, detailModalVisible, detailMode, selectedItem, selectedResult, totalWeight, objectiveWeight,
  subjectiveWeight, statsOkCount, statsDangerCount, statsNeutralCount, estTotalScore, actualTotalScore,
  openDetail, open, runScoring, reparse,
  estimatesRefreshing, estimateRefreshError, estimateRefreshResult, staleEstimateCount, estimateCheckedAt, refreshEstimates,
  decisionBadgeType, decisionBadgeText, decisionBadgeVisible, reviewReason, sourceDiagnostics,
  candidateFacts, schemes, selectedSchemeId, schemeSelectionRequired, candidateTotalWeight, declaredTotalWeight,
  adoptionStatus, isAdoptingCandidates, adoptCandidateFacts,
} = useScoreParseDrawer(props, emit)

defineExpose({ open, runScoring, scoringOverlayVisible, loading })
</script>

<style scoped src="./scoreParseDrawer.css"></style>
<style scoped>
.estimate-refresh-toolbar {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--bg-white);
  padding: 8px 0;
  gap: 16px;
  flex-wrap: wrap;
}
.estimate-refresh-toolbar .btn-tool { margin-left: auto; flex-shrink: 0; }
</style>

<!-- 全局：el-drawer teleport 后根元素不带 scoped data-v，scoped/:deep 选择器无法命中，必须用类前缀全局规则 -->
<style>
.score-parse-drawer .el-drawer__body { padding: 0; display: flex; flex-direction: column; }
.score-parse-drawer .el-drawer__header { padding: 16px 24px 8px; }
</style>
