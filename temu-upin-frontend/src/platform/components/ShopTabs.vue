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
  gap: 8px;
}

.shop-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
}

.shop-chip:hover {
  border-color: #4096ff;
  color: #1677ff;
}

.shop-chip.active {
  border-color: #1677ff;
  background: #f0f7ff;
  color: #1677ff;
}

.shop-chip-name {
  display: inline-block;
  color: #1f1f1f;
  font-weight: 500;
  line-height: 1.4;
}

.shop-chip-id {
  display: inline-block;
  color: #8c8c8c;
  font-size: 12px;
}

.shop-chip.active .shop-chip-name,
.shop-chip.active .shop-chip-id {
  color: inherit;
}
</style>
