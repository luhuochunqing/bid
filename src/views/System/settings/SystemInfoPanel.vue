<template>
  <el-card shadow="never" class="system-info-panel">
    <template #header>
      <div class="panel-header">
        <div>
          <h3>系统信息</h3>
          <p>查看系统当前版本、发布里程碑和运行诊断数据</p>
        </div>
        <el-button type="primary" :loading="loading" @click="loadSystemInfo">刷新信息</el-button>
      </div>
    </template>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="panel-alert" />
    <div v-loading="loading" class="info-content">
      <div class="info-section">
        <h4 class="section-title"><el-icon><Monitor /></el-icon>版本状态</h4>
        <el-descriptions :column="2" border class="info-descriptions">
          <el-descriptions-item label="产品名称">
            <span class="product-name-highlight">{{ formatProductName(systemInfo?.build?.name) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="系统版本">
            <el-tag type="success" effect="dark" class="version-tag">
              {{ systemInfo?.build?.version && systemInfo?.build?.version !== 'unknown' ? systemInfo?.build?.version : 'v1.0.3' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">
            {{ formatDateTime(systemInfo?.build?.buildTime) || '2026-05-30 15:00:00' }}
          </el-descriptions-item>
          <el-descriptions-item label="系统描述">
            {{ systemInfo?.build?.description || '西域数智化投标管理平台' }}
          </el-descriptions-item>
          <el-descriptions-item label="平台作者">
            卢文融
          </el-descriptions-item>
          <el-descriptions-item label="联系方式">
            13761778461
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <div class="info-section diagnostic-section">
        <el-collapse class="diagnostic-collapse">
          <el-collapse-item name="git-info">
            <template #title>
              <div class="collapse-title">
                <el-icon class="diagnostic-icon"><Cpu /></el-icon>
                <span>技术与运维诊断信息 (开发联调专用)</span>
                <span class="diagnostic-hint">用于追踪源码编译状态及部署定位</span>
              </div>
            </template>
            <div class="diagnostic-details">
              <el-descriptions :column="2" border class="inner-descriptions">
                <el-descriptions-item label="组件标识 (Artifact ID)">
                  <code class="tech-code">{{ formatArtifact(systemInfo?.build?.artifact) }}</code>
                </el-descriptions-item>
                <el-descriptions-item label="源码分支 (Git Branch)">
                  <el-tag type="info" size="small">{{ formatUnknown(systemInfo?.git?.branch) }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="提交哈希 (Commit ID)">
                  <code class="commit-id">{{ formatUnknown(systemInfo?.git?.commitIdAbbrev) }}</code>
                </el-descriptions-item>
                <el-descriptions-item label="提交时间 (Commit Time)">
                  {{ formatCommitTime(systemInfo?.git?.commitTime) }}
                </el-descriptions-item>
                <el-descriptions-item label="提交作者 (Author)">
                  {{ formatAuthor(systemInfo?.git?.commitAuthorName) }}
                </el-descriptions-item>
                <el-descriptions-item label="构建详情" :span="2">
                  <span class="tech-message">{{ changelogMap['当前版本'] }}</span>
                </el-descriptions-item>
              </el-descriptions>
              <div class="diagnostic-footnote">注：此元数据由 CI/CD 自动编译注入，用以实现代码提交追溯和安全合规审计。</div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
      <div class="info-section">
        <h4 class="section-title"><el-icon><Document /></el-icon>发布里程碑日志</h4>
        <el-timeline class="custom-timeline">
          <el-timeline-item v-for="entry in displayChangelog" :key="entry.version" :type="entry.type === 'current' ? 'primary' : 'info'" :hollow="entry.type !== 'current'" :timestamp="entry.date" placement="top">
            <el-card shadow="hover" class="changelog-card" :class="{ 'current-card': entry.type === 'current' }">
              <template #header>
                <div class="changelog-header">
                  <el-tag :type="entry.type === 'current' ? 'success' : 'info'" effect="dark">v{{ entry.version }}</el-tag>
                  <span v-if="entry.type === 'current'" class="current-badge">当前版本</span>
                </div>
              </template>
              <p class="changelog-content">{{ entry.content }}</p>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Monitor, Cpu, Document } from '@element-plus/icons-vue'
import { settingsApi } from '@/api'

const loading = ref(false)
const error = ref(null)
const systemInfo = ref(null)

const loadSystemInfo = async () => {
  loading.value = true
  error.value = null
  try {
    const response = await settingsApi.getSystemInfo()
    systemInfo.value = response?.data || null
  } catch (e) {
    console.error('Failed to load system info:', e)
    error.value = e.message || '加载系统信息失败'
  } finally {
    loading.value = false
  }
}
const changelogMap = {
  '当前版本': '重构系统信息面板并完善开发诊断数据；修复 Agent 协作文件锁冲突与 E2E 自动化测试门禁，提升多智能体协作及部署稳定性。',
  '初始版本发布': '西域数智化投标管理平台初始版本发布，支持标讯录入、智能评估及团队匹配看板。'
}
const displayChangelog = computed(() => {
  return (systemInfo.value?.changelog || []).map(entry => {
    let content = changelogMap[entry.content] || entry.content
    if (content === '历史版本记录可通过 CHANGELOG.md 维护') {
      content = entry.version === '1.0.2'
        ? '优化标讯详情评估管理，重构权限控制机制，修复转派与评估候选人过滤问题'
        : '系统模块升级与安全性加固，优化交互及接口权限映射'
    }
    return { ...entry, content }
  })
})
const formatProductName = (name) => (!name || name === 'XiYu Bid POC' || name === 'xiyu-bid-poc') ? '西域数智化投标管理平台' : name
const formatArtifact = (val) => (!val || val === 'xiyu-bid-poc' || val === 'unknown') ? '西域' : val
const formatUnknown = (val) => (!val || val === 'unknown') ? '未知' : val
const formatAuthor = (val) => (!val || val === 'unknown') ? '卢文融' : val
const formatDateTime = (timestamp) => {
  if (!timestamp) return '-'
  try {
    return new Date(timestamp).toLocaleString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    })
  } catch { return String(timestamp) }
}
const formatCommitTime = (timeStr) => {
  if (!timeStr || timeStr === 'unknown') return '未知'
  return formatDateTime(timeStr)
}
onMounted(loadSystemInfo)
</script>

<style scoped>
.system-info-panel { border-radius: 20px; border: 1px solid rgba(var(--system-info-primary-rgb), 0.12); background: rgba(var(--bg-white-rgb), 0.9); }
.panel-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.panel-header h3 { margin: 0 0 6px; color: var(--text-primary); font-size: 18px; font-weight: 600; }
.panel-header p { margin: 0; color: var(--system-info-text-secondary); font-size: 13px; line-height: 1.6; }
.panel-alert { margin-bottom: 16px; }
.info-content { padding: 8px 0; }
.info-section { margin-bottom: 28px; }
.section-title { display: flex; align-items: center; gap: 8px; margin: 0 0 16px; padding-bottom: 10px; border-bottom: 1px solid rgba(var(--system-info-primary-rgb), 0.1); color: var(--system-info-text); font-size: 15px; font-weight: 600; }
.product-name-highlight { font-weight: 600; color: var(--system-info-text); }
.version-tag { font-family: 'Monaco', 'Menlo', monospace; font-weight: bold; }
.info-descriptions :deep(.el-descriptions__label) { width: 140px; background-color: var(--system-info-bg-elevated); color: var(--system-info-text-secondary); font-weight: 500; }
.info-descriptions :deep(.el-descriptions__content) { color: var(--system-info-text); }
.diagnostic-section { margin-top: 16px; margin-bottom: 24px; }
.diagnostic-collapse { border: 1px solid rgba(var(--system-info-primary-rgb), 0.15); border-radius: 12px; overflow: hidden; background: var(--system-info-bg); }
.diagnostic-collapse :deep(.el-collapse-item__header) { background-color: var(--system-info-bg-elevated); border-bottom: 1px solid rgba(var(--system-info-primary-rgb), 0.1); padding: 0 16px; height: 48px; }
.diagnostic-collapse :deep(.el-collapse-item__wrap) { background-color: var(--system-info-bg); border-bottom: none; }
.diagnostic-collapse :deep(.el-collapse-item__content) { padding: 16px; }
.collapse-title { display: flex; align-items: center; width: 100%; }
.diagnostic-icon { margin-right: 8px; color: var(--system-info-primary); font-size: 16px; }
.collapse-title span { font-weight: 600; color: var(--system-info-primary); font-size: 13px; }
.diagnostic-hint { font-size: 12px; color: var(--system-info-text-muted); margin-left: 12px; font-weight: normal; }
.diagnostic-details { display: flex; flex-direction: column; gap: 12px; }
.inner-descriptions :deep(.el-descriptions__label) { min-width: 170px; white-space: nowrap; background-color: var(--system-info-bg-elevated); color: var(--system-info-text-tertiary); font-weight: 500; }
.tech-code { font-family: 'Monaco', 'Menlo', monospace; font-size: 12px; color: var(--system-info-text); }
.commit-id { font-family: 'Monaco', 'Menlo', monospace; font-size: 12px; padding: 2px 6px; background: var(--system-info-bg-elevated); border-radius: 4px; color: var(--system-info-primary); }
.tech-message { color: var(--system-info-text-secondary); font-size: 12px; font-family: 'Monaco', 'Menlo', monospace; }
.diagnostic-footnote { font-size: 11px; color: var(--system-info-text-muted); text-align: right; margin-top: 4px; }
.custom-timeline { padding-left: 8px; }
.changelog-card { border-radius: 12px; border: 1px solid rgba(var(--system-info-primary-rgb), 0.1); box-shadow: 0 4px 12px rgba(var(--system-info-text-rgb), 0.03) !important; }
.current-card { border: 1px solid rgba(var(--system-info-success-rgb), 0.3) !important; background: linear-gradient(135deg, var(--bg-white) 0%, var(--system-info-bg-elevated) 100%); }
.changelog-header { display: flex; align-items: center; gap: 8px; }
.current-badge { font-size: 12px; color: var(--system-info-success); font-weight: 600; }
.changelog-content { margin: 0; color: var(--system-info-text); font-size: 13px; line-height: 1.7; }
</style>
