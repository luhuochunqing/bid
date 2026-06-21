<template>
  <!-- Adaptive Dynamic Form (M4.1: Dynamic Form Engine — project.basic scope) -->
  <AdaptiveFormPage
    ref="adaptiveForm"
    scope="project.basic"
    :model-value="basicForm"
    :disabled="false"
    @update:model-value="handleDynamicUpdate"
    @submit="$emit('submit')"
  >
    <!-- #fallback-form: original hardcoded basic-info form -->
    <template #fallback-form>
      <el-form ref="formRef" :model="basicForm" :rules="basicRules" label-width="120px">
        <el-alert
          title="提示：可以从CRM系统同步客户信息，或手动填写以下信息"
          type="info"
          :closable="false"
          show-icon
          class="mb-16"
        />

        <el-form-item label="CRM同步" prop="syncFromCRM">
          <el-button type="primary" :icon="Refresh" :loading="syncing" @click="handleSyncFromCRM">
            从CRM同步客户信息
          </el-button>
          <span v-if="syncedFromCRM" class="sync-tip">已从CRM同步</span>
        </el-form-item>

        <el-divider content-position="left">基本信息</el-divider>

        <el-form-item label="项目名称" prop="name">
          <el-input v-model="basicForm.name" placeholder="请输入项目名称" clearable />
        </el-form-item>

        <el-form-item label="客户名称" prop="customer">
          <el-input v-model="basicForm.customer" placeholder="请输入客户名称" clearable />
        </el-form-item>

        <el-form-item label="预算(万元)" prop="budget">
          <el-input-number
            v-model="basicForm.budget"
            :min="0"
            :precision="2"
            :step="10"
            controls-position="right"
            style="width: 200px"
            @focus="handleBudgetFocus"
          />
        </el-form-item>

        <el-form-item label="行业" prop="industry">
          <el-select v-model="basicForm.industry" placeholder="请选择行业" clearable>
            <el-option label="政府" value="政府" />
            <el-option label="能源" value="能源" />
            <el-option label="交通" value="交通" />
            <el-option label="金融" value="金融" />
            <el-option label="教育" value="教育" />
            <el-option label="医疗" value="医疗" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>

        <el-form-item label="地区" prop="region">
          <el-input v-model="basicForm.region" placeholder="请输入地区" clearable />
        </el-form-item>

        <el-form-item label="招标平台" prop="platform">
          <el-select
            v-model="basicForm.platform"
            placeholder="请选择招标平台"
            filterable
            allow-create
            clearable
            style="width: 100%"
            @change="(val) => $emit('platform-change', val)"
          >
            <el-option
              v-for="site in platformOptions"
              :key="site.id"
              :label="site.name"
              :value="site.name"
            >
              <span>{{ site.name }}</span>
              <span :style="{ float: 'right', color: 'var(--el-text-color-placeholder)', fontSize: '12px' }">{{ site.region }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="投标截止日期" prop="deadline">
          <el-date-picker
            v-model="basicForm.deadline"
            type="date"
            placeholder="请选择日期"
            value-format="YYYY-MM-DD"
            :disabled-date="disabledDate"
          />
        </el-form-item>

        <el-form-item label="项目负责人" prop="manager">
          <el-select v-model="basicForm.manager" placeholder="请选择负责人">
            <el-option
              v-for="user in userList"
              :key="user.id"
              :label="formatUserLabel(user)"
              :value="user.name"
            />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">竞争对手信息</el-divider>

        <el-form-item label="竞争对手">
          <el-select
            v-model="basicForm.competitors"
            multiple
            filterable
            allow-create
            placeholder="选择或输入竞争对手名称"
            style="width: 100%"
            @change="(val) => $emit('competitors-change', val)"
          >
            <el-option
              v-for="item in competitorOptions"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
        </el-form-item>

        <el-form-item
          v-if="competitorAnalysis.length > 0"
          label="竞争分析"
          class="competitor-analysis-item"
        >
          <el-table :data="competitorAnalysis" size="small" border>
            <el-table-column prop="name" label="竞争对手" width="150" />
            <el-table-column prop="strength" label="优势分析" width="200">
              <template #default="{ row }">
                <el-input v-model="row.strength" placeholder="优势分析" size="small" />
              </template>
            </el-table-column>
            <el-table-column prop="weakness" label="劣势分析" width="200">
              <template #default="{ row }">
                <el-input v-model="row.weakness" placeholder="劣势分析" size="small" />
              </template>
            </el-table-column>
            <el-table-column prop="winRate" label="历史中标率" width="150">
              <template #default="{ row }">
                <div class="win-rate-input">
                  <el-input-number
                    v-model="row.winRate"
                    :min="0"
                    :max="100"
                    :precision="0"
                    size="small"
                    controls-position="right"
                  />
                  <span>%</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="history" label="历史中标项目" min-width="200">
              <template #default="{ row }">
                <el-input
                  v-model="row.history"
                  placeholder="历史中标项目，用逗号分隔"
                  size="small"
                  type="textarea"
                  :rows="2"
                />
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
      </el-form>
    </template>
  </AdaptiveFormPage>
</template>

<script setup>
import { ref, shallowRef, nextTick } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AdaptiveFormPage from '@/components/common/AdaptiveFormPage.vue'
import { formatUserLabel } from '@/utils/formatUserLabel.js'

const basicForm = defineModel('basicForm', { type: Object, required: true })
defineProps({
  competitorAnalysis: { type: Array, required: true },
  platformOptions: { type: Array, default: () => [] },
  userList: { type: Array, default: () => [] },
  competitorOptions: { type: Array, default: () => [] }
})

const emit = defineEmits(['platform-change', 'competitors-change', 'sync-crm-data'])

const formRef = ref(null)
const adaptiveForm = shallowRef(null)
const syncing = ref(false)
const syncedFromCRM = ref(false)

const basicRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  customer: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  deadline: [{ required: true, message: '请选择投标截止日期', trigger: 'change' }],
  manager: [{ required: true, message: '请选择项目负责人', trigger: 'change' }]
}

const disabledDate = (time) => time.getTime() < Date.now() - 24 * 60 * 60 * 1000

async function handleSyncFromCRM() {
  syncing.value = true
  try {
    await new Promise((resolve) => setTimeout(resolve, 1000))
    emit('sync-crm-data', {
      name: '某央企智慧办公平台采购项目',
      customer: '某央企集团',
      budget: 500,
      industry: '政府',
      region: '北京'
    })
    syncedFromCRM.value = true
    ElMessage.success('CRM数据同步成功')
  } catch (error) {
    ElMessage.error('CRM数据同步失败')
  } finally {
    syncing.value = false
  }
}

function handleBudgetFocus() {
  if (basicForm.value.budget === 0) {
    nextTick(() => {
      basicForm.value.budget = null
    })
  }
}

function handleDynamicUpdate(value) {
  Object.assign(basicForm.value, value)
}

async function validate() {
  if (adaptiveForm.value?.isDynamic?.value) {
    const result = await adaptiveForm.value.validate()
    return result === ''
  }
  return formRef.value?.validate().catch(() => false)
}

defineExpose({ validate })
</script>

<style scoped>
.mb-16 { margin-bottom: 16px; }

.sync-tip {
  margin-left: 12px;
  color: var(--el-color-success);
  font-size: 14px;
}

.competitor-analysis-item { display: block; }

.competitor-analysis-item :deep(.el-form-item__content) { width: 100%; }

.win-rate-input {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
