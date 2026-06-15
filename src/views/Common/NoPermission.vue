<template>
  <div class="no-permission-container">
    <el-result
      icon="warning"
      title="权限不足"
      sub-title="您当前没有访问该页面的权限，请联系管理员获取相应权限。"
    >
      <template #extra>
        <el-button type="primary" @click="goBack">返回上一页</el-button>
        <el-button @click="goToLogin">重新登录</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const goToLogin = async () => {
  await userStore.logout()
}

const goBack = () => {
  // CO-210: If user has no permissions at all, don't try to go back
  // Instead, redirect to login
  const perms = userStore.menuPermissions || []
  if (perms.length === 0 && !perms.includes('all')) {
    goToLogin()
  } else {
    router.back()
  }
}
</script>

<style scoped lang="scss">
.no-permission-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 200px);
  padding: 40px;
}
</style>
