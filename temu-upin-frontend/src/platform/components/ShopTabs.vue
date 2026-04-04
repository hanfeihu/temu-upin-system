<template>
  <div class="shop-tabs-wrap">
    <a-spin :spinning="loading">
      <a-empty v-if="!shops.length" :description="emptyText" />
      <div v-else class="shop-chip-list">
        <button
          v-for="shop in shops"
          :key="shop.shopId"
          type="button"
          class="shop-chip"
          :class="{ active: modelValue === shop.shopId }"
          @click="handleClick(shop.shopId)"
        >
          <span class="shop-chip-name">{{ shop.shopName || shop.shopId }}</span>
          <span class="shop-chip-id">{{ shop.shopId }}</span>
        </button>
      </div>
    </a-spin>
  </div>
</template>

<script setup>
const props = defineProps({
  shops: {
    type: Array,
    default: () => []
  },
  modelValue: {
    type: [String, Number, null],
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  },
  emptyText: {
    type: String,
    default: '暂无可用店铺'
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const handleClick = (shopId) => {
  if (!shopId || shopId === props.modelValue) {
    return
  }
  emit('update:modelValue', shopId)
  emit('change', shopId)
}
</script>

<style scoped>
.shop-tabs-wrap {
  width: 100%;
}

.shop-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.shop-chip {
  min-width: 180px;
  padding: 12px 14px;
  border: 1px solid #d9d9d9;
  border-radius: 12px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.shop-chip:hover {
  border-color: #4096ff;
  box-shadow: 0 6px 16px rgba(64, 150, 255, 0.12);
}

.shop-chip.active {
  border-color: #1677ff;
  background: #e6f4ff;
  box-shadow: 0 8px 20px rgba(22, 119, 255, 0.14);
}

.shop-chip-name {
  display: block;
  color: #1f1f1f;
  font-weight: 600;
  line-height: 1.4;
}

.shop-chip-id {
  display: block;
  margin-top: 4px;
  color: #8c8c8c;
  font-size: 12px;
}
</style>