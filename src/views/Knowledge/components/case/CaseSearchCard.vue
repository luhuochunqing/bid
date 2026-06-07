<template>
  <el-card class="search-card">
    <el-form :inline="true" :model="searchForm">
      <el-form-item label="关键词">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索案例名称、客户、亮点"
          clearable
          :prefix-icon="Search"
          style="width: 300px"
        />
      </el-form-item>
      <el-form-item label="行业">
        <el-select v-model="searchForm.industry" placeholder="全部行业" clearable>
          <el-option
            v-for="item in industries"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="年份">
        <el-select v-model="searchForm.year" placeholder="全部年份" clearable>
          <el-option
            v-for="item in years"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="金额范围">
        <el-select v-model="searchForm.amount" placeholder="全部金额" clearable>
          <el-option
            v-for="item in amountRanges"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="$emit('search')">
          搜索
        </el-button>
        <el-button @click="$emit('reset')">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="tags-filter">
      <span class="tags-label">常用标签：</span>
      <el-tag
        v-for="tag in commonTags"
        :key="tag"
        :type="selectedTags.includes(tag) ? '' : 'info'"
        :effect="selectedTags.includes(tag) ? 'dark' : 'plain'"
        class="tag-item"
        @click="$emit('toggle-tag', tag)"
      >
        {{ tag }}
      </el-tag>
    </div>
  </el-card>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'

defineModel('searchForm', { type: Object, required: true })
defineProps({
  industries: {
    type: Array,
    required: true
  },
  years: {
    type: Array,
    required: true
  },
  amountRanges: {
    type: Array,
    required: true
  },
  commonTags: {
    type: Array,
    required: true
  },
  selectedTags: {
    type: Array,
    required: true
  }
})

defineEmits(['reset', 'search', 'toggle-tag'])
</script>

<style scoped lang="scss">
.search-card {
  margin-bottom: 20px;

  .tags-filter {
    margin-top: 16px;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;

    .tags-label {
      font-size: 14px;
      color: var(--text-secondary-ui);
      margin-right: 4px;
    }

    .tag-item {
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        opacity: 0.8;
      }
    }
  }
}
</style>
