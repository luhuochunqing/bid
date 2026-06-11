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
    >
      <!-- Fixed column: Role name -->
      <el-table-column
        prop="roleLabel"
        :label="fixedColumn.label"
        :width="fixedColumn.width"
        fixed
      />

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
            <!-- Free text single line -->
            <el-input
              v-if="col.type === 'text'"
              v-model="row[col.key]"
              :disabled="disabled"
              size="small"
              :placeholder="col.placeholder"
              clearable
              @change="onDataChange"
            />
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
              <el-option label="支持" value="支持" />
              <el-option label="中立" value="中立" />
              <el-option label="反对" value="反对" />
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
                :key="opt"
                :label="opt"
                :value="opt"
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
                :key="opt"
                :label="opt"
                :value="opt"
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
} from './customerInfoMatrixConfig.js'

defineProps({
  localData: { type: Array, required: true },
  editableColumns: { type: Array, required: true },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['data-change'])
const fixedColumn = CUSTOMER_INFO_COLUMNS[0]

function onDataChange() {
  emit('data-change')
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
</style>
