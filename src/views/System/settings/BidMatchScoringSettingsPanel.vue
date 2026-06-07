<template>
  <div class="bid-match-scoring-panel">
    <div class="scoring-toolbar">
      <div>
        <p class="panel-kicker">Bid Match Scoring</p>
        <h2>投标匹配评分</h2>
      </div>
      <div class="toolbar-actions">
        <el-button :loading="activating" @click="activateCurrentModel">
          设为当前
        </el-button>
        <el-button :loading="saving" type="primary" @click="save">
          <el-icon><Check /></el-icon>
          保存配置
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="!weightValidation.valid"
      :title="weightValidation.message"
      type="warning"
      show-icon
      :closable="false"
      class="scoring-alert"
    />

    <el-alert
      v-if="currentModel.validationErrors?.length"
      :title="currentModel.validationErrors.join('；')"
      type="warning"
      show-icon
      :closable="false"
      class="scoring-alert"
    />

    <div v-loading="loading" class="scoring-content">
      <el-form label-position="top" class="model-form">
        <el-form-item label="模型名称">
          <el-input v-model="currentModel.name" placeholder="请输入模型名称" />
        </el-form-item>
        <el-form-item label="模型说明">
          <el-input
            v-model="currentModel.description"
            type="textarea"
            :rows="2"
            placeholder="说明评分模型适用范围"
          />
        </el-form-item>
      </el-form>

      <div class="dimension-header">
        <div>
          <h3>评分维度</h3>
          <p>{{ enabledDimensionCount }} 个已启用，权重 {{ weightValidation.total }}%。</p>
        </div>
        <el-button @click="addDimension">
          <el-icon><Plus /></el-icon>
          新增维度
        </el-button>
      </div>

      <el-table :data="currentModel.dimensions" row-key="key" class="dimension-table">
        <el-table-column type="expand" width="48">
          <template #default="{ row }">
            <BidMatchRulesEditor
              :dimension="row"
              :evidence-key-options="evidenceKeyOptions"
              :rule-type-options="ruleTypeOptions"
              @add-rule="addRule"
              @remove-rule="removeRule"
            />
          </template>
        </el-table-column>
        <el-table-column label="启用" width="82">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" />
          </template>
        </el-table-column>
        <el-table-column label="维度名称" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.name" placeholder="维度名称" />
          </template>
        </el-table-column>
        <el-table-column label="权重" width="130">
          <template #default="{ row }">
            <el-input-number
              v-model="row.weight"
              :min="0"
              :max="100"
              :step="5"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column label="规则数" width="96">
          <template #default="{ row }">
            <el-tag effect="plain" size="small">{{ row.rules.length }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeDimension(row.key)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { Check, Plus } from '@element-plus/icons-vue'
import BidMatchRulesEditor from './BidMatchRulesEditor.vue'

defineModel('currentModel', { type: Object, required: true })
defineProps({
  loading: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  activating: { type: Boolean, default: false },
  weightValidation: { type: Object, required: true },
  enabledDimensionCount: { type: Number, default: 0 },
  save: { type: Function, required: true },
  activateCurrentModel: { type: Function, required: true },
  addDimension: { type: Function, required: true },
  removeDimension: { type: Function, required: true },
  addRule: { type: Function, required: true },
  removeRule: { type: Function, required: true },
  evidenceKeyOptions: { type: Array, required: true },
  ruleTypeOptions: { type: Array, required: true },
})
</script>

<style scoped>
.bid-match-scoring-panel,
.scoring-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.scoring-toolbar,
.dimension-header,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.scoring-toolbar,
.dimension-header {
  justify-content: space-between;
}

.scoring-toolbar {
  padding: 6px 2px 10px;
  border-bottom: 1px solid rgba(67, 89, 55, 0.1);
}

.panel-kicker {
  margin: 0 0 6px;
  color: #6d7d5d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.scoring-toolbar h2,
.dimension-header h3 {
  margin: 0;
  color: #1f2d1d;
}

.dimension-header p {
  margin: 6px 0 0;
  color: #66705f;
}

.model-form {
  display: grid;
  grid-template-columns: minmax(220px, 340px) minmax(320px, 1fr);
  gap: 16px;
}

.scoring-alert {
  border-radius: 8px;
}

.dimension-table {
  width: 100%;
}

.dimension-table :deep(.el-table__expand-icon) {
  color: #1f2d1d;
  font-weight: 700;
}

.dimension-table :deep(.el-table__expand-icon .el-icon) {
  font-size: 16px;
}

.dimension-table :deep(.el-table__expand-column .cell) {
  display: flex;
  justify-content: center;
}

@media (max-width: 960px) {
  .scoring-toolbar,
  .dimension-header,
  .toolbar-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .model-form {
    grid-template-columns: 1fr;
  }
}
</style>
