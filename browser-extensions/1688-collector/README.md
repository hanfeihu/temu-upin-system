# 1688 页面HTML采集插件（右侧浮窗一键采集上传）

这个浏览器扩展会在 1688 页面右侧显示一个浮动面板，点击按钮即可把**当前页面的 HTML 采集并发送到你的接口**，并且会自动移除：

- `<script>`
- `<style>`
- `<noscript>`

这样得到的 HTML 更小、更适合你后续做内容解析（例如提取 1688 商品价格、标题等）。

## 接口说明

- dev `baseUrl`：`http://127.0.0.1:8080`
- prod `baseUrl`：`https://www.tminos.com`

请求：

- `POST /tminos/api/pull/html/import`
- `Content-Type: text/plain`
- 请求体：HTML 文本（已移除 `<script>/<style>/<noscript>`）

## 安装（Chrome / Edge）

1. 打开扩展管理页：
   - Chrome：`chrome://extensions/`
   - Edge：`edge://extensions/`
2. 打开右上角的「开发者模式」
3. 点击「加载已解压的扩展程序」
4. 选择本项目的扩展目录：

   `browser-extensions/1688-collector/`

安装成功后，打开 1688 页面右侧就会出现浮窗。

## 使用

- 点击「采集并上传HTML」：把净化后的 HTML 以 `text/plain` 发送到接口
- 点击「复制净化HTML到剪贴板」：把净化后的 HTML 复制到剪贴板

浮窗里有「切换环境（dev/prod）」按钮：会把当前环境保存到 `chrome.storage.local`，下次打开页面仍然生效。

文件名包含域名、路径和时间戳，便于批量采集。

## 修改 dev/prod 地址（可选）

默认地址在 [config.js](/Users/a1/Desktop/TEMU上品系统-新仓库/browser-extensions/1688-collector/config.js) 里：

- `TMINOS_COLLECTOR_DEFAULTS.env`
- `TMINOS_COLLECTOR_DEFAULTS.baseUrls`

## 说明与限制

- 下载内容来自当前页面 DOM（即你看到的页面结构）；若页面数据由 JS 动态渲染，通常也会包含在下载结果里。
- 不会自动点击分页/滚动加载；如果页面需要下拉加载更多内容，请先在页面里加载完成再点击采集。

## 来源目录

当前仓库中的这个插件目录直接同步自本机源目录：

`/Users/a1/Nextcloud/1688 采集插件/extension`





