<template>
  <div class="header-container">
    <div class="header-left">
      <el-icon class="collapse-icon" @click="handleToggle" v-if="!isMobile">
        <Expand v-if="collapse" />
        <Fold v-else />
      </el-icon>
      <el-icon class="mobile-menu-icon" @click="handleMobileMenuClick" v-else>
        <Menu />
      </el-icon>
      <button v-if="showBack" type="button" class="back-btn" @click="$emit('back')" title="返回上一页">
        <el-icon class="back-icon"><ArrowLeft /></el-icon>
        <span class="back-text">返回</span>
      </button>
      <div v-if="showBack" class="header-separator"></div>
      <div class="logo">
        <img class="logo-mark" src="/favicon.ico" alt="" aria-hidden="true">
        <span class="logo-icon">西域MRO</span>
        <span class="logo-text">投标管理平台</span>
      </div>
    </div>

    <div class="header-center" v-if="!isMobile && globalSearchEnabled">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索标讯、项目、知识..."
        class="search-input"
        :prefix-icon="Search"
        clearable
        @keyup.enter="handleSearch"
      />
    </div>

    <div class="header-center-mobile" v-else-if="globalSearchEnabled">
      <el-icon class="mobile-search-icon" @click="showMobileSearch = true">
        <Search />
      </el-icon>
    </div>

    <div class="header-right">
      <el-popover
        :visible="showNotificationPanel"
        placement="bottom-end"
        :width="360"
        :show-arrow="false"
        @update:visible="showNotificationPanel = $event"
      >
        <template #reference>
          <el-badge :value="notificationStore.unreadCount" :hidden="notificationStore.unreadCount === 0" class="notification-badge">
            <el-icon class="header-icon" @click="showNotificationPanel = !showNotificationPanel">
              <Bell />
            </el-icon>
          </el-badge>
        </template>
        <NotificationPanel @close="showNotificationPanel = false" />
      </el-popover>

      <el-dropdown @command="handleCommand">
        <div class="user-info">
          <span class="user-avatar">{{ userAvatar }}</span>
          <span class="user-name" v-if="!isMobile">{{ userName }}</span>
          <el-icon class="dropdown-icon">
            <ArrowDown />
          </el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled class="user-info-dropdown">
              <div class="dropdown-user-detail">
                <span class="dropdown-avatar">{{ userAvatar }}</span>
                <div class="dropdown-user-text">
                  <div class="dropdown-user-name">{{ userName }}</div>
                  <div class="dropdown-user-role">{{ userRoleText }}</div>
                </div>
              </div>
            </el-dropdown-item>
            <el-dropdown-item v-if="globalSearchEnabled" command="profile">
              <el-icon><User /></el-icon>
              个人中心
            <el-dropdown-item command="keyword-subscription">
              <el-icon><Bell /></el-icon>
              关键词订阅
            </el-dropdown-item>
            </el-dropdown-item>
            <el-dropdown-item v-if="canAccessSettings" command="settings">
              <el-icon><Setting /></el-icon>
              系统设置
            </el-dropdown-item>
            <el-dropdown-item command="operation-log">
              <el-icon><DocumentChecked /></el-icon>
              操作日志
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-dialog v-if="globalSearchEnabled" v-model="showMobileSearch" title="搜索"
      :width="isMobile ? '90%' : '500px'"
      class="mobile-search-dialog"
    >
      <el-input
        v-model="searchKeyword"
        placeholder="搜索标讯、项目、知识..."
        size="large"
        clearable
        @keyup.enter="handleMobileSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <template #footer>
        <el-button @click="showMobileSearch = false">取消</el-button>
        <el-button type="primary" @click="handleMobileSearch">搜索</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Search, Bell, ArrowDown, ArrowLeft, User, Setting,
  SwitchButton, Expand, StarFilled, Fold, Menu, DocumentChecked
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notifications'
import { useNotifications } from '@/composables/useNotifications'
import NotificationPanel from '@/components/common/NotificationPanel.vue'

defineProps({
  collapse: {
    type: Boolean,
    default: false
  },
  showBack: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['toggleCollapse', 'mobileMenuClick', 'back'])

const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()
useNotifications()

const searchKeyword = ref('')
const showNotificationPanel = ref(false)
const showMobileSearch = ref(false)
const globalSearchEnabled = true

// 移动端检测
const isMobile = ref(false)

// 检测是否为移动端
const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

const roleTextMap = {
  admin: '管理员',
  manager: '经理',
  sales: '项目负责人',
  staff: '员工',
  guest: '游客'
}

const userAvatar = computed(() => {
  const name = userStore.currentUser?.name || '游客'
  return name.charAt(0).toUpperCase()
})
const userName = computed(() => userStore.currentUser?.name || '游客')
const userRoleText = computed(() => userStore.currentUser?.roleName || roleTextMap[userStore.userRole] || '游客')
const canAccessSettings = computed(() => userStore.hasPermission('settings'))

const handleToggle = () => {
  emit('toggleCollapse')
}

const handleMobileMenuClick = () => {
  emit('mobileMenuClick')
}

const handleSearch = () => {
  if (searchKeyword.value.trim()) ElMessage.info(`搜索: ${searchKeyword.value}`)
}

const handleMobileSearch = () => {
  if (searchKeyword.value.trim()) {
    ElMessage.info(`搜索: ${searchKeyword.value}`)
    showMobileSearch.value = false
  }
}

const handleCommand = async (command) => {
  switch (command) {
    case 'profile':
      router.push('/profile')
      break
    case 'keyword-subscription':
      router.push('/bidding/keyword-subscription')
      break
    case 'settings':
      if (canAccessSettings.value) {
        router.push('/settings')
      } else {
        ElMessage.warning('当前角色无权访问系统设置')
      }
      break
    case 'operation-log':
      router.push('/operation-logs')
      break
    case 'logout':
      try {
        await userStore.logout()
        await router.replace('/login')
        ElMessage.success('已退出登录')
      } catch {
        // 用户取消
      }
      break
  }
}

</script>

<style src="./Header.css" scoped></style>
