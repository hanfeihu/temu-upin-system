<template>
  <div class="channel-page">
    <van-nav-bar
      title="渠道管理"
      left-arrow
      @click-left="goBack"
      fixed
    />
    
    <div class="content">
      <van-cell-group inset>
        <van-cell title="渠道列表" is-link @click="showChannelList = true">
          <template #value>
            <span>{{ channels.length }} 个渠道</span>
          </template>
        </van-cell>
      </van-cell-group>
      
      <div class="add-btn">
        <van-button type="primary" size="large" round @click="openAdd">
          添加渠道
        </van-button>
      </div>
      
        <van-popup v-model:show="showChannelList" position="bottom" style="height: 80%">
          <div class="popup-content">
            <div class="popup-header">
              <span>渠道列表</span>
              <van-icon name="close" @click="showChannelList = false" />
            </div>

            <div class="list-tools">
              <van-search v-model="search" placeholder="搜索渠道/平台/模型" :clearable="true" />
            </div>
            
            <van-cell-group inset>
              <van-cell
                v-for="channel in filteredChannels"
                :key="channel.id"
                is-link
                @click="openEdit(channel)"
              >
                <template #title>
                  <div class="channel-cell">
                    <van-tag :type="channel.enabled ? 'success' : 'default'" size="small">
                      {{ channel.enabled ? '启用' : '禁用' }}
                    </van-tag>
                    <span class="channel-name">{{ channel.name }}</span>
                  </div>
                </template>
                <template #label>
                  <div class="channel-label">
                    <span class="platform">{{ channel.platform }}</span>
                    <span class="model">{{ channel.model }}</span>
                    <span class="cred" :class="{ bad: !isChannelOk(channel) }">{{ credentialHint(channel) }}</span>
                  </div>
                </template>
                <template #value>
                  <div class="cell-controls" @click.stop>
                    <van-switch
                      size="20"
                      :model-value="channel.enabled"
                      @update:model-value="(val) => toggleEnabled(channel, val)"
                    />
                    <div class="sort-control">
                      <span class="sort-label">排序</span>
                      <van-stepper
                        :model-value="channel.sortOrder"
                        :min="0"
                        :max="999"
                        :integer="true"
                        @change="(val) => updateSortOrder(channel, val)"
                      />
                    </div>
                  </div>
                </template>
              </van-cell>
            </van-cell-group>
          </div>
        </van-popup>
      
      <van-popup v-model:show="showEdit" position="bottom" style="height: 90%">
        <div class="popup-content">
          <div class="popup-header">
            <span>{{ editingChannel.id ? '编辑渠道' : '添加渠道' }}</span>
            <van-icon name="close" @click="showEdit = false" />
          </div>
          
          <van-form @submit="saveChannel">
            <van-cell-group inset>
              <van-field
                v-model="editingChannel.name"
                name="name"
                label="渠道名称"
                placeholder="请输入渠道名称"
                :rules="[{ required: true, message: '请输入渠道名称' }]"
              />
              
              <van-field
                v-model="editingChannel.platform"
                name="platform"
                label="平台"
                placeholder="如: stability, volcengine, jimeng"
                :rules="[{ required: true, message: '请输入平台' }]"
              />
              
              <van-field
                v-model="editingChannel.model"
                name="model"
                label="模型"
                placeholder="如: sd3, img2img_cartoon_style"
                :rules="[{ required: true, message: '请输入模型' }]"
              />
              
              <van-field
                v-model="editingChannel.apiKey"
                name="apiKey"
                label="API Key"
                placeholder="请输入API Key"
              />
              
              <van-field
                v-model="editingChannel.apiSecret"
                name="apiSecret"
                label="API Secret"
                placeholder="请输入API Secret"
              />
              
              <van-field
                v-model="editingChannel.baseUrl"
                name="baseUrl"
                label="Base URL"
                placeholder="如: https://api.stability.ai"
              />
              
              <van-field
                name="enabled"
                label="启用状态"
              >
                <template #input>
                  <van-switch v-model="editingChannel.enabled" />
                </template>
              </van-field>
              
              <van-field
                v-model="editingChannel.description"
                name="description"
                label="描述"
                type="textarea"
                placeholder="请输入描述"
                rows="2"
              />
              
              <van-field
                v-model.number="editingChannel.sortOrder"
                name="sortOrder"
                label="排序"
                type="digit"
                placeholder="数字越小越靠前"
              />
            </van-cell-group>
            
            <div class="form-actions">
              <van-button v-if="editingChannel.id" type="danger" size="large" round @click="deleteChannel">
                删除
              </van-button>
              <van-button type="primary" size="large" round native-type="submit">
                保存
              </van-button>
            </div>
          </van-form>
        </div>
      </van-popup>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showFailToast, showDialog } from 'vant'
import { channelApi } from '@/api'

const router = useRouter()

const channels = ref([])
const showChannelList = ref(false)
const showEdit = ref(false)
const search = ref('')

const editingChannel = reactive({
  id: null,
  name: '',
  platform: '',
  model: '',
  apiKey: '',
  apiSecret: '',
  baseUrl: '',
  enabled: true,
  description: '',
  sortOrder: 0
})

const goBack = () => router.back()

const loadChannels = async () => {
  try {
    const res = await channelApi.getAll()
    if (res.success) {
      channels.value = res.data || []
    }
  } catch (error) {
    console.error(error)
  }
}

const requiresSecret = (platform) => ['volcengine', 'jimeng_i2i', 'jimeng_t2i'].includes(platform)

const isChannelOk = (ch) => {
  if (!ch) return false
  if (!ch.hasApiKey) return false
  if (requiresSecret(ch.platform) && !ch.hasApiSecret) return false
  return true
}

const credentialHint = (ch) => {
  if (!ch) return ''
  if (!ch.hasApiKey) return '缺少Key'
  if (requiresSecret(ch.platform) && !ch.hasApiSecret) return '缺少Secret'
  return '凭证OK'
}

const filteredChannels = computed(() => {
  const q = (search.value || '').trim().toLowerCase()
  if (!q) return channels.value
  return channels.value.filter(ch => (`${ch.name} ${ch.platform} ${ch.model}`).toLowerCase().includes(q))
})

const toggleEnabled = async (channel, val) => {
  try {
    await channelApi.update(channel.id, { enabled: !!val })
    channel.enabled = !!val
    showToast(val ? '已启用' : '已禁用')
  } catch (error) {
    showFailToast(error.message || '更新失败')
  }
}

const updateSortOrder = async (channel, val) => {
  try {
    const next = Number(val)
    await channelApi.update(channel.id, { sortOrder: next })
    channel.sortOrder = next
  } catch (error) {
    showFailToast(error.message || '更新失败')
  }
}

const resetForm = () => {
  editingChannel.id = null
  editingChannel.name = ''
  editingChannel.platform = ''
  editingChannel.model = ''
  editingChannel.apiKey = ''
  editingChannel.apiSecret = ''
  editingChannel.baseUrl = ''
  editingChannel.enabled = true
  editingChannel.description = ''
  editingChannel.sortOrder = 0
}

const openAdd = () => {
  resetForm()
  showEdit.value = true
}

const openEdit = (channel) => {
  editingChannel.id = channel.id
  editingChannel.name = channel.name
  editingChannel.platform = channel.platform
  editingChannel.model = channel.model
  editingChannel.baseUrl = channel.baseUrl || ''
  editingChannel.enabled = channel.enabled
  editingChannel.description = channel.description || ''
  editingChannel.sortOrder = channel.sortOrder
  editingChannel.apiKey = ''
  editingChannel.apiSecret = ''
  showChannelList.value = false
  showEdit.value = true
}

const saveChannel = async () => {
  try {
    const data = {
      name: editingChannel.name,
      platform: editingChannel.platform,
      model: editingChannel.model,
      apiKey: editingChannel.apiKey || null,
      apiSecret: editingChannel.apiSecret || null,
      baseUrl: editingChannel.baseUrl || null,
      enabled: editingChannel.enabled,
      description: editingChannel.description || null,
      sortOrder: editingChannel.sortOrder
    }
    
    if (editingChannel.id) {
      await channelApi.update(editingChannel.id, data)
      showToast('更新成功')
    } else {
      await channelApi.create(data)
      showToast('添加成功')
    }
    
    showEdit.value = false
    await loadChannels()
  } catch (error) {
    showFailToast(error.message || '操作失败')
  }
}

const deleteChannel = async () => {
  try {
    await showDialog({
      title: '确认删除',
      message: '确定要删除这个渠道吗？'
    })
    
    await channelApi.delete(editingChannel.id)
    showToast('删除成功')
    showEdit.value = false
    await loadChannels()
  } catch (error) {
    if (error.message !== 'cancel') {
      showFailToast(error.message || '删除失败')
    }
  }
}

onMounted(() => {
  loadChannels()
})
</script>

<style scoped>
.channel-page {
  min-height: 100vh;
  background: #f5f5f5;
}

.content {
  padding: 46px 0 50px 0;
}

.add-btn {
  padding: 16px;
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

.list-tools {
  padding: 8px 12px 0;
}

.channel-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.channel-name {
  font-weight: 500;
}

.channel-label {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.channel-label .platform {
  font-size: 12px;
  color: #646566;
}

.channel-label .model {
  font-size: 12px;
  color: #1989fa;
}

.channel-label .cred {
  font-size: 12px;
  color: #07c160;
}

.channel-label .cred.bad {
  color: #ee0a24;
}

.cell-controls {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.sort-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sort-label {
  font-size: 12px;
  color: #969799;
}

.form-actions {
  padding: 16px;
  display: flex;
  gap: 12px;
}

.form-actions .van-button {
  flex: 1;
}
</style>
