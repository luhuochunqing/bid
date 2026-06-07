<template>
  <div class="keyword-subscription-page">
    <div class="page-header">
      <h2>标讯关键词订阅</h2>
      <div class="header-actions">
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>新建订阅
        </el-button>
        <el-button @click="fetchMatchResults" :loading="loadingResults">
          查看匹配结果
        </el-button>
      </div>
    </div>

    <el-alert
      title="设置关键词组合，系统每日自动匹配新增标讯，并通过站内通知发送匹配结果"
      type="info"
      :closable="false"
      show-icon
      class="info-alert"
    />

    <!-- 订阅列表 -->
    <el-card v-loading="loading" class="list-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>我的订阅</span>
          <span class="subscription-count">共 {{ total }} 个订阅</span>
        </div>
      </template>

      <el-empty v-if="!loading && subscriptions.length === 0" description="暂无订阅，点击上方按钮新建" />

      <div v-else class="subscription-list">
        <div
          v-for="sub in subscriptions"
          :key="sub.id"
          class="subscription-item"
          :class="{ 'is-paused': sub.status === 'PAUSED' }"
        >
          <div class="sub-header">
            <div class="sub-name-row">
              <span class="sub-name">{{ sub.name }}</span>
              <el-tag :type="sub.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                {{ sub.status === 'ACTIVE' ? '启用' : '暂停' }}
              </el-tag>
              <el-tag v-if="sub.logicOperator === 'AND'" type="warning" size="small">AND</el-tag>
              <el-tag v-else type="primary" size="small">OR</el-tag>
            </div>
            <div class="sub-actions">
              <el-button text size="small" @click="viewMatchResults(sub)">匹配结果</el-button>
              <el-button text size="small" @click="openEditDialog(sub)">编辑</el-button>
              <el-button text size="small" @click="toggleStatus(sub)">
                {{ sub.status === 'ACTIVE' ? '暂停' : '启用' }}
              </el-button>
              <el-popconfirm
                title="确定删除该订阅？"
                confirm-button-text="删除"
                @confirm="handleDelete(sub.id)"
              >
                <template #reference>
                  <el-button text size="small" type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>
          <div class="sub-keywords">
            <el-tag
              v-for="(kw, idx) in sub.keywords"
              :key="idx"
              size="small"
              class="keyword-tag"
              :type="sub.logicOperator === 'AND' ? 'warning' : 'primary'"
              effect="plain"
            >
              {{ kw }}
            </el-tag>
            <span v-if="sub.keywords.length === 0" class="no-keywords">无关键词</span>
          </div>
          <div class="sub-meta">
            <span v-if="sub.lastMatchedAt">上次匹配：{{ formatTime(sub.lastMatchedAt) }}</span>
            <span v-else>尚未匹配</span>
            <span>创建时间：{{ formatTime(sub.createdAt) }}</span>
          </div>
        </div>
      </div>

      <div v-if="totalPages > 1" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="totalElements"
          layout="prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <KeywordSubscriptionDialog
      v-model="dialogVisible"
      :edit-data="editingSubscription"
      @saved="fetchSubscriptions"
    />

    <!-- 匹配结果对话框 -->
    <el-drawer
      v-model="resultsDrawerVisible"
      :title="resultsDrawerTitle"
      size="500px"
    >
      <div v-loading="loadingResults" class="results-list">
        <el-empty v-if="!loadingResults && matchResults.length === 0" description="暂无匹配结果" />
        <div v-for="item in matchResults" :key="item.id" class="result-item">
          <div class="result-title">
            <el-tag size="small" type="info" class="result-sub-name">{{ item.subscriptionName }}</el-tag>
            {{ item.tenderTitle }}
          </div>
          <div class="result-meta">
            <span>{{ formatTime(item.createdAt) }}</span>
            <el-tag v-if="item.notified" size="small" type="success">已通知</el-tag>
            <el-tag v-else size="small" type="info">未通知</el-tag>
          </div>
        </div>
        <div v-if="resultsTotalPages > 1" class="pagination-wrapper">
          <el-pagination
            v-model:current-page="resultsCurrentPage"
            :page-size="resultsPageSize"
            :total="resultsTotalElements"
            layout="prev, pager, next"
            small
            @current-change="handleResultsPageChange"
          />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import httpClient from '@/api/client.js'
import KeywordSubscriptionDialog from './KeywordSubscriptionDialog.vue'

// === State ===
const loading = ref(false)
const subscriptions = ref([])
const currentPage = ref(1)
const pageSize = 10
const totalElements = ref(0)
const totalPages = ref(0)
const total = ref(0)

const dialogVisible = ref(false)
const editingSubscription = ref(null)

// Match results drawer
const resultsDrawerVisible = ref(false)
const resultsDrawerTitle = ref('匹配结果')
const loadingResults = ref(false)
const matchResults = ref([])
const resultsCurrentPage = ref(1)
const resultsPageSize = 10
const resultsTotalElements = ref(0)
const resultsTotalPages = ref(0)
const viewingSubscriptionId = ref(null)

// === Methods ===
const fetchSubscriptions = async () => {
  loading.value = true
  try {
    const resp = await httpClient.get('/api/tender-keyword-subscriptions', {
      params: { page: currentPage.value - 1, size: pageSize }
    })
    if (resp.success) {
      subscriptions.value = resp.data.content || []
      totalElements.value = resp.data.totalElements || 0
      totalPages.value = resp.data.totalPages || 0
      total.value = subscriptions.value.length
    }
  } catch (e) {
    console.error('Failed to fetch subscriptions:', e)
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page) => {
  currentPage.value = page
  fetchSubscriptions()
}

const openCreateDialog = () => {
  editingSubscription.value = null
  dialogVisible.value = true
}

const openEditDialog = (sub) => {
  editingSubscription.value = { ...sub }
  dialogVisible.value = true
}

const toggleStatus = async (sub) => {
  try {
    const resp = await httpClient.patch(`/api/tender-keyword-subscriptions/${sub.id}/toggle`)
    if (resp.success) {
      ElMessage.success(sub.status === 'ACTIVE' ? '已暂停' : '已启用')
      fetchSubscriptions()
    }
  } catch (e) {
    console.error('Failed to toggle status:', e)
  }
}

const handleDelete = async (id) => {
  try {
    const resp = await httpClient.delete(`/api/tender-keyword-subscriptions/${id}`)
    if (resp.success) {
      ElMessage.success('删除成功')
      fetchSubscriptions()
    }
  } catch (e) {
    console.error('Failed to delete subscription:', e)
  }
}

const fetchMatchResultsForSubscription = async (subId) => {
  loadingResults.value = true
  try {
    const resp = await httpClient.get(`/api/tender-keyword-subscriptions/${subId}/match-results`, {
      params: { page: resultsCurrentPage.value - 1, size: resultsPageSize }
    })
    if (resp.success) {
      matchResults.value = resp.data.content || []
      resultsTotalElements.value = resp.data.totalElements || 0
      resultsTotalPages.value = resp.data.totalPages || 0
    }
  } catch (e) {
    console.error('Failed to fetch match results:', e)
  } finally {
    loadingResults.value = false
  }
}

const viewMatchResults = (sub) => {
  viewingSubscriptionId.value = sub.id
  resultsDrawerTitle.value = `「${sub.name}」匹配结果`
  resultsCurrentPage.value = 1
  fetchMatchResultsForSubscription(sub.id)
  resultsDrawerVisible.value = true
}

const fetchMatchResults = async () => {
  viewingSubscriptionId.value = null
  resultsDrawerTitle.value = '全部匹配结果'
  resultsCurrentPage.value = 1
  loadingResults.value = true
  resultsDrawerVisible.value = true
  try {
    const resp = await httpClient.get('/api/tender-keyword-subscriptions/match-results', {
      params: { page: 0, size: resultsPageSize }
    })
    if (resp.success) {
      matchResults.value = resp.data.content || []
      resultsTotalElements.value = resp.data.totalElements || 0
      resultsTotalPages.value = resp.data.totalPages || 0
    }
  } catch (e) {
    console.error('Failed to fetch all match results:', e)
  } finally {
    loadingResults.value = false
  }
}

const handleResultsPageChange = (page) => {
  resultsCurrentPage.value = page
  if (viewingSubscriptionId.value) {
    fetchMatchResultsForSubscription(viewingSubscriptionId.value)
  } else {
    fetchMatchResults()
  }
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

onMounted(() => {
  fetchSubscriptions()
})
</script>

<style scoped src="./KeywordSubscription.css"></style>
