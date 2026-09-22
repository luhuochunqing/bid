<template>
  <div class="bar-sop-detail" v-loading="loading">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <el-button link @click="$router.back()">
          <el-icon><Back /></el-icon>
          返回站点
        </el-button>
        <h2 class="page-title">找回SOP - {{ site?.name }}</h2>
      </div>
      <div class="header-actions">
        <el-button @click="handleEdit">
          <el-icon><Edit /></el-icon>
          编辑
        </el-button>
        <el-button @click="handleExport">
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </div>
    </div>

    <template v-if="site && site.sop">
      <!-- 找回入口 -->
      <el-card class="sop-card" shadow="never">
        <template #header>
          <span class="card-title">
            <el-icon><Link /></el-icon>
            找回入口
          </span>
        </template>
        <div class="link-list">
          <div v-if="site.sop.resetUrl" class="link-item">
            <span class="link-label">密码找回：</span>
            <a :href="site.sop.resetUrl" target="_blank" class="link-url">
              {{ site.sop.resetUrl }}
              <el-icon><TopRight /></el-icon>
            </a>
          </div>
          <div v-if="site.sop.unlockUrl" class="link-item">
            <span class="link-label">CA解锁：</span>
            <a :href="site.sop.unlockUrl" target="_blank" class="link-url">
              {{ site.sop.unlockUrl }}
              <el-icon><TopRight /></el-icon>
            </a>
          </div>
          <div v-if="site.sop.contacts" class="link-item">
            <span class="link-label">人工客服：</span>
            <span class="contact-info">{{ site.sop.contacts.join('、') }}</span>
          </div>
        </div>
      </el-card>

      <!-- 所需材料 -->
      <el-card class="sop-card" shadow="never">
        <template #header>
          <span class="card-title">
            <el-icon><Document /></el-icon>
            所需材料
          </span>
        </template>
        <div class="doc-list">
          <div
            v-for="(doc, index) in site.sop.requiredDocs"
            :key="index"
            class="doc-item"
            :class="{ required: doc.required }"
          >
            <el-icon v-if="doc.required" class="required-icon"><Star /></el-icon>
            <span>{{ doc.name }}</span>
          </div>
        </div>
      </el-card>

      <!-- 常见问题 -->
      <el-card class="sop-card" shadow="never">
        <template #header>
          <span class="card-title">
            <el-icon><QuestionFilled /></el-icon>
            常见问题
          </span>
        </template>
        <el-collapse v-if="site.sop.faqs && site.sop.faqs.length > 0">
          <el-collapse-item
            v-for="(faq, index) in site.sop.faqs"
            :key="index"
            :title="faq.q"
          >
            <div class="faq-answer">{{ faq.a }}</div>
          </el-collapse-item>
        </el-collapse>
        <el-empty v-else description="暂无常见问题" :image-size="80" />
      </el-card>

      <!-- 历史处理记录 -->
      <el-card class="sop-card" shadow="never">
        <template #header>
          <span class="card-title">
            <el-icon><Clock /></el-icon>
            历史处理记录
          </span>
        </template>
        <el-timeline v-if="site.sop.history && site.sop.history.length > 0">
          <el-timeline-item
            v-for="(record, index) in site.sop.history"
            :key="index"
            :timestamp="record.date"
            placement="top"
          >
            <div class="history-record">
              <span class="action">{{ record.action }}</span>
              <span class="user">{{ record.user }}</span>
              <span class="duration" v-if="record.duration">（耗时 {{ record.duration }}）</span>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无处理记录" :image-size="80" />
      </el-card>

      <!-- 预计时长 -->
      <div v-if="site.sop.estimatedTime" class="time-estimate">
        <el-icon><InfoFilled /></el-icon>
        <span>预计处理时长：<strong>{{ site.sop.estimatedTime }}</strong></span>
      </div>
    </template>

    <el-empty v-else description="暂无SOP信息" :image-size="120" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useBarStore } from '@/stores/bar'
import {
  Back, Edit, Download, Link, TopRight, Document,
  QuestionFilled, Clock, Star, InfoFilled
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const barStore = useBarStore()

const loading = ref(false)
const site = ref(null)

const downloadTextFile = (filename, content, mimeType = 'text/plain;charset=utf-8') => {
  const blob = new Blob([content], { type: mimeType })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(link.href)
}

const handleEdit = () => {
  if (!site.value?.sop) return
  const nextHistory = Array.isArray(site.value.sop.history) ? [...site.value.sop.history] : []
  nextHistory.unshift({
    date: new Date().toLocaleString('zh-CN', { hour12: false }),
    action: '更新了 SOP 联系方式与所需材料',
    user: '李总',
    duration: '10分钟'
  })
  const nextSop = {
    ...site.value.sop,
    history: nextHistory }
  barStore.updateSop(site.value.id, nextSop).then(async (response) => {
    if (!response?.success) {
      ElMessage.error(response?.msg || 'SOP 保存失败')
      return
    }
    const latestSite = await barStore.getSiteById(route.params.siteId)
    site.value = latestSite
    ElMessage.success(`已保存「${site.value?.name || '当前站点'}」SOP 修改`)
  })
}

const handleExport = () => {
  const content = [
    `站点：${site.value?.name || '站点'}`,
    `找回入口：${site.value?.sop?.resetUrl || '-'}`,
    `联系方式：${site.value?.sop?.contacts?.join('、') || '-'}`,
    `预计时长：${site.value?.sop?.estimatedTime || '-'}`,
  ].join('\n')
  downloadTextFile(`${site.value?.name || '站点'}_找回SOP.txt`, content)
  ElMessage.success(`已导出 SOP：${site.value?.name || '站点'}_找回SOP.txt`)
}

onMounted(async () => {
  loading.value = true
  await barStore.getSites()
  const latestSite = await barStore.getSiteById(route.params.siteId)
  site.value = latestSite
  loading.value = false
})
</script>

<style scoped>
.bar-sop-detail {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 8px 0 0 0;
  color: var(--gray-750);
}

.sop-card {
  margin-bottom: 16px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-750);
  display: flex;
  align-items: center;
  gap: 6px;
}

.link-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.link-item {
  display: flex;
  align-items: center;
  font-size: 14px;
}

.link-label {
  color: var(--text-secondary-ui);
  min-width: 80px;
}

.link-url {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #409eff;
  text-decoration: none;
}

.link-url:hover {
  text-decoration: underline;
}

.contact-info {
  color: var(--gray-750);
}

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.doc-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--bg-subtle);
  border-radius: 4px;
  font-size: 14px;
}

.doc-item.required {
  background: #fef0f0;
  border-left: 3px solid #f56c6c;
}

.required-icon {
  color: #f56c6c;
}

.faq-answer {
  padding: 8px 0;
  color: var(--text-secondary-ui);
  line-height: 1.6;
}

.history-record {
  font-size: 14px;
}

.history-record .action {
  font-weight: 500;
  color: var(--gray-750);
}

.history-record .user {
  margin-left: 8px;
  color: var(--text-secondary-ui);
}

.history-record .duration {
  margin-left: 8px;
  color: var(--text-muted);
}

.time-estimate {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 6px;
  color: #409eff;
  font-size: 14px;
}

.time-estimate strong {
  color: var(--gray-750);
}
</style>
