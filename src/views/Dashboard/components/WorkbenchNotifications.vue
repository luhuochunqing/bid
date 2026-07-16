<!-- Input: 复用 useNotificationStore
Output: 工作台消息通知列表（未读点 + 标题 + 描述 + 时间），点项进详情
Pos: src/views/Dashboard/components/ - 工作台改造组件
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="rebuild-notif-card">
    <div class="rebuild-card-header">
      <div class="rebuild-card-title"><span class="icon"></span>消息通知</div>
      <span style="font-size:0.78rem;color:#909399" v-if="total > 0">
        共 {{ total }} 条 · 未读 {{ unread }}
      </span>
    </div>
    <div class="rebuild-notif-list" v-loading="store.loading">
      <div
        v-for="item in notifications"
        :key="item.id"
        class="rebuild-notif-item"
        role="button"
        tabindex="0"
        @click="handleClick(item)"
        @keydown.enter.prevent="handleClick(item)"
      >
        <div class="unread-dot" :class="{ read: item.read }"></div>
        <div class="ni-body">
          <div class="ni-title">{{ item.title }}</div>
          <div class="ni-desc" v-if="item.body">{{ item.body }}</div>
        </div>
        <div class="ni-time">{{ formatTime(item.createdAt) }}</div>
      </div>
      <div class="rebuild-notif-empty" v-if="!store.loading && notifications.length === 0">
        暂无通知
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import { formatNotificationTime, resolveNotificationRoute } from '@/utils/notificationHelpers'

const router = useRouter()
const store = useNotificationStore()

const notifications = computed(() => store.notifications || [])
const unread = computed(() => store.unreadCount || 0)
const total = computed(() => store.totalElements || 0)

const formatTime = (time) => formatNotificationTime(time)

const handleClick = (item) => {
  const route = resolveNotificationRoute(item)
  if (!item.read) {
    store.markAsRead({ userNotificationId: item.id, notificationId: item.notificationId })
  }
  if (route) {
    router.push(route)
  }
}

onMounted(() => {
  store.fetchNotifications({ page: 0, size: 8 })
})
</script>
