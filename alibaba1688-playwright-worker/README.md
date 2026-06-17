# alibaba1688-playwright-worker

本地 Playwright worker，负责两件事：

1. 打开真实浏览器，让你手动登录 1688，并把 `storageState` 保存到后台的 `1688凭证管理`
2. 轮询后台的 `1688详情采集任务`，打开详情页并把 HTML / 提取结果回传到 `1688详情数据`

## 安装

```bash
cd /Users/a1/Desktop/TEMU上品系统-新仓库/alibaba1688-playwright-worker
npm install
```

## 登录保存 1688 凭证

推荐用 worker 专用 key，不需要后台账号密码：

```bash
npm run login -- \
  --base-url http://chenqi.tminos.com:20080 \
  --worker-api-key Han123... \
  --credential-id 1 \
  --use-installed-market-mate true \
  --headless false
```

老的后台登录方式仍然保留：

```bash
npm run login -- \
  --base-url http://localhost:8080 \
  --username admin \
  --password admin778899 \
  --session-name "1688主账号" \
  --use-installed-market-mate true \
  --headless false
```

可选参数：

- `--credential-id 1`：回写到已有凭证 ID
- `--remark "采购A使用"`：保存备注
- `--channel chrome`：默认使用本机 Chrome；传 `chromium` 则使用 Playwright 自带浏览器
- `--use-installed-market-mate true`：自动加载你本机 Chrome 已安装的 `1688采购助手`
- `--extension-path /path/to/extension`：手动指定扩展目录
- `--user-data-dir ./.runtime/credential-1`：指定持久化浏览器目录
- `--executable-path /Applications/Google Chrome.app/Contents/MacOS/Google Chrome`：手动指定浏览器可执行文件
- `--prefer-local-chrome false`：禁用“优先使用真实本机 Chrome”，回退到 Playwright 默认浏览器
- `--disable-stealth true`：关闭基础自动化指纹压低逻辑

## 运行采集 worker

推荐用 worker 专用 key：

```bash
npm run run -- \
  --base-url http://chenqi.tminos.com:20080 \
  --worker-api-key Han123... \
  --credential-id 1 \
  --worker-name 1688-worker-A \
  --use-installed-market-mate true \
  --user-data-dir ./.runtime/credential-1 \
  --headless false
```

查看可用凭证：

```bash
npm run credentials -- \
  --base-url http://chenqi.tminos.com:20080 \
  --worker-api-key Han123...
```

老的后台登录方式仍然保留：

```bash
npm run run -- \
  --base-url http://localhost:8080 \
  --username admin \
  --password admin778899 \
  --credential-id 1 \
  --worker-name 1688-worker-A \
  --use-installed-market-mate true \
  --user-data-dir ./.runtime/credential-1 \
  --headless false
```

可选参数：

- `--interval-ms 60000`：每次领取任务之间的等待时间；有任务时会等当前任务处理完成并等待该间隔后才领取下一条
- `--settle-delay-ms 1200`：详情页基础等待时间
- `--networkidle-timeout-ms 8000`：等待页面网络空闲的最长时间，传 `0` 可跳过
- `--scroll-delay-ms 160`：滚动加载时每步等待时间
- `--scroll-final-delay-ms 1200`：滚动到底后等待懒加载内容的时间
- `--extension-insights-timeout-ms 12000`：等待 1688采购助手数据出现的最长时间
- `--extension-insights-poll-ms 800`：检测采购助手数据的间隔
- `--once true`：只领取一次任务，没有任务就退出
- `--max-tasks 10`：最多处理多少条后退出
- `--channel chrome`：默认 `chrome`
- `--extension-path /path/to/extension`：手动指定扩展目录
- `--executable-path /Applications/Google Chrome.app/Contents/MacOS/Google Chrome`：手动指定浏览器可执行文件
- `--prefer-local-chrome false`：禁用“优先使用真实本机 Chrome”，回退到 Playwright 默认浏览器
- `--disable-stealth true`：关闭基础自动化指纹压低逻辑

## 建议

- 首次登录时用 `--headless false`
- 如果要拿 `1688采购助手` 的 `上架时间` 等数据，建议开启 `--use-installed-market-mate true`
- 当前实测 `headless=true + 1688采购助手` 时，1688 很容易直接跳到登录页；采详情推荐继续使用 `--headless false`
- 一个凭证建议先只跑一个 worker，避免账号风控和重复操作
- 当前版本会默认优先使用你机器上的真实 `Google Chrome.app`，并移除 `--enable-automation` 等高风险默认痕迹；如果本机 Chrome 启动失败，才会自动回退到 Playwright Chromium
