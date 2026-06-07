<template>
  <el-drawer v-model="visible" title="AI 评分标准解析" size="780px" :close-on-click-modal="false">
    <div v-if="loading" style="text-align:center;padding:60px 0;color:var(--text-muted);">
      <div style="font-size:28px;margin-bottom:12px;">🤖</div>
      <div>AI 正在解析招标文件评分标准，联动知识库做智能比对<span class="dots"></span></div>
    </div>
    <div v-else-if="error" style="text-align:center;padding:60px 0;color:var(--color-warning);">
      <div>⚠ {{ error }}</div>
    </div>
    <template v-else>
      <!-- 摘要统计 -->
      <div style="display:grid;grid-template-columns:repeat(5,1fr);gap:8px;margin-bottom:16px;">
        <div v-for="s in stats" :key="s.label" style="text-align:center;padding:10px 4px;background:var(--bg-muted);border-radius:6px;border:1px solid var(--gray-100);cursor:pointer;" @click="activeTab = s.key">
          <div :style="{fontSize:'20px',fontWeight:700,color:s.color||'var(--brand-xiyu-logo)'}">{{ s.count }}</div>
          <div style="font-size:11px;color:var(--text-light);margin-top:2px;">{{ s.label }}</div>
        </div>
      </div>

      <!-- 来源信息 + 工具栏 -->
      <div style="font-size:12px;color:var(--text-muted);margin-bottom:12px;display:flex;justify-content:space-between;align-items:center;">
        <span>📄 来源招标文件 · 解析时间 {{ parseTime }}</span>
        <div style="display:flex;gap:6px;">
          <button style="padding:4px 10px;background:var(--bg-white);border:1px solid var(--gray-200);border-radius:4px;font-size:11px;cursor:pointer;color:var(--border-focus);" @click="reparse">🔄 重新解析</button>
          <button style="padding:4px 10px;background:var(--bg-white);border:1px solid var(--gray-200);border-radius:4px;font-size:11px;cursor:pointer;color:var(--border-focus);" @click="exportReport">📤 导出报告</button>
        </div>
      </div>

      <!-- 折叠面板 -->
      <div v-for="tab in tabs" :key="tab.key" style="background:var(--bg-white);border:1px solid var(--border-base);border-radius:8px;margin-bottom:8px;overflow:hidden;">
        <div style="padding:10px 14px;display:flex;justify-content:space-between;align-items:center;cursor:pointer;background:var(--bg-white);border-bottom:activeTab===tab.key?'1px solid var(--gray-100)':'transparent';" @click="toggleTab(tab.key)">
          <div style="font-size:13px;font-weight:600;color:var(--gray-700);">{{ tab.icon }} {{ tab.title }}</div>
          <div style="display:flex;align-items:center;gap:8px;">
            <span v-for="p in tab.pills" :key="p.txt" :style="{fontSize:'11px',padding:'1px 6px',borderRadius:'3px',fontWeight:600,background:p.cls==='danger'?'var(--status-danger-bg)':p.cls==='warn'?'var(--status-warning-bg)':'var(--status-success-bg)',color:p.cls==='danger'?'var(--status-danger-color)':p.cls==='warn'?'var(--status-warning-color)':'var(--status-success-color)'}">{{ p.txt }}</span>
            <span style="color:var(--text-lighter);font-size:14px;transition:transform .2s;">{{ activeTab === tab.key ? '▴' : '▾' }}</span>
          </div>
        </div>
        <div v-if="activeTab === tab.key" style="padding:12px 14px;border-top:1px solid var(--gray-100);">
          <!-- 评分表 -->
          <table v-if="tab.key === 'score'" style="width:100%;border-collapse:collapse;font-size:12px;">
            <thead><tr style="background:var(--bg-muted-2);"><th style="padding:6px 8px;border:1px solid var(--border-base);text-align:left;">编号</th><th style="padding:6px 8px;border:1px solid var(--border-base);text-align:left;">维度</th><th style="padding:6px 8px;border:1px solid var(--border-base);text-align:left;">指标</th><th style="padding:6px 8px;border:1px solid var(--border-base);text-align:center;">权重</th></tr></thead>
            <tbody>
              <tr v-for="s in scoreItems" :key="s.code"><td style="padding:6px 8px;border:1px solid var(--gray-100);">{{ s.code }}</td><td style="padding:6px 8px;border:1px solid var(--gray-100);">{{ s.dim }}</td><td style="padding:6px 8px;border:1px solid var(--gray-100);">{{ s.detail }}</td><td style="padding:6px 8px;border:1px solid var(--gray-100);text-align:center;font-weight:700;color:var(--brand-xiyu-logo);">{{ s.weight }}</td></tr>
              <tr style="background:var(--bg-muted);"><td colspan="3" style="padding:6px 8px;text-align:right;font-weight:600;">合计</td><td style="padding:6px 8px;text-align:center;font-weight:700;color:var(--brand-xiyu-logo);">{{ totalWeight }}</td></tr>
            </tbody>
          </table>

          <!-- 资质要求（含状态标签 + 筛选） -->
          <div v-if="tab.key === 'qual'">
            <div style="margin-bottom:8px;display:flex;gap:6px;">
              <span v-for="f in qualFilters" :key="f.key" :style="{padding:'3px 10px',borderRadius:'12px',fontSize:'11px',cursor:'pointer',fontWeight:600,border:f.active?'2px solid var(--brand-xiyu-logo)':'1px solid var(--gray-200)',background:f.active?'var(--status-success-bg-soft-active)':'var(--bg-white)',color:f.active?'var(--brand-xiyu-logo)':'var(--text-secondary)'}" @click="setQualFilter(f.key)">{{ f.label }}</span>
            </div>
            <div v-for="q in filteredQualItems" :key="q.id" :style="{padding:'10px 12px',marginBottom:'8px',borderRadius:'6px',background:q.status==='ok'?'var(--status-success-bg-soft)':q.status==='warn'?'var(--status-warning-bg-soft)':q.status==='danger'?'var(--status-danger-bg-soft)':'var(--bg-muted)',border:'1px solid '+(q.status==='ok'?'var(--status-success-border)':q.status==='warn'?'var(--status-warning-border)':q.status==='danger'?'var(--status-danger-border)':'var(--gray-100)')}">
              <div style="display:flex;justify-content:space-between;align-items:center;">
                <div style="font-size:12px;font-weight:600;color:var(--gray-700);">{{ q.name }}</div>
                <span :style="{fontSize:'11px',padding:'2px 8px',borderRadius:'3px',fontWeight:600,background:q.status==='ok'?'var(--status-success-bg)':q.status==='warn'?'var(--status-warning-bg)':q.status==='danger'?'var(--status-danger-bg)':'var(--status-neutral-bg)',color:q.status==='ok'?'var(--status-success-color)':q.status==='warn'?'var(--status-warning-color)':q.status==='danger'?'var(--status-danger-color)':'var(--text-badge)'}">{{ q.statusLabel }}</span>
              </div>
              <div style="font-size:11px;color:var(--gray-650);margin-top:6px;line-height:1.6;">
                <div>📋 招标要求：{{ q.requirement }}</div>
                <div style="margin-top:4px;">🔗 联动 <b style="color:var(--brand-primary);cursor:pointer;text-decoration:underline;" @click="jumpToSource(q.sourceUrl || q.source)">{{ q.source }}</b>：{{ q.detail }}</div>
              </div>
            </div>
          </div>

          <!-- 通用列表（技术/商务/红线） -->
          <div v-if="['tech','biz','red'].includes(tab.key)">
            <div v-for="item in tabItems(tab.key)" :key="item.txt || item.clause" :style="{padding:'8px 10px',marginBottom:'6px',fontSize:'12px',lineHeight:1.6,borderBottom:'1px dashed var(--gray-100)'}">
              <span v-if="tab.key==='red'" style="font-weight:600;color:var(--status-danger-color);">⚠ </span>
              <span v-if="item.section" style="color:var(--brand-primary);font-size:11px;cursor:pointer;" @click="jumpToSection(item.section)">📌 §{{ item.section }}</span>
              <span style="color:var(--gray-600);">{{ item.txt || item.clause }}{{ item.desc ? ' — ' + item.desc : '' }}</span>
              <span v-if="item.tag" style="display:inline-block;background:var(--gray-100);color:var(--text-badge-2);padding:1px 6px;border-radius:3px;font-size:10px;margin-left:4px;">{{ item.tag }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { bidAgentApi } from '@/api/modules/bidAgent.js'

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const emit = defineEmits(['parsed'])
const visible = ref(false)
const loading = ref(false)
const error = ref('')
const activeTab = ref('qual')

const parseTime = ref('')
const scoreItems = ref([])
const qualItems = ref([])
const techItems = ref([])
const bizItems = ref([])
const redItems = ref([])
const rawAnalysis = ref(null)

const totalWeight = computed(() => scoreItems.value.reduce((a, b) => a + (b.weight || 0), 0))

const stats = computed(() => [
  { key: 'score', label: '评分项', count: scoreItems.value.length, color: 'var(--brand-xiyu-logo)' },
  { key: 'qual', label: '资质要求', count: qualItems.value.length, color: qualItems.value.some(q => q.status === 'danger') ? 'var(--color-danger)' : qualItems.value.some(q => q.status === 'warn') ? 'var(--color-warning)' : 'var(--brand-xiyu-logo)' },
  { key: 'tech', label: '技术要点', count: techItems.value.length, color: 'var(--brand-xiyu-logo)' },
  { key: 'biz', label: '商务条款', count: bizItems.value.length, color: 'var(--brand-xiyu-logo)' },
  { key: 'red', label: '风险/红线', count: redItems.value.length, color: 'var(--color-danger)' },
])

const qualPills = computed(() => {
  const p = []
  const danger = qualItems.value.filter(q => q.status === 'danger').length
  const warn = qualItems.value.filter(q => q.status === 'warn').length
  if (danger) p.push({ cls: 'danger', txt: danger + ' 项不满足' })
  if (warn) p.push({ cls: 'warn', txt: warn + ' 项需关注' })
  if (!danger && !warn) p.push({ cls: 'ok', txt: '全部满足' })
  return p
})

const tabs = computed(() => [
  { key: 'score', icon: '📋', title: '评分标准提取', pills: scoreItems.value.length ? [{ cls: 'ok', txt: scoreItems.value.length + ' 项' }] : [] },
  { key: 'qual', icon: '🛡️', title: '资质要求识别（联动知识库）', pills: qualPills.value },
  { key: 'tech', icon: '⚙️', title: '技术要点提取', pills: techItems.value.length ? [{ cls: 'ok', txt: techItems.value.length + ' 条' }] : [] },
  { key: 'biz', icon: '💼', title: '商务条款解析', pills: bizItems.value.length ? [{ cls: 'ok', txt: bizItems.value.length + ' 条' }] : [] },
  { key: 'red', icon: '🚫', title: '废标红线标记', pills: redItems.value.length ? [{ cls: 'danger', txt: redItems.value.length + ' 条' }] : [] },
])

function tabItems(key) {
  if (key === 'tech') return techItems.value
  if (key === 'biz') return bizItems.value
  if (key === 'red') return redItems.value
  return []
}

function toggleTab(key) {
  activeTab.value = activeTab.value === key ? '' : key
}

async function open() {
  visible.value = true
  loading.value = true
  error.value = ''
  scoreItems.value = []
  qualItems.value = []
  techItems.value = []
  bizItems.value = []
  redItems.value = []
  activeTab.value = 'score'

  try {
    const [analysisResp, qualResp] = await Promise.all([
      bidAgentApi.getFullAnalysis(props.projectId),
      bidAgentApi.getQualificationMatch(props.projectId),
    ])

    const analysis = analysisResp?.data
    if (!analysis) { error.value = '暂无分析数据'; return }

    rawAnalysis.value = analysis
    parseTime.value = new Date().toLocaleString('zh-CN', { hour12: false })

    // Score items
    const si = analysis.scoringCriteria?.items || analysis.scoringItems || analysis.scoringCriteria || []
    const scoreCodes = ['A1','A2','A3','A4','B1','B2','C1','C2','C3','D1','D2','D3','E1']
    const scoreDims = ['技术方案','技术方案','技术方案','技术方案','商务方案','商务方案','实施服务','实施服务','实施服务','资质业绩','资质业绩','资质业绩','加分项']
    scoreItems.value = (Array.isArray(si) ? si : []).map((s, i) => ({
      code: s.code || scoreCodes[i] || '',
      dim: s.dim || s.dimension || s.category || scoreDims[i] || '',
      detail: s.detail || s.name || s.description || s.title || '',
      weight: s.weight || s.score || s.points || 0,
    }))

    // Qualification items from qualification-match API
    const qualData = qualResp?.data
    if (qualData?.items || qualData?.qualifications) {
      const items = qualData.items || qualData.qualifications || []
      qualItems.value = items.map(q => ({
        id: q.id || q.requirementId || Math.random().toString(36).slice(2, 6),
        name: q.name || q.requirement || q.title || '',
        requirement: q.requirement || q.description || '',
        status: q.status === 'OK' ? 'ok' : q.status === 'MATCHED' ? 'ok' : q.status === 'WARN' ? 'warn' : q.status === 'NOT_MATCHED' ? 'danger' : q.status || 'warn',
        statusLabel: q.statusLabel || q.status === 'OK' ? '✓ 符合' : q.status === 'MATCHED' ? '✓ 符合' : q.status === 'WARN' ? '⚠ 需关注' : q.status === 'NOT_MATCHED' ? '✗ 不满足' : '待确认',
        source: q.source || q.sourceName || q.sourceType || '资质库',
        detail: q.detail || q.message || q.note || q.comment || '',
      }))
    }

    // Technical items
    const tech = analysis.technicalClassification?.items || analysis.technicalRequirements || analysis.technicalItems || []
    techItems.value = (Array.isArray(tech) ? tech : []).map(t => ({
      txt: t.txt || t.text || t.name || t.title || t.description || t.requirement || (typeof t === 'string' ? t : ''),
      tag: t.tag || t.type || t.category || t.level || '功能',
    }))

    // Commercial items
    const biz = analysis.commercialClassification?.items || analysis.commercialRequirements || analysis.commercialItems || []
    bizItems.value = (Array.isArray(biz) ? biz : []).map(b => ({
      txt: b.txt || b.text || b.name || b.title || b.description || b.clause || b.requirement || (typeof b === 'string' ? b : ''),
      tag: b.tag || b.type || b.category || '条款',
    }))

    // Redline items
    const red = analysis.riskClassification?.items || analysis.riskItems || analysis.redLines || analysis.redlineItems || []
    redItems.value = (Array.isArray(red) ? red : []).map(r => ({
      clause: r.clause || r.name || r.title || r.risk || '',
      desc: r.desc || r.description || r.detail || r.message || r.text || '',
      section: r.section || r.source || '',
    }))

    emit('parsed', {
      dangerCount: qualItems.value.filter(q => q.status === 'danger').length + redItems.value.length,
      warnCount: qualItems.value.filter(q => q.status === 'warn').length,
    })
  } catch (e) { error.value = e?.response?.data?.msg || '评分标准解析失败' }
  finally { loading.value = false }
}

const qualFilter = ref('all')
const qualFilters = computed(() => {
  const all = qualItems.value.length
  const ok = qualItems.value.filter(q => q.status === 'ok').length
  const warn = qualItems.value.filter(q => q.status === 'warn').length
  const danger = qualItems.value.filter(q => q.status === 'danger').length
  return [
    { key: 'all', label: '全部 (' + all + ')', active: qualFilter.value === 'all' },
    { key: 'ok', label: '✓ 已满足 (' + ok + ')', active: qualFilter.value === 'ok' },
    { key: 'warn', label: '⚠ 需关注 (' + warn + ')', active: qualFilter.value === 'warn' },
    { key: 'danger', label: '✗ 不满足 (' + danger + ')', active: qualFilter.value === 'danger' },
  ]
})

function setQualFilter(key) { qualFilter.value = key }

const filteredQualItems = computed(() => {
  if (qualFilter.value === 'all') return qualItems.value
  return qualItems.value.filter(q => q.status === qualFilter.value)
})

// Fix qual section to use filteredQualItems
function jumpToSource(source) {
  const routes = { '资质库': '/knowledge/qualification', '人员库': '/knowledge/personnel', '品牌授权': '/knowledge/brand', '业绩库': '/knowledge/performance' }
  const path = routes[source] || null
  if (path) window.open(path, '_blank')
  else window.alert('跳转至 ' + source + '（演示）')
}
function jumpToSection(section) { window.alert('定位至招标文件 §' + section + '（演示）') }
async function reparse() { await open() }
function exportReport() { window.alert('已导出 AI 评分解析报告 PDF（演示）') }

defineExpose({ open })
</script>

<style scoped>
.dots::after { content: '...'; animation: d 1.4s infinite; }
@keyframes d { 0%,20%{content:''} 40%{content:'.'} 60%{content:'..'} 80%,100%{content:'...'} }
</style>
