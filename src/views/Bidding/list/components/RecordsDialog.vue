<template>
  <el-dialog :model-value="modelValue" title="分发记录" width="720px" @update:model-value="$emit('update:modelValue', $event)">
    <el-table :data="records" max-height="420">
      <el-table-column prop="tenderTitle" label="标讯标题" min-width="220" show-overflow-tooltip />
      <el-table-column prop="assignee" label="被分配人" width="120" />
      <el-table-column prop="type" label="分发方式" width="110">
        <template #default="{ row = {} } = {}">
          <el-tag :type="row.type === 'auto' ? 'success' : 'primary'" size="small">
            {{ row.type === 'auto' ? '智能分发' : '手动指定' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="time" label="分发时间" width="170" />
      <el-table-column prop="operator" label="操作人" width="100" />
    </el-table>
    <template #footer>
      <el-button type="primary" @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  records: { type: Array, default: () => [] },
})

defineEmits(['update:modelValue'])
</script>
