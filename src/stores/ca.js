// Input: caApi module
// Output: useCaStore - Pinia store for CA certificate management
// Pos: src/stores/ - State management layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { defineStore } from 'pinia'
import { caApi } from '@/api/modules/ca.js'

export const useCaStore = defineStore('ca', {
  state: () => ({
    certificates: [],
    overview: { total: 0, expiring: 0, expired: 0, borrowed: 0 },
    listLoading: false,
    overviewLoading: false,
    error: null
  }),

  getters: {
    // 在库且有效的实体CA（可用于借用）
    eligibleForBorrow: (state) => state.certificates.filter(
      c => c.caType === 'ENTITY_CA' && c.borrowStatus === 'IN_STOCK' && c.status !== 'EXPIRED' && c.status !== 'INACTIVE'
    ),
    // 即将到期（30天内）
    expiringList: (state) => state.certificates.filter(c => c.status === 'EXPIRING'),
    // 已过期
    expiredList: (state) => state.certificates.filter(c => c.status === 'EXPIRED'),
    // 已借出
    borrowedList: (state) => state.certificates.filter(c => c.borrowStatus === 'BORROWED')
  },

  actions: {
    async loadCertificates(filters = {}) {
      this.listLoading = true
      this.error = null
      try {
        const result = await caApi.getList(filters)
        if (result?.success !== false) {
          this.certificates = Array.isArray(result?.data) ? result.data : []
          return result
        }
        this.certificates = []
        return result
      } catch (e) {
        this.error = e.message || '加载CA证书列表失败'
        this.certificates = []
        throw e
      } finally {
        this.listLoading = false
      }
    },

    async loadOverview() {
      this.overviewLoading = true
      try {
        const result = await caApi.getOverview()
        if (result?.success !== false && result?.data) {
          this.overview = result.data
        }
        return result
      } catch {
        this.overview = { total: 0, expiring: 0, expired: 0, borrowed: 0 }
      } finally {
        this.overviewLoading = false
      }
    },

    async createCertificate(payload) {
      const result = await caApi.create(payload)
      if (result?.success !== false && result?.data) {
        this.certificates.unshift(result.data)
        await this.loadOverview()
      }
      return result
    },

    async updateCertificate(id, payload) {
      const result = await caApi.update(id, payload)
      if (result?.success !== false && result?.data) {
        const idx = this.certificates.findIndex(c => c.id === id)
        if (idx !== -1) this.certificates[idx] = result.data
        await this.loadOverview()
      }
      return result
    },

    async deactivateCertificate(id) {
      const result = await caApi.deactivate(id)
      if (result?.success !== false) {
        this.certificates = this.certificates.filter(c => c.id !== id)
        await this.loadOverview()
      }
      return result
    },

    async borrowCertificate(caId, payload) {
      const result = await caApi.borrow(caId, payload)
      if (result?.success !== false) {
        await this.loadCertificates()
        await this.loadOverview()
      }
      return result
    },

    async approveApplication(applicationId, data) {
      const result = await caApi.approve(applicationId, data)
      return result
    },

    async rejectApplication(applicationId, data) {
      const result = await caApi.reject(applicationId, data)
      return result
    },

    async returnCertificate(applicationId, data) {
      const result = await caApi.returnCa(applicationId, data)
      if (result?.success !== false) {
        await this.loadCertificates()
        await this.loadOverview()
      }
      return result
    },

    async cancelBorrow(applicationId) {
      const result = await caApi.cancelBorrow(applicationId)
      return result
    }
  }
})
