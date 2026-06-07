<template>
  <div class="case-list" v-loading="loading">
    <FeaturePlaceholder
      v-if="featurePlaceholder"
      :title="featurePlaceholder.title"
      :message="featurePlaceholder.message"
      :hint="featurePlaceholder.hint"
    />

    <div
      v-else
      v-for="item in cases"
      :key="item.id"
      class="case-card"
      @click="$emit('view', item)"
    >
      <div class="case-card-header">
        <h3 class="case-title">{{ item.title }}</h3>
        <el-tag :type="getYearTagType(item.year)" size="small">
          {{ item.year || '-' }}年
        </el-tag>
      </div>

      <div class="case-card-body">
        <div class="case-info-row">
          <span class="info-label">
            <el-icon><OfficeBuilding /></el-icon>
            客户
          </span>
          <span class="info-value">{{ item.customer || '-' }}</span>
        </div>

        <div class="case-info-row">
          <span class="info-label">
            <el-icon><Coin /></el-icon>
            金额
          </span>
          <span class="info-value amount">{{ formatAmount(item.amount) }}</span>
        </div>

        <div class="case-info-row">
          <span class="info-label">
            <el-icon><Location /></el-icon>
            地区
          </span>
          <span class="info-value">{{ item.location || '-' }}</span>
        </div>

        <div class="case-info-row">
          <span class="info-label">
            <el-icon><Calendar /></el-icon>
            时间
          </span>
          <span class="info-value">{{ item.period || '-' }}</span>
        </div>
      </div>

      <div class="case-tags">
        <el-tag
          v-for="tag in item.tags"
          :key="tag"
          size="small"
          :type="getTagType(tag)"
          effect="plain"
        >
          {{ tag }}
        </el-tag>
      </div>

      <div class="case-highlights">
        <div class="highlights-title">
          <el-icon><Star /></el-icon>
          项目亮点
        </div>
        <ul class="highlights-list">
          <li v-for="(highlight, index) in item.highlights.slice(0, 3)" :key="index">
            {{ highlight }}
          </li>
        </ul>
      </div>

      <div class="case-card-footer">
        <span class="view-count">
          <el-icon><View /></el-icon>
          {{ item.viewCount }}
        </span>
        <span class="use-count">
          <el-icon><DocumentCopy /></el-icon>
          引用 {{ item.useCount }} 次
        </span>
      </div>
    </div>

    <el-empty
      v-if="!featurePlaceholder && cases.length === 0"
      description="暂无案例数据"
      :image-size="120"
    />
  </div>
</template>

<script setup>
import { Calendar, Coin, DocumentCopy, Location, OfficeBuilding, Star, View } from '@element-plus/icons-vue'
import FeaturePlaceholder from '@/components/common/FeaturePlaceholder.vue'
import { formatAmount, getTagType, getYearTagType } from './caseMeta.js'

defineProps({
  cases: {
    type: Array,
    required: true
  },
  loading: {
    type: Boolean,
    default: false
  },
  featurePlaceholder: {
    type: Object,
    default: null
  }
})

defineEmits(['view'])
</script>

<style scoped lang="scss">
.case-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 20px;
  min-height: 400px;

  .case-card {
    background: var(--bg-card);
    border: 1px solid var(--gray-250);
    border-radius: 8px;
    padding: 20px;
    cursor: pointer;
    transition: all 0.3s;

    &:hover {
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
    }

    .case-card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 16px;

      .case-title {
        font-size: 16px;
        font-weight: 600;
        color: var(--gray-750);
        margin: 0;
        flex: 1;
        line-height: 1.5;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }
    }

    .case-card-body {
      margin-bottom: 16px;

      .case-info-row {
        display: flex;
        align-items: center;
        margin-bottom: 10px;
        font-size: 14px;

        &:last-child {
          margin-bottom: 0;
        }

        .info-label {
          display: flex;
          align-items: center;
          gap: 4px;
          color: var(--text-muted);
          width: 70px;
          flex-shrink: 0;
        }

        .info-value {
          color: var(--text-secondary-ui);
          flex: 1;

          &.amount {
            color: #f56c6c;
            font-weight: 600;
          }
        }
      }
    }

    .case-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin-bottom: 16px;
    }

    .case-highlights {
      background: var(--bg-subtle);
      border-radius: 6px;
      padding: 12px;
      margin-bottom: 16px;

      .highlights-title {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 14px;
        font-weight: 600;
        color: #409eff;
        margin-bottom: 8px;
      }

      .highlights-list {
        margin: 0;
        padding-left: 16px;
        font-size: 13px;
        color: var(--text-secondary-ui);
        line-height: 1.8;

        li {
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }
      }
    }

    .case-card-footer {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 16px;
      padding-top: 12px;
      border-top: 1px solid var(--gray-250);
      font-size: 13px;
      color: var(--text-muted);

      span {
        display: flex;
        align-items: center;
        gap: 4px;
      }
    }
  }
}
</style>
