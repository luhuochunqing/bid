<template>
  <el-dialog
    v-model="dialogVisible"
    title="复用案例到当前标书"
    width="540px"
    destroy-on-close
    append-to-body
    class="ai-case-reuse-dialog"
  >
    <div v-if="caseData" class="reuse-form-container">
      <div class="case-brief">
        <div class="brief-title">待复用案例：</div>
        <div class="brief-content">{{ caseData.scoringPointTitle }}</div>
      </div>

      <el-form label-position="top" class="reuse-form">
        <el-form-item label="目标标书章节" required>
          <el-cascader
            v-if="treeOptions && treeOptions.length"
            v-model="selectedSection"
            :options="treeOptions"
            :props="{ checkStrictly: true, value: 'label', label: 'label' }"
            placeholder="请选择要写入的目标章节"
            class="full-width"
          />
          <el-input
            v-else
            v-model="customChapterInput"
            placeholder="请输入目标章节名称（如：第3章 技术方案/3.2 架构设计）"
          />
        </el-form-item>

        <el-form-item label="写入模式" required>
          <el-radio-group v-model="reuseMode" class="mode-radio-group">
            <el-radio-button label="APPEND">
              ➕ 追加到章节末尾（推荐）
            </el-radio-button>
            <el-radio-button label="OVERWRITE">
              🔄 覆盖当前章节内容
            </el-radio-button>
          </el-radio-group>
          <div class="mode-tip">
            {{ reuseMode === 'APPEND' ? '将在目标章节现有正文下方追加该应答案例，保留已有内容。' : '将使用当前案例应答原文完全替换目标章节现有内容，请谨慎操作。' }}
          </div>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          class="confirm-reuse-btn"
          @click="handleConfirmReuse"
        >
          确认写入标书
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import { caseRecommendationApi } from '@/api/modules/caseRecommendation'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  caseData: {
    type: Object,
    default: () => null
  },
  projectId: {
    type: [Number, String],
    default: null
  },
  scoreItemId: {
    type: [Number, String],
    default: null
  },
  defaultChapter: {
    type: String,
    default: ''
  },
  sectionTree: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'reuse-success'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const selectedSection = ref([])
const customChapterInput = ref('')
const reuseMode = ref('APPEND')
const submitting = ref(false)

const treeOptions = computed(() => {
  if (!props.sectionTree || !props.sectionTree.length) {
    return [
      {
        value: '第1章 商务标响应',
        label: '第1章 商务标响应',
        children: [
          { value: '1.1 投标承诺书', label: '1.1 投标承诺书' },
          { value: '1.2 资格证明材料', label: '1.2 资格证明材料' }
        ]
      },
      {
        value: '第2章 技术服务方案',
        label: '第2章 技术服务方案',
        children: [
          { value: '2.1 系统总体架构设计', label: '2.1 系统总体架构设计' },
          { value: '2.2 高可用与容灾保障', label: '2.2 高可用与容灾保障' },
          { value: '2.3 应急响应与运维方案', label: '2.3 应急响应与运维方案' }
        ]
      }
    ]
  }
  return props.sectionTree
})

watch(
  () => props.defaultChapter,
  (val) => {
    if (val) {
      customChapterInput.value = val
    }
  },
  { immediate: true }
)

const handleConfirmReuse = async () => {
  if (!props.caseData || !props.caseData.id) {
    ElMessage.warning('缺少有效案例数据')
    return
  }

  let targetChapter = customChapterInput.value
  if (Array.isArray(selectedSection.value) && selectedSection.value.length > 0) {
    targetChapter = selectedSection.value.join(' / ')
  }

  if (!targetChapter) {
    targetChapter = '第2章 技术服务方案'
  }

  submitting.value = true
  try {
    const payload = {
      projectId: props.projectId,
      scoreItemId: props.scoreItemId,
      targetChapter: targetChapter,
      reuseMode: reuseMode.value,
      referenceTarget: targetChapter,
      referenceContext: `从案例库复用【${props.caseData.scoringPointTitle}】到【${targetChapter}】`
    }

    const res = await caseRecommendationApi.reuseCase(props.caseData.id, payload)
    ElMessage.success('案例已成功复用并写入当前标书章节！')
    emit('reuse-success', {
      caseData: props.caseData,
      targetChapter,
      reuseMode: reuseMode.value,
      result: res
    })
    dialogVisible.value = false
  } catch (err) {
    notifyErrorUnlessRateLimit(err, '复用案例失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.reuse-form-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.case-brief {
  background: var(--gray-50);
  border: 1px solid var(--gray-200);
  border-radius: 6px;
  padding: 10px 14px;
  font-size: 13px;
}

.brief-title {
  color: var(--text-muted);
  margin-bottom: 2px;
}

.brief-content {
  font-weight: 600;
  color: var(--text-primary);
}

.full-width {
  width: 100%;
}

.mode-radio-group {
  width: 100%;
  display: flex;
}

.mode-radio-group :deep(.el-radio-button) {
  flex: 1;
}

.mode-radio-group :deep(.el-radio-button__inner) {
  width: 100%;
}

.mode-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
}

.confirm-reuse-btn {
  background-color: var(--brand-xiyu-logo);
  border-color: var(--brand-xiyu-logo);
}

.confirm-reuse-btn:hover {
  background-color: var(--brand-xiyu-logo-hover);
  border-color: var(--brand-xiyu-logo-hover);
}
</style>
