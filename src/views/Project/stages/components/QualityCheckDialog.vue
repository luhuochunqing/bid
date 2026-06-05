<template>
  <el-dialog v-model="visible" title="AI 标书质量核查" width="720px" :close-on-click-modal="false">
    <div v-if="loading" style="text-align:center;padding:60px 0;color:#909399;">
      <div style="font-size:28px;margin-bottom:12px;">🤖</div>
      <div>正在扫描投标文件内容合规性...</div>
    </div>
    <template v-else-if="result">
      <div style="display:flex;gap:8px;margin-bottom:16px;align-items:center;justify-content:space-between;">
        <div style="display:flex;gap:8px;align-items:center;">
          <span style="font-size:13px;color:#606266;">整体评分：</span>
          <el-tag :type="scoreType" size="large">{{ result.score }} 分</el-tag>
          <span style="font-size:12px;color:#909399;">{{ statusText }}</span>
        </div>
        <div style="display:flex;gap:6px;">
          <el-button size="small" @click="recheck">🔄 重新检查</el-button>
          <el-button size="small" @click="exportReport">📤 导出报告</el-button>
        </div>
      </div>

      <div v-for="group in groups" :key="group.key" style="background:#fff;border:1px solid #e8e8e8;border-radius:8px;margin-bottom:8px;overflow:hidden;">
        <div style="padding:10px 14px;display:flex;justify-content:space-between;align-items:center;cursor:pointer;background:#fff;" @click="toggleGroup(group.key)">
          <div style="font-size:13px;font-weight:600;color:#333;">{{ group.label }}</div>
          <div style="display:flex;align-items:center;gap:8px;">
            <span v-if="group.failCount" style="font-size:11px;padding:1px 6px;border-radius:3px;font-weight:600;background:#fee2e2;color:#991b1b;">不通过 {{ group.failCount }}</span>
            <span v-if="group.warnCount" style="font-size:11px;padding:1px 6px;border-radius:3px;font-weight:600;background:#fef3c7;color:#92400e;">警告 {{ group.warnCount }}</span>
            <span v-if="group.manualCount" style="font-size:11px;padding:1px 6px;border-radius:3px;font-weight:600;background:#e2e8f0;color:#475569;">需确认 {{ group.manualCount }}</span>
            <span style="color:#9ca3af;font-size:14px;">{{ openGroups[group.key] ? '▴' : '▾' }}</span>
          </div>
        </div>
        <div v-if="openGroups[group.key]" style="border-top:1px solid #eee;">
          <div v-for="(item, i) in group.items" :key="i" style="padding:8px 14px;border-bottom:1px solid #f5f5f5;cursor:pointer;display:flex;justify-content:space-between;align-items:center;" @click="showDetail(item)">
            <div>
              <span style="font-size:12px;color:#666;">{{ item.item }}</span>
            </div>
            <el-tag v-if="item.status === 'pass'" size="small" type="success" effect="plain">通过</el-tag>
            <el-tag v-else-if="item.status === 'fail'" size="small" type="danger" effect="plain">不通过</el-tag>
            <el-tag v-else-if="item.status === 'manual'" size="small" type="info" effect="plain">需人工确认</el-tag>
            <el-tag v-else size="small" type="warning" effect="plain">警告</el-tag>
          </div>
        </div>
      </div>
    </template>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="检查项详情" width="560px" :close-on-click-modal="false" append-to-body>
      <div v-if="detailItem" class="detail-content">
        <div class="dt-row"><div class="dt-label">检查状态</div><div><el-tag v-if="detailItem.status==='pass'" type="success">✓ 通过</el-tag><el-tag v-else-if="detailItem.status==='fail'" type="danger">✗ 不通过</el-tag><el-tag v-else-if="detailItem.status==='manual'" type="info">◐ 需人工确认</el-tag><el-tag v-else type="warning">⚠ 警告</el-tag></div></div>
        <div class="dt-row"><div class="dt-label">检查项说明</div><div class="dt-val">{{ detailItem.item }}</div></div>
        <div class="dt-row"><div class="dt-label">AI 检查结论</div><div class="dt-val">{{ detailItem.suggestion || 'AI已完成该项检查' }}</div></div>
        <div v-if="detailItem.status === 'fail'" class="dt-row">
          <div class="dt-label">建议操作</div>
          <div class="dt-val" style="color:#991b1b;font-weight:600;">🚨 高风险项，建议立即修订后重新上传标书并再次执行检查。</div>
        </div>
        <div v-else-if="detailItem.status === 'warn'" class="dt-row">
          <div class="dt-label">建议操作</div>
          <div class="dt-val" style="color:#92400e;">⚠ 投标管理员/组长复核后决定是否修订。</div>
        </div>
        <div v-else-if="detailItem.status === 'manual'" class="dt-row">
          <div class="dt-label">建议操作</div>
          <div class="dt-val" style="color:#475569;">◐ AI 无法完成该项检查，请相关人员线下核对后在系统中标记完成。</div>
        </div>
      </div>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { complianceApi } from '@/api/modules/ai.js'

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const visible = ref(false)
const loading = ref(false)
const result = ref(null)
const detailVisible = ref(false)
const detailItem = ref(null)

const openGroups = reactive({ basic: true, detail: true, attach: true })

const groups = computed(() => {
  if (!result.value?.issues) return []
  const all = result.value.issues
  const basicCheckItems = ['cover_info','bid_letter_format','authorization_letter','business_license','qualification_match','price_consistency','currency_unit','schedule_response','signature_seal','contact_info']
  const attachCheckItems = ['id_auth_letter','bid_opening_table','deposit_receipt','ca_certificate','deposit_refund']
  // Map from checkItem to group
  const mapping = {}
  for (const issue of all) {
    if (basicCheckItems.includes(issue.checkItem || '')) mapping[issue.checkItem] = 'basic'
    else if (attachCheckItems.includes(issue.checkItem || '')) mapping[issue.checkItem] = 'attach'
    else mapping[issue.checkItem || ''] = 'detail'
  }
  const groupBy = (key, label) => {
    const items = all.filter(i => mapping[i.checkItem || ''] === key)
    return { key, label, items,
      failCount: items.filter(i => i.status === 'fail').length,
      warnCount: items.filter(i => i.status === 'warn').length,
      manualCount: items.filter(i => i.status === 'manual').length,
    }
  }
  return [groupBy('basic', '基本信息检查'), groupBy('detail', '分项检查'), groupBy('attach', '附件清单')]
})

const scoreType = computed(() => {
  if (!result.value) return 'info'
  const s = result.value.score
  return s >= 80 ? 'success' : s >= 60 ? 'warning' : 'danger'
})
const statusText = computed(() => {
  if (!result.value) return ''
  const s = result.value.overallStatus
  return s === 'PASS' ? '全部通过' : s === 'WARNING' ? '有警告项，建议复核' : s === 'FAIL' ? '有不通过项，需立即修订' : '需人工确认'
})

function toggleGroup(key) { openGroups[key] = !openGroups[key] }
function showDetail(item) { detailItem.value = item; detailVisible.value = true }

async function open(data) {
  result.value = data
  visible.value = true
}

async function recheck() {
  loading.value = true
  try {
    const resp = await complianceApi.checkBidDocumentQuality(props.projectId)
    result.value = resp?.data
    ElMessage.success('标书质量核查完成')
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '检查失败') }
  finally { loading.value = false }
}

function exportReport() {
  window.alert('已导出 AI 标书质量核查报告 PDF（演示）')
}

defineExpose({ open })
</script>

<style scoped>
.detail-content { padding: 0 4px; }
.dt-row { margin-bottom: 14px; }
.dt-label { font-size: 11px; color: #888; font-weight: 600; margin-bottom: 4px; text-transform: uppercase; letter-spacing: .5px; }
.dt-val { font-size: 13px; color: #333; line-height: 1.6; }
</style>
