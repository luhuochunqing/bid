// Input: resourcesApi, API mode switch, and BAR capability helpers
// Output: useBarStore - Pinia store for BAR sites and certificates
// Pos: src/stores/ - State management layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { defineStore } from 'pinia'
import {  resourcesApi } from '@/api'

function computeCapability(site) {
  const accounts = Array.isArray(site.accounts) ? site.accounts : []
  const uks = Array.isArray(site.uks) ? site.uks : []
  const hasDetailedChildData = accounts.length > 0 || uks.length > 0

  const hasAccount = accounts.length > 0
  const hasAvailableUK = uks.some((uk) => uk.status === 'available')
  const hasRisk = Boolean(site.hasRisk) || uks.some((uk) => {
    if (!uk.expiryDate) return false
    const daysLeft = Math.ceil((new Date(uk.expiryDate) - new Date()) / (1000 * 60 * 60 * 24))
    return daysLeft <= 30
  })

  let status = 'available'
  if (hasDetailedChildData && (!hasAccount || (uks.length > 0 && !hasAvailableUK))) {
    status = 'unavailable'
  } else if (!hasDetailedChildData && site.status !== 'active') {
    status = 'unavailable'
  } else if (hasRisk) {
    status = 'risk'
  }

  return {
    status,
    hasAccount,
    hasAvailableUK,
    hasRisk,
    accountCount: accounts.length,
    ukCount: uks.length,
    availableUkCount: uks.filter((uk) => uk.status === 'available').length,
    primaryOwner: accounts[0]?.owner || '',
    primaryPhone: accounts[0]?.phone || '' }
}

function buildAuditLog(verifications = [], certificates = [], attachments = []) {
  const verifyLogs = verifications.map((item) => ({
    time: item.verifiedAt || '',
    user: item.verifiedBy || 'system',
    action: item.message || '执行了站点验证' }))

  const borrowLogs = certificates.flatMap((certificate) => {
    const records = Array.isArray(certificate.borrowRecords) ? certificate.borrowRecords : []
    return records.map((record) => ({
      time: record.returnedAt || record.borrowedAt || '',
      user: record.borrower || '',
      action: record.status === 'RETURNED'
        ? `归还 ${certificate.type} (${certificate.serialNo})`
        : `借用 ${certificate.type} (${certificate.serialNo})` }))
  })

  const attachmentLogs = attachments.map((item) => ({
    time: item.uploadedAt || '',
    user: item.uploadedBy || 'system',
    action: `上传附件 ${item.name}` }))

  return [...verifyLogs, ...borrowLogs, ...attachmentLogs]
    .filter((item) => item.time || item.action)
    .sort((left, right) => String(right.time).localeCompare(String(left.time)))
}

export const useBarStore = defineStore('bar', {
  state: () => ({
    sites: [],
    currentSite: null,
    loading: false }),

  getters: {
    activeSites: (state) => state.sites.filter((site) => site.status === 'active'),
    riskSites: (state) => state.sites.filter((site) => site.hasRisk),
    getSiteByUrl: (state) => (url) => state.sites.find((site) => site.url === url) },

  actions: {
    async getSites(params = {}) {
      this.loading = true
      try {
        const response = await resourcesApi.barSites.getList(params)
        if (!response?.success) {
          return response
        }

        const sites = Array.isArray(response.data) ? response.data : []
        const withCertificates = await Promise.all(sites.map(async (site) => {
          const accountsResponse = await resourcesApi.barSiteAccounts.getList(site.id)
          const certificatesResponse = await resourcesApi.certificates.getList(site.id)
          const verificationResponse = await resourcesApi.barSites.getVerificationRecords(site.id)
          const verifications = verificationResponse?.success && Array.isArray(verificationResponse.data)
            ? verificationResponse.data
            : []
          const accounts = accountsResponse?.success && Array.isArray(accountsResponse.data)
            ? accountsResponse.data
            : []
          return {
            ...site,
            accounts,
            uks: certificatesResponse?.success && Array.isArray(certificatesResponse.data)
              ? certificatesResponse.data
              : [],
            lastVerifyTime: verifications[0]?.verifiedAt || site.lastVerifyTime }
        }))
        this.sites = withCertificates
        return { success: true, data: this.sites }
      } finally {
        this.loading = false
      }
    },

    async getSiteById(id) {
      const existing = this.sites.find((site) => String(site.id) === String(id))
      if (existing) {
        this.currentSite = existing
        return existing
      }

      const response = await resourcesApi.barSites.getDetail(id)
      if (!response?.success) {
        return null
      }

      let site = response.data
      if (site) {
        const [accountsResponse, certificatesResponse, verificationResponse, sopResponse, attachmentsResponse] = await Promise.all([
          resourcesApi.barSiteAccounts.getList(id),
          resourcesApi.certificates.getList(id),
          resourcesApi.barSites.getVerificationRecords(id),
          resourcesApi.barSiteSop.get(id),
          resourcesApi.barSiteAttachments.getList(id),
        ])
        const certificates = certificatesResponse?.success && Array.isArray(certificatesResponse.data)
          ? certificatesResponse.data
          : []
        const certificatesWithRecords = await Promise.all(certificates.map(async (certificate) => {
          const borrowRecordsResponse = await resourcesApi.certificates.getBorrowRecords(id, certificate.id)
          return {
            ...certificate,
            borrowRecords: borrowRecordsResponse?.success && Array.isArray(borrowRecordsResponse.data)
              ? borrowRecordsResponse.data
              : [] }
        }))
        const verifications = verificationResponse?.success && Array.isArray(verificationResponse.data)
          ? verificationResponse.data
          : []
        const attachments = attachmentsResponse?.success && Array.isArray(attachmentsResponse.data)
          ? attachmentsResponse.data
          : []
        site = {
          ...site,
          accounts: accountsResponse?.success && Array.isArray(accountsResponse.data) ? accountsResponse.data : [],
          uks: certificatesWithRecords,
          sop: sopResponse?.success ? sopResponse.data : null,
          attachments,
          lastVerifyTime: verifications[0]?.verifiedAt || site.lastVerifyTime,
          auditLog: buildAuditLog(verifications, certificatesWithRecords, attachments) }
      }

      this.currentSite = site
      if (site) {
        const index = this.sites.findIndex((site) => String(site.id) === String(id))
        if (index === -1) {
          this.sites.push(site)
        } else {
          this.sites[index] = site
        }
      }
      return site
    },

    async checkSiteCapability(siteNameOrUrl) {
      if (!this.sites.length) {
        const response = await this.getSites()
        if (!response?.success) {
          return { found: false, siteNameOrUrl }
        }
      }

      const keyword = String(siteNameOrUrl || '').trim()
      const site = this.sites.find((item) =>
        item.name?.includes(keyword) || item.url?.includes(keyword),
      )

      if (!site) {
        return { found: false, siteNameOrUrl }
      }

      return {
        found: true,
        site,
        capability: computeCapability(site) }
    },

    async createSite(data) {
      const response = await resourcesApi.barSites.create(data)
      if (!response?.success) {
        return response
      }

      this.sites.unshift(response.data)
      return response
    },

    async updateSite(id, data) {
      const response = await resourcesApi.barSites.update(id, data)
      if (!response?.success) {
        return response
      }

      const index = this.sites.findIndex((site) => String(site.id) === String(id))
      if (index !== -1) {
        this.sites[index] = response.data
      }
      if (this.currentSite && String(this.currentSite.id) === String(id)) {
        this.currentSite = response.data
      }
      return response
    },

    async deleteSite(id) {
      const response = await resourcesApi.barSites.delete(id)
      if (!response?.success) {
        return response
      }

      const index = this.sites.findIndex((site) => String(site.id) === String(id))
      if (index !== -1) {
        this.sites.splice(index, 1)
      }
      if (this.currentSite && String(this.currentSite.id) === String(id)) {
        this.currentSite = null
      }
      return response
    },

    async addAccount(siteId, accountData) {
      const response = await resourcesApi.barSiteAccounts.create(siteId, accountData)
      if (response?.success) {
        await this.getSiteById(siteId)
      }
      return response
    },

    async updateAccount(siteId, accountId, data) {
      const response = await resourcesApi.barSiteAccounts.update(siteId, accountId, data)
      if (response?.success) {
        await this.getSiteById(siteId)
      }
      return response
    },

    async deleteAccount(siteId, accountId) {
      const response = await resourcesApi.barSiteAccounts.delete(siteId, accountId)
      if (response?.success) {
        await this.getSiteById(siteId)
      }
      return response
    },

    async addUk(siteId, ukData) {
      return resourcesApi.certificates.create(siteId, ukDataToPayload(ukData))
    },

    async updateUk(siteId, ukId, data) {
      return resourcesApi.certificates.update(siteId, ukId, ukDataToPayload(data))
    },

    async deleteUk(siteId, ukId) {
      return resourcesApi.certificates.delete(siteId, ukId)
    },

    async borrowUk(siteId, ukId, borrowData) {
      return resourcesApi.certificates.borrow(siteId, ukId, {
        borrower: borrowData.borrower,
        projectId: borrowData.projectId ? Number(borrowData.projectId) : null,
        purpose: borrowData.purpose,
        remark: borrowData.remark,
        expectedReturnDate: borrowData.returnDate })
    },

    async returnUk(siteId, ukId) {
      return resourcesApi.certificates.return(siteId, ukId, {})
    },

    async updateSiteStatus(siteId, status) {
      const response = await resourcesApi.barSites.updateStatus(siteId, status)
      if (response?.success) {
        await this.getSiteById(siteId)
      }
      return response
    },

    async verifySite(siteId, payload = {}) {
      const response = await resourcesApi.barSites.verify(siteId, payload)
      if (response?.success) {
        await this.getSiteById(siteId)
      }
      return response
    },

    async updateSop(siteId, sop) {
      const response = await resourcesApi.barSiteSop.update(siteId, sop)
      if (response?.success) {
        await this.getSiteById(siteId)
      }
      return response
    },

    async addAttachment(siteId, attachment) {
      const response = await resourcesApi.barSiteAttachments.create(siteId, attachment)
      if (response?.success) {
        await this.getSiteById(siteId)
      }
      return response
    },

    async deleteAttachment(siteId, attachmentId) {
      const response = await resourcesApi.barSiteAttachments.delete(siteId, attachmentId)
      if (response?.success) {
        await this.getSiteById(siteId)
      }
      return response
    } } })

function ukDataToPayload(data) {
  return {
    type: data.type,
    provider: data.provider,
    serialNo: data.serialNo,
    holder: data.holder,
    location: data.location,
    expiryDate: data.expiryDate,
    remark: data.remark }
}
