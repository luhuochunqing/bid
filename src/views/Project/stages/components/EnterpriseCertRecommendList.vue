<template>
  <div v-if="certificates.length > 0" class="cert-recommend-section">
    <div class="section-label-row">
      <label class="section-label">
        匹配企业资质证书
        <el-tag size="small" type="success">{{ certificates.length }} 项证书匹配</el-tag>
      </label>
      <span class="cert-tip">直连企业资质库，原件清晰可直接查阅与复制引用</span>
    </div>
    <div class="cert-grid">
      <div v-for="cert in certificates" :key="cert.id" class="cert-card">
        <div class="cert-card-header">
          <span class="cert-title" :title="cert.name">{{ cert.name }}</span>
          <el-tag
            size="small"
            :type="cert.status === 'VALID' ? 'success' : (cert.status === 'EXPIRING' ? 'warning' : 'danger')"
          >
            {{ cert.status === 'VALID' ? '有效' : (cert.status === 'EXPIRING' ? '即将到期' : '已过期') }}
          </el-tag>
        </div>
        <div class="cert-card-body">
          <div class="cert-preview-wrap">
            <el-image
              v-if="cert.fileUrl"
              :src="cert.fileUrl"
              :preview-src-list="[cert.fileUrl]"
              fit="cover"
              class="cert-thumbnail"
              preview-teleported
            >
              <template #error>
                <div class="cert-img-error">证书原件</div>
              </template>
            </el-image>
            <div v-else class="cert-no-img">暂无扫描件</div>
          </div>
          <div class="cert-meta">
            <div v-if="cert.certificateNo" class="cert-meta-item">
              <span class="meta-label">证书编号：</span>
              <span class="meta-val">{{ cert.certificateNo }}</span>
            </div>
            <div v-if="cert.issuer" class="cert-meta-item">
              <span class="meta-label">发证机构：</span>
              <span class="meta-val">{{ cert.issuer }}</span>
            </div>
            <div v-if="cert.expiryDate" class="cert-meta-item">
              <span class="meta-label">有效期至：</span>
              <span class="meta-val">{{ cert.expiryDate }}</span>
            </div>
            <div v-if="cert.certScope" class="cert-meta-item">
              <span class="meta-label">认证范围：</span>
              <span class="meta-val cert-scope" :title="cert.certScope">{{ cert.certScope }}</span>
            </div>
          </div>
        </div>
        <div v-if="cert.matchReasons && cert.matchReasons.length > 0" class="cert-reasons">
          <el-tag v-for="(reason, idx) in cert.matchReasons" :key="idx" size="small" effect="plain" type="info">
            {{ reason }}
          </el-tag>
        </div>
        <div class="cert-actions">
          <el-button size="small" type="primary" plain @click="handleCopyCertInfo(cert)">复制证书信息</el-button>
          <el-button v-if="cert.fileUrl" size="small" @click="handleCopyCertUrl(cert.fileUrl)">复制图片链接</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'

defineProps({
  certificates: {
    type: Array,
    default: () => []
  }
})

async function handleCopyCertInfo(cert) {
  if (!cert) return
  const lines = [
    `证书名称：${cert.name || ''}`,
    cert.certificateNo ? `证书编号：${cert.certificateNo}` : null,
    cert.issuer ? `发证机构：${cert.issuer}` : null,
    cert.expiryDate ? `有效期至：${cert.expiryDate}` : null,
    cert.certScope ? `认证范围：${cert.certScope}` : null,
  ].filter(Boolean).join('\n')
  try {
    await navigator.clipboard.writeText(lines)
    ElMessage.success('证书信息已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

async function handleCopyCertUrl(url) {
  if (!url) return
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('证书原件图片链接已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}
</script>

<style scoped>
.cert-recommend-section { margin-top: 4px; }
.section-label-row { display: flex; align-items: center; justify-content: space-between; }
.section-label { font-size: 14px; font-weight: 600; color: var(--text-secondary-ui); display: flex; align-items: center; gap: 8px; }
.cert-tip { font-size: 12px; color: var(--text-muted); }
.cert-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 8px; }
@media (max-width: 720px) { .cert-grid { grid-template-columns: 1fr; } }
.cert-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-bg-color);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: all 0.2s ease;
}
.cert-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: var(--shadow-sm);
}
.cert-card-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.cert-title { font-size: 13px; font-weight: 600; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cert-card-body { display: flex; gap: 10px; align-items: flex-start; }
.cert-preview-wrap { width: 80px; height: 80px; flex-shrink: 0; border-radius: 4px; overflow: hidden; background: var(--el-fill-color-light); border: 1px solid var(--el-border-color-lighter); }
.cert-thumbnail { width: 100%; height: 100%; cursor: pointer; display: block; }
.cert-img-error, .cert-no-img { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; font-size: 11px; color: var(--text-muted); text-align: center; }
.cert-meta { display: flex; flex-direction: column; gap: 4px; font-size: 12px; overflow: hidden; flex: 1; }
.cert-meta-item { display: flex; line-height: 1.4; }
.meta-label { color: var(--text-secondary-ui); flex-shrink: 0; }
.meta-val { color: var(--text-primary); word-break: break-all; }
.cert-scope { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.cert-reasons { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 2px; }
.cert-actions { display: flex; gap: 8px; margin-top: 4px; }
</style>
