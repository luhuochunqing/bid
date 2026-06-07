// Input: qualifications API module and feature-availability helpers
// Output: useQualificationStore - Pinia store for qualification list and borrow flow state
// Pos: src/stores/ - State management layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { defineStore } from 'pinia'
import { getFeaturePlaceholder, isFeatureUnavailableResponse, qualificationsApi } from '@/api'

const qualificationFallbackPlaceholder = {
  title: '资质库暂未接入',
  message: '当前环境未返回真实资质列表，请确认后端服务和权限配置。',
  hint: '数据来源为后端接口，请联系管理员确认权限或检查服务状态。'
}

export const useQualificationStore = defineStore('qualification', {
  state: () => ({
    qualifications: [],
    borrowRecords: [],
    listLoading: false,
    borrowLoading: false,
    listFeaturePlaceholder: null,
    borrowFeaturePlaceholder: null
  }),

  actions: {
    async loadQualifications(filters = {}) {
      this.listLoading = true
      try {
        const result = await qualificationsApi.getList(filters)

        if (result?.success) {
          this.qualifications = Array.isArray(result.data) ? result.data : []
          this.listFeaturePlaceholder = null
          return result
        }

        this.qualifications = []
        this.listFeaturePlaceholder = getFeaturePlaceholder(result) || qualificationFallbackPlaceholder
        return result
      } finally {
        this.listLoading = false
      }
    },

    async createQualification(payload) {
      const result = await qualificationsApi.create(payload)
      if (result?.success && result?.data) {
        this.qualifications.unshift(result.data)
      }
      return result
    },

    async deleteQualification(id) {
      const result = await qualificationsApi.delete(id)
      if (result?.success !== false) {
        this.qualifications = this.qualifications.filter(item => String(item.id) !== String(id))
      }
      return result
    },

    async loadBorrowRecords(qualificationId = null) {
      this.borrowLoading = true
      try {
        const result = await qualificationsApi.getBorrowRecords(qualificationId)

        if (result?.success) {
          this.borrowRecords = Array.isArray(result.data) ? result.data : []
          this.borrowFeaturePlaceholder = null
          return result
        }

        this.borrowRecords = []
        this.borrowFeaturePlaceholder = isFeatureUnavailableResponse(result)
          ? getFeaturePlaceholder(result)
          : null
        return result
      } finally {
        this.borrowLoading = false
      }
    },

    async submitBorrow(qualificationId, payload) {
      const result = await qualificationsApi.createBorrow(qualificationId, payload)

      if (result?.success) {
        await this.loadBorrowRecords()
        return result
      }

      this.borrowFeaturePlaceholder = isFeatureUnavailableResponse(result)
        ? getFeaturePlaceholder(result)
        : null
      return result
    },

    async returnBorrow(recordId) {
      const result = await qualificationsApi.returnBorrow(recordId)

      if (result?.success) {
        await this.loadBorrowRecords()
        return result
      }

      this.borrowFeaturePlaceholder = isFeatureUnavailableResponse(result)
        ? getFeaturePlaceholder(result)
        : null
      return result
    }
  }
})
