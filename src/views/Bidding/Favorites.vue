<template>
  <div class="favorites-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="page-header-left">
        <h2 class="page-title">我的收藏</h2>
        <span class="page-subtitle" v-if="total > 0">共 {{ total }} 条收藏</span>
      </div>
      <div class="page-header-actions">
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
    </div>

    <el-empty v-else-if="favorites.length === 0" description="还没有收藏标讯">
      <template #image>
      </template>
      <el-button type="primary" @click="router.push('/bidding')">去标讯列表看看</el-button>
    </el-empty>

    <!-- 收藏列表 -->
    <div v-else class="favorites-list">
      <el-card
        v-for="item in favorites"
        :key="item.favoriteId"
        class="favorite-card"
        shadow="hover"
      >
        <div class="card-body">
          <div class="card-main" @click="goToDetail(item.tender.id)">
            <!-- 标题 -->
            <div class="card-title-row">
              <el-link type="primary" :underline="false" class="card-title">
                {{ item.tender.title }}
              </el-link>
            </div>
            <!-- 标签 -->
            <div class="card-tags">
              <el-tag
                :type="getStatusTagType(item.tender.status)"
                size="small"
              >
                {{ getStatusText(item.tender.status) }}
              </el-tag>
              <el-tag v-if="item.tender.source" size="small" type="info">
                {{ item.tender.source }}
              </el-tag>
              <el-tag v-if="item.tender.priority" size="small" :class="'priority-tag-' + item.tender.priority">
                {{ item.tender.priority }}
              </el-tag>
            </div>
            <!-- 元信息 -->
            <div class="card-meta">
              <span class="meta-item" v-if="item.tender.budget">
                <el-icon><Coin /></el-icon>
                {{ formatBudgetWan(item.tender.budget) }}万元
              </span>
              <span class="meta-item" v-if="item.tender.region">
                <el-icon><Location /></el-icon>
                {{ item.tender.region }}
              </span>
              <span class="meta-item" v-if="item.tender.purchaserName">
                <el-icon><User /></el-icon>
                {{ item.tender.purchaserName }}
              </span>
              <span class="meta-item" v-if="item.tender.deadline">
                <el-icon><Timer /></el-icon>
                截止：{{ formatDate(item.tender.deadline) }}
              </span>
            </div>
            <!-- 收藏时间 -->
            <div class="card-footer">
              <span class="favorite-time">
                收藏于 {{ formatDate(item.favoritedAt) }}
              </span>
            </div>
          </div>
          <!-- 操作区 -->
          <div class="card-actions">
            <el-button
              text
              type="warning"
              :icon="StarFilled"
              @click.stop="handleUnfavorite(item)"
            >
              取消收藏
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 分页 -->
      <div class="pagination-wrapper" v-if="total > pageSize">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadFavorites"
          @size-change="loadFavorites"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { StarFilled, Star, Coin, Location, User, Timer } from '@element-plus/icons-vue'
import { tenderFavoritesApi } from '@/api'
import { getTenderStatusTagType, getTenderStatusText } from '@/views/Bidding/bidding-utils-status.js'
import { formatBudgetWan } from '@/views/Bidding/bidding-utils.js'

const router = useRouter()

const favorites = ref([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

onMounted(() => {
  loadFavorites()
})

async function loadFavorites() {
  loading.value = true
  try {
    const res = await tenderFavoritesApi.getMyFavorites({
      page: currentPage.value - 1,
      size: pageSize.value
    })
    const pageData = res?.data
    favorites.value = pageData?.content || []
    total.value = pageData?.totalElements || 0
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '获取收藏列表失败')
  } finally {
    loading.value = false
  }
}

async function handleUnfavorite(item) {
  try {
    await ElMessageBox.confirm(`确定取消收藏「${item.tender.title}」吗？`, '取消收藏', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await tenderFavoritesApi.removeFavorite(item.tender.id)
    ElMessage.success('已取消收藏')
    favorites.value = favorites.value.filter(f => f.favoriteId !== item.favoriteId)
    total.value = Math.max(0, total.value - 1)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '取消收藏失败')
  }
}

function goToDetail(id) {
  router.push(`/bidding/${id}`)
}

function formatDate(val) {
  if (!val) return '-'
  const d = new Date(val)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const getStatusTagType = (status) => getTenderStatusTagType(status)
const getStatusText = (status) => getTenderStatusText(status)
</script>

<style scoped>
.favorites-page {
  padding: 20px 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.page-subtitle {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.loading-container {
  padding: 40px 0;
}

.favorites-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.favorite-card {
  cursor: default;
  border-radius: 8px;
}

.card-body {
  display: flex;
  justify-content: space-between;
  align-items: stretch;
  gap: 16px;
}

.card-main {
  flex: 1;
  cursor: pointer;
  min-width: 0;
}

.card-title-row {
  margin-bottom: 8px;
}

.card-title {
  font-size: 16px;
  font-weight: 500;
}

.card-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 8px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.card-footer {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.card-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  padding-left: 16px;
  border-left: 1px solid var(--el-border-color-lighter);
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
  padding: 16px 0;
}

.empty-icon {
  color: var(--el-border-color);
}
</style>
