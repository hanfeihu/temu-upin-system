<template>
  <a-modal
    v-model:open="open"
    title="SKU 转换 (TEMU)"
    width="1100"
    :destroyOnClose="true"
    :mask-closable="!saving && !initing"
    :keyboard="!saving && !initing"
  >
    <template #footer>
      <a-space>
        <a-button :disabled="saving || initing" @click="open = false">取消</a-button>
        <a-button :loading="initing" :disabled="saving" @click="initFromOrigin(false)">初始化</a-button>
        <a-popconfirm
          title="确定重新初始化？会覆盖你当前编辑的 TEMU SKU"
          ok-text="覆盖"
          cancel-text="取消"
          @confirm="initFromOrigin(true)"
        >
          <a-button :loading="initing" :disabled="saving" danger>重新初始化</a-button>
        </a-popconfirm>
        <a-button type="primary" :loading="saving" :disabled="initing" @click="save">保存</a-button>
      </a-space>
    </template>

    <div class="sku-convert">
      <div class="hint">
        原 SKU 只用于填充，TEMU SKU 可编辑、可增删。
      </div>

      <div class="actions">
        <a-button type="dashed" @click="addRow">新增一行</a-button>
      </div>

      <a-table
        :data-source="localSkus"
        :columns="columns"
        row-key="__key"
        :pagination="false"
        size="small"
        :scroll="{ x: 1200 }"
      >
        <template #headerCell="{ column }">
          <template v-if="isVariantColumn(column)">
            <div class="spec-header">
              <span>{{ column.title }}</span>
              <a-button size="small" type="link" @click="applyVariantPrefix(column)">变种</a-button>
            </div>
          </template>
        </template>

        <template #bodyCell="{ column, record, index }">
          <template v-if="column.key === 'image'">
            <button
              class="img-pick"
              type="button"
              :disabled="uploadingIndex === index"
              :title="record.image ? '点击更换' : '点击设置'"
              @click="openImagePicker(record, index)"
            >
              <img v-if="record.image" :src="record.image" alt="" />
              <div v-else class="img-empty">点击设置</div>
              <div v-if="uploadingIndex === index" class="img-mask">上传中...</div>
            </button>
          </template>

          <template v-else-if="column.key === 'specKey'">
            <a-input v-model:value="record.specKey" placeholder="颜色>尺码..." allow-clear />
          </template>

          <template v-else-if="String(column.key || '').startsWith('specJson:')">
            <a-input
              :value="getSpecJsonValue(record, String(column.key || '').slice(9))"
              @update:value="(val) => setSpecJsonValue(record, String(column.key || '').slice(9), val)"
              placeholder="规格值..."
              allow-clear
            />
          </template>

          <template v-else-if="column.key === 'temuSkuId'">
            <a-input v-model:value="record.temuSkuId" placeholder="temu skuId" allow-clear />
          </template>

          <template v-else-if="column.key === 'originSkuId'">
            <a-input v-model:value="record.originSkuId" placeholder="原 skuId" allow-clear />
          </template>

          <template v-else-if="column.key === 'originPrice'">
            <a-input-number v-model:value="record.originPrice" :min="0" style="width: 120px" />
          </template>

          <template v-else-if="column.key === 'supplyPrice'">
            <a-input-number v-model:value="record.supplyPrice" :min="0" style="width: 120px" />
          </template>

          <template v-else-if="column.key === 'weightG'">
            <a-input-number v-model:value="record.weightG" :min="0" style="width: 110px" />
          </template>

          <template v-else-if="column.key === 'dim'">
            <div class="dim">
              <a-input-number v-model:value="record.lengthCm" :min="0" style="width: 90px" />
              <span class="x">x</span>
              <a-input-number v-model:value="record.widthCm" :min="0" style="width: 90px" />
              <span class="x">x</span>
              <a-input-number v-model:value="record.heightCm" :min="0" style="width: 90px" />
            </div>
          </template>

          <template v-else-if="column.key === 'ops'">
            <a-space>
              <a-button size="small" @click="recalc(index)">重算供货价</a-button>
              <a-popconfirm title="删除这行？" ok-text="删除" cancel-text="取消" @confirm="remove(index)">
                <a-button size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>
  </a-modal>

  <a-modal
    v-model:open="imgPick.open"
    title="更换 SKU 图片"
    :confirm-loading="imgPick.uploading"
    ok-text="上传并替换"
    cancel-text="取消"
    :mask-closable="!imgPick.uploading"
    :keyboard="!imgPick.uploading"
    @ok="confirmImagePicker"
  >
    <div class="img-pick-modal">
      <div class="img-pick-hint">
        填写图片 URL 后，会调用 TEMU 图片上传接口生成新的图片 URL。
      </div>
      <a-input v-model:value="imgPick.url" placeholder="https://..." allow-clear />
      <div class="img-pick-actions">
        <a-button type="link" danger :disabled="imgPick.uploading" @click="clearImage">清空当前图片</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { productCollectionApi } from '@/platform/api/productCollections'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  record: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue'])

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const initing = ref(false)
const saving = ref(false)
const uploadingIndex = ref(-1)
const localSkus = ref([])

const imgPick = reactive({
  open: false,
  index: -1,
  url: '',
  uploading: false
})

const baseColumns = [
  { title: '图片', key: 'image', width: 120, fixed: 'left' },
  { title: '属性组合', key: 'specKey', width: 220 },
]

const tailColumns = [
  { title: 'temu skuId', key: 'temuSkuId', width: 140 },
  { title: '原 skuId', key: 'originSkuId', width: 140 },
  { title: '原价', key: 'originPrice', width: 120 },
  { title: '供货价', key: 'supplyPrice', width: 140 },
  { title: '重量(g)', key: 'weightG', width: 120 },
  { title: '尺寸(cm)', key: 'dim', width: 320 },
  { title: '操作', key: 'ops', width: 170, fixed: 'right' }
]

const parseSpecJson = (value) => {
  if (!value || typeof value !== 'string') return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

const specJsonColumns = computed(() => {
  const keys = []
  for (const row of localSkus.value) {
    const parsed = parseSpecJson(row?.specJson)
    for (const key of Object.keys(parsed)) {
      if (key && !keys.includes(key)) keys.push(key)
    }
  }
  return keys.map((key) => ({
    title: key,
    key: `specJson:${key}`,
    width: 160
  }))
})

const columns = computed(() => [
  ...baseColumns,
  ...specJsonColumns.value,
  ...tailColumns
])

const getSpecJsonValue = (row, key) => {
  const parsed = parseSpecJson(row?.specJson)
  const value = parsed?.[key]
  return value == null ? '' : String(value)
}

const setSpecJsonValue = (row, key, newValue) => {
  const parsed = parseSpecJson(row?.specJson)
  parsed[key] = newValue ?? ''
  row.specJson = JSON.stringify(parsed)
}

const isVariantColumn = (column) => {
  const key = String(column?.key || '')
  return key === 'specKey' || key.startsWith('specJson:')
}

const loadExisting = async () => {
  const id = props.record?.id
  if (!id) return
  try {
    const res = await productCollectionApi.listTemuSkus(id)
    if (res?.success) {
      const list = Array.isArray(res.data) ? res.data : []
      localSkus.value = list.map((r, idx) => ({ __key: `${Date.now()}_${idx}`, ...r }))
      return
    }
  } catch (e) {
    message.error(e.message || '加载 TEMU SKU 失败')
  }
}

watch(
  () => open.value,
  (v) => {
    if (v) {
      localSkus.value = []
      loadExisting()
    }
  }
)

const initFromOrigin = async (force) => {
  const id = props.record?.id
  if (!id) return
  initing.value = true
  try {
    const res = await productCollectionApi.initTemuSkus(id, !!force)
    if (res?.success) {
      const list = Array.isArray(res.data) ? res.data : []
      localSkus.value = list.map((r, idx) => ({ __key: `${Date.now()}_${idx}`, ...r }))
      message.success('已初始化')
      return
    }
    message.error(res?.message || '初始化失败')
  } catch (e) {
    message.error(e.message || '初始化失败')
  } finally {
    initing.value = false
  }
}

const openImagePicker = (row, index) => {
  imgPick.open = true
  imgPick.index = index
  imgPick.url = row?.image || ''
}

const clearImage = () => {
  if (imgPick.index < 0) return
  const row = localSkus.value[imgPick.index]
  if (row) row.image = ''
  imgPick.open = false
}

const confirmImagePicker = async () => {
  const id = props.record?.id
  if (!id) return
  if (imgPick.index < 0) return

  const url = (imgPick.url || '').trim()
  if (!url) {
    message.error('请填写图片URL')
    return
  }

  imgPick.uploading = true
  uploadingIndex.value = imgPick.index
  try {
    const res = await productCollectionApi.uploadTemuSkuImage(id, url)
    if (res?.success && res.data?.imageUrl) {
      const row = localSkus.value[imgPick.index]
      if (row) row.image = res.data.imageUrl
      message.success('图片已更换')
      imgPick.open = false
      return
    }
    message.error(res?.message || '更换失败')
  } catch (e) {
    message.error(e.message || '更换失败')
  } finally {
    imgPick.uploading = false
    uploadingIndex.value = -1
  }
}

const addRow = () => {
  localSkus.value = [
    ...localSkus.value,
    {
      __key: `${Date.now()}_${Math.random()}`,
      id: null,
      temuSkuId: '',
      originSkuId: '',
      specKey: '',
      specJson: '',
      image: '',
      originPrice: null,
      supplyPrice: null,
      weightG: 150,
      lengthCm: 10,
      widthCm: 5,
      heightCm: 5
    }
  ]
}

const remove = (idx) => {
  localSkus.value = localSkus.value.filter((_, i) => i !== idx)
}

const num = (v) => {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

const recalc = (idx) => {
  const r = localSkus.value[idx]
  if (!r) return

  // supplyPrice = ((originPrice + baseFreight + 6) + weightG * 0.069) * 3
  // baseFreight is stored on record, but list api doesn't include it; user can keep supplyPrice editable.
  // Here we only do a minimal recalculation using (originPrice + 6 + weightG * 0.069) * 3
  const originPrice = num(r.originPrice)
  const weightG = num(r.weightG)
  const supply = ((originPrice + 6) + (weightG * 0.069)) * 3
  r.supplyPrice = Math.round(supply * 100) / 100
}

const stripVariantPrefix = (value) => {
  const text = String(value || '').trim()
  return text.replace(/^G-\d{3}-\s*/i, '')
}

const applyVariantPrefix = (column) => {
  if (!localSkus.value.length) {
    message.info('暂无可处理的 SKU')
    return
  }

  const columnKey = String(column?.key || '')
  if (!isVariantColumn(column)) return

  localSkus.value = localSkus.value.map((row, index) => {
    const prefix = `G-${String(index + 1).padStart(3, '0')}-`
    if (columnKey === 'specKey') {
      const base = stripVariantPrefix(row?.specKey)
      return {
        ...row,
        specKey: `${prefix}${base}`
      }
    }

    const specName = columnKey.slice(9)
    const parsed = parseSpecJson(row?.specJson)
    const base = stripVariantPrefix(parsed?.[specName])
    parsed[specName] = `${prefix}${base}`
    return {
      ...row,
      specJson: JSON.stringify(parsed)
    }
  })

  message.success(`已批量生成${column?.title || ''}变种编号`)
}

const save = async () => {
  const id = props.record?.id
  if (!id) return
  saving.value = true
  try {
    const payload = localSkus.value.map(({ __key, ...rest }) => rest)
    const res = await productCollectionApi.saveTemuSkus(id, payload)
    if (res?.success) {
      message.success('已保存')
      open.value = false
      return
    }
    message.error(res?.message || '保存失败')
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.sku-convert {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hint {
  color: rgba(15, 23, 42, 0.62);
  font-size: 12px;
}

.actions {
  display: flex;
  justify-content: flex-end;
}

.img-pick {
  width: 56px;
  height: 56px;
  padding: 0;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
  background: #ffffff;
  cursor: pointer;
  position: relative;
  display: grid;
  place-items: center;
}

.img-pick:hover {
  border-color: rgba(37, 99, 235, 0.35);
  box-shadow: 0 8px 18px -14px rgba(37, 99, 235, 0.35);
}

.img-pick:disabled {
  cursor: not-allowed;
  opacity: 0.85;
}

.img-pick img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.img-mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  color: rgba(255, 255, 255, 0.92);
  font-weight: 800;
  font-size: 12px;
  display: grid;
  place-items: center;
}

.img-empty {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  color: rgba(15, 23, 42, 0.55);
  font-size: 12px;
  font-weight: 800;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.03), rgba(15, 23, 42, 0.01));
}

.img-pick-modal {
  display: grid;
  gap: 10px;
}

.img-pick-hint {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.62);
}

.img-pick-actions {
  display: flex;
  justify-content: flex-end;
}

.spec-json-cell {
  display: inline-block;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.spec-header {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.dim {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.x {
  color: rgba(15, 23, 42, 0.5);
  font-weight: 700;
}
</style>
