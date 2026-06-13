<template>
  <div class="form-action-bar">
    <div class="form-action-bar-inner">
      <div class="action-bar-right">
        <template v-if="isEditMode && createdTenderId">
          <el-button size="large" @click="$emit('cancel-edit')">取消</el-button>
          <el-button type="primary" size="large" :loading="saving" :disabled="!canSave" @click="$emit('save')">保存</el-button>
        </template>
        <template v-else-if="!createdTenderId">
          <el-button size="large" @click="$emit('cancel')">取消</el-button>
          <el-button type="primary" size="large" :loading="saving" :disabled="!canSave" @click="$emit('save')">保存</el-button>
        </template>
        <template v-else-if="tenderStatus === 'PENDING_ASSIGNMENT' && isAdminOrLead">
          <el-button size="large" @click="$emit('cancel')">返回列表</el-button>
          <el-button type="primary" size="large" @click="$emit('assign')">分配</el-button>
        </template>
        <template v-else-if="tenderStatus === 'TRACKING' && canProceedToNext">
          <template v-if="activeTab === 'basic'">
            <el-button size="large" @click="$emit('cancel')">返回列表</el-button>
            <el-button type="success" size="large" @click="$emit('next-step')">下一步</el-button>
          </template>
          <template v-else-if="activeTab === 'evaluation'">
            <el-button size="large" @click="$emit('cancel')">返回列表</el-button>
            <el-button type="primary" size="large" :loading="submittingEval" @click="$emit('submit-eval')">提交</el-button>
          </template>
        </template>
        <template v-else>
          <el-button size="large" @click="$emit('cancel')">返回列表</el-button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  isEditMode: Boolean,
  createdTenderId: [Number, null],
  tenderStatus: String,
  isAdminOrLead: Boolean,
  canProceedToNext: Boolean,
  activeTab: String,
  canSave: Boolean,
  saving: Boolean,
  submittingEval: Boolean,
})

defineEmits(['save', 'cancel', 'cancel-edit', 'assign', 'next-step', 'submit-eval'])
</script>

<style scoped>
.form-action-bar { position: sticky; bottom: 0; z-index: 10; margin-top: 24px; padding: 20px 24px; background: rgba(255,255,255,0.95); backdrop-filter: blur(8px); border: 1px solid var(--gray-150); border-radius: var(--radius-md); }
.form-action-bar-inner { display: flex; justify-content: flex-end; align-items: center; gap: 12px; }
</style>
