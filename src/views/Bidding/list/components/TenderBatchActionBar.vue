<template>
  <div v-if="selectedCount > 0" class="batch-actions">
    <div class="batch-info">
      <el-checkbox :model-value="selectAllChecked" :indeterminate="isIndeterminate" @change="$emit('select-all', $event)">
        已选择 {{ selectedCount }} 条标讯
      </el-checkbox>
    </div>
    <div class="batch-buttons">
      <el-button v-if="canManageTenders" type="primary" @click="$emit('distribute')">
        <el-icon><Share /></el-icon>
        批量分发
      </el-button>
      <el-button v-if="canManageTenders" type="success" @click="$emit('claim')">
        <el-icon><CircleCheck /></el-icon>
        领取标讯
      </el-button>
      <el-button v-if="canManageTenders" type="warning" @click="$emit('follow')">
        <el-icon><Star /></el-icon>
        批量关注
      </el-button>
      <el-button @click="$emit('clear')">取消选择</el-button>
    </div>
  </div>
</template>

<script setup>
import { CircleCheck, Share, Star } from '@element-plus/icons-vue'

defineProps({
  selectedCount: { type: Number, default: 0 },
  selectAllChecked: { type: Boolean, default: false },
  isIndeterminate: { type: Boolean, default: false },
  canManageTenders: { type: Boolean, default: false },
})

defineEmits(['select-all', 'distribute', 'claim', 'follow', 'clear'])
</script>
