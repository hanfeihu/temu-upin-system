import apiClient from '@/api'

export const productCollectionApi = {
  list(params = {}) {
    return apiClient.get('/platform/product-collections', { params })
  },

  listTemuCategories(params = {}) {
    return apiClient.get('/platform/product-collections/temu-categories', { params })
  },

  get(id) {
    return apiClient.get(`/platform/product-collections/${id}`)
  },

  update(id, data) {
    return apiClient.put(`/platform/product-collections/${id}`, data)
  },

  split(id, data) {
    return apiClient.post(`/platform/product-collections/${id}/split`, data, { timeout: 240000 })
  },

  delete(id) {
    return apiClient.delete(`/platform/product-collections/${id}`)
  },

  matchTemuCategory(id) {
    return apiClient.post(`/platform/product-collections/${id}/temu-category/match`)
  },

  generateTemuTitleOptimization(id) {
    return apiClient.post(`/platform/product-collections/${id}/temu-title-optimizer/generate`, null, { timeout: 240000 })
  },

  saveTemuCategory(id, data) {
    return apiClient.post(`/platform/product-collections/${id}/temu-category/save`, data)
  },

  getTemuCategoryAttributes(id) {
    return apiClient.post(`/platform/product-collections/${id}/temu-category/attributes`)
  },

  saveTemuAttributes(id, temuAttributes) {
    return apiClient.post(`/platform/product-collections/${id}/temu-attributes/save`, { temuAttributes })
  },

  aiFillTemuAttributes(id) {
    // AI may take longer; override axios timeout.
    return apiClient.post(`/platform/product-collections/${id}/temu-attributes/ai-fill`, null, { timeout: 240000 })
  },

  translateImage(id, payload) {
    // translation can take a while; keep a long timeout.
    return apiClient.post(`/platform/product-collections/${id}/image/translate`, payload, { timeout: 240000 })
  },

  translateAllImages(id, payload = {}) {
    return apiClient.post(`/platform/product-collections/${id}/images/translate-all`, payload, { timeout: 600000 })
  },

  listTemuSkus(id) {
    return apiClient.get(`/platform/product-collections/${id}/temu/skus`)
  },

  initTemuSkus(id, force = false) {
    return apiClient.post(`/platform/product-collections/${id}/temu/skus/init`, { force }, { timeout: 240000 })
  },

  saveTemuSkus(id, skus) {
    return apiClient.post(`/platform/product-collections/${id}/temu/skus/save`, { skus }, { timeout: 240000 })
  },

  uploadTemuSkuImage(id, imageUrl) {
    return apiClient.post(`/platform/product-collections/${id}/temu/skus/image/upload`, { imageUrl }, { timeout: 240000 })
  },

  publishToTemu(id) {
    return apiClient.post(`/platform/product-collections/${id}/temu/publish`, null, { timeout: 600000 })
  },

  replaceImagesToKwcdn(id) {
    return apiClient.post(`/platform/product-collections/${id}/temu/images/kwcdn-replace`, null, { timeout: 600000 })
  },

  normalizeAllImagesTo800(id) {
    return apiClient.post(`/platform/product-collections/${id}/temu/images/normalize-800`, null, { timeout: 600000 })
  },

  requeuePostImportTask(id, force = false) {
    return apiClient.post(`/platform/product-collections/${id}/post-import/requeue?force=${force ? 'true' : 'false'}`)
  },

  fuseImages(id, payload) {
    return apiClient.post(`/platform/product-collections/${id}/images/fuse`, payload, { timeout: 600000 })
  },

  listTemuAttrRules(params = {}) {
    return apiClient.get('/platform/temu-attr-rules', { params })
  },

  createTemuAttrRule(payload) {
    return apiClient.post('/platform/temu-attr-rules', payload)
  },

  updateTemuAttrRule(id, payload) {
    return apiClient.put(`/platform/temu-attr-rules/${id}`, payload)
  },

  deleteTemuAttrRule(id) {
    return apiClient.delete(`/platform/temu-attr-rules/${id}`)
  }
}
