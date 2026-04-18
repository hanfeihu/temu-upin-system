<template>
  <ProLayout title="TEMU 店铺管理">
    <div class="page">
      <a-alert
        class="notice"
        type="info"
        show-icon
        message="平台配置已并入店铺配置"
        description="发布站点、仓库、库存、产地、运费模板和发货时限现在都跟随店铺保存，发布商品时会按商品绑定店铺读取。"
      />

      <div class="toolbar">
        <a-space>
          <a-button type="primary" @click="openCreate">新增店铺</a-button>
          <a-button @click="reload" :loading="loading">刷新</a-button>
        </a-space>
      </div>

      <a-table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        row-key="id"
        :pagination="false"
        bordered
        size="middle"
        :scroll="{ x: 1600 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '禁用' }}</a-tag>
          </template>

          <template v-else-if="column.key === 'tokenMasked'">
            <span>{{ record.tokenMasked || '-' }}</span>
          </template>

          <template v-else-if="column.key === 'shopName'">
            <div class="shop-cell">
              <div class="shop-title">{{ record.shopName || '-' }}</div>
              <div class="shop-sub">{{ record.shopId || '-' }}</div>
            </div>
          </template>

          <template v-else-if="column.key === 'publishConfig'">
            <div class="config-summary">
              <span class="config-chip">站点 {{ record.siteId ?? '-' }}</span>
              <span class="config-chip">仓库 {{ record.warehouseId || '-' }}</span>
              <span class="config-chip">库存 {{ record.defaultStock ?? '-' }}/{{ record.maxStock ?? '-' }}</span>
              <span class="config-chip">产地 {{ record.originRegion1ShortName || '-' }}/{{ record.originRegion2Id ?? '-' }}</span>
              <span class="config-chip">运费模板 {{ record.freightTemplateId || '-' }}</span>
              <span class="config-chip">发货时限 {{ record.shipmentLimitSecond ?? '-' }}s</span>
            </div>
          </template>

          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button size="small" @click="openEdit(record)">编辑</a-button>
              <a-button size="small" danger @click="confirmDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <a-modal
        v-model:open="editOpen"
        :title="editForm.id ? '编辑店铺' : '新增店铺'"
        :confirm-loading="saving"
        @ok="save"
        @cancel="closeEdit"
        width="920"
        :bodyStyle="{ maxHeight: '70vh', overflowY: 'auto', overflowX: 'hidden' }"
      >
        <a-form layout="vertical" :model="editForm">
          <div class="section-title">店铺凭证</div>
          <a-row :gutter="16">
            <a-col :xs="24" :md="12">
              <a-form-item label="店铺名称" required>
                <a-input v-model:value="editForm.shopName" placeholder="例如：TEMU-店铺A" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="店铺ID" required>
                <a-input v-model:value="editForm.shopId" placeholder="例如：634418212966313" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="TOKEN" :required="!editForm.id">
                <a-input-password
                  v-model:value="editForm.token"
                  placeholder="新增必填；编辑留空表示不修改"
                  autocomplete="new-password"
                />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="应用" required>
                <a-select
                  v-model:value="editForm.appId"
                  placeholder="请选择应用"
                  :loading="appsLoading"
                  show-search
                  :filter-option="filterAppOption"
                >
                  <a-select-option v-for="a in apps" :key="a.id" :value="a.id">
                    {{ a.appName }}（{{ a.appKey }}）
                  </a-select-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="启用">
                <a-switch v-model:checked="editForm.enabled" />
              </a-form-item>
            </a-col>
          </a-row>

          <a-divider />

          <div class="section-title">店铺发布配置</div>
          <a-row :gutter="16">
            <a-col :xs="24" :md="12">
              <a-form-item label="站点 ID" required>
                <a-input-number v-model:value="editForm.siteId" :min="1" :precision="0" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="仓库 ID" required>
                <a-input v-model:value="editForm.warehouseId" placeholder="例如：WH-03304781516934009" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="默认库存" required>
                <a-input-number v-model:value="editForm.defaultStock" :min="1" :precision="0" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="最大库存" required>
                <a-input-number v-model:value="editForm.maxStock" :min="1" :precision="0" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="产地区域1简称" required>
                <a-input v-model:value="editForm.originRegion1ShortName" placeholder="例如：CN" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="产地区域2 ID" required>
                <a-input-number v-model:value="editForm.originRegion2Id" :min="1" :precision="0" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="运费模板 ID" required>
                <a-input v-model:value="editForm.freightTemplateId" placeholder="例如：HFT-14851213328261424009" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="发货时限秒数" required>
                <a-input-number v-model:value="editForm.shipmentLimitSecond" :min="1" :precision="0" style="width: 100%" />
              </a-form-item>
            </a-col>
          </a-row>
        </a-form>
      </a-modal>
    </div>
  </ProLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { temuShopsApi } from '@/platform/api/temuShops'
import { temuAppsApi } from '@/platform/api/temuApps'

const SHOP_DEFAULTS = {
  siteId: 100,
  warehouseId: 'WH-03304781516934009',
  defaultStock: 1000,
  maxStock: 10842,
  originRegion1ShortName: 'CN',
  originRegion2Id: 43000000000016,
  freightTemplateId: 'HFT-14851213328261424009',
  shipmentLimitSecond: 777600
}

const loading = ref(false)
const rows = ref([])

const appsLoading = ref(false)
const apps = ref([])

const editOpen = ref(false)
const saving = ref(false)
const editForm = reactive({
  id: null,
  shopName: '',
  shopId: '',
  token: '',
  appId: null,
  enabled: true,
  siteId: SHOP_DEFAULTS.siteId,
  warehouseId: SHOP_DEFAULTS.warehouseId,
  defaultStock: SHOP_DEFAULTS.defaultStock,
  maxStock: SHOP_DEFAULTS.maxStock,
  originRegion1ShortName: SHOP_DEFAULTS.originRegion1ShortName,
  originRegion2Id: SHOP_DEFAULTS.originRegion2Id,
  freightTemplateId: SHOP_DEFAULTS.freightTemplateId,
  shipmentLimitSecond: SHOP_DEFAULTS.shipmentLimitSecond
})

const columns = [
  { title: '店铺', dataIndex: 'shopName', key: 'shopName', width: 260 },
  { title: '应用', dataIndex: 'appName', key: 'appName', width: 220 },
  { title: '发布配置', key: 'publishConfig', width: 520 },
  { title: 'TOKEN', key: 'tokenMasked', width: 180 },
  { title: '启用', key: 'enabled', width: 90 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 170 },
  { title: '操作', key: 'actions', width: 160, fixed: 'right' }
]

const filterAppOption = (input, option) => {
  const text = (option?.children || '').toString().toLowerCase()
  return text.includes((input || '').toLowerCase())
}

const loadApps = async () => {
  appsLoading.value = true
  try {
    const res = await temuAppsApi.list({ enabled: true })
    if (res?.success) {
      apps.value = Array.isArray(res.data) ? res.data : []
      return
    }
  } catch (e) {
    // ignore
  } finally {
    appsLoading.value = false
  }
}

const reload = async () => {
  loading.value = true
  try {
    const res = await temuShopsApi.list()
    if (res?.success) {
      rows.value = Array.isArray(res.data) ? res.data : []
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const openCreate = async () => {
  editForm.id = null
  editForm.shopName = ''
  editForm.shopId = ''
  editForm.token = ''
  editForm.appId = null
  editForm.enabled = true
  editForm.siteId = SHOP_DEFAULTS.siteId
  editForm.warehouseId = SHOP_DEFAULTS.warehouseId
  editForm.defaultStock = SHOP_DEFAULTS.defaultStock
  editForm.maxStock = SHOP_DEFAULTS.maxStock
  editForm.originRegion1ShortName = SHOP_DEFAULTS.originRegion1ShortName
  editForm.originRegion2Id = SHOP_DEFAULTS.originRegion2Id
  editForm.freightTemplateId = SHOP_DEFAULTS.freightTemplateId
  editForm.shipmentLimitSecond = SHOP_DEFAULTS.shipmentLimitSecond
  editOpen.value = true
  await loadApps()
}

const openEdit = async (r) => {
  editForm.id = r?.id ?? null
  editForm.shopName = r?.shopName || ''
  editForm.shopId = r?.shopId || ''
  editForm.token = ''
  editForm.appId = r?.appId ?? null
  editForm.enabled = !!r?.enabled
  editForm.siteId = r?.siteId ?? SHOP_DEFAULTS.siteId
  editForm.warehouseId = r?.warehouseId || SHOP_DEFAULTS.warehouseId
  editForm.defaultStock = r?.defaultStock ?? SHOP_DEFAULTS.defaultStock
  editForm.maxStock = r?.maxStock ?? SHOP_DEFAULTS.maxStock
  editForm.originRegion1ShortName = r?.originRegion1ShortName || SHOP_DEFAULTS.originRegion1ShortName
  editForm.originRegion2Id = r?.originRegion2Id ?? SHOP_DEFAULTS.originRegion2Id
  editForm.freightTemplateId = r?.freightTemplateId || SHOP_DEFAULTS.freightTemplateId
  editForm.shipmentLimitSecond = r?.shipmentLimitSecond ?? SHOP_DEFAULTS.shipmentLimitSecond
  editOpen.value = true
  await loadApps()
}

const closeEdit = () => {
  editOpen.value = false
}

const save = async () => {
  const payload = {
    shopName: (editForm.shopName || '').trim(),
    shopId: (editForm.shopId || '').trim(),
    token: (editForm.token || '').trim(),
    appId: editForm.appId,
    enabled: !!editForm.enabled,
    siteId: editForm.siteId,
    warehouseId: (editForm.warehouseId || '').trim(),
    defaultStock: editForm.defaultStock,
    maxStock: editForm.maxStock,
    originRegion1ShortName: (editForm.originRegion1ShortName || '').trim(),
    originRegion2Id: editForm.originRegion2Id,
    freightTemplateId: (editForm.freightTemplateId || '').trim(),
    shipmentLimitSecond: editForm.shipmentLimitSecond
  }

  if (!payload.shopName) return message.error('请输入店铺名称')
  if (!payload.shopId) return message.error('请输入店铺ID')
  if (!payload.appId) return message.error('请选择应用')
  if (!editForm.id && !payload.token) return message.error('请输入 TOKEN')
  if (!payload.siteId) return message.error('请输入站点 ID')
  if (!payload.warehouseId) return message.error('请输入仓库 ID')
  if (!payload.defaultStock) return message.error('请输入默认库存')
  if (!payload.maxStock) return message.error('请输入最大库存')
  if (!payload.originRegion1ShortName) return message.error('请输入产地区域1简称')
  if (!payload.originRegion2Id) return message.error('请输入产地区域2 ID')
  if (!payload.freightTemplateId) return message.error('请输入运费模板 ID')
  if (!payload.shipmentLimitSecond) return message.error('请输入发货时限秒数')
  if (Number(payload.maxStock) < Number(payload.defaultStock)) return message.error('最大库存不能小于默认库存')

  saving.value = true
  try {
    let res
    if (editForm.id) {
      if (!payload.token) delete payload.token
      res = await temuShopsApi.update(editForm.id, payload)
    } else {
      res = await temuShopsApi.create(payload)
    }

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

const confirmDelete = (r) => {
  if (!r?.id) return
  Modal.confirm({
    title: '删除店铺？',
    content: '删除后不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await temuShopsApi.delete(r.id)
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
  })
}

onMounted(async () => {
  await loadApps()
  await reload()
})
</script>

<style scoped>
.page {
  /* Using block layout here avoids some edge cases with Ant Table fixed columns + horizontal scroll. */
  display: block;
}

.page > * + * {
  margin-top: 12px;
}

.notice {
  border-radius: 14px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
}

.shop-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.shop-title {
  font-weight: 600;
  color: #1f2937;
}

.shop-sub {
  color: #6b7280;
  font-size: 12px;
  word-break: break-all;
}

.config-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.config-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: #f3f6fb;
  color: #334155;
  font-size: 12px;
  line-height: 1.4;
}

.section-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
</style>
