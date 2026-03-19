<template>
  <div class="create-page">
    <van-nav-bar
      title="新建产品"
      left-arrow
      @click-left="goBack"
      fixed
    />
    
    <div class="content">
      <van-form @submit="onSubmit">
        <van-cell-group inset>
          <van-field
            v-model="form.name"
            name="name"
            label="产品名称"
            placeholder="请输入产品名称"
            :rules="[{ required: true, message: '请输入产品名称' }]"
          />
          
          <van-field
            v-model="form.description"
            name="description"
            label="产品描述"
            type="textarea"
            placeholder="请输入产品描述"
            rows="3"
          />
          
          <van-field
            v-model="form.category"
            name="category"
            label="类目"
            placeholder="如：饰品"
          />
          
          <van-field
            v-model="form.brand"
            name="brand"
            label="品牌"
            placeholder="请输入品牌名称"
          />
          
          <van-field
            v-model="form.tags"
            name="tags"
            label="标签"
            placeholder="用逗号分隔"
          />
        </van-cell-group>
        
        <div class="sku-header">
          <div class="section-title">SKU 列表</div>
          <van-button size="small" type="primary" plain icon="plus" @click="addSku">
            添加SKU
          </van-button>
        </div>

        <van-cell-group inset>
          <div v-if="skus.length === 0" class="sku-empty">
            <van-empty description="请先添加SKU" />
          </div>

          <div v-else class="sku-list">
            <div v-for="(sku, index) in skus" :key="sku._key" class="sku-item">
              <div class="sku-image">
                <van-uploader
                  v-model="sku.fileList"
                  :max-count="1"
                  :after-read="(file) => onSkuUpload(file, sku)"
                  accept="image/*"
                  :preview-image="false"
                  :preview-full-image="false"
                >
                  <div class="sku-upload-btn" :class="{ 'has-image': !!sku.previewUrl }">
                    <van-image
                      v-if="sku.previewUrl"
                      :src="sku.previewUrl"
                      width="72"
                      height="72"
                      fit="cover"
                      radius="10"
                    />
                    <div v-else class="sku-upload-placeholder">
                      <van-icon name="photograph" size="24" />
                    </div>
                    <van-icon
                      v-if="sku.previewUrl"
                      name="cross"
                      class="sku-clear"
                      @click.stop="onSkuDeleteFile(sku)"
                    />
                  </div>
                </van-uploader>
              </div>

              <div class="sku-info">
                <van-field v-model="sku.skuCode" placeholder="SKU编码" size="small" />
                <van-field v-model="sku.color" placeholder="颜色" size="small" />
              </div>

              <van-button
                class="remove-sku"
                size="mini"
                type="danger"
                plain
                @click="removeSku(index)"
              >
                删除
              </van-button>
            </div>
          </div>
        </van-cell-group>
        
        <div class="submit-btn">
          <van-button type="primary" size="large" round native-type="submit" :loading="submitting">
            创建产品
          </van-button>
        </div>
      </van-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showFailToast } from 'vant'
import { productApi, uploadApi } from '@/api'

const router = useRouter()
const submitting = ref(false)
const skus = reactive([])

const form = reactive({
  name: '',
  description: '',
  category: '',
  brand: '',
  tags: ''
})

const goBack = () => {
  router.back()
}

const addSku = () => {
  skus.push({
    _key: `${Date.now()}_${Math.random().toString(16).slice(2)}`,
    fileList: [],
    previewUrl: '',
    imageUrl: '',
    skuCode: `SKU${Date.now()}`,
    color: '',
  })
}

// create with one empty SKU by default
addSku()

const onSkuDeleteFile = (sku) => {
  sku.previewUrl = ''
  sku.imageUrl = ''
  sku.fileList = []
  return true
}

const onSkuUpload = async (file, sku) => {
  const f = Array.isArray(file) ? file[0] : file
  try {
    const res = await uploadApi.uploadImage(f.file)
    if (res.success) {
      sku.previewUrl = f.content
      sku.imageUrl = res.data.url
    }
  } catch (error) {
    showFailToast('图片上传失败')
    sku.fileList = []
  }
}

const removeSku = (index) => {
  skus.splice(index, 1)
}

const onSubmit = async () => {
  if (!form.name) {
    showFailToast('请输入产品名称')
    return
  }
  
  if (skus.length === 0) {
    showFailToast('请至少添加一个SKU')
    return
  }

  const missing = skus.find(s => !s.imageUrl)
  if (missing) {
    showFailToast('每个SKU必须上传一张图片')
    return
  }
  
  submitting.value = true
  try {
    const res = await productApi.create(form)
    if (res.success && res.data) {
      const productId = res.data.id
      
      for (const sku of skus) {
        await productApi.addSku(productId, {
          skuCode: sku.skuCode,
          color: sku.color,
          originalImageUrl: sku.imageUrl
        })
      }
      
      showToast('创建成功')
      router.replace(`/ai/product/${productId}`)
    }
  } catch (error) {
    showFailToast(error.message || '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.create-page {
  min-height: 100vh;
  background: #f5f5f5;
}

.content {
  padding-top: 46px;
  padding-bottom: 20px;
}

.section-title {
  padding: 16px 16px 8px;
  font-size: 14px;
  font-weight: 500;
  color: #323233;
}

.sku-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 16px;
}

.sku-upload-area {
  padding: 16px;
}

.upload-tile {
  width: 100%;
  min-height: 72px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 12px;
  border-radius: 10px;
  border: 1px dashed #d9ecff;
  background: linear-gradient(180deg, #f7fbff 0%, #ffffff 100%);
  color: #323233;
}

.tile-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: #e8f3ff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1989fa;
}

.tile-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tile-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}

.tile-sub {
  font-size: 12px;
  color: #969799;
}

.sku-list {
  padding: 0 16px 16px;
}

.sku-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #ebedf0;
  position: relative;
}

.sku-item:last-child {
  border-bottom: none;
}

.sku-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sku-image {
  width: 72px;
  flex: 0 0 72px;
}

.sku-upload-btn {
  width: 72px;
  height: 72px;
  border-radius: 10px;
  overflow: hidden;
  position: relative;
}

.sku-upload-btn:not(.has-image) {
  border: 1px dashed #d9ecff;
  background: linear-gradient(180deg, #f7fbff 0%, #ffffff 100%);
}

.sku-upload-placeholder {
  width: 72px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1989fa;
}

.sku-clear {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
}

.remove-sku {
  align-self: flex-start;
}

.sku-empty {
  padding: 10px 0;
}

.submit-btn {
  padding: 16px;
}
</style>
