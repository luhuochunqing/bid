<template>
  <el-dialog v-model="detail.reviewerDialogVisible.value" title="添加评审人" width="500px">
    <el-form :model="detail.reviewerForm.value" label-width="100px">
      <el-form-item label="评审人" required>
        <UserPicker
          v-model="detail.reviewerForm.value.userId"
          placeholder="搜索评审人（姓名/工号/拼音）"
          style="width: 100%;"
        />
      </el-form-item>
      <el-form-item label="评审角色" required><el-select v-model="detail.reviewerForm.value.role" placeholder="请选择评审角色" style="width: 100%;"><el-option label="技术评审" value="tech" /><el-option label="商务评审" value="business" /><el-option label="法务评审" value="legal" /><el-option label="财务评审" value="finance" /></el-select></el-form-item>
    </el-form>
    <template #footer><el-button @click="detail.reviewerDialogVisible.value = false">取消</el-button><el-button type="primary" @click="detail.handleConfirmAddReviewer">确定</el-button></template>
  </el-dialog>
</template>

<script setup>
import { inject } from 'vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'
import UserPicker from '@/components/common/UserPicker.vue'
</script>
