<template>
  <a-config-provider :locale="zhCN" :theme="themeConfig">
    <div id="app">
      <router-view />
    </div>
  </a-config-provider>
</template>

<script setup>
import zhCN from 'ant-design-vue/es/locale/zh_CN'

const themeConfig = {
  token: {
    colorPrimary: '#1677ff',
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorError: '#ff4d4f',
    colorInfo: '#1677ff',
    colorBgBase: '#ffffff',
    colorTextBase: '#000000',
    fontSize: 14,
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
    borderRadius: 6,
    borderRadiusSM: 4,
    borderRadiusLG: 8,
    borderRadiusXS: 2,
    wireframe: false,
    controlHeight: 32,
    controlHeightSM: 24,
    controlHeightLG: 40,
    sizeStep: 4,
    sizeUnit: 4,
    colorLink: '#1677ff',
    colorLinkHover: '#69b1ff',
    colorLinkActive: '#0958d9'
  },
  components: {
    Layout: {
      siderBg: '#001529',
      headerBg: '#ffffff',
      bodyBg: '#f5f5f5',
      headerHeight: 64,
      headerPadding: '0 24px',
      headerColor: 'rgba(0, 0, 0, 0.88)'
    },
    Menu: {
      darkItemBg: '#001529',
      darkSubMenuItemBg: '#000c17',
      darkPopupBg: '#001529',
      darkGroupTitleColor: 'rgba(255, 255, 255, 0.45)',
      darkItemColor: 'rgba(255, 255, 255, 0.65)',
      darkItemHoverBg: 'transparent',
      darkItemHoverColor: '#ffffff',
      darkItemSelectedBg: '#1677ff',
      darkItemSelectedColor: '#ffffff',
      itemBg: '#ffffff',
      itemColor: 'rgba(0, 0, 0, 0.88)',
      itemHoverBg: '#f5f5f5',
      itemHoverColor: '#1677ff',
      itemSelectedBg: '#e6f4ff',
      itemSelectedColor: '#1677ff',
      itemActiveBg: '#e6f4ff',
      groupTitleColor: 'rgba(0, 0, 0, 0.45)',
      groupTitleFontSize: 14,
      itemBorderRadius: 6,
      subMenuItemBorderRadius: 6,
      iconSize: 14,
      collapsedIconSize: 16,
      collapsedWidth: 80,
      itemHeight: 40,
      itemMarginInline: 4,
      itemMarginBlock: 4,
      popupBg: '#ffffff'
    }
  }
}
</script>

<style>
html,
body,
#app {
  min-height: 100%;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 14px;
  line-height: 1.5715;
  background-color: #f5f5f5;
  color: rgba(0, 0, 0, 0.88);
  -webkit-text-size-adjust: 100%;
  font-synthesis: none;
}

#app {
  min-height: 100vh;
}

/* ========== 统一的全局搜索框（Toolbar）样式优化 ========== */
.page .toolbar {
  border-radius: 12px !important;
  background-color: #fff !important;
  padding: 20px 24px !important;
  margin-bottom: 16px !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04) !important;
  border: 1px solid rgba(0, 0, 0, 0.02) !important;
}

.page .toolbar.ant-card .ant-card-body {
  padding: 0 !important;
}

/* ShopTabs 分类标签若在搜索框上方，预留间距 */
.page .toolbar .shop-tabs-wrap + * {
  margin-top: 20px !important;
}

/* 核心优化：让行内表单自动换行并对齐，彻底解决挤在一行或高度不齐的问题 */
.page .toolbar .ant-form-inline {
  display: flex !important;
  flex-wrap: wrap !important;
  column-gap: 24px !important;
  row-gap: 16px !important;
  align-items: center !important; 
}

/* 剥离组件自带的外边距，统一由外层的 gap 管控间距 */
.page .toolbar .ant-form-inline .ant-form-item {
  margin: 0 !important;
  display: flex !important;
}

/* 文本标签垂直居中 */
.page .toolbar .ant-form-item-label {
  display: flex !important;
  align-items: center !important;
  padding: 0 10px 0 0 !important;
  line-height: inherit !important;
}

/* 美化区间筛选的悬浮“-”号 */
.page .toolbar .range-sep {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  padding: 0 8px !important;
  color: #94a3b8 !important;
  font-weight: bold !important;
}

/* 优化搜索和操作按钮的排列 */
.page .toolbar .ant-space {
  gap: 12px !important;
}
</style>
