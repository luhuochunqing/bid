<template>
  <el-drawer
    v-model="visible"
    title="案例切片推荐"
    size="680px"
    append-to-body
    @open="handleOpen"
  >
    <div class="case-slice-drawer">
      <div class="drawer-section">
        <label class="section-label">搜索关键词</label>
        <el-input
          v-model="searchQuery"
          placeholder="输入关键词检索相关案例切片..."
          clearable
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
          </template>
        </el-input>
      </div>

      <div class="drawer-section">
        <label class="section-label">
          推荐结果
          <el-tag v-if="searchQuery" size="small" type="info">{{ recommendations.length }} 个结果</el-tag>
        </label>
        <div v-loading="loading" class="results-container">
          <template v-if="recommendations.length > 0">
            <div
              v-for="item in recommendations"
              :key="item.id"
              class="recommend-card"
              :class="{ 'high-similarity': item.similarity >= 80 }"
            >
              <div class="card-header">
                <div class="title-row">
                  <el-tag v-if="item.similarity >= 80" size="small" type="danger" effect="dark">高匹配</el-tag>
                  <el-tag size="small" type="warning">{{ (item.similarity * 100).toFixed(1) }}%</el-tag>
                  <h4 class="card-title">{{ item.title }}</h4>
                </div>
                <div class="tag-row">
                  <el-tag size="small" type="primary" effect="plain">{{ item.docxLabel }}</el-tag>
                  <el-tag size="small" type="success" effect="plain">{{ item.projectDir }}</el-tag>
                </div>
              </div>
              <div class="card-preview">{{ item.textPreview }}</div>
              <div class="card-meta">
                <span>段落数: {{ item.paraCount }}</span>
                <span>字数: {{ item.textLength }}</span>
              </div>
              <div class="card-footer">
                <el-button type="primary" size="small" link @click="viewDetail(item)">详情</el-button>
                <el-button type="success" size="small" @click="handleInsert(item)">插入</el-button>
              </div>
            </div>
          </template>
          <el-empty
            v-else-if="!loading && searchQuery"
            description="暂无匹配的案例切片"
            :image-size="80"
          />
          <div v-else class="select-hint">输入关键词搜索相关案例切片</div>
        </div>
      </div>
    </div>

    <el-dialog v-model="detailVisible" title="" width="900px" :close-on-click-modal="false" destroy-on-close class="detail-dialog">
      <div v-if="selectedSlice" class="detail-body">
        <div class="detail-left">
          <div class="detail-section"><div class="detail-label">标题</div><div class="detail-val">{{ selectedSlice.title }}</div></div>
          <div class="detail-section"><div class="detail-label">匹配度</div><div class="detail-val"><el-tag :type="selectedSlice.similarity >= 0.8 ? 'danger' : 'primary'">{{ (selectedSlice.similarity * 100).toFixed(1) }}%</el-tag></div></div>
          <div class="detail-section"><div class="detail-label">来源项目</div><div class="detail-val">{{ selectedSlice.projectDir }}</div></div>
          <div class="detail-section"><div class="detail-label">文档类型</div><div class="detail-val">{{ selectedSlice.docxLabel }}</div></div>
          <div class="detail-section"><div class="detail-label">段落数</div><div class="detail-val">{{ selectedSlice.paraCount }}</div></div>
          <div class="detail-section"><div class="detail-label">字数</div><div class="detail-val">{{ selectedSlice.textLength }}</div></div>
        </div>
        <div class="detail-right">
          <div class="detail-content-head">📄 内容预览</div>
          <div class="detail-content">{{ selectedSlice.textPreview }}</div>
        </div>
      </div>
      <template #footer>
        <div class="detail-footer">
          <el-button type="primary" @click="handleInsert(selectedSlice)">📋 插入到文档</el-button>
          <el-button @click="detailVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { caseSlicesApi } from '@/api'

const props = defineProps({
  projectId: { type: [String, Number], default: null },
  defaultQuery: { type: String, default: '' }
})

const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['insert'])

const searchQuery = ref('')
const recommendations = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const selectedSlice = ref(null)

async function handleOpen() {
  if (props.defaultQuery) {
    searchQuery.value = props.defaultQuery
    await handleSearch()
  }
}

async function handleSearch() {
  if (!searchQuery.value.trim()) {
    recommendations.value = []
    return
  }
  loading.value = true
  try {
    const result = await caseSlicesApi.recommendByQuery(searchQuery.value.trim(), 10)
    recommendations.value = Array.isArray(result?.data) ? result.data : []
  } catch {
    recommendations.value = []
  } finally {
    loading.value = false
  }
}

watch(searchQuery, (newVal) => {
  if (!newVal.trim()) {
    recommendations.value = []
  }
})

function viewDetail(item) {
  selectedSlice.value = item
  detailVisible.value = true
}

async function handleInsert(item) {
  if (!item) return
  const content = item.textPreview || item.title || ''
  try {
    await navigator.clipboard.writeText(content)
    emit('insert', item)
    ElMessage.success('已复制案例内容到剪贴板')
  } catch {
    emit('insert', item)
    ElMessage.success('案例内容已准备插入')
  }
}
</script>

<style scoped>
.case-slice-drawer { display: flex; flex-direction: column; gap: 20px; }
.drawer-section { display: flex; flex-direction: column; gap: 8px; }
.section-label { font-size: 14px; font-weight: 600; color: var(--text-secondary-ui); }
.results-container { min-height: 300px; }
.select-hint { color: var(--text-muted); font-size: 14px; text-align: center; padding: 40px 0; }

.recommend-card {
  background: var(--bg-card); border: 1px solid var(--gray-250); border-radius: 8px;
  padding: 16px; margin-bottom: 12px; transition: box-shadow 0.3s;
}
.recommend-card:hover { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); }
.recommend-card.high-similarity { border-color: var(--el-color-danger); background: linear-gradient(135deg, var(--el-color-danger-light-9) 0%, transparent 30%); }

.card-header { margin-bottom: 8px; }
.title-row { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.card-title { margin: 0; font-size: 15px; font-weight: 600; color: var(--gray-750); flex: 1; line-height: 1.5; }
.tag-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }

.card-preview { font-size: 13px; color: var(--text-secondary-ui); line-height: 1.6; margin-bottom: 12px; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.card-meta { font-size: 12px; color: var(--text-muted); display: flex; gap: 16px; margin-bottom: 12px; }
.card-footer { display: flex; align-items: center; justify-content: flex-end; gap: 8px; padding-top: 10px; border-top: 1px solid var(--gray-250); }

.detail-body { display: flex; gap: 0; min-height: 300px; }
.detail-left { flex: 0 0 280px; padding: 0 16px 0 0; overflow-y: auto; border-right: 1px solid #eee; }
.detail-right { flex: 1; padding: 0 0 0 16px; overflow-y: auto; }

.detail-section { margin-bottom: 14px; }
.detail-label { font-size: 11px; color: #888; font-weight: 600; margin-bottom: 4px; text-transform: uppercase; letter-spacing: .5px; }
.detail-val { font-size: 13px; color: #333; line-height: 1.6; }

.detail-content-head { font-size: 12px; color: #888; margin-bottom: 10px; }
.detail-content { background: #f5f8f6; border-left: 3px solid #2E7659; padding: 12px 14px; border-radius: 4px; font-size: 13px; line-height: 1.7; color: #444; min-height: 150px; }

.detail-footer { display: flex; justify-content: flex-end; gap: 8px; }
</style>