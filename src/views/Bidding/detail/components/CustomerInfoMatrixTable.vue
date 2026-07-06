<template>
  <div class="matrix-table-wrapper">
    <el-table
      :data="localData"
      border
      stripe
      size="small"
      style="width: 100%"
      max-height="600"
      :show-header="true"
      highlight-current-row
      empty-text="暂无客户信息"
    >
      <!-- Editable columns -->
      <el-table-column
        v-for="col in editableColumns"
        :key="col.key"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
      >
        <template #default="{ row }">
          <template v-if="row">
            <!-- Free text single line：长文本悬停 Tooltip 展示全量（CO-519） -->
            <el-tooltip
              v-if="col.type === 'text'"
              :content="String(row[col.key] || '')"
              :disabled="!shouldShowOverflowTooltip(row[col.key])"
              placement="top"
              :show-after="300"
            >
              <el-input
                v-model="row[col.key]"
                :disabled="disabled"
                size="small"
                :placeholder="col.placeholder"
                clearable
                @change="onDataChange"
              />
            </el-tooltip>
            <!-- Yes/No dropdown -->
            <el-select
              v-else-if="col.type === 'yesno'"
              v-model="row[col.key]"
              :disabled="disabled"
              size="small"
              placeholder="请选择"
              clearable
              style="width: 100%"
              @change="onDataChange"
            >
              <el-option label="是" :value="true" />
              <el-option label="否" :value="false" />
            </el-select>
            <!-- Support/Neutral/Oppose dropdown -->
            <el-select
              v-else-if="col.type === 'tendency'"
              v-model="row[col.key]"
              :disabled="disabled"
              size="small"
              placeholder="请选择"
              clearable
              style="width: 100%"
              @change="onDataChange"
            >
              <el-option
                v-for="opt in TENDENCY_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <!-- Position dropdown (14 options) -->
            <el-select
              v-else-if="col.type === 'position'"
              v-model="row[col.key]"
              :disabled="disabled"
              size="small"
              placeholder="请选择"
              clearable
              style="width: 100%"
              @change="onDataChange"
            >
              <el-option
                v-for="opt in POSITION_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <!-- Contact method dropdown (7 options) -->
            <el-select
              v-else-if="col.type === 'contactMethod'"
              v-model="row[col.key]"
              :disabled="disabled"
              size="small"
              placeholder="请选择"
              clearable
              style="width: 100%"
              @change="onDataChange"
            >
              <el-option
                v-for="opt in CONTACT_METHOD_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <!-- Switch for clear winner bid info -->
            <el-switch
              v-else-if="col.type === 'switch'"
              v-model="row[col.key]"
              :disabled="disabled"
              @change="onDataChange"
            />
            <!-- 6-level impact dropdown -->
            <el-select
              v-else-if="col.type === 'impact'"
              v-model="row[col.key]"
              :disabled="disabled"
              size="small"
              placeholder="请选择"
              clearable
              style="width: 100%"
              @change="onDataChange"
            >
              <el-option
                v-for="opt in IMPACT_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import {
  CONTACT_METHOD_OPTIONS,
  CUSTOMER_INFO_COLUMNS,
  IMPACT_OPTIONS,
  POSITION_OPTIONS,
  TENDENCY_OPTIONS,
} from './customerInfoMatrixConfig.js'

defineProps({
  localData: { type: Array, required: true },
  editableColumns: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['data-change'])

function onDataChange() {
  emit('data-change')
}

/**
 * CO-519: 判断字段值长度是否超出列宽可视范围，超长则启用 hover Tooltip 展示全量
 * 列宽 200px / 160px，small 字号 12px，中文字符约 12px 宽，15 字符阈值覆盖大部分超长场景
 */
function shouldShowOverflowTooltip(value, maxChars = 15) {
  const str = value == null ? '' : String(value)
  return str.length > maxChars
}
</script>

<style scoped>
.matrix-table-wrapper {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}

/* 表头与单元格：文字单行显示，不换行 */
.matrix-table-wrapper :deep(.el-table th.el-table__cell),
.matrix-table-wrapper :deep(.el-table td.el-table__cell) {
  white-space: nowrap;
}

.matrix-table-wrapper :deep(.cell) {
  white-space: nowrap;
}

/* CO-519: interactions.css 对 .is-disabled 全局设了 pointer-events:none，
   导致 disabled el-input 上的 el-tooltip 收不到 hover 事件，tooltip 不弹出。
   这里恢复 .el-tooltip__trigger 的 pointer-events，仅让 hover 触发 tooltip；
   内层 input 仍 disabled 不可编辑。 */
.matrix-table-wrapper :deep(.el-tooltip__trigger.is-disabled) {
  pointer-events: auto;
}

</style>
