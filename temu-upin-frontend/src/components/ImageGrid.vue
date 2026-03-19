<template>
  <div class="image-grid">
    <van-loading v-if="loading" size="24px" class="loading-center" />
    <van-empty v-else-if="images.length === 0" description="暂无图片" />
    <van-grid v-else :column-num="2" :gutter="10">
      <van-grid-item v-for="img in images" :key="img.id">
        <div class="image-card">
          <div class="badge">
            <van-tag :type="img.success ? 'success' : 'danger'" size="small">
              {{ img.success ? '成功' : '失败' }}
            </van-tag>
          </div>

          <div class="actions" v-if="showDelete">
            <van-button size="mini" type="danger" plain @click.stop="onDelete(img)">删除</van-button>
          </div>

          <van-image
            v-if="img.success && img.imageUrl"
            :src="img.imageUrl"
            width="100%"
            height="150"
            fit="cover"
            radius="4"
          />
          <div v-else class="failed-preview">
            <div class="failed-title">生成失败</div>
            <div class="failed-msg">{{ img.errorMessage || '未知错误' }}</div>
          </div>
        </div>
        <div class="image-info">
          <span>{{ img.width }}x{{ img.height }}</span>
        </div>
      </van-grid-item>
    </van-grid>
  </div>
</template>

<script setup>
import { showDialog } from 'vant'

const props = defineProps({
  images: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  showDelete: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['delete'])

const onDelete = async (img) => {
  try {
    await showDialog({
      title: '确认删除',
      message: '删除后无法恢复，是否继续？'
    })
    emit('delete', img)
  } catch (e) {
    // cancelled
  }
}
</script>

<style scoped>
.image-grid {
  padding: 16px;
  min-height: 200px;
}

.loading-center {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.image-info {
  font-size: 12px;
  color: #969799;
  margin-top: 4px;
}

.image-card {
  position: relative;
}

.badge {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 2;
}

.actions {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 2;
}

.failed-preview {
  height: 150px;
  border-radius: 4px;
  background: #fff;
  border: 1px solid #ebedf0;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  justify-content: center;
}

.failed-title {
  font-size: 13px;
  font-weight: 600;
  color: #323233;
}

.failed-msg {
  font-size: 12px;
  color: #ee0a24;
  line-height: 1.25;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
</style>
