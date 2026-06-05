<template>
  <div class="calendar-page">
    <div class="page-header">
      <h2>投标日历</h2>
      <el-button type="primary" @click="showDialog('create')">添加事件</el-button>
    </div>

    <el-calendar v-model="currentDate">
      <template #date-cell="{ data }">
        <div class="calendar-cell" :class="{ 'is-selected': data.isSelected }">
          <span class="date-num">{{ data.day.split('-').slice(2).join('') }}</span>
          <div class="event-list">
            <div
              v-for="event in getEventsForDate(data.day)"
              :key="event.id"
              class="event-item"
              :class="'event-' + event.riskLevel"
              @click="showDialog('view', event)"
            >
              {{ event.title }}
            </div>
          </div>
        </div>
      </template>
    </el-calendar>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.eventType">
            <el-option label="截止日期" value="DEADLINE" />
            <el-option label="会议" value="MEETING" />
            <el-option label="里程碑" value="MILESTONE" />
            <el-option label="提醒" value="REMINDER" />
            <el-option label="提交" value="SUBMISSION" />
            <el-option label="审核" value="REVIEW" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="form.riskLevel">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="紧急" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <template v-if="dialogMode === 'view'">
          <el-button @click="dialogVisible = false">关闭</el-button>
          <el-button type="primary" @click="dialogMode = 'edit'">编辑</el-button>
        </template>
        <template v-else>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveEvent">保存</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { calendarApi } from '@/api/modules/collaboration.js'

const currentDate = ref(new Date())
const events = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('添加事件')
const dialogMode = ref('create')
const form = reactive({
  id: null, title: '', eventType: 'REMINDER', startDate: '', endDate: '',
  riskLevel: 'MEDIUM', description: '', priority: 'MEDIUM', allDay: true
})

onMounted(() => { loadEvents() })

async function loadEvents() {
  loading.value = true
  try {
    const year = currentDate.value.getFullYear()
    const month = currentDate.value.getMonth() + 1
    const res = await calendarApi.getEventsByMonth(year, month)
    events.value = res.data || []
  } catch (e) {
    ElMessage.error('加载日历事件失败')
  } finally {
    loading.value = false
  }
}

function getEventsForDate(date) {
  return events.value.filter(e => e.startDate === date || (e.startDate <= date && e.endDate >= date))
}

function showDialog(mode, event = null) {
  dialogMode.value = mode
  if (mode === 'create') {
    dialogTitle.value = '添加事件'
    Object.assign(form, { id: null, title: '', eventType: 'REMINDER', startDate: '', endDate: '', riskLevel: 'MEDIUM', description: '', priority: 'MEDIUM', allDay: true })
  } else if (mode === 'view') {
    dialogTitle.value = '事件详情'
    Object.assign(form, { ...event })
  } else {
    dialogTitle.value = '编辑事件'
    Object.assign(form, { ...event })
  }
  dialogVisible.value = true
}

async function saveEvent() {
  try {
    if (form.id) {
      await calendarApi.updateEvent(form.id, form)
    } else {
      await calendarApi.createEvent(form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadEvents()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}
</script>

<style scoped>
.calendar-page { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
.calendar-cell { min-height: 80px; }
.event-item { font-size: 12px; padding: 2px 4px; margin-bottom: 2px; border-radius: 4px; cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.event-LOW { background: #e8f5e9; color: #2e7d32; }
.event-MEDIUM { background: #fff3e0; color: #ef6c00; }
.event-HIGH { background: #fce4ec; color: #c62828; }
.event-CRITICAL { background: #ffebee; color: #b71c1c; }
</style>
