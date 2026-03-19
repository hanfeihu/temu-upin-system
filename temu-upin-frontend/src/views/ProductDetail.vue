<template>
  <div class="detail-page">
    <van-nav-bar
      :title="product?.name || '产品详情'"
      left-arrow
      @click-left="goBack"
      fixed
    />
    
    <div class="content" v-if="product">
      <van-tabs v-model:active="activeTab">
        <van-tab title="基本信息">
          <van-cell-group inset>
            <van-cell title="产品名称" :value="product.name" />
            <van-cell title="类目" :value="product.category || '-'" />
            <van-cell title="品牌" :value="product.brand || '-'" />
            <van-cell title="材质" :value="product.material || '-'" />
            <van-cell title="描述" :value="product.description || '-'" />
            <van-cell title="状态">
              <van-tag :type="getStatusType(product.status)">
                {{ getStatusText(product.status) }}
              </van-tag>
            </van-cell>
          </van-cell-group>
          
          <div class="section-title">SKU列表</div>
          <van-cell-group inset>
            <van-cell
              v-for="sku in skus"
              :key="sku.id"
              is-link
              @click="editSku(sku)"
            >
              <template #title>
                <div class="sku-cell">
                  <van-image
                    :src="sku.originalImageUrl"
                    width="50"
                    height="50"
                    fit="cover"
                    radius="4"
                  />
                  <div class="sku-info">
                    <div>{{ sku.skuCode }}</div>
                    <div class="sku-meta">
                      {{ sku.color || '' }}
                    </div>
                  </div>
                </div>
              </template>
            </van-cell>
            <van-cell title="添加SKU" is-link @click="showAddSku = true" />
          </van-cell-group>
        </van-tab>
        
        <van-tab title="图片生成">
          <div class="generate-section">
            <van-notice-bar v-if="product.status === 'PROCESSING'" text="图片生成中，请稍候..." />
            
            <van-cell-group inset title="生成配置">
              <van-cell title="渠道" is-link @click="showChannelPicker = true">
                <template #value>
                  <span>{{ selectedChannelLabel }}</span>
                </template>
              </van-cell>
              <van-cell v-if="selectedChannel && ['jimeng_i2i', 'jimeng_t2i'].includes(selectedChannel.platform)" title="提示词">
                <van-field v-model="generationConfig.prompt" placeholder="输入图片描述指令" />
              </van-cell>
            </van-cell-group>

            <van-popup v-model:show="showChannelPicker" position="bottom" style="height: 70%">
              <div class="popup-content">
                <div class="popup-header">
                  <span>选择渠道</span>
                  <van-icon name="close" @click="showChannelPicker = false" />
                </div>
                <van-loading v-if="channelsLoading" size="24px" class="loading-center" />
                <van-empty v-else-if="channels.length === 0" description="暂无可用渠道" />
                <van-cell-group v-else inset>
                  <van-cell
                    v-for="ch in channels"
                    :key="ch.id"
                    is-link
                    @click="selectChannel(ch)"
                  >
                    <template #title>
                      <div class="channel-cell">
                        <van-tag :type="ch.enabled ? 'success' : 'default'" size="small">
                          {{ ch.enabled ? '启用' : '禁用' }}
                        </van-tag>
                        <span class="channel-name">{{ ch.name }}</span>
                      </div>
                    </template>
                    <template #value>
                      <div class="channel-meta">
                        <div>{{ ch.platform }}</div>
                        <div class="model-name">{{ ch.model }}</div>
                      </div>
                    </template>
                  </van-cell>
                </van-cell-group>
              </div>
            </van-popup>
            
              <van-cell-group inset title="缩略图设置">
                <van-cell title="宽度">
                <van-stepper v-model="generationConfig.thumbnail.width" :min="100" :max="2000" :step="100" />
                </van-cell>
                <van-cell title="高度">
                <van-stepper v-model="generationConfig.thumbnail.height" :min="100" :max="2000" :step="100" />
                </van-cell>
                <van-cell title="数量">
                <van-stepper v-model="generationConfig.thumbnail.count" :min="1" :max="10" />
                </van-cell>
              </van-cell-group>
            
              <van-cell-group inset title="轮播图设置">
                <van-cell title="宽度">
                <van-stepper v-model="generationConfig.carousel.width" :min="100" :max="2000" :step="100" />
                </van-cell>
                <van-cell title="高度">
                <van-stepper v-model="generationConfig.carousel.height" :min="100" :max="2000" :step="100" />
                </van-cell>
                <van-cell title="数量">
                <van-stepper v-model="generationConfig.carousel.count" :min="1" :max="10" />
                </van-cell>
              </van-cell-group>
            
              <van-cell-group inset title="详情图设置">
                <van-cell title="宽度">
                <van-stepper v-model="generationConfig.detail.width" :min="100" :max="2000" :step="100" />
                </van-cell>
                <van-cell title="高度">
                <van-stepper v-model="generationConfig.detail.height" :min="100" :max="2000" :step="100" />
                </van-cell>
                <van-cell title="数量">
                <van-stepper v-model="generationConfig.detail.count" :min="1" :max="10" />
                </van-cell>
              </van-cell-group>
            
            <div class="generate-btn">
              <van-button
                type="primary"
                size="large"
                round
                :loading="generating"
                :disabled="skus.length === 0 || product.status === 'PROCESSING'"
                @click="generateImages"
              >
                一键生成全部图片
              </van-button>
            </div>
          </div>
        </van-tab>
        
        <van-tab title="生成结果">
          <div class="result-actions">
            <van-button size="small" type="danger" plain @click="deleteFailed('THUMBNAIL')">删除失败缩略图</van-button>
            <van-button size="small" type="danger" plain @click="deleteFailed('CAROUSEL')">删除失败轮播图</van-button>
            <van-button size="small" type="danger" plain @click="deleteFailed('DETAIL')">删除失败详情图</van-button>
          </div>
          <van-tabs v-model:active="imageTab" title-active-color="#1989fa">
            <van-tab title="缩略图" name="thumbnail">
              <image-grid :images="thumbnailImages" show-delete @delete="deleteOne" />
            </van-tab>
            <van-tab title="轮播图" name="carousel">
              <image-grid :images="carouselImages" show-delete @delete="deleteOne" />
            </van-tab>
            <van-tab title="详情图" name="detail">
              <image-grid :images="detailImages" show-delete @delete="deleteOne" />
            </van-tab>
          </van-tabs>
        </van-tab>
      </van-tabs>
    </div>
    
    <van-loading v-else size="24px" class="loading-center" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showFailToast, showDialog } from 'vant'
import { productApi, uploadApi, channelApi } from '@/api'

const ImageGrid = defineAsyncComponent(() => import('@/components/ImageGrid.vue'))

const route = useRoute()
const router = useRouter()
const productId = route.params.id

const activeTab = ref(0)
const imageTab = ref('thumbnail')
const generating = ref(false)
const showAddSku = ref(false)
const product = ref(null)
const skus = ref([])
const images = ref([])

const channels = ref([])
const channelsLoading = ref(false)
const showChannelPicker = ref(false)
const selectedChannel = ref(null)

const generationConfig = reactive({
  channelId: null,
  prompt: '',
  thumbnail: {
    width: 800,
    height: 800,
    count: 1
  },
  carousel: {
    width: 1200,
    height: 1200,
    count: 5
  },
  detail: {
    width: 1200,
    height: 1600,
    count: 3
  }
})

const selectedChannelLabel = computed(() => {
  if (!selectedChannel.value) {
    return '请选择'
  }
  return `${selectedChannel.value.name} (${selectedChannel.value.platform}/${selectedChannel.value.model})`
})

const selectChannel = (ch) => {
  selectedChannel.value = ch
  generationConfig.channelId = ch.id
  showChannelPicker.value = false
}

const thumbnailImages = computed(() => 
  images.value.filter(img => img.imageType === 'THUMBNAIL')
)

const carouselImages = computed(() => 
  images.value.filter(img => img.imageType === 'CAROUSEL')
)

const detailImages = computed(() => 
  images.value.filter(img => img.imageType === 'DETAIL')
)

const getStatusType = (status) => {
  const map = { DRAFT: 'default', PROCESSING: 'warning', COMPLETED: 'success', FAILED: 'danger' }
  return map[status] || 'default'
}

const getStatusText = (status) => {
  const map = { DRAFT: '草稿', PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败' }
  return map[status] || status
}

const goBack = () => router.back()

const loadProduct = async () => {
  try {
    const res = await productApi.getById(productId)
    if (res.success) {
      product.value = res.data
    }
  } catch (error) {
    showFailToast('加载失败')
  }
}

const loadSkus = async () => {
  try {
    const res = await productApi.getSkus(productId)
    if (res.success) {
      skus.value = res.data || []
    }
  } catch (error) {
    console.error(error)
  }
}

const loadImages = async () => {
  try {
    const res = await productApi.getImages(productId)
    if (res.success) {
      images.value = res.data || []
    }
  } catch (error) {
    console.error(error)
  }
}

const deleteOne = async (img) => {
  if (!img?.id) {
    return
  }
  try {
    const res = await productApi.deleteImage(productId, img.id)
    if (res.success) {
      showToast('已删除')
      await loadImages()
      await loadProduct()
    }
  } catch (error) {
    showFailToast(error.message || '删除失败')
  }
}

const deleteFailed = async (type) => {
  try {
    await showDialog({
      title: '确认删除失败图片',
      message: '将删除该类型下所有失败图片，且会同步删除 OSS 文件（如有）。'
    })
  } catch {
    return
  }
  try {
    const res = await productApi.deleteFailedImages(productId, type)
    if (res.success) {
      showToast(`已删除${res.data || 0}张失败图`)
      await loadImages()
      await loadProduct()
    }
  } catch (error) {
    showFailToast(error.message || '删除失败')
  }
}

const loadChannels = async () => {
  channelsLoading.value = true
  try {
    const res = await channelApi.getEnabled()
    if (res.success) {
      channels.value = res.data || []
      if (!selectedChannel.value && channels.value.length > 0) {
        selectChannel(channels.value[0])
      }
    }
  } catch (error) {
    console.error(error)
  } finally {
    channelsLoading.value = false
  }
}

const editSku = (sku) => {
  showToast('编辑SKU功能开发中')
}

const generateImages = async () => {
  if (skus.value.length === 0) {
    showFailToast('请先添加SKU')
    return
  }
  
  try {
    await showDialog({
      title: '确认生成',
      message: `将生成 ${generationConfig.thumbnail.count} 张缩略图，${generationConfig.carousel.count} 张轮播图，${generationConfig.detail.count} 张详情图，是否继续？`
    })
  } catch {
    return
  }
  
  generating.value = true
  try {
    const res = await productApi.generateImages(productId, {
      thumbnail: {
        imageType: 'THUMBNAIL',
        width: generationConfig.thumbnail.width,
        height: generationConfig.thumbnail.height,
        count: generationConfig.thumbnail.count,
        prompt: generationConfig.prompt,
        channelId: generationConfig.channelId
      },
      carousel: {
        imageType: 'CAROUSEL',
        width: generationConfig.carousel.width,
        height: generationConfig.carousel.height,
        count: generationConfig.carousel.count,
        prompt: generationConfig.prompt,
        channelId: generationConfig.channelId
      },
      detail: {
        imageType: 'DETAIL',
        width: generationConfig.detail.width,
        height: generationConfig.detail.height,
        count: generationConfig.detail.count,
        prompt: generationConfig.prompt,
        channelId: generationConfig.channelId
      }
    })
    
    if (res.success) {
      showToast('生成成功')
      await loadProduct()
      await loadImages()
      activeTab.value = 2
    }
  } catch (error) {
    showFailToast(error.message || '生成失败')
  } finally {
    generating.value = false
  }
}

onMounted(() => {
  loadProduct()
  loadSkus()
  loadImages()
  loadChannels()
})
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  background: #f5f5f5;
}

.content {
  padding-top: 46px;
  padding-bottom: 50px;
}

.loading-center {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.section-title {
  padding: 16px 16px 8px;
  font-size: 14px;
  font-weight: 500;
  color: #323233;
}

.sku-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sku-info {
  flex: 1;
}

.sku-meta {
  font-size: 12px;
  color: #969799;
  margin-top: 4px;
}

.generate-section {
  padding: 16px 0;
}

.generate-btn {
  padding: 16px;
  margin-top: 16px;
}

.result-actions {
  display: flex;
  gap: 8px;
  padding: 12px 16px 0;
  flex-wrap: wrap;
}

.popup-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #ebedf0;
  font-size: 16px;
  font-weight: 500;
}

.channel-groups {
  flex: 1;
  overflow: auto;
  padding-bottom: 8px;
}

.channel-group {
  padding-top: 8px;
}

.group-title {
  padding: 8px 16px;
  font-size: 12px;
  color: #969799;
}

.group-empty {
  padding-top: 16px;
}

.channel-label {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.model-hint {
  font-size: 12px;
  color: #969799;
}

.cred-hint {
  font-size: 12px;
  color: #07c160;
}

.cred-hint.bad {
  color: #ee0a24;
}

.channel-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.channel-name {
  font-weight: 500;
}

.channel-meta {
  text-align: right;
  color: #969799;
  font-size: 12px;
}

.model-name {
  color: #1989fa;
}
</style>
