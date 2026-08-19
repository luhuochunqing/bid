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
    show-checkbox
    filterable
    size="small"
    class="tree-select-dropdown"
    node-key="value"
    :props="{
      label: 'label',
      children: 'children',
      value: 'value',
      disabled: 'disabled'
    }"
    @update:model-value="handleChange"
    @clear="handleClear"
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

const emit = defineEmits(['update:modelValue', 'change'])

const handleChange = (val) => {
  emit('update:modelValue', val || [])
  emit('change', val || [])
}

const handleClear = () => {
  emit('update:modelValue', [])
  emit('change', [])
}
</script>