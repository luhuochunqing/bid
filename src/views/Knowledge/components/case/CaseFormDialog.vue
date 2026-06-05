<template>
  <el-dialog
    v-model="modelValue"
    title="添加案例"
    width="800px"
    class="add-case-dialog"
    @closed="handleDialogClosed"
  >
    <el-tabs v-model="activeTab">
      <el-tab-pane label="从项目转案例" name="fromProject">
        <el-form
          ref="caseFormRef"
          :model="caseForm"
          label-width="120px"
          :rules="caseFormRules"
        >
          <el-form-item label="选择已中标项目" prop="sourceProjectId">
            <el-select
              v-model="caseForm.sourceProjectId"
              placeholder="选择已中标项目"
              filterable
              :loading="projectOptionsLoading"
              @change="$emit('project-change', $event)"
              style="width: 100%"
            >
              <el-option
                v-for="project in projectOptions"
                :key="project.id"
                :label="project.projectName"
                :value="project.id"
              >
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span>{{ project.projectName }}</span>
                  <span class="option-extra">
                    {{ formatAmount(project.amount) }} | {{ project.source || '结果同步' }}
                  </span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>

          <el-divider content-position="left">项目信息（自动填充）</el-divider>

          <el-form-item label="案例标题" prop="title">
            <el-input v-model="caseForm.title" placeholder="将自动从项目名称生成" />
          </el-form-item>

          <el-form-item label="客户行业" prop="industry">
            <el-select v-model="caseForm.industry" placeholder="请选择行业" style="width: 100%">
              <el-option
                v-for="item in industries"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="项目金额" prop="amount">
            <el-input-number v-model="caseForm.amount" :min="0" :step="10" />
            <span style="margin-left: 8px">万元</span>
          </el-form-item>

          <el-form-item label="实施周期" prop="period">
            <el-date-picker
              v-model="caseForm.period"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 100%"
              value-format="YYYY.MM"
            />
          </el-form-item>

          <el-form-item label="所在地区" prop="location">
            <el-input v-model="caseForm.location" placeholder="如：浙江杭州" />
          </el-form-item>

          <el-form-item label="标签" prop="tags">
            <el-select v-model="caseForm.tags" multiple filterable allow-create placeholder="选择或输入标签" style="width: 100%">
              <el-option
                v-for="item in tagOptions"
                :key="item"
                :label="item"
                :value="item"
              />
            </el-select>
          </el-form-item>

          <el-divider content-position="left">结构化归档</el-divider>

          <el-form-item label="项目概述" prop="description">
            <el-input
              v-model="caseForm.description"
              type="textarea"
              :rows="3"
              placeholder="简要描述项目背景、目标和主要内容..."
            />
          </el-form-item>

          <el-form-item label="技术亮点" prop="techHighlights">
            <el-input
              v-model="caseForm.techHighlights"
              type="textarea"
              :rows="4"
              placeholder="记录本次项目的技术亮点和创新点，每行一个..."
            />
          </el-form-item>

          <el-form-item label="报价策略" prop="priceStrategy">
            <el-input
              v-model="caseForm.priceStrategy"
              type="textarea"
              :rows="3"
              placeholder="记录报价策略和定价逻辑..."
            />
          </el-form-item>

          <el-form-item label="成功关键因素">
            <el-checkbox-group v-model="caseForm.successFactors">
              <el-checkbox value="技术优势">技术优势</el-checkbox>
              <el-checkbox value="价格合理">价格合理</el-checkbox>
              <el-checkbox value="客户关系">客户关系</el-checkbox>
              <el-checkbox value="交付能力">交付能力</el-checkbox>
              <el-checkbox value="品牌影响力">品牌影响力</el-checkbox>
              <el-checkbox value="响应速度">响应速度</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <el-form-item label="经验教训" prop="lessons">
            <el-input
              v-model="caseForm.lessons"
              type="textarea"
              :rows="3"
              placeholder="记录经验教训和改进建议..."
            />
          </el-form-item>

          <el-form-item label="附件">
            <el-upload
              v-model:file-list="caseForm.attachments"
              action="#"
              :auto-upload="false"
              multiple
            >
              <el-button type="primary" plain>上传项目文档</el-button>
              <template #tip>
                <div class="upload-tip">
                  支持上传技术方案、报价单、合同等相关文档
                </div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="手动录入" name="manual">
        <el-form
          ref="manualCaseFormRef"
          :model="manualCaseForm"
          label-width="120px"
          :rules="caseFormRules"
        >
          <el-form-item label="案例标题" prop="title">
            <el-input v-model="manualCaseForm.title" placeholder="请输入案例标题" />
          </el-form-item>

          <el-form-item label="客户名称" prop="customer">
            <el-input v-model="manualCaseForm.customer" placeholder="请输入客户名称" />
          </el-form-item>

          <el-form-item label="客户行业" prop="industry">
            <el-select v-model="manualCaseForm.industry" placeholder="请选择行业" style="width: 100%">
              <el-option
                v-for="item in industries"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="项目金额" prop="amount">
            <el-input-number v-model="manualCaseForm.amount" :min="0" :step="10" />
            <span style="margin-left: 8px">万元</span>
          </el-form-item>

          <el-form-item label="实施周期" prop="period">
            <el-date-picker
              v-model="manualCaseForm.period"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 100%"
              value-format="YYYY.MM"
            />
          </el-form-item>

          <el-form-item label="所在地区" prop="location">
            <el-input v-model="manualCaseForm.location" placeholder="如：浙江杭州" />
          </el-form-item>

          <el-form-item label="标签" prop="tags">
            <el-select v-model="manualCaseForm.tags" multiple filterable allow-create placeholder="选择或输入标签" style="width: 100%">
              <el-option
                v-for="item in tagOptions"
                :key="item"
                :label="item"
                :value="item"
              />
            </el-select>
          </el-form-item>

          <el-divider content-position="left">案例详情</el-divider>

          <el-form-item label="项目概述" prop="description">
            <el-input
              v-model="manualCaseForm.description"
              type="textarea"
              :rows="3"
              placeholder="简要描述项目背景、目标和主要内容..."
            />
          </el-form-item>

          <el-form-item label="技术亮点" prop="techHighlights">
            <el-input
              v-model="manualCaseForm.techHighlights"
              type="textarea"
              :rows="4"
              placeholder="记录本次项目的技术亮点和创新点，每行一个..."
            />
          </el-form-item>

          <el-form-item label="报价策略" prop="priceStrategy">
            <el-input
              v-model="manualCaseForm.priceStrategy"
              type="textarea"
              :rows="3"
              placeholder="记录报价策略和定价逻辑..."
            />
          </el-form-item>

          <el-form-item label="成功关键因素">
            <el-checkbox-group v-model="manualCaseForm.successFactors">
              <el-checkbox value="技术优势">技术优势</el-checkbox>
              <el-checkbox value="价格合理">价格合理</el-checkbox>
              <el-checkbox value="客户关系">客户关系</el-checkbox>
              <el-checkbox value="交付能力">交付能力</el-checkbox>
              <el-checkbox value="品牌影响力">品牌影响力</el-checkbox>
              <el-checkbox value="响应速度">响应速度</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <el-form-item label="经验教训" prop="lessons">
            <el-input
              v-model="manualCaseForm.lessons"
              type="textarea"
              :rows="3"
              placeholder="记录经验教训和改进建议..."
            />
          </el-form-item>

          <el-form-item label="附件">
            <el-upload
              v-model:file-list="manualCaseForm.attachments"
              action="#"
              :auto-upload="false"
              multiple
            >
              <el-button type="primary" plain>上传项目文档</el-button>
              <template #tip>
                <div class="upload-tip">
                  支持上传技术方案、报价单、合同等相关文档
                </div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="modelValue = false">取消</el-button>
      <el-button type="primary" @click="handleSave" :loading="saving">保存案例</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { caseCommonTags, formatAmount } from './caseMeta.js'

const modelValue = defineModel({ type: Boolean, default: false })
const activeTab = defineModel('activeTab', { type: String, default: 'fromProject' })
const caseForm = defineModel('caseForm', { type: Object, required: true })
const manualCaseForm = defineModel('manualCaseForm', { type: Object, required: true })

defineProps({
  caseFormRules: {
    type: Object,
    required: true
  },
  projectOptions: {
    type: Array,
    required: true
  },
  projectOptionsLoading: {
    type: Boolean,
    default: false
  },
  saving: {
    type: Boolean,
    default: false
  },
  industries: {
    type: Array,
    required: true
  }
})

const emit = defineEmits([
  'attachment-change',
  'attachment-remove',
  'project-change',
  'save'
])

const caseFormRef = ref(null)
const manualCaseFormRef = ref(null)

const tagOptions = computed(() => caseCommonTags)

const handleSave = async () => {
  const formRef = activeTab.value === 'fromProject' ? caseFormRef.value : manualCaseFormRef.value
  if (!formRef) return

  try {
    await formRef.validate()
    emit('save', activeTab.value)
  } catch (error) {
    if (error !== false) {
      ElMessage.warning('请先完善表单信息')
    }
  }
}

const handleDialogClosed = () => {
  activeTab.value = 'fromProject'
}
</script>

<style scoped lang="scss">
.add-case-dialog {
  :deep(.el-dialog__body) {
    max-height: 70vh;
    overflow-y: auto;
  }

  :deep(.el-divider) {
    margin: 20px 0;
  }

  :deep(.el-divider__text) {
    font-weight: 600;
    color: #409eff;
  }

  :deep(.el-select-dropdown__item) {
    height: auto;
    padding: 8px 20px;
  }

  .option-extra {
    color: var(--text-muted);
    font-size: 12px;
    margin-left: 8px;
  }

  .upload-tip {
    color: var(--text-muted);
    font-size: 12px;
    margin-top: 4px;
  }
}
</style>
