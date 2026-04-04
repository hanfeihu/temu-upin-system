<template>
  <ProLayout title="同步配置">
    <div class="page">
      <a-card class="toolbar" :bordered="false">
        <ShopTabs v-model="shopId" :shops="shops" :loading="shopsLoading" empty-text="暂无可用店铺，请先配置店铺" @change="onShopChange" />
      </a-card>

      <a-card v-if="shopId" class="table-card" :bordered="false" :loading="loading">
        <a-table :columns="columns" :dataSource="configs" rowKey="configKey" :pagination="false" bordered size="middle">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'configValue'">
              <a-input v-model:value="record.configValue" style="width: 300px" />
            </template>
          </template>
        </a-table>
        <div style="margin-top: 16px; text-align: right">
          <a-button type="primary" :loading="saving" @click="saveConfigs">保存配置</a-button>
        </div>
      </a-card>
    </div>
  </ProLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import ShopTabs from '@/platform/components/ShopTabs.vue'
import { syncApi } from '@/platform/api/sync'
import { temuShopsApi } from '@/platform/api/temuShops'

const shopsLoading = ref(false)
const shops = ref([])
const shopId = ref(null)
const loading = ref(false)
const saving = ref(false)
const configs = ref([])

const columns = [
  { title: '配置项', dataIndex: 'configKey', key: 'configKey', width: 280 },
  { title: '说明', dataIndex: 'configDesc', key: 'configDesc', width: 240 },
  { title: '值', key: 'configValue' }
]

const loadShops = async () => {
  shopsLoading.value = true
  try {
    const res = await temuShopsApi.list({ enabled: true })
    shops.value = res?.success ? (res.data || []) : []
    const firstShopId = shops.value[0]?.shopId || null
    if (!firstShopId) {
      shopId.value = null
      configs.value = []
      return
    }
    if (!shopId.value || !shops.value.some((item) => item.shopId === shopId.value)) {
      shopId.value = firstShopId
      await loadConfigs()
    }
  } catch (e) { message.error('店铺加载失败') }
  finally { shopsLoading.value = false }
}

const onShopChange = () => { if (shopId.value) loadConfigs() }

const loadConfigs = async () => {
  loading.value = true
  try {
    const res = await syncApi.getConfigs(shopId.value)
    if (res?.success) configs.value = res.data?.configs || []
    else message.error(res?.message || '加载失败')
  } catch (e) { message.error(e.message || '加载失败') }
  finally { loading.value = false }
}

const saveConfigs = async () => {
  saving.value = true
  try {
    const res = await syncApi.saveConfigs({ shopId: shopId.value, configs: configs.value })
    if (res?.success) message.success('配置已保存')
    else message.error(res?.message || '保存失败')
  } catch (e) { message.error(e.message || '保存失败') }
  finally { saving.value = false }
}

onMounted(() => { loadShops() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.toolbar { margin-bottom: 0; }
</style>
