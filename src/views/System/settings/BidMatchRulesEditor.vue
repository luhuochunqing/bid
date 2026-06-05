<template>
  <div class="rules-editor">
    <div class="rules-toolbar">
      <span>{{ dimension.name }}</span>
      <el-button link type="primary" @click="$emit('add-rule', dimension)">
        <el-icon><Plus /></el-icon>
        新增规则
      </el-button>
    </div>

    <el-table :data="dimension.rules" size="small" class="rules-table">
      <el-table-column label="启用" width="72">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" />
        </template>
      </el-table-column>
      <el-table-column label="规则名称" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.name" placeholder="规则名称" />
        </template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-select v-model="row.type">
            <el-option
              v-for="option in ruleTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="证据字段" min-width="170">
        <template #default="{ row }">
          <el-select v-model="row.evidenceKey" filterable allow-create default-first-option>
            <el-option
              v-for="option in evidenceKeyOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="关键词/阈值" min-width="210">
        <template #default="{ row }">
          <el-select
            v-if="row.type === 'KEYWORD'"
            v-model="row.keywords"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="关键词"
          />
          <div v-else-if="row.type === 'RANGE'" class="number-range">
            <el-input-number v-model="row.minValue" :controls="false" placeholder="最小值" />
            <span>-</span>
            <el-input-number v-model="row.maxValue" :controls="false" placeholder="最大值" />
          </div>
          <el-input-number
            v-else-if="row.type === 'QUANTITY'"
            v-model="row.minValue"
            :controls="false"
            placeholder="最小值"
          />
          <span v-else class="exists-rule">存在即可</span>
        </template>
      </el-table-column>
      <el-table-column label="权重" width="120">
        <template #default="{ row }">
          <el-input-number
            v-model="row.weight"
            :min="0"
            :max="100"
            :step="5"
            controls-position="right"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="82" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" @click="$emit('remove-rule', dimension, row.key)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'

defineProps({
  dimension: { type: Object, required: true },
  evidenceKeyOptions: { type: Array, required: true },
  ruleTypeOptions: { type: Array, required: true },
})

defineEmits(['add-rule', 'remove-rule'])
</script>

<style scoped>
.rules-editor {
  display: grid;
  gap: 10px;
  padding: 8px 0 12px 56px;
}

.rules-toolbar,
.number-range {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rules-toolbar {
  justify-content: space-between;
  color: #52624c;
  font-weight: 700;
}

.rules-table {
  width: 100%;
}

.number-range :deep(.el-input-number) {
  width: 96px;
}

.exists-rule {
  color: #66705f;
}
</style>
