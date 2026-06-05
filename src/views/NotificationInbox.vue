<template>
  <div class="notification-inbox">
    <div class="inbox-header">
      <h2 class="inbox-title">通知中心</h2>
      <el-button
        v-if="store.unreadCount > 0"
        type="primary"
        plain
        size="small"
        @click="handleMarkAllRead"
      >
        全部已读
      </el-button>
    </div>

    <!-- Tabs are a curated subset of NOTIFICATION_TYPE_LABELS; other types fall under "全部". -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="系统通知" name="SYSTEM" />
      <el-tab-pane label="任务更新" name="TASK_UPDATE" />
      <el-tab-pane label="截止提醒" name="DEADLINE" />
    </el-tabs>

    <div v-if="store.loading" class="inbox-loading">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="store.notifications.length === 0" class="inbox-empty">
      <el-empty description="暂无通知" />
    </div>

    <div v-else class="inbox-list" role="list">
      <div
        v-for="item in store.notifications"
        :key="item.id"
        class="inbox-item"
        :class="{ 'inbox-item--unread': !item.read }"
        role="button"
        tabindex="0"
        :aria-label="`${item.read ? '' : '未读 '}${getNotificationTypeLabel(item.type)}：${item.title}`"
        @click="handleClick(item)"
        @keyup.enter="handleClick(item)"
        @keyup.space="handleClick(item)"
      >
        <div class="inbox-item-left">
          <div class="inbox-item-icon" aria-hidden="true">
            <el-icon :size="18">
              <component :is="getNotificationIcon(item.type)" />
            </el-icon>
          </div>
        </div>
        <div class="inbox-item-body">
          <div class="inbox-item-title">{{ item.title }}</div>
          <div v-if="item.body" class="inbox-item-desc">{{ item.body }}</div>
          <ChangeDiffCard v-if="hasChangeDiff(item)" :changes="extractChanges(item)" />
          <div class="inbox-item-meta">
            <el-tag v-if="item.type" size="small" type="info">{{ getNotificationTypeLabel(item.type) }}</el-tag>
            <span class="inbox-item-time">{{ formatNotificationTime(item.createdAt) }}</span>
          </div>
        </div>
        <div class="inbox-item-right">
          <div v-if="!item.read" class="inbox-item-dot" aria-label="未读" />
        </div>
      </div>
    </div>

    <div v-if="store.totalPages > 1" class="inbox-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="store.totalElements"
        layout="prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notifications'
import {
  getNotificationIcon,
  getNotificationTypeLabel,
  formatNotificationTime,
  resolveNotificationRoute,
  extractChanges,
  hasChangeDiff
} from '@/utils/notificationHelpers'
import ChangeDiffCard from '@/components/common/ChangeDiffCard.vue'

const router = useRouter()
const store = useNotificationStore()

const activeTab = ref('all')
const currentPage = ref(1)
const pageSize = 20

const fetchData = () => {
  const params = { page: currentPage.value - 1, size: pageSize }
  if (activeTab.value !== 'all') {
    params.type = activeTab.value
  }
  store.fetchNotifications(params)
}

const handleTabChange = () => {
  currentPage.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const handleClick = async (item) => {
  if (!item.read) {
    await store.markAsRead({ userNotificationId: item.id, notificationId: item.notificationId })
  }
  const target = resolveNotificationRoute(item)
  if (target) {
    router.push(target)
  }
}

const handleMarkAllRead = async () => {
  await store.markAllAsRead()
}

onMounted(fetchData)
</script>

<style scoped>
.notification-inbox {
  max-width: 800px;
  margin: 0 auto;
}

.inbox-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.inbox-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
  margin: 0;
}

.inbox-loading,
.inbox-empty {
  padding: 40px 0;
}

.inbox-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: var(--border-color, #f1f5f9);
  border-radius: 8px;
  overflow: hidden;
}

.inbox-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: var(--bg-card);
  cursor: pointer;
  transition: background 150ms ease;
}

.inbox-item:hover {
  background: var(--surface-hover, #f8fafc);
}

.inbox-item--unread {
  background: rgba(46, 118, 89, 0.03);
}

.inbox-item-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--surface-hover, #f1f5f9);
  color: var(--brand-xiyu-logo, #2E7659);
  flex-shrink: 0;
}

.inbox-item-body {
  flex: 1;
  min-width: 0;
}

.inbox-item-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary, #1e293b);
  line-height: 1.4;
}

.inbox-item-desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-secondary, #64748b);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.inbox-item-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.inbox-item-time {
  font-size: 12px;
  color: var(--text-tertiary, #94a3b8);
}

.inbox-item-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding-top: 4px;
}

.inbox-item-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-danger);
}

.inbox-pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}
</style>
