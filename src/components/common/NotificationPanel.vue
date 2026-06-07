<template>
  <div class="notification-panel" role="region" aria-label="通知中心">
    <div class="notification-panel-header">
      <span class="notification-panel-title">通知</span>
      <el-button
        v-if="store.unreadCount > 0"
        link
        type="primary"
        size="small"
        @click="handleMarkAllRead"
      >
        全部已读
      </el-button>
    </div>

    <div class="notification-panel-body">
      <div v-if="store.loading" class="notification-panel-loading">
        <el-skeleton :rows="3" animated />
      </div>
      <div v-else-if="store.notifications.length === 0" class="notification-panel-empty">
        <el-empty description="暂无通知" :image-size="60" />
      </div>
      <div v-else class="notification-list" role="list">
        <div
          v-for="item in store.notifications"
          :key="item.id"
          class="notification-item"
          :class="{ 'notification-item--unread': !item.read }"
          role="button"
          tabindex="0"
          :aria-label="`${item.read ? '' : '未读 '}通知：${item.title}`"
          @click="handleClick(item)"
          @keyup.enter="handleClick(item)"
          @keyup.space="handleClick(item)"
        >
          <div class="notification-item-icon" aria-hidden="true">
            <el-icon :size="16">
              <component :is="getNotificationIcon(item.type)" />
            </el-icon>
          </div>
          <div class="notification-item-content">
            <div class="notification-item-title">{{ item.title }}</div>
            <div class="notification-item-time">{{ formatNotificationTime(item.createdAt) }}</div>
          </div>
          <div v-if="!item.read" class="notification-item-dot" aria-label="未读" />
        </div>
      </div>
    </div>

    <div class="notification-panel-footer">
      <el-button link type="primary" @click="handleViewAll">查看全部</el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import {
  getNotificationIcon,
  formatNotificationTime,
  resolveNotificationRoute
} from '@/utils/notificationHelpers'

const emit = defineEmits(['close'])
const router = useRouter()
const store = useNotificationStore()

const handleClick = async (item) => {
  if (!item.read) {
    await store.markAsRead({ userNotificationId: item.id, notificationId: item.notificationId })
  }
  const target = resolveNotificationRoute(item)
  if (target) {
    router.push(target)
    emit('close')
  }
}

const handleMarkAllRead = async () => {
  await store.markAllAsRead()
}

const handleViewAll = () => {
  router.push('/inbox')
  emit('close')
}

onMounted(() => {
  store.fetchNotifications({ page: 0, size: 10 })
})
</script>

<style scoped>
.notification-panel {
  width: 360px;
  max-width: 100vw;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
}

.notification-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color, #f1f5f9);
}

.notification-panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
}

.notification-panel-body {
  max-height: 420px;
  overflow-y: auto;
}

.notification-panel-loading,
.notification-panel-empty {
  padding: 24px 16px;
}

.notification-list {
  display: flex;
  flex-direction: column;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--border-color, #f1f5f9);
  transition: background 150ms ease;
  position: relative;
}

.notification-item:last-child {
  border-bottom: none;
}

.notification-item:hover {
  background: var(--surface-hover, #f8fafc);
}

.notification-item--unread {
  background: rgba(46, 118, 89, 0.04);
}

.notification-item-icon {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--surface-hover, #f1f5f9);
  color: var(--brand-xiyu-logo);
}

.notification-item-content {
  flex: 1;
  min-width: 0;
}

.notification-item-title {
  font-size: 13px;
  color: var(--text-primary, #1e293b);
  line-height: 1.4;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  word-break: break-word;
}

.notification-item-time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-tertiary, #94a3b8);
}

.notification-item-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-danger);
  margin-top: 8px;
}

.notification-panel-footer {
  padding: 8px 16px;
  border-top: 1px solid var(--border-color, #f1f5f9);
  text-align: center;
}
</style>
