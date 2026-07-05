<template>
  <!--
    ctx 是 reactive 包装的对象（见 ProjectDetailShell.vue 的 projectDetailContext = reactive({...transfer})），
    Vue 3 reactive 会自动 unwrap ref 字段：访问 ctx.transferDialogVisible 已是 boolean，
    不能再写 .value（会拿到 undefined，导致 v-model 失效、dialog 永不弹出）。
  -->
  <el-dialog
    v-model="ctx.transferDialogVisible"
    title="项目转移"
    width="520px"
    :close-on-click-modal="false"
    append-to-body
  >
    <el-form :model="ctx.transferForm" label-width="100px">
      <el-form-item label="项目名称">
        <span>{{ ctx.project?.name }}</span>
      </el-form-item>
      <el-form-item label="当前负责人">
        <span>{{ ctx.project?.projectLeaderName || ctx.project?.managerName || '—' }}</span>
      </el-form-item>
      <el-form-item label="新负责人" required>
        <UserPicker
          v-model="ctx.transferForm.newOwnerUserId"
          mode="search"
          placeholder="搜索人员（姓名/工号/拼音）"
          style="width: 100%;"
          :exclude-ids="ctx.excludeOwnerIds"
          :role-filter="PROJECT_TRANSFER_TARGET_ROLES"
        />
      </el-form-item>
      <el-form-item label="转移原因">
        <el-input
          v-model="ctx.transferForm.reason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="可选，最多 500 字符"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="ctx.closeTransfer">取消</el-button>
      <el-button
        type="primary"
        :loading="ctx.transferring"
        @click="ctx.handleTransferConfirm"
      >
        确认转移
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { inject } from 'vue'
import UserPicker from '@/components/common/UserPicker.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'
import { PROJECT_TRANSFER_TARGET_ROLES } from '@/constants/roleCodes.js'

const ctx = inject(projectDetailKey)
</script>
