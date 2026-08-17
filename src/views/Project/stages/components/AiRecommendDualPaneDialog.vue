<template>
  <el-dialog
    :model-value="modelValue"
    title=""
    width="1100px"
    :close-on-click-modal="false"
    destroy-on-close
    class="dual-pane-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="selectedCase" class="dual-pane-body">
      <div class="dual-pane-left">
        <div class="dp-section"><div class="dp-label">命中评分项</div><div class="dp-val">{{ selectedCase.scoringTitle }}</div></div>
        <div class="dp-section"><div class="dp-label">匹配度</div><div class="dp-val"><el-tag :type="selectedCase.score >= 85 ? 'danger' : 'primary'">{{ selectedCase.score }}% {{ selectedCase.scoreLabel }}</el-tag></div></div>
        <div class="dp-section"><div class="dp-label">来源项目</div><div class="dp-val">{{ selectedCase.sourceProjectName }}</div></div>
        <div class="dp-section"><div class="dp-label">中标结果</div><div class="dp-val">{{ selectedCase.bidResult === 'WON' ? '中标' : selectedCase.bidResult === 'LOST' ? '未中标' : '—' }}</div></div>
        <div class="dp-section"><div class="dp-label">复用次数</div><div class="dp-val">{{ selectedCase.reuseCount }} 次</div></div>
        <div class="dp-section"><div class="dp-label">应答原文</div><div class="dp-snippet">{{ selectedCase.responseTextSummary || selectedCase.responseText }}</div></div>
      </div>
      <div class="dual-pane-right">
        <div class="reader-head">📄 {{ selectedCase.docName || selectedCase.sourceProjectName || '来源标书' }}<span v-if="selectedCase.page"> · 第 {{ selectedCase.page }} 页</span></div>
        <div class="reader-page">
          <h5>{{ selectedCase.scoringTitle }}</h5>
          <div v-html="safeHtml(selectedCase.readerHTML || selectedCase.highlightedText || selectedCase.responseText)"></div>
        </div>
      </div>
    </div>
    <template #footer>
      <div class="dp-footer">
        <el-button type="primary" @click="emit('reuse', selectedCase)">📋 复用此案例</el-button>
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { safeHtml } from '@/utils/safeHtml.js'

defineProps({
  modelValue: { type: Boolean, default: false },
  selectedCase: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue', 'reuse'])
</script>

<style scoped>
.dual-pane-body { display: flex; gap: 0; min-height: 400px; }
.dual-pane-left { flex: 0 0 380px; padding: 0 16px 0 0; overflow-y: auto; border-right: 1px solid var(--gray-200); }
.dual-pane-right { flex: 1; padding: 0 0 0 16px; overflow-y: auto; }
.dp-section { margin-bottom: 14px; }
.dp-label { font-size: 11px; color: var(--text-muted); font-weight: 600; margin-bottom: 4px; text-transform: uppercase; letter-spacing: .5px; }
.dp-val { font-size: 13px; color: var(--gray-750); line-height: 1.6; }
.dp-snippet { background: var(--el-fill-color-lighter); border-left: 3px solid #2E7659; padding: 10px 12px; border-radius: 4px; font-size: 12px; line-height: 1.7; color: var(--text-secondary-ui); }
.reader-head { font-size: 12px; color: var(--text-muted); margin-bottom: 10px; }
.reader-page { background: var(--bg-card); border: 1px solid var(--gray-250); border-radius: 4px; padding: 16px 20px; font-size: 13px; line-height: 1.9; color: var(--gray-750); }
.reader-page h5 { font-size: 14px; font-weight: 600; margin: 0 0 8px; color: var(--gray-800); }
.reader-page :deep(.hit) { background: #fff4b8; padding: 1px 3px; border-radius: 2px; }
.dp-footer { display: flex; justify-content: flex-end; gap: 8px; }
</style>
