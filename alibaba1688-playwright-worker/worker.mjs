import { chromium } from 'playwright';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createInterface } from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';
import {
  claimNextTask,
  getWorkerStorageState,
  listWorkerCredentials,
  loginAdmin,
  reportTaskFailure,
  reportTaskSuccess,
  saveWorkerSession,
  updateWorkerSessionStatus,
} from './lib/backend-client.mjs';
import {
  dismissPurchaseAssistantWelcome,
  detectAuthExpired,
  extractAccountMeta,
  extractStructuredData,
  scrollPageForLazyContent,
  serializePageWithShadowDom,
  waitForExtensionInsights,
  waitForSettledPage,
} from './lib/page-extractors.mjs';

const DEFAULT_VIEWPORT = { width: 1440, height: 900 };
const DEFAULT_LOCALE = 'zh-CN';
const MARKET_MATE_EXTENSION_ID = 'kphldkppgfpjadpabfkghmjbhpcmgpdg';

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i += 1) {
    const item = argv[i];
    if (!item.startsWith('--')) continue;
    const [rawKey, inlineValue] = item.slice(2).split('=');
    if (inlineValue !== undefined) {
      out[rawKey] = inlineValue;
      continue;
    }
    const next = argv[i + 1];
    if (!next || next.startsWith('--')) {
      out[rawKey] = 'true';
      continue;
    }
    out[rawKey] = next;
    i += 1;
  }
  return out;
}

function toBoolean(value, fallback = false) {
  if (value === undefined || value === null || value === '') {
    return fallback;
  }
  return ['1', 'true', 'yes', 'y', 'on'].includes(String(value).toLowerCase());
}

function toNumber(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function sanitizePathSegment(value, fallback = 'default') {
  return String(value || fallback)
    .trim()
    .replace(/[^\w.-]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
    || fallback;
}

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
  return dirPath;
}

function resolveInstalledMarketMateExtensionPath() {
  const profileRoots = [
    path.join(os.homedir(), 'Library/Application Support/Google/Chrome/Default/Extensions', MARKET_MATE_EXTENSION_ID),
    path.join(os.homedir(), 'Library/Application Support/Google/Chrome/Profile 1/Extensions', MARKET_MATE_EXTENSION_ID),
    path.join(os.homedir(), 'Library/Application Support/Google/Chrome/Profile 2/Extensions', MARKET_MATE_EXTENSION_ID),
  ];

  for (const root of profileRoots) {
    if (!fs.existsSync(root)) {
      continue;
    }

    const versions = fs.readdirSync(root, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name)
      .sort((a, b) => b.localeCompare(a, undefined, { numeric: true, sensitivity: 'base' }));

    if (versions.length > 0) {
      return path.join(root, versions[0]);
    }
  }

  return null;
}

function resolveExtensionPath(args) {
  const manualPath = String(args['extension-path'] || '').trim();
  if (manualPath) {
    return manualPath;
  }

  if (!toBoolean(args['use-installed-market-mate'], false)) {
    return null;
  }

  return resolveInstalledMarketMateExtensionPath();
}

function shouldEnableStealth(args) {
  return !toBoolean(args['disable-stealth'], false);
}

function shouldUsePersistentContext(args, extensionPath) {
  return Boolean(extensionPath || args['user-data-dir']);
}

function resolveLocalChromeExecutablePath() {
  const candidates = [
    process.env.GOOGLE_CHROME_BIN,
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary',
    '/Applications/Chromium.app/Contents/MacOS/Chromium',
  ]
    .map((item) => String(item || '').trim())
    .filter(Boolean);

  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }

  return null;
}

function resolveExecutablePath(args) {
  const manualPath = String(args['executable-path'] || '').trim();
  if (manualPath) {
    return path.resolve(manualPath);
  }

  if (!toBoolean(args['prefer-local-chrome'], true)) {
    return null;
  }

  return resolveLocalChromeExecutablePath();
}

function resolveLaunchChannel(args, { extensionPath = null, executablePath = null } = {}) {
  if (executablePath) {
    return undefined;
  }

  const rawChannel = String(args.channel || (extensionPath ? 'chromium' : 'chrome')).trim();
  if (extensionPath && rawChannel !== 'chromium') {
    console.log('检测到扩展模式，自动切换到 chromium channel。');
    return 'chromium';
  }
  return rawChannel || undefined;
}

function resolveTimezoneId(args) {
  const manualTimezone = String(args['timezone-id'] || '').trim();
  if (manualTimezone) {
    return manualTimezone;
  }
  return Intl.DateTimeFormat().resolvedOptions().timeZone || undefined;
}

function buildContextOptions(args) {
  const timezoneId = resolveTimezoneId(args);
  const options = {
    viewport: DEFAULT_VIEWPORT,
    locale: String(args.locale || DEFAULT_LOCALE).trim() || DEFAULT_LOCALE,
  };

  if (timezoneId) {
    options.timezoneId = timezoneId;
  }

  return options;
}

function buildLaunchArgs(extensionPath, args) {
  const launchArgs = [];
  if (extensionPath) {
    launchArgs.push(
      `--disable-extensions-except=${extensionPath}`,
      `--load-extension=${extensionPath}`,
    );
  }

  if (shouldEnableStealth(args)) {
    launchArgs.push('--disable-blink-features=AutomationControlled');
  }

  const locale = String(args.locale || DEFAULT_LOCALE).trim();
  if (locale) {
    launchArgs.push(`--lang=${locale}`);
  }

  return launchArgs;
}

function buildIgnoreDefaultArgs(args) {
  if (!shouldEnableStealth(args)) {
    return undefined;
  }

  return ['--enable-automation'];
}

function resolvePersistentUserDataDir(args, nameHint) {
  const manualDir = String(args['user-data-dir'] || '').trim();
  if (manualDir) {
    return ensureDir(path.resolve(manualDir));
  }

  return ensureDir(path.resolve(process.cwd(), '.runtime', sanitizePathSegment(nameHint)));
}

function parseStorageState(storageStateJson) {
  try {
    const parsed = JSON.parse(storageStateJson || '{}');
    return {
      cookies: Array.isArray(parsed.cookies) ? parsed.cookies : [],
      origins: Array.isArray(parsed.origins) ? parsed.origins : [],
    };
  } catch {
    return { cookies: [], origins: [] };
  }
}

async function primePersistentContext(context, storageState) {
  if (Array.isArray(storageState?.cookies) && storageState.cookies.length > 0) {
    await context.addCookies(storageState.cookies).catch(() => {});
  }

  const localStorageByOrigin = Object.fromEntries(
    (Array.isArray(storageState?.origins) ? storageState.origins : [])
      .filter((item) => item?.origin && Array.isArray(item.localStorage) && item.localStorage.length > 0)
      .map((item) => [item.origin, item.localStorage]),
  );

  if (Object.keys(localStorageByOrigin).length === 0) {
    return;
  }

  await context.addInitScript(({ seededLocalStorageByOrigin }) => {
    try {
      const entries = seededLocalStorageByOrigin?.[location.origin];
      if (!Array.isArray(entries)) {
        return;
      }

      for (const entry of entries) {
        if (!entry?.name) continue;
        localStorage.setItem(entry.name, String(entry.value ?? ''));
      }
    } catch {
      // ignore storage seeding failure
    }
  }, {
    seededLocalStorageByOrigin: localStorageByOrigin,
  }).catch(() => {});
}

async function installStealthInitScripts(context, args) {
  if (!shouldEnableStealth(args)) {
    return;
  }

  await context.addInitScript(() => {
    try {
      Object.defineProperty(Navigator.prototype, 'webdriver', {
        configurable: true,
        get: () => undefined,
      });
    } catch {
      // ignore webdriver patch failure
    }

    try {
      if (!window.chrome) {
        Object.defineProperty(window, 'chrome', {
          configurable: true,
          value: { runtime: {} },
        });
      } else if (!window.chrome.runtime) {
        window.chrome.runtime = {};
      }
    } catch {
      // ignore chrome patch failure
    }

    try {
      const permissions = navigator.permissions;
      if (permissions && typeof permissions.query === 'function') {
        const originalQuery = permissions.query.bind(permissions);
        permissions.query = async (parameters) => {
          if (parameters?.name === 'notifications') {
            return {
              state: Notification.permission,
              onchange: null,
              addEventListener() {},
              removeEventListener() {},
              dispatchEvent() { return false; },
            };
          }
          return originalQuery(parameters);
        };
      }
    } catch {
      // ignore permissions patch failure
    }
  }).catch(() => {});
}

async function waitForEnter(promptText) {
  const rl = createInterface({ input, output });
  try {
    await rl.question(promptText);
  } finally {
    rl.close();
  }
}

async function launchBrowser(args) {
  const executablePath = resolveExecutablePath(args);
  const channel = resolveLaunchChannel(args, { executablePath });
  const ignoreDefaultArgs = buildIgnoreDefaultArgs(args);
  const launchOptions = {
    headless: toBoolean(args.headless, false),
    channel,
    executablePath,
    ignoreDefaultArgs,
    args: buildLaunchArgs(null, args),
  };

  Object.keys(launchOptions).forEach((key) => {
    if (launchOptions[key] === undefined || (Array.isArray(launchOptions[key]) && launchOptions[key].length === 0)) {
      delete launchOptions[key];
    }
  });

  return chromium.launch(launchOptions);
}

async function launchPersistentContext(args, {
  extensionPath = null,
  nameHint = 'persistent-session',
} = {}) {
  const userDataDir = resolvePersistentUserDataDir(args, nameHint);
  const contextOptions = buildContextOptions(args);
  const executablePath = resolveExecutablePath(args);
  const channel = resolveLaunchChannel(args, { extensionPath, executablePath });
  const ignoreDefaultArgs = buildIgnoreDefaultArgs(args);

  if (extensionPath && toBoolean(args.headless, false)) {
    console.warn('当前已启用 1688 采购助手扩展；实测 headless=true 时 1688 很容易跳到登录页，建议使用 --headless false。');
  }

  const launchOptions = {
    ...contextOptions,
    headless: toBoolean(args.headless, false),
    channel,
    executablePath,
    ignoreDefaultArgs,
    args: buildLaunchArgs(extensionPath, args),
  };

  Object.keys(launchOptions).forEach((key) => {
    if (launchOptions[key] === undefined || (Array.isArray(launchOptions[key]) && launchOptions[key].length === 0)) {
      delete launchOptions[key];
    }
  });

  try {
    const context = await chromium.launchPersistentContext(userDataDir, launchOptions);
    await installStealthInitScripts(context, args);
    return {
      context,
      userDataDir,
      channel,
      extensionPath,
      executablePath,
      usedFallback: false,
    };
  } catch (error) {
    if (!executablePath) {
      throw error;
    }

    console.warn(`使用本机 Chrome 启动失败，回退到 Playwright Chromium：${error instanceof Error ? error.message : String(error)}`);
    const fallbackChannel = resolveLaunchChannel(args, { extensionPath, executablePath: null });
    const fallbackOptions = {
      ...contextOptions,
      headless: toBoolean(args.headless, false),
      channel: fallbackChannel,
      ignoreDefaultArgs,
      args: buildLaunchArgs(extensionPath, args),
    };

    Object.keys(fallbackOptions).forEach((key) => {
      if (fallbackOptions[key] === undefined || (Array.isArray(fallbackOptions[key]) && fallbackOptions[key].length === 0)) {
        delete fallbackOptions[key];
      }
    });

    const context = await chromium.launchPersistentContext(userDataDir, fallbackOptions);
    await installStealthInitScripts(context, args);
    return {
      context,
      userDataDir,
      channel: fallbackChannel,
      extensionPath,
      executablePath: null,
      usedFallback: true,
    };
  }
}

async function createLoginSession(args, { credentialId, sessionName }) {
  const extensionPath = resolveExtensionPath(args);
  if (shouldUsePersistentContext(args, extensionPath)) {
    const hint = credentialId ? `credential-${credentialId}` : `login-${sessionName || 'session'}`;
    const runtime = await launchPersistentContext(args, {
      extensionPath,
      nameHint: hint,
    });
    return {
      mode: 'persistent',
      browser: null,
      context: runtime.context,
      extensionPath,
      userDataDir: runtime.userDataDir,
      executablePath: runtime.executablePath,
      usedFallback: runtime.usedFallback,
    };
  }

  const browser = await launchBrowser(args);
  const context = await browser.newContext({
    ...buildContextOptions(args),
  });
  await installStealthInitScripts(context, args);
  return {
    mode: 'ephemeral',
    browser,
    context,
    extensionPath,
    userDataDir: null,
  };
}

async function createWorkerRuntime({ args, baseUrl, token, credentialId }) {
  const extensionPath = resolveExtensionPath(args);

  if (shouldUsePersistentContext(args, extensionPath)) {
    const storage = await getWorkerStorageState(baseUrl, token, credentialId);
    const storageState = parseStorageState(storage?.storageStateJson);
    const runtime = await launchPersistentContext(args, {
      extensionPath,
      nameHint: `credential-${credentialId}`,
    });

    await primePersistentContext(runtime.context, storageState);
    if (extensionPath) {
      console.log(`已启用 1688采购助手扩展: ${extensionPath}`);
    }
    if (runtime.executablePath) {
      console.log(`已优先使用本机 Chrome: ${runtime.executablePath}`);
    } else if (runtime.usedFallback) {
      console.log('当前已回退到 Playwright Chromium。');
    }

    return {
      mode: 'persistent',
      browser: null,
      context: runtime.context,
      extensionPath,
      userDataDir: runtime.userDataDir,
      executablePath: runtime.executablePath,
      usedFallback: runtime.usedFallback,
    };
  }

  return {
    mode: 'ephemeral',
    browser: await launchBrowser(args),
    context: null,
    extensionPath: null,
    userDataDir: null,
  };
}

async function closeRuntime(runtime) {
  if (runtime?.mode === 'persistent') {
    await runtime.context?.close?.().catch(() => {});
    return;
  }

  await runtime?.browser?.close?.().catch(() => {});
}

async function commandLogin(args) {
  const baseUrl = args['base-url'];
  const username = args.username;
  const password = args.password;
  const workerApiKey = args['worker-api-key'];
  const sessionName = args['session-name'];
  const credentialId = args['credential-id'] ? Number(args['credential-id']) : undefined;
  const remark = args.remark;

  if (!sessionName && !credentialId) {
    throw new Error('Missing --session-name or --credential-id');
  }

  let token = workerApiKey ? { workerApiKey } : null;
  if (!token) {
    token = await loginAdmin(baseUrl, username, password);
    if (!token) {
      throw new Error('后台登录失败，未拿到 accessToken');
    }
  }

  const loginSession = await createLoginSession(args, { credentialId, sessionName });

  try {
    const context = loginSession.context;
    const page = context.pages()[0] || await context.newPage();
    await page.goto('https://login.1688.com/member/signin.htm', {
      waitUntil: 'domcontentloaded',
      timeout: 60000,
    });

    console.log('\n1688 登录页已打开。');
    if (loginSession.extensionPath) {
      console.log(`已加载 1688采购助手扩展，userDataDir=${loginSession.userDataDir}`);
    }
    if (loginSession.userDataDir) {
      console.log('当前优先使用真实本机 Chrome 启动，以尽量减少 Playwright 默认浏览器指纹。');
    }
    if (loginSession.executablePath) {
      console.log(`本次登录使用的本机 Chrome: ${loginSession.executablePath}`);
    } else if (loginSession.usedFallback) {
      console.log('本次登录已回退到 Playwright Chromium。');
    }
    console.log('请在浏览器中完成登录、二次验证，确认已经进入 1688 页面后，回到终端按回车继续保存。\n');
    await waitForEnter('登录完成后按回车保存会话 ... ');

    await page.goto('https://www.1688.com/', {
      waitUntil: 'domcontentloaded',
      timeout: 60000,
    }).catch(() => {});
    await waitForSettledPage(page);

    const storageState = await context.storageState();
    const accountMeta = await extractAccountMeta(page);

    const saved = await saveWorkerSession(baseUrl, token, {
      id: credentialId,
      sessionName,
      accountNick: accountMeta.accountNick,
      memberId: accountMeta.memberId,
      homeUrl: accountMeta.homeUrl,
      remark,
      storageStateJson: JSON.stringify(storageState),
    });

    console.log(`\n1688 登录态已保存到后台。credentialId=${saved?.id}, sessionName=${saved?.sessionName}\n`);
  } finally {
    await closeRuntime(loginSession);
  }
}

function resolvePageWaitOptions(args, timeoutMs) {
  return {
    settle: {
      settleDelayMs: toNumber(args['settle-delay-ms'], 1200),
      networkidleTimeoutMs: toNumber(args['networkidle-timeout-ms'], 8000),
    },
    scroll: {
      scrollDelayMs: toNumber(args['scroll-delay-ms'], 160),
      finalDelayMs: toNumber(args['scroll-final-delay-ms'], 1200),
    },
    extensionInsightsTimeoutMs: toNumber(args['extension-insights-timeout-ms'], Math.min(timeoutMs, 12000)),
    extensionInsightsPollMs: toNumber(args['extension-insights-poll-ms'], 800),
  };
}

async function processSingleTask({ baseUrl, token, runtime, credentialId, workerName, timeoutMs, pageWaitOptions }) {
  const claim = await claimNextTask(baseUrl, token, {
    credentialId,
    workerName,
  });

  if (!claim?.claimed || !claim?.task) {
    return { handled: false };
  }

  const task = claim.task;
  console.log(`[task:${task.id}] claimed offerId=${task.offerId}`);
  let context = null;
  let page = null;

  try {
    if (runtime.mode === 'persistent') {
      context = runtime.context;
      page = await context.newPage();
    } else {
      const storage = await getWorkerStorageState(baseUrl, token, credentialId);
      context = await runtime.browser.newContext({
        storageState: JSON.parse(storage.storageStateJson),
        ...buildContextOptions(args),
      });
      await installStealthInitScripts(context, args);
      page = await context.newPage();
    }

    await page.goto(task.detailUrl, {
      waitUntil: 'domcontentloaded',
      timeout: timeoutMs,
    });
    await waitForSettledPage(page, pageWaitOptions.settle);

    if (await detectAuthExpired(page)) {
      throw Object.assign(new Error('1688 登录态失效，页面跳转到登录/验证页'), { authExpired: true });
    }

    if (runtime.extensionPath) {
      await dismissPurchaseAssistantWelcome(page);
      await waitForSettledPage(page, pageWaitOptions.settle);
    }

    await scrollPageForLazyContent(page, pageWaitOptions.scroll);
    await waitForSettledPage(page, pageWaitOptions.settle);
    if (runtime.extensionPath) {
      await dismissPurchaseAssistantWelcome(page);
      await waitForSettledPage(page, pageWaitOptions.settle);
      await waitForExtensionInsights(
        page,
        Math.min(timeoutMs, pageWaitOptions.extensionInsightsTimeoutMs),
        pageWaitOptions.extensionInsightsPollMs,
      );
    }

    if (await detectAuthExpired(page)) {
      throw Object.assign(new Error('1688 登录态失效，页面跳转到登录/验证页'), { authExpired: true });
    }

    const [html, extracted, pageTitle] = await Promise.all([
      serializePageWithShadowDom(page),
      extractStructuredData(page),
      page.title().catch(() => ''),
    ]);

    await reportTaskSuccess(baseUrl, token, task.id, {
      claimToken: task.claimToken,
      finalUrl: page.url(),
      pageTitle,
      html,
      extractedJson: JSON.stringify(extracted),
    });

    await updateWorkerSessionStatus(baseUrl, token, credentialId, {
      status: 'ACTIVE',
      lastError: null,
    }).catch(() => {});

    console.log(`[task:${task.id}] success`);
    return { handled: true };
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);
    const authExpired = Boolean(error?.authExpired) || (page ? (await detectAuthExpired(page).catch(() => false)) : false);
    await reportTaskFailure(baseUrl, token, task.id, {
      claimToken: task.claimToken,
      errorMessage,
      currentUrl: page?.url?.() || '',
      authExpired,
    }).catch((reportError) => {
      console.error(`[task:${task.id}] failed to report failure`, reportError);
    });

    if (authExpired) {
      await updateWorkerSessionStatus(baseUrl, token, credentialId, {
        status: 'EXPIRED',
        lastError: errorMessage,
      }).catch(() => {});
    }

    console.error(`[task:${task.id}] failed: ${errorMessage}`);
    return { handled: true, failed: true, authExpired };
  } finally {
    if (runtime.mode === 'persistent') {
      await page?.close?.().catch(() => {});
    } else {
      await context?.close?.().catch(() => {});
    }
  }
}

async function commandRun(args) {
  const baseUrl = args['base-url'];
  const username = args.username;
  const password = args.password;
  const workerApiKey = args['worker-api-key'];
  const credentialId = Number(args['credential-id']);
  const workerName = args['worker-name'] || `1688-worker-${process.pid}`;
  const intervalMs = toNumber(args['interval-ms'], 60000);
  const maxTasks = toNumber(args['max-tasks'], 0);
  const once = toBoolean(args.once, false);
  const timeoutMs = toNumber(args['timeout-ms'], 60000);
  const pageWaitOptions = resolvePageWaitOptions(args, timeoutMs);

  if (!credentialId) {
    throw new Error('Missing --credential-id');
  }

  let token = workerApiKey ? { workerApiKey } : null;
  if (!token) {
    token = await loginAdmin(baseUrl, username, password);
    if (!token) {
      throw new Error('后台登录失败，未拿到 accessToken');
    }
  }

  const runtime = await createWorkerRuntime({
    args,
    baseUrl,
    token,
    credentialId,
  });
  let processed = 0;

  try {
    while (true) {
      const result = await processSingleTask({
        baseUrl,
        token,
        runtime,
        credentialId,
        workerName,
        timeoutMs,
        pageWaitOptions,
      });

      if (result.handled) {
        processed += 1;
      }

      if (result.authExpired) {
        console.error('检测到 1688 登录态失效，worker 停止。请重新执行 login 命令保存一次登录态。');
        break;
      }

      if (maxTasks > 0 && processed >= maxTasks) {
        console.log(`已达到 maxTasks=${maxTasks}，worker 退出。`);
        break;
      }

      if (!result.handled && once) {
        console.log('当前没有待处理任务，worker 退出。');
        break;
      }

      if (!once) {
        await sleep(intervalMs);
      }
    }
  } finally {
    await closeRuntime(runtime);
  }
}

async function main() {
  const [command, ...rest] = process.argv.slice(2);
  const args = parseArgs(rest);

  if (!command || ['login', 'run', 'credentials'].includes(command) === false) {
    console.log('Usage: node worker.mjs <login|run|credentials> --base-url ... (--worker-api-key ... 或 --username ... --password ...)');
    process.exit(1);
  }

  if (!args['base-url']) {
    throw new Error('Missing required arg: --base-url');
  }
  if (!args['worker-api-key'] && (!args.username || !args.password)) {
    throw new Error('Missing auth args: use --worker-api-key or --username --password');
  }

  if (command === 'credentials') {
    const rows = await listWorkerCredentials(args['base-url'], args['worker-api-key']);
    for (const item of rows) {
      console.log(`${item.id}\t${item.sessionName || ''}\t${item.accountNick || ''}\t${item.status || ''}`);
    }
    return;
  }

  if (command === 'login') {
    await commandLogin(args);
    return;
  }

  await commandRun(args);
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
