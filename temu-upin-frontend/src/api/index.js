import axios from 'axios'
import { clearPlatformAuth, getPlatformAuthToken } from '@/utils/platformAuth'

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

apiClient.interceptors.request.use((config) => {
  const token = getPlatformAuthToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response?.status === 401) {
      clearPlatformAuth()
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.replace('/login')
      }
    }
    const message = error.response?.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

export const productApi = {
  getAll() {
    return apiClient.get('/products')
  },
  
  getById(id) {
    return apiClient.get(`/products/${id}`)
  },
  
  create(data) {
    return apiClient.post('/products', data)
  },
  
  update(id, data) {
    return apiClient.put(`/products/${id}`, data)
  },
  
  delete(id) {
    return apiClient.delete(`/products/${id}`)
  },
  
  addSku(productId, data) {
    return apiClient.post(`/products/${productId}/skus`, data)
  },
  
  updateSku(productId, skuId, data) {
    return apiClient.put(`/products/${productId}/skus/${skuId}`, data)
  },
  
  deleteSku(productId, skuId) {
    return apiClient.delete(`/products/${productId}/skus/${skuId}`)
  },
  
  getSkus(productId) {
    return apiClient.get(`/products/${productId}/skus`)
  },
  
  generateImages(productId, data) {
    return apiClient.post(`/products/${productId}/generate`, data)
  },
  
  getImages(productId) {
    return apiClient.get(`/products/${productId}/images`)
  },
  
  getImagesByType(productId, type) {
    return apiClient.get(`/products/${productId}/images/${type}`)
  },
  
  deleteImages(productId) {
    return apiClient.delete(`/products/${productId}/images`)
  },

  deleteImage(productId, imageId) {
    return apiClient.delete(`/products/${productId}/images/${imageId}`)
  },

  deleteFailedImages(productId, type = null) {
    const q = type ? `?type=${encodeURIComponent(type)}` : ''
    return apiClient.delete(`/products/${productId}/images/failed${q}`)
  }
}

export const uploadApi = {
  uploadImage(file) {
    const formData = new FormData()
    formData.append('file', file)
    return apiClient.post('/upload/image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },
  
  uploadImages(files) {
    const formData = new FormData()
    files.forEach(file => {
      formData.append('files', file)
    })
    return apiClient.post('/upload/images', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  }
}

export const channelApi = {
  getAll() {
    return apiClient.get('/channels')
  },
  
  getEnabled() {
    return apiClient.get('/channels/enabled')
  },
  
  getById(id) {
    return apiClient.get(`/channels/${id}`)
  },
  
  create(data) {
    return apiClient.post('/channels', data)
  },
  
  update(id, data) {
    return apiClient.put(`/channels/${id}`, data)
  },
  
  delete(id) {
    return apiClient.delete(`/channels/${id}`)
  },

  test(id, data = {}) {
    return apiClient.post(`/channels/${id}/test`, data)
  }
}

export default apiClient
