async function sendHttpRequest({ url, method = 'GET', token, body }) {
  const headers = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json;charset=utf-8'
  }

  const response = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })

  const bodyText = await response.text().catch(() => '')
  return {
    ok: response.ok,
    status: response.status,
    statusText: response.statusText,
    bodyText
  }
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (!message?.type) return

  ;(async () => {
    if (message.type === 'TMINOS_AUTH_LOGIN') {
      const { url, username, password } = message
      if (typeof url !== 'string' || !url) {
        sendResponse({ ok: false, error: 'Missing url' })
        return
      }
      sendResponse(await sendHttpRequest({
        url,
        method: 'POST',
        body: { username, password }
      }))
      return
    }

    if (message.type === 'TMINOS_FETCH_SHOPS') {
      const { url, token } = message
      if (typeof url !== 'string' || !url) {
        sendResponse({ ok: false, error: 'Missing url' })
        return
      }
      sendResponse(await sendHttpRequest({
        url,
        method: 'GET',
        token
      }))
      return
    }

    if (message.type === 'TMINOS_IMPORT_HTML') {
      const { url, html, extractedJson, token, targetShopIds } = message
      if (typeof url !== 'string' || !url) {
        sendResponse({ ok: false, error: 'Missing url' })
        return
      }
      if (typeof html !== 'string') {
        sendResponse({ ok: false, error: 'Missing html' })
        return
      }
      if (typeof extractedJson !== 'string') {
        sendResponse({ ok: false, error: 'Missing extractedJson' })
        return
      }

      sendResponse(await sendHttpRequest({
        url,
        method: 'POST',
        token,
        body: {
          html,
          extractedJson,
          targetShopIds: Array.isArray(targetShopIds) ? targetShopIds : []
        }
      }))
      return
    }

    if (message.type === 'TMINOS_IMPORT_CARD_LINKS') {
      const { url, token, items } = message
      if (typeof url !== 'string' || !url) {
        sendResponse({ ok: false, error: 'Missing url' })
        return
      }
      if (!Array.isArray(items)) {
        sendResponse({ ok: false, error: 'Missing items' })
        return
      }

      sendResponse(await sendHttpRequest({
        url,
        method: 'POST',
        token,
        body: {
          items
        }
      }))
      return
    }
  })().catch((err) => {
    sendResponse({ ok: false, error: err?.message || String(err) })
  })

  return true
})
