<template>
  <el-dialog
    v-model="visible"
    title="资质证书附件关联结果"
    width="680px"
    :close-on-click-modal="false"
    @closed="handleClosed"
  >
    <div class="attach-result-dialog">
      <!-- 统计卡片 -->
      <div class="stat-cards">
        <div class="stat-card total">
          <div class="stat-number">{{ data.total }}</div>
          <div class="stat-label">总文件数</div>
        </div>
        <div class="stat-card success">
          <div class="stat-number">{{ data.success }}</div>
          <div class="stat-label">成功关联</div>
        </div>
        <div class="stat-card failed">
          <div class="stat-number">{{ data.failed }}</div>
          <div class="stat-label">未匹配</div>
        </div>
      </div>

      <!-- 成功关联列表 -->
      <div v-if="data.matched?.length" class="matched-section">
        <div class="section-title">成功关联</div>
        <el-table :data="data.matched" size="small" border style="width: 100%">
          <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
          <el-table-column prop="certificateNo" label="证书编号" width="140" show-overflow-tooltip />
          <el-table-column prop="qualificationName" label="证书名称" min-width="140" show-overflow-tooltip />
        </el-table>
      </div>

      <!-- 未匹配文件列表 -->
      <div v-if="data.unmatched?.length" class="unmatched-section">
        <div class="section-title">未匹配文件</div>
        <el-table :data="data.unmatched" size="small" border style="width: 100%">
          <el-table-column prop="fileName" label="文件名" min-width="240" show-overflow-tooltip />
          <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
        </el-table>
      </div>

      <!-- 全部成功提示 -->
      <div v-if="!data.unmatched?.length && data.matched?.length" class="all-success">
        <el-icon class="success-icon" :size="48"><CircleCheck /></el-icon>
        <p>全部附件关联成功</p>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { CircleCheck } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  data: {
    type: Object,
    default: () => ({ total: 0, success: 0, failed: 0, matched: [], unmatched: [] })
  }
})

const emit = defineEmits(['update:modelValue', 'closed'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const handleClosed = () => {
  emit('closed')
}
</script>

<style scoped lang="scss">
.attach-result-dialog {
  .stat-cards {
    display: flex;
    gap: 16px;
    margin-bottom: 20px;
  }
  .stat-card {
    flex: 1;
    text-align: center;
    padding: 16px 12px;
    border-radius: 8px;
    background: var(--el-fill-color-light);
    border: 1px solid var(--el-border-color-lighter);
    &.success {
      border-color: var(--el-color-success-light);
      background: var(--el-color-success-light-9);
      .stat-number { color: var(--el-color-success); }
    }
    &.failed {
      border-color: var(--el-color-warning-light);
      background: var(--el-color-warning-light-9);
      .stat-number { color: var(--el-color-warning); }
    }
    .stat-number {
      font-size: 28px;
      font-weight: 600;
      line-height: 1.2;
      color: var(--el-text-color-primary);
    }
    .stat-label {
      margin-top: 4px;
      font-size: 13px;
      color: var(--el-text-color-secondary);
    }
  }
  .matched-section,
  .unmatched-section {
    margin-bottom: 16px;
    .section-title {
      font-weight: 500;
      margin-bottom: 8px;
      color: var(--el-text-color-primary);
    }
  }
  .all-success {
    text-align: center;
    padding: 24px 0;
    .success-icon {
      margin-bottom: 12px;
    }
    p {
      color: var(--el-text-color-secondary);
      font-size: 14px;
    }
  }
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
