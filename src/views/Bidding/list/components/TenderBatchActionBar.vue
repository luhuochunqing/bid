<template>
  <div v-if="selectedCount > 0" class="batch-actions">
    <div class="batch-info">
      <el-checkbox :model-value="selectAllChecked" :indeterminate="isIndeterminate" @change="$emit('select-all', $event)">
        已选择 {{ selectedCount }} 条标讯
      </el-checkbox>
    </div>
  </div>
</template>

<script setup>
defineProps({
  selectedCount: { type: Number, default: 0 },
  selectAllChecked: { type: Boolean, default: false },
  isIndeterminate: { type: Boolean, default: false },
})

defineEmits(['select-all'])
</script>
