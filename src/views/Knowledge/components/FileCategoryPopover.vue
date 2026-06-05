<template>
  <el-popover
    :width="300"
    placement="top"
    trigger="hover"
    :open-delay="500"
    popper-class="file-category-popover"
  >
    <template #reference>
      <div class="popover-trigger">
        <slot>
          <el-button type="primary" link>
            <el-icon class="mr-1"><Files /></el-icon>
            查看文件明细
          </el-button>
        </slot>
      </div>
    </template>

    <div class="popover-content">
      <h4 class="popover-title">项目文件分类统计</h4>
      <div class="category-list">
        <div v-for="cat in categoryItems" :key="cat.key" class="category-item" :class="`border-${cat.key}`">
          <div class="category-label">
            <span class="dot" :class="`bg-${cat.key}`"></span>
            {{ cat.label }}
          </div>
          <div class="category-count">{{ cat.count }}</div>
        </div>
        <div class="total-bar">
          <span>总计</span>
          <span class="total-count">{{ totalCount }}</span>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { computed } from 'vue'
import { Files } from '@element-plus/icons-vue'

const props = defineProps({
  categoryDetails: {
    type: Object,
    default: null
  },
  fileCount: {
    type: Number,
    default: 0
  }
})

const CATEGORY_MAP = {
  TENDER: { key: 'tender', label: '招标文件', color: 'var(--el-color-primary)' },
  BID: { key: 'bid', label: '标书文件', color: 'var(--el-color-success)' },
  OPEN_LIST: { key: 'open', label: '开标一览表', color: 'var(--el-color-warning)' },
  WIN_NOTICE: { key: 'award', label: '中标通知书', color: 'var(--el-color-warning)' },
  DEPOSIT_RECEIPT: { key: 'deposit', label: '保证金银行回单', color: 'var(--el-color-info)' },
  // legacy compat for existing data
  CONTRACT: { key: 'contract', label: '合同文件', color: 'var(--el-color-info)' },
  PROCESS: { key: 'process', label: '过程文件', color: 'var(--el-color-info)' },
  RETROSPECTIVE: { key: 'retrospective', label: '复盘文件', color: 'var(--el-color-danger)' },
  OTHER: { key: 'other', label: '其他', color: 'var(--el-color-info)' }
}

const categoryItems = computed(() => {
  const details = props.categoryDetails || {}
  return Object.entries(CATEGORY_MAP).map(([code, config]) => ({
    key: config.key,
    label: config.label,
    color: config.color,
    count: details[code] || 0
  }))
})

const totalCount = computed(() => {
  if (props.categoryDetails) {
    return Object.values(props.categoryDetails).reduce((sum, v) => sum + (Number(v) || 0), 0)
  }
  return props.fileCount || 0
})
</script>

<style lang="scss">
.file-category-popover {
  padding: 12px 16px !important;
  border-radius: 8px !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08) !important;
  border: 1px solid var(--el-border-color-lighter) !important;
}
</style>

<style scoped lang="scss">
.popover-trigger {
  display: inline-block;
}

.popover-content {
  min-height: 40px;
}

.popover-title {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 8px;
}

.category-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.category-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 13px;
  border-left: 3px solid transparent;
  transition: all 0.2s ease;
  background-color: var(--el-fill-color-blank);

  &:hover {
    background-color: var(--el-fill-color-light);
    transform: translateX(2px);
  }
}

.border-tender { border-left-color: var(--el-color-primary); }
.border-bid { border-left-color: var(--el-color-success); }
.border-award { border-left-color: var(--el-color-warning); }
.border-contract { border-left-color: var(--el-color-info); }
.border-process { border-left-color: var(--el-color-info); }
.border-retrospective { border-left-color: var(--el-color-danger); }
.border-other { border-left-color: var(--el-color-info); }

.category-label {
  display: flex;
  align-items: center;
  color: var(--el-text-color-regular);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 8px;
}

.bg-tender { background-color: var(--el-color-primary); }
.bg-bid { background-color: var(--el-color-success); }
.bg-award { background-color: var(--el-color-warning); }
.bg-contract { background-color: var(--el-color-info); }
.bg-process { background-color: var(--el-color-info); }
.bg-retrospective { background-color: var(--el-color-danger); }
.bg-other { background-color: var(--el-color-info); }

.category-count {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.total-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.total-count {
  color: var(--el-color-primary);
  font-size: 14px;
}
</style>
