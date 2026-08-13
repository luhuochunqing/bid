<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    multiple
    collapse-tags
    collapse-tags-tooltip
    clearable
    size="small"
    style="width: 100%"
    @update:model-value="$emit('update:modelValue', $event)"
    @clear="$emit('update:modelValue', [])"
  >
    <el-option
      v-for="item in options"
      :key="item.value"
      :label="item.label"
      :value="item.value"
      :disabled="item.disabled"
    />
  </el-select>
</template>

<script setup>
defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  options: {
    type: Array,
    default: () => []
  },
  placeholder: {
    type: String,
    default: '请选择'
  }
})

defineEmits(['update:modelValue'])
</script>