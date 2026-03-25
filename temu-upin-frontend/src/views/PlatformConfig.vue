<template>
  <ProLayout title="平台配置">
    <div class="page">
      <a-card :bordered="false" class="card">
        <div class="head">
          <div class="title">发布默认配置</div>
          <a-space>
            <a-button @click="reload" :loading="loading">刷新</a-button>
            <a-button type="primary" @click="openCreate">新增配置</a-button>
          </a-space>
        </div>

        <a-table
          rowKey="id"
          :dataSource="profiles"
          :columns="columns"
          :loading="loading"
          :pagination="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <div class="name">
                <span class="n">{{ record.name }}</span>
                <a-tag v-if="record.isDefault" color="green">默认</a-tag>
              </div>
            </template>
            <template v-else-if="column.key === 'items'">
              <div class="items">
                  <div class="item" v-for="meta in keyMetas" :key="meta.key">
                    <span class="k">{{ meta.label }}</span>
                    <span class="v mono">{{ formatConfigValue(meta.key, record.items?.[meta.key]) }}</span>
                </div>
              </div>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="openEdit(record)">编辑</a-button>
                <a-button size="small" type="primary" ghost :disabled="record.isDefault" @click="setDefault(record)">设为默认</a-button>
                <a-popconfirm
                  title="确定删除？"
                  ok-text="删除"
                  cancel-text="取消"
                  @confirm="remove(record)"
                >
                  <a-button size="small" danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-modal
        v-model:open="editOpen"
        :title="editing?.id ? '编辑配置' : '新增配置'"
        ok-text="保存"
        cancel-text="取消"
        :confirm-loading="saving"
        @ok="save"
      >
        <a-form layout="vertical">
          <a-form-item label="名称">
            <a-input v-model:value="form.name" placeholder="例如：默认配置" />
          </a-form-item>
          <a-form-item>
            <a-checkbox v-model:checked="form.isDefault">设为默认</a-checkbox>
          </a-form-item>

          <a-divider />

          <a-form-item v-for="meta in keyMetas" :key="meta.key" :label="meta.label">
            <a-select
              v-if="meta.key === SHOP_KEY"
              v-model:value="form.items[meta.key]"
              allow-clear
              show-search
              placeholder="请选择默认店铺"
              :loading="shopsLoading"
              :filter-option="filterShopOption"
              :options="shopOptions"
            />
            <a-input v-else v-model:value="form.items[meta.key]" />
          </a-form-item>
        </a-form>
      </a-modal>
    </div>
  </ProLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { platformConfigApi } from '@/platform/api/platformConfig'
import { temuShopsApi } from '@/platform/api/temuShops'

const SHOP_KEY = 'temu.shopRefId'

const keyMetas = [
  { key: SHOP_KEY, label: '默认 TEMU 店铺' },
  { key: 'default.siteId', label: '默认站点 ID' },
  { key: 'default.warehouseId', label: '默认仓库 ID' },
  { key: 'sku.defaultStock', label: '默认库存' },
  { key: 'origin.region1ShortName', label: '产地区域1简称' },
  { key: 'origin.region2Id', label: '产地区域2 ID' },
  { key: 'shipment.freightTemplateId', label: '运费模板 ID' },
  { key: 'shipment.limitSecond', label: '发货时限秒数' }
]

const columns = [
  { title: '配置', key: 'name', width: 220 },
  { title: '字段', key: 'items' },
  { title: '操作', key: 'actions', width: 220, fixed: 'right' }
]

const loading = ref(false)
const profiles = ref([])
const shopsLoading = ref(false)
const shops = ref([])

const shopOptions = ref([])

const loadShops = async () => {
  shopsLoading.value = true
  try {
    const res = await temuShopsApi.list()
    if (res?.success) {
      shops.value = Array.isArray(res.data) ? res.data : []
      shopOptions.value = shops.value.map((shop) => ({
        value: String(shop.id),
        label: `${shop.shopName || '未命名店铺'}（${shop.shopId || '-'}）${shop.enabled ? '' : ' [禁用]'}`
      }))
      return
    }
    message.error(res?.message || '加载店铺失败')
  } catch (e) {
    message.error(e.message || '加载店铺失败')
  } finally {
    shopsLoading.value = false
  }
}

const findShop = (value) => {
  const id = String(value || '').trim()
  if (!id) return null
  return shops.value.find((shop) => String(shop.id) === id) || null
}

const formatConfigValue = (key, value) => {
  const text = value == null ? '' : String(value).trim()
  if (!text) return '-'
  if (key !== SHOP_KEY) return text
  const shop = findShop(text)
  if (!shop) return text
  return `${shop.shopName || '未命名店铺'}（${shop.shopId || '-'}）`
}

const filterShopOption = (input, option) => {
  const label = String(option?.label || '').toLowerCase()
  return label.includes(String(input || '').toLowerCase())
}

const reload = async () => {
  loading.value = true
  try {
    const res = await platformConfigApi.listProfiles()
    if (res?.success) {
      profiles.value = res.data?.profiles || []
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const editOpen = ref(false)
const saving = ref(false)
const editing = ref(null)
const form = reactive({
  name: '',
  isDefault: false,
  items: {}
})

const resetForm = () => {
  form.name = ''
  form.isDefault = false
  const out = {}
  for (const meta of keyMetas) out[meta.key] = ''
  form.items = out
}

const openCreate = () => {
  editing.value = null
  resetForm()
  editOpen.value = true
}

const openEdit = (r) => {
  editing.value = r
  resetForm()
  form.name = r?.name || ''
  form.isDefault = !!r?.isDefault
  for (const meta of keyMetas) {
    form.items[meta.key] = r?.items?.[meta.key] ?? ''
  }
  editOpen.value = true
}

const save = async () => {
  saving.value = true
  try {
    const payload = {
      name: form.name,
      isDefault: form.isDefault,
      items: form.items
    }
    const id = editing.value?.id
    const res = id
      ? await platformConfigApi.updateProfile(id, payload)
      : await platformConfigApi.createProfile(payload)

    if (res?.success) {
      message.success('已保存')
      editOpen.value = false
      await reload()
      return
    }
    message.error(res?.message || '保存失败')
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const setDefault = async (r) => {
  try {
    const res = await platformConfigApi.setDefault(r.id)
    if (res?.success) {
      message.success('已设为默认')
      await reload()
      return
    }
    message.error(res?.message || '设置失败')
  } catch (e) {
    message.error(e.message || '设置失败')
  }
}

const remove = async (r) => {
  try {
    const res = await platformConfigApi.deleteProfile(r.id)
    if (res?.success) {
      message.success('已删除')
      await reload()
      return
    }
    message.error(res?.message || '删除失败')
  } catch (e) {
    message.error(e.message || '删除失败')
  }
}

onMounted(reload)
onMounted(loadShops)
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
}

.card {
  border-radius: 14px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.title {
  font-weight: 900;
  color: #0f172a;
}

.name {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.items {
  display: grid;
  gap: 6px;
}

.item {
  display: grid;
  grid-template-columns: 230px 1fr;
  gap: 10px;
}

.k {
  color: rgba(15, 23, 42, 0.62);
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}
</style>
