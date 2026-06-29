<template>
  <el-tooltip v-if="actions.edit" content="编辑" placement="top">
    <el-button :icon="Edit" circle size="small" @click.stop="$emit('edit', row)" />
  </el-tooltip>
  <el-tooltip v-if="actions.return" content="登记归还" placement="top">
    <el-button :icon="CircleCheck" circle size="small" type="success" @click.stop="$emit('return', row)" />
  </el-tooltip>
  <el-tooltip v-if="actions.takeDown" content="下架" placement="top">
    <el-button :icon="Delete" circle size="small" type="danger" @click.stop="$emit('take-down', row)" />
  </el-tooltip>
  <el-tooltip v-if="actions.borrow || actions.apply" :content="actions.apply ? '申请使用' : '借用'" placement="top">
    <el-button :icon="Key" circle size="small" type="primary" :disabled="row.status !== 'available'" @click.stop="$emit('borrow', row)" />
  </el-tooltip>
</template>

<script setup>
import { Edit, CircleCheck, Delete, Key } from '@element-plus/icons-vue'

defineProps({
  row: { type: Object, required: true },
  actions: { type: Object, required: true }
})
defineEmits(['edit', 'return', 'borrow', 'take-down'])
</script>
