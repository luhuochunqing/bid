<template>
  <el-tree-select
    :model-value="modelValue"
    :data="options"
    :placeholder="placeholder"
    multiple
    collapse-tags
    collapse-tags-tooltip
    clearable
    check-strictly
    check-on-click-node
    size="small"
    style="width: 100%"
    node-key="value"
    :props="{
      label: 'label',
      children: 'children',
      value: 'value',
      disabled: 'disabled'
    }"
    @update:model-value="$emit('update:modelValue', $event)"
    @clear="$emit('update:modelValue', [])"
  />
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