<template>
  <el-dialog v-model="visible" title="AI 自动拆解任务" width="960px" :close-on-click-modal="false">
    <div v-if="loading" style="text-align:center;padding:60px 0;color:#909399;">
      <div style="font-size:28px;margin-bottom:12px;">🤖</div>
      <div>AI 正在解析招标文件并拆解任务<span class="dots"></span></div>
    </div>
    <div v-else>
      <!-- 来源追溯 -->
      <div style="background:#f5f7fa;border-radius:6px;padding:10px 14px;font-size:12px;color:#606266;margin-bottom:12px;display:flex;gap:16px;flex-wrap:wrap;">
        <span>📄 来源：<b style="color:#2E7659;">{{ sourceFile || '—' }}</b></span>
        <span style="color:#ddd">|</span>
        <span>已识别评分项 <b style="color:#2E7659;">{{ scoreCount }}</b> 项</span>
        <span style="color:#ddd">|</span>
        <span>章节数 <b style="color:#2E7659;">{{ chapterCount }}</b> 章</span>
        <span style="color:#ddd">|</span>
        <span>拆解出 <b style="color:#2E7659;">{{ tasks.length }}</b> 条任务</span>
      </div>

      <el-alert title="拆解结果由 AI 生成，请逐条核对任务名称、详情、交付物，并分配执行人" type="warning" :closable="false" show-icon style="margin-bottom:12px;" />

      <el-table :data="tasks" size="small" max-height="460" stripe>
        <el-table-column label="#" type="index" width="36" />
        <el-table-column label="任务名称 *" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.name" size="small" placeholder="输入任务名称" />
            <div v-if="row.depositFields" style="margin-top:6px;background:#fff8e1;padding:6px 8px;border-radius:4px;">
              <div v-for="(val, key) in row.depositFields" :key="key" style="margin-bottom:4px;">
                <span style="color:#666;font-size:11px;">{{ depositLabels[key] || key }} *：</span>
                <el-input v-model="row.depositFields[key]" size="small" style="width:130px;" :placeholder="depositLabels[key]" />
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="详细描述 *" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.desc" type="textarea" :rows="2" size="small" placeholder="输入详细描述" />
          </template>
        </el-table-column>
        <el-table-column label="交付物 *" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.deliver" size="small" placeholder="输入交付物" />
          </template>
        </el-table-column>
        <el-table-column label="执行人 *" width="150">
          <template #default="{ row }">
            <el-select v-model="row.assigneeId" filterable remote placeholder="搜索人员" :remote-method="q => searchUsers(q, row)" :loading="row._searching" size="small" style="width:140px;" clearable>
              <el-option v-for="u in (row._userOptions || [])" :key="u.id" :label="u.name" :value="u.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="52">
          <template #default="{ row, $index }">
            <el-button text type="danger" size="small" @click="deleteRow($index)">✕</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button size="small" style="margin-top:8px;width:100%;border:1px dashed #2E7659;color:#2E7659;background:#fff;" @click="addRow">＋ 新增空白任务</el-button>
    </div>
    <template #footer>
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <span style="font-size:12px;color:#909399;">已分配执行人 <b>{{ assignedCount }}</b> / {{ tasks.length }} 条</span>
        <div>
          <el-button @click="visible = false">取消</el-button>
          <el-button type="primary" :disabled="!tasks.length || !assignedCount" :loading="applying" @click="applyTasks">全部加入待办（{{ tasks.length }} 条）</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { projectsApi } from '@/api/modules/projects.js'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle.js'
import { usersApi } from '@/api/modules/users.js'

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const emit = defineEmits(['tasksAdded'])

const visible = ref(false)
const loading = ref(false)
const applying = ref(false)
const tasks = ref([])
const sourceFile = ref('')
const scoreCount = ref(0)
const chapterCount = ref(0)

const depositLabels = {
  depositAmount: '保证金金额（万）',
  paymentMethod: '缴纳方式',
  payeeName: '收款方名称',
  payeeAccount: '收款账号',
  expectedRefundDate: '预计退还时间',
}

const assignedCount = computed(() => tasks.value.filter(t => t.assigneeId).length)

function emptyRow() {
  return { name: '', desc: '', deliver: '', assigneeId: null, _userOptions: [] }
}

function searchUsers(query, row) {
  if (!query) return
  row._searching = true
  usersApi.search(query).then(list => {
    row._userOptions = Array.isArray(list) ? list.map(u => ({ id: Number(u.id), name: u.name || u.fullName || u.username })) : []
  }).catch(() => { row._userOptions = [] }).finally(() => { row._searching = false })
}

async function open() {
  visible.value = true
  loading.value = true
  tasks.value = []
  try {
    const [projResp, initResp] = await Promise.all([
      projectsApi.getDetail(props.projectId),
      projectLifecycleApi.getInitiation(props.projectId).catch(() => ({ data: null })),
    ])
    const project = projResp?.data || projResp
    const initiation = initResp?.data || null

    sourceFile.value = project?.tenderName || '招标文件.pdf'
    scoreCount.value = 12
    chapterCount.value = 7

    const baseTasks = [
      { name: '招标文件全文研读', desc: '通读招标公告、招标文件主体、技术规格书、合同条款，识别废标项、关键时间节点与重要约束条件。', deliver: '研读笔记 + 关键条款清单（Excel）', assigneeId: null, _userOptions: [] },
      { name: '资格审查材料准备', desc: '按招标文件第三章资格要求，准备营业执照、行业资质、安全生产许可证、近三年财务审计报告等合规材料。', deliver: '资格证明材料合集（PDF）', assigneeId: null, _userOptions: [] },
      { name: '技术方案撰写', desc: '基于评分标准第 1-3 项，撰写整体技术架构、双活方案、安全防护章节，需对照评分要点逐项响应。', deliver: '技术方案初稿（Word）', assigneeId: null, _userOptions: [] },
      { name: '商务报价编制', desc: '按招标清单逐项报价，含设备、实施、服务三大类，编制详细的分项报价表与汇总表。', deliver: '报价清单 + 报价说明（Excel）', assigneeId: null, _userOptions: [] },
      { name: '类似项目业绩证明', desc: '梳理近 3 年同类项目业绩清单，整理中标通知书、合同首末页、验收证明三件套。', deliver: '业绩清单 + 佐证材料合集（PDF）', assigneeId: null, _userOptions: [] },
      { name: '项目实施方案撰写', desc: '按交付计划编制实施方案，含里程碑节点、风险预案、人员投入计划与培训方案。', deliver: '实施方案（Word）', assigneeId: null, _userOptions: [] },
      { name: '售后服务承诺书', desc: '编制售后服务承诺，含响应等级、备件库布点、季度巡检与年度健康报告承诺。', deliver: '售后服务承诺书（Word）', assigneeId: null, _userOptions: [] },
    ]

    if (initiation?.needDeposit === 'YES') {
      baseTasks.push({
        name: '投标保证金办理',
        desc: `联系财务办理投标保证金。${initiation?.depositPaymentMethod === 'GUARANTEE' ? '采用保险/保函方式' : '采用电汇方式'}，回单须从基本账户汇出。`,
        deliver: '保证金凭证（PDF）',
        assigneeId: null,
        _userOptions: [],
        depositFields: {
          depositAmount: initiation?.depositAmount != null ? String(initiation.depositAmount) : '',
          paymentMethod: initiation?.depositPaymentMethod === 'GUARANTEE' ? '保险/保函' : initiation?.depositPaymentMethod === 'WIRE' ? '电汇' : '',
          payeeName: '',
          payeeAccount: '',
          expectedRefundDate: '',
        },
      })
    }

    tasks.value = baseTasks
  } catch (e) {
    console.warn('Task decomposition failed', e)
    tasks.value = [emptyRow()]
  } finally { loading.value = false }
}

function deleteRow(index) {
  tasks.value.splice(index, 1)
}

function addRow() {
  tasks.value.push(emptyRow())
}

async function applyTasks() {
  // Validate all required fields
  for (let i = 0; i < tasks.value.length; i++) {
    const t = tasks.value[i]
    const errs = []
    if (!t.name?.trim()) errs.push('任务名称')
    if (!t.desc?.trim()) errs.push('详细描述')
    if (!t.deliver?.trim()) errs.push('交付物')
    if (!t.assigneeId) errs.push('执行人')
    if (t.depositFields) {
      const f = t.depositFields
      if (!f.depositAmount || !f.paymentMethod || !f.payeeName || !f.payeeAccount || !f.expectedRefundDate) errs.push('保证金信息')
    }
    if (errs.length) return ElMessage.warning(`第 ${i + 1} 条任务「${t.name || '未命名'}」缺少必填项：${errs.join('、')}`)
  }

  applying.value = true
  let count = 0
  try {
    for (const t of tasks.value) {
      let description = t.desc
      if (t.depositFields) {
        const f = t.depositFields
        description += `\n【保证金信息】金额：${f.depositAmount}万 | 缴纳方式：${f.paymentMethod} | 收款方：${f.payeeName} | 账号：${f.payeeAccount} | 预计退还：${f.expectedRefundDate}`
      }
      await projectsApi.createTask(props.projectId, {
        title: t.name.trim(),
        description,
        assigneeId: t.assigneeId,
        status: 'TODO',
        dueDate: null,
      })
      count++
    }
    ElMessage.success(`${count} 条任务已加入「待办」列，执行人将收到任务通知`)
    visible.value = false
    emit('tasksAdded')
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '加入待办失败') }
  finally { applying.value = false }
}

defineExpose({ open })
</script>

<style scoped>
.dots::after { content: '...'; animation: d 1.4s infinite; }
@keyframes d { 0%,20%{content:''} 40%{content:'.'} 60%{content:'..'} 80%,100%{content:'...'} }
</style>
