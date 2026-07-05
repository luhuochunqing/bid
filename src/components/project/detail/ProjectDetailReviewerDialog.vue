<template>
  <!--
    detail 是 reactive 包装的对象（见 ProjectDetailShell.vue 的 projectDetailContext = reactive({...})），
    Vue 3 reactive 会自动 unwrap ref 字段：访问 detail.reviewerDialogVisible 已是 boolean，
    不能再写 .value（会拿到 undefined，导致 v-model 失效、dialog 永不弹出）。
  -->
  <el-dialog v-model="detail.reviewerDialogVisible" title="添加评审人" width="500px">
    <el-form :model="detail.reviewerForm" label-width="100px">
      <el-form-item label="评审人" required>
        <UserPicker
          v-model="detail.reviewerForm.userId"
          mode="search"
          placeholder="请选择评审人"
          style="width: 100%;"
          @select="detail.handleReviewerSelect"
        />
      </el-form-item>
      <el-form-item label="评审角色" required>
        <el-select v-model="detail.reviewerForm.role" placeholder="请选择评审角色" style="width: 100%;">
          <el-option label="技术评审" value="tech" />
          <el-option label="商务评审" value="business" />
          <el-option label="法务评审" value="legal" />
          <el-option label="财务评审" value="finance" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="detail.closeReviewerDialog">取消</el-button>
      <el-button type="primary" @click="detail.handleConfirmAddReviewer">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { inject } from 'vue'
import UserPicker from '@/components/common/UserPicker.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const detail = inject(projectDetailKey)
</script>
