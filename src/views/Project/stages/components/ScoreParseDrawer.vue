<!--
  Input: projectId, open options (stage, autoScore, file)
  Output: AI 评分标准解析 V3 抽屉（阶段 1 招标文件提取 + 阶段 2 投标文件实际打分）
  Pos: src/views/Project/stages/components/ScoreParseDrawer.vue
  一旦我被更新，务必更新我的开头注释。
-->
<template>
  <el-drawer
    v-model="visible"
    title="AI 评分标准解析"
    size="960px"
    :close-on-click-modal="false"
    class="score-parse-drawer"
  >
    <div v-if="loading" class="loading-state">
      <div class="loading-icon">🤖</div>
      <div class="loading-text">AI 正在解析评分标准，联动知识库做智能比对<span class="dots"></span></div>
    </div>

    <div v-else-if="error" class="error-state">
      <div>⚠ {{ error }}</div>
      <el-button size="small" style="margin-top: 12px" @click="reparse">重试</el-button>
    </div>

    <div v-else class="drawer-content-wrapper">
      <div class="scoring-overlay" :class="{ show: scoringOverlayVisible }">
        <div class="spinner"></div>
        <div class="scoring-text">AI 正在对标已上传标书进行实际打分...</div>
        <div class="scoring-sub">读取评分项 → 解析标书内容 → 比对知识库资质证书、仓库信息、品牌授权资料 → 计算客观项得分</div>
      </div>

      <!-- 顶部操作与阶段指示栏 -->
      <div class="top-action-bar">
        <div class="stage-tag-info">
          <span class="stage-badge" :class="currentStage === 1 ? 'stage-1' : 'stage-2'">
            {{ currentStage === 1 ? '阶段 1 · 招标文件解析' : '阶段 2 · 投标文件打分' }}
          </span>
          <span class="sub-hint">
            {{ currentStage === 1 ? '标书制作前评分项提取与满足度预判' : '针对已上传标书进行对标实际打分' }}
          </span>
        </div>
        <div class="btn-group">
          <button class="btn-tool" @click="reparse">🔄 重新解析</button>
          <button class="btn-tool primary" :disabled="importing || scoreItems.length === 0" @click="importToDrafts">📥 导入到评分草稿</button>
          <button class="btn-tool" @click="exportReport">📤 导出报告</button>
        </div>
      </div>

      <!-- 第一部分：招标文件解析 -->
      <div class="section-divider">
        <div class="section-title-bar">
          <span class="section-num">1</span>
          招标文件解析
          <span class="section-sub">（规则提取与满足预判）</span>
          <div class="section-actions">
            <button class="btn-primary-sm" @click="reparse">🔄 重新解析</button>
          </div>
        </div>

        <div class="toolbar">
          <div class="toolbar-info">
            <span class="doc-tag">📄 招标文件：{{ sourceFileName }}</span>
            <span class="doc-time">解析时间 {{ parseTime }}</span>
          </div>
        </div>

        <div class="collapse-item expanded">
          <div class="collapse-header" @click="isSection1Expanded = !isSection1Expanded">
            <div class="collapse-title">📋 评分标准提取</div>
            <div class="collapse-meta">
              <span class="pill ok">{{ scoreItems.length }} 项</span>
              <span class="collapse-arrow">{{ isSection1Expanded ? '▴' : '▾' }}</span>
            </div>
          </div>
          <div v-show="isSection1Expanded" class="collapse-body">
            <div v-if="scoreItems.length === 0" class="scoring-placeholder">
              <div class="placeholder-icon">📋</div>
              <div class="placeholder-text">尚未解析到评分标准，请上传招标文件后点击「重新解析」</div>
              <div class="placeholder-sub">
                <button class="btn-tool" @click="reparse">重新解析</button>
              </div>
            </div>
            <ScoreParseTable
              v-else
              mode="est"
              :items="scoreItems"
              :results="scoreResults"
              :total-weight="totalWeight"
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
          <span class="section-sub">（标书对标打分与引用建议）</span>
          <div class="section-actions">
            <button class="btn-primary-sm" :disabled="currentStage === 1 || scoreItems.length === 0" @click="runScoring(false)">
              {{ currentStage === 1 ? '⚡ AI 实际打分（需先上传标书）' : scored ? '⚡ 重新打分' : '⚡ AI 实际打分' }}
            </button>
          </div>
        </div>

        <div class="toolbar">
          <div class="toolbar-info">
            <span class="doc-tag">📦 投标文件：{{ bidFileName }}</span>
            <span class="doc-time">评分时间 {{ scoreTime }}</span>
          </div>
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

        <div v-else-if="!scored" class="scoring-placeholder">
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
          <b class="legend-title">阶段 1 说明：</b> 当前仅展示满足状态（基于知识库资质证书预判），<b>不计算实际得分</b>。上传投标文件后进入阶段 2，AI 将自动对标打分。<br />
          <span class="status-cell ok">✓ 满足</span> 知识库已匹配证书 ｜
          <span class="status-cell danger">✗ 不满足</span> 知识库未匹配 ｜
          <span class="status-cell neutral">待确认</span> 主观项或需人工判断
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
import { useScoreParseDrawer } from '@/composables/projectDetail/useScoreParseDrawer.js'
import ScoreItemDetailModal from './ScoreItemDetailModal.vue'
import ScoreParseTable from './ScoreParseTable.vue'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
})

const emit = defineEmits(['parsed', 'imported'])

const {
  visible, loading, error, isSection1Expanded, currentStage, scored, scoringOverlayVisible,
  sourceFileName, parseTime, bidFileName, scoreTime, importing, scoreItems, scoreResults,
  detailModalVisible, detailMode, selectedItem, selectedResult, totalWeight, objectiveWeight,
  subjectiveWeight, statsOkCount, statsDangerCount, statsNeutralCount, estTotalScore, actualTotalScore,
  openDetail, open, runScoring, reparse, exportReport, importToDrafts,
} = useScoreParseDrawer(props, emit)

defineExpose({ open, runScoring })
</script>

<style scoped>
.score-parse-drawer :deep(.el-drawer__body) { padding: 0; display: flex; flex-direction: column; }
.drawer-content-wrapper { position: relative; flex: 1; overflow-y: auto; padding: 20px 24px 32px; background: var(--bg-white); }
.loading-state, .error-state { text-align: center; padding: 80px 20px; color: var(--text-muted); }
.loading-icon { font-size: 32px; margin-bottom: 12px; }
.dots::after { content: '...'; animation: d 1.4s infinite; }
@keyframes d { 0%, 20% { content: ''; } 40% { content: '.'; } 60% { content: '..'; } 80%, 100% { content: '...'; } }

.top-action-bar { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: var(--bg-subtle); border-radius: var(--radius-md); margin-bottom: 20px; }
.stage-tag-info { display: flex; align-items: center; gap: 8px; }
.stage-badge { padding: 4px 10px; border-radius: var(--radius-sm); font-size: 12px; font-weight: 600; }
.stage-badge.stage-1 { background: var(--status-info-bg); color: var(--status-info-color); }
.stage-badge.stage-2 { background: var(--status-success-bg); color: var(--status-success-color); }
.sub-hint { font-size: 12px; color: var(--text-secondary-ui); }

.btn-group { display: flex; gap: 8px; }
.btn-tool { padding: 5px 12px; background: var(--bg-white); border: 1px solid var(--gray-200); border-radius: var(--radius-sm); font-size: 12px; cursor: pointer; color: var(--border-focus); transition: all 0.16s ease; display: inline-flex; align-items: center; gap: 4px; }
.btn-tool:hover { border-color: var(--brand-xiyu-logo); color: var(--brand-xiyu-logo); }
.btn-tool.primary { background: var(--brand-xiyu-logo); color: var(--bg-white); border-color: var(--brand-xiyu-logo); font-weight: 600; }
.btn-tool:disabled { opacity: 0.6; cursor: not-allowed; }

.section-divider { margin-top: 20px; margin-bottom: 16px; }
.section-divider:first-of-type { margin-top: 0; }
.section-title-bar { font-size: 15px; font-weight: 600; color: var(--text-primary); display: flex; align-items: center; gap: 8px; padding-bottom: 8px; border-bottom: 2px solid var(--brand-xiyu-logo); margin-bottom: 12px; }
.section-num { width: 22px; height: 22px; background: var(--brand-xiyu-logo); color: var(--bg-white); border-radius: 4px; display: inline-flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; }
.section-sub { font-size: 12px; font-weight: 400; color: var(--text-muted); margin-left: 4px; }
.section-actions { margin-left: auto; display: flex; gap: 6px; align-items: center; }
.btn-primary-sm { padding: 4px 12px; background: var(--brand-xiyu-logo); border: 1px solid var(--brand-xiyu-logo); border-radius: var(--radius-sm); font-size: 12px; font-weight: 600; cursor: pointer; color: var(--bg-white); transition: all 0.16s ease; display: inline-flex; align-items: center; gap: 4px; }
.btn-primary-sm:hover:not(:disabled) { background: var(--brand-xiyu-logo-hover); border-color: var(--brand-xiyu-logo-hover); }
.btn-primary-sm:disabled { background: var(--gray-100); border-color: var(--gray-100); color: var(--gray-300); cursor: not-allowed; }

.toolbar { font-size: 12px; color: var(--text-muted); margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center; }
.toolbar-info { display: flex; align-items: center; gap: 12px; width: 100%; }
.toolbar-info .doc-tag { display: inline-flex; align-items: center; gap: 4px; }
.toolbar-info .doc-time { margin-left: auto; white-space: nowrap; }

.collapse-item { background: var(--bg-white); border: 1px solid var(--border-base); border-radius: var(--radius-md); margin-bottom: 8px; overflow: hidden; }
.collapse-header { padding: 10px 14px; display: flex; justify-content: space-between; align-items: center; cursor: pointer; background: var(--bg-white); transition: background 0.16s ease; }
.collapse-header:hover { background: var(--bg-muted); }
.collapse-title { font-size: 13px; font-weight: 600; color: var(--gray-700); display: flex; align-items: center; gap: 6px; }
.collapse-meta { display: flex; align-items: center; gap: 8px; }
.collapse-arrow { color: var(--text-lighter); font-size: 14px; }
.collapse-body { padding: 12px 14px; border-top: 1px solid var(--gray-100); }

.scoring-placeholder { text-align: center; padding: 36px 20px; background: var(--bg-muted); border-radius: var(--radius-md); border: 1px dashed var(--gray-200); }
.placeholder-icon { font-size: 32px; margin-bottom: 10px; }
.placeholder-text { font-size: 14px; font-weight: 600; color: var(--text-primary-ui); margin-bottom: 6px; }
.placeholder-sub { font-size: 12px; color: var(--text-muted); }

.scoring-overlay { position: absolute; inset: 0; background: rgba(255, 255, 255, 0.92); display: none; align-items: center; justify-content: center; flex-direction: column; z-index: 100; border-radius: var(--radius-md); }
.scoring-overlay.show { display: flex; }
.spinner { width: 42px; height: 42px; border: 3px solid var(--gray-100); border-top-color: var(--brand-xiyu-logo); border-radius: 50%; animation: spin 0.8s linear infinite; margin-bottom: 14px; }
@keyframes spin { to { transform: rotate(360deg); } }
.scoring-text { font-size: 14px; color: var(--text-primary-ui); font-weight: 600; }
.scoring-sub { font-size: 12px; color: var(--text-muted); margin-top: 6px; }

.legend { font-size: 12px; color: var(--gray-650); line-height: 1.8; margin-top: 14px; padding: 10px 14px; background: var(--bg-muted); border-radius: var(--radius-sm); border: 1px solid var(--gray-100); }
.legend-title { color: var(--brand-xiyu-logo); }
.pill { font-size: 11px; padding: 1px 6px; border-radius: 3px; font-weight: 600; display: inline-block; }
.pill.ok { background: var(--status-success-bg); color: var(--status-success-color); }
.pill.info { background: var(--status-info-bg); color: var(--status-info-color); }
.pill.neutral { background: var(--status-neutral-bg); color: var(--status-neutral-color); }
.status-cell { font-size: 12px; font-weight: 500; }
.status-cell.ok { color: var(--status-success-color); }
.status-cell.danger { color: var(--status-danger-color); }
.status-cell.neutral { color: var(--text-muted); }
</style>
