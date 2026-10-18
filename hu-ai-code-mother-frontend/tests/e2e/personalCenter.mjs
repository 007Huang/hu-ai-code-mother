import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import path from 'node:path'

const require = createRequire(import.meta.url)
let playwright
try {
  playwright = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
} catch {
  const globalModule = path.join(process.env.APPDATA || '', 'npm/node_modules/@playwright/mcp/node_modules/playwright')
  playwright = require(globalModule)
}
const baseUrl = process.env.PERSONAL_CENTER_TEST_URL || 'http://127.0.0.1:5187'
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(baseUrl).hostname), '仅允许本地前端测试地址')
const output = path.resolve(import.meta.dirname, '../../../target/personal-center-browser')
await mkdir(output, { recursive: true })
const browser = await playwright.chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1080 } })
const page = await context.newPage()
const errors = []
const unexpected = []
page.on('pageerror', (error) => errors.push(error.message))
const userId = '1999999999999999999'
const state = {
  loggedIn: true,
  user: { id: userId, userName: '测试用户', userAccount: 'tester', userAvatar: '', userProfile: '', userRole: 'user', createTime: '2026-01-01T12:00:00' },
  appLimit: null,
  forcedUsed: null,
  failQuota: false,
  profileWrites: [],
  quotaWrites: [],
  deleteWrites: [],
  worksRequests: [],
  apps: [
    { id: '2999999999999999999', userId, appName: '测试作品一', codeGenType: 'html', initPrompt: '测试应用', createTime: '2026-10-09T12:00:00' },
    { id: '3999999999999999999', userId, appName: '测试作品二', codeGenType: 'html', initPrompt: '测试应用', createTime: '2026-10-09T12:00:00' },
  ],
}
function quota() {
  const usedCount = state.forcedUsed ?? state.apps.length
  const limitCount = state.appLimit ?? 10
  return { usedCount, limitCount, remainingCount: Math.max(0, limitCount - usedCount), canCreate: usedCount < limitCount, overLimit: usedCount > limitCount, appLimit: state.appLimit, usesDefault: state.appLimit === null, defaultLimit: 10 }
}
const success = (data) => ({ code: 0, message: 'ok', data })

// 所有 API 被拦截；未知路由立即失败，不能回落到真实后端。
await context.route((url) => url.pathname.startsWith('/api/'), async (route) => {
  const url = new URL(route.request().url())
  const request = route.request()
  const body = request.method() === 'POST' ? request.postDataJSON() : null
  let response
  switch (url.pathname) {
    case '/api/user/get/login': response = state.loggedIn ? success(state.user) : { code: 40100, message: '未登录' }; break
    case '/api/user/login': state.loggedIn = true; response = success(state.user); break
    case '/api/user/logout': state.loggedIn = false; response = success(true); break
    case '/api/user/update/my':
      state.profileWrites.push(body)
      Object.assign(state.user, body)
      response = success(state.user)
      break
    case '/api/app/my/quota':
      response = state.failQuota ? { code: 50000, message: '测试额度查询失败' } : success(quota())
      break
    case '/api/app/my/list/page/vo': {
      state.worksRequests.push(body)
      const matches = state.apps.filter((app) => !body.appName || app.appName.includes(body.appName))
      const start = (Number(body.pageNum) - 1) * Number(body.pageSize)
      response = success({ records: matches.slice(start, start + Number(body.pageSize)).map((app) => ({ ...app, user: state.user })), totalRow: String(matches.length), pageNumber: body.pageNum, pageSize: body.pageSize })
      break
    }
    case '/api/app/good/list/page/vo': response = success({ records: [], totalRow: '0' }); break
    case '/api/app/delete':
      state.deleteWrites.push(body)
      state.apps = state.apps.filter((app) => app.id !== body.id)
      response = success(true)
      break
    case '/api/user/list/page/vo': response = success({ records: [state.user], totalRow: '1' }); break
    case '/api/user/quota/get': assert.equal(url.searchParams.get('id'), userId); response = success(quota()); break
    case '/api/user/quota/update':
      assert.equal(body.id, userId)
      assert.ok(Object.hasOwn(body, 'appLimit'))
      state.quotaWrites.push(body)
      state.appLimit = body.appLimit
      response = success(quota())
      break
    case '/api/app/add': response = { code: 40301, message: '作品额度已满' }; break
    default:
      unexpected.push(`${request.method()} ${url.pathname}`)
      response = { code: 50000, message: '未预期的测试 API，禁止真实请求' }
  }
  await route.fulfill({ json: response })
})
const checks = []
async function check(name, action) {
  await action()
  checks.push(name)
  console.log('PASS', name)
}
try {
  await check('个人中心加载、校验与保存同步昵称', async () => {
    await page.goto(`${baseUrl}/user/center`)
    await page.getByTestId('profile-name').waitFor()
    await page.getByTestId('profile-name').fill('A')
    await page.getByTestId('profile-save').click()
    await page.getByText('昵称去除首尾空格后须为 2～20 个字符').waitFor()
    assert.equal(state.profileWrites.length, 0)
    await page.getByTestId('profile-name').fill('新昵称')
    await page.getByTestId('profile-save').click()
    await page.getByText('个人资料已保存').waitFor()
    assert.equal(state.profileWrites.length, 1)
    assert.deepEqual(Object.keys(state.profileWrites[0]).sort(), ['userAvatar', 'userName', 'userProfile'])
    assert.equal(state.user.userName, '新昵称')
    await page.getByTestId('account-menu-trigger').click()
    await page.getByText('应用 2 / 10，剩余 8').waitFor()
    await page.waitForTimeout(250)
    await page.screenshot({ path: path.join(output, 'profile-and-account.png'), fullPage: true })
    await page.getByTestId('account-menu-trigger').click()
  })
  await check('作品名称模糊搜索、Enter提交、空结果和清空恢复', async () => {
    await page.getByRole('tab', { name: '我的作品' }).click()
    await page.locator('.work-item').nth(1).waitFor()
    const search = page.getByPlaceholder('搜索我的应用', { exact: true })
    await search.fill('  作品一  ')
    const response = page.waitForResponse((res) => res.url().includes('/api/app/my/list/page/vo') && res.request().postDataJSON().appName === '作品一')
    await page.locator('.works-search').getByRole('button', { name: /^搜\s*索$/ }).click()
    await response
    await page.waitForFunction(() => document.querySelectorAll('.work-item').length === 1)
    assert.equal(await page.locator('.app-title').innerText(), '测试作品一')
    assert.equal(state.worksRequests.at(-1).appName, '作品一')
    assert.equal(Number(state.worksRequests.at(-1).pageNum), 1)
    await page.screenshot({ path: path.join(output, 'works-search.png'), fullPage: true })
    await search.fill('不存在的名称')
    const emptyResponse = page.waitForResponse((res) => res.url().includes('/api/app/my/list/page/vo') && res.request().postDataJSON().appName === '不存在的名称')
    await search.press('Enter')
    await emptyResponse
    await page.getByText('没有找到匹配的作品').waitFor()
    assert.equal(await page.locator('.work-item').count(), 0)
    await search.fill('')
    const resetResponse = page.waitForResponse((res) => res.url().includes('/api/app/my/list/page/vo') && !res.request().postDataJSON().appName)
    await page.locator('.works-search').getByRole('button', { name: /^搜\s*索$/ }).click()
    await resetResponse
    await page.locator('.work-item').nth(1).waitFor()
  })
  await check('本人作品详情删除、字符串ID及额度刷新', async () => {
    await page.getByRole('tab', { name: '我的作品' }).click()
    await page.getByTestId('work-detail').first().click()
    const dialog = page.getByRole('dialog')
    await dialog.locator('.app-actions button').filter({ hasText: /删\s*除/ }).click()
    await page.getByRole('button', { name: /^确\s*定$/ }).click()
    await page.getByText('应用已删除，额度将重新统计').waitFor()
    assert.equal(state.deleteWrites[0].id, '2999999999999999999')
    await page.getByRole('tab', { name: '应用额度' }).click()
    await page.getByTestId('my-app-quota').getByText('1 个应用', { exact: true }).waitFor()
  })
  await check('满额阻止新建且保留描述', async () => {
    state.forcedUsed = 10
    await page.goto(baseUrl)
    const prompt = page.locator('.prompt-input')
    await prompt.fill('保留的创作描述')
    await page.getByText('应用数量已达上限，描述已保留').waitFor()
    assert.equal(await page.getByTestId('create-app').isDisabled(), true)
    assert.equal(await prompt.inputValue(), '保留的创作描述')
    await page.getByRole('link', { name: '管理我的作品', exact: true }).click()
    await page.getByTestId('work-detail').waitFor()
  })
  await check('额度失败显示重试，不伪装为零', async () => {
    state.failQuota = true
    await page.goto(`${baseUrl}/user/center?tab=quota`)
    await page.getByText('测试额度查询失败').waitFor()
    assert.equal(await page.getByTestId('my-app-quota').count(), 0)
    state.failQuota = false
    state.forcedUsed = 12
    await page.getByRole('button', { name: /^重\s*试$/ }).click()
    await page.getByText('现有应用数量超过当前上限', { exact: true }).waitFor()
    await page.waitForFunction(() => !document.querySelector('.ant-spin-blur'))
    await page.waitForTimeout(250)
    await page.screenshot({ path: path.join(output, 'over-limit-quota.png'), fullPage: true })
  })
  await check('管理员设置0与null继承语义明确', async () => {
    state.user.userRole = 'admin'
    state.forcedUsed = 1
    await page.goto(`${baseUrl}/admin/userManage`)
    await page.getByTestId('user-quota-open').click()
    const dialog = page.getByRole('dialog')
    await dialog.getByTestId('quota-inherit').uncheck()
    await dialog.getByTestId('quota-limit').fill('0')
    await dialog.getByRole('button', { name: /^保\s*存$/ }).click()
    await page.getByText('用户应用额度已更新').waitFor()
    assert.deepEqual(state.quotaWrites.at(-1), { id: userId, appLimit: 0 })
    await page.getByTestId('user-quota-open').click()
    await dialog.getByTestId('quota-inherit').check()
    await dialog.getByRole('button', { name: /^保\s*存$/ }).click()
    await page.waitForFunction(() => !document.querySelector('.ant-modal-wrap:not([style*="display: none"])'))
    assert.deepEqual(state.quotaWrites.at(-1), { id: userId, appLimit: null })
  })
  await check('移动端个人中心可用，无横向页面溢出', async () => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto(`${baseUrl}/user/center`)
    await page.getByTestId('profile-name').waitFor()
    await page.getByTestId('profile-name').fill('移动端修改')
    await page.getByTestId('profile-save').scrollIntoViewIfNeeded()
    await page.waitForTimeout(250)
    await page.screenshot({ path: path.join(output, 'mobile-profile.png'), fullPage: true })
    const dimensions = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, width: window.innerWidth }))
    assert.ok(dimensions.scroll <= dimensions.width + 1, JSON.stringify(dimensions))
  })
  await check('未登录个人中心返回路径安全并在登录后恢复', async () => {
    state.loggedIn = false
    await page.goto(`${baseUrl}/user/center?tab=works`)
    await page.waitForURL('**/user/login?redirect=**')
    const loginUrl = new URL(page.url())
    assert.equal(loginUrl.searchParams.get('redirect'), '/user/center?tab=works')
    await page.getByPlaceholder('请输入账号', { exact: true }).fill('tester')
    await page.getByPlaceholder('请输入密码', { exact: true }).fill('test-password')
    await page.getByRole('button', { name: /^登\s*录$/ }).click()
    await page.waitForURL('**/user/center?tab=works')
    await page.getByTestId('work-detail').waitFor()
    state.loggedIn = false
    await page.goto(`${baseUrl}/user/login?redirect=https%3A%2F%2Fevil.example`)
    await page.getByPlaceholder('请输入账号', { exact: true }).fill('tester')
    await page.getByPlaceholder('请输入密码', { exact: true }).fill('test-password')
    await page.getByRole('button', { name: /^登\s*录$/ }).click()
    await page.waitForURL(`${baseUrl}/`)
  })
  assert.deepEqual(unexpected, [], '不允许未预期API')
  assert.deepEqual(errors, [], '页面不允许运行时异常')
  await writeFile(path.join(output, 'results.json'), JSON.stringify({ checks, pageErrors: errors, unexpectedApi: unexpected, realBackendCalls: 0 }, null, 2))
  console.log(`Browser checks passed: ${checks.length}; screenshots: ${output}`)
} catch (error) {
  console.log('BUTTONS', await page.locator('button').evaluateAll((buttons) => buttons.map((button) => ({ text: button.textContent, aria: button.getAttribute('aria-label'), role: button.getAttribute('role'), class: button.className }))))
  await page.screenshot({ path: path.join(output, 'failure.png'), fullPage: true })
  await writeFile(path.join(output, 'results.json'), JSON.stringify({ checks, pageErrors: errors, unexpectedApi: unexpected, failure: String(error) }, null, 2))
  throw error
} finally {
  await browser.close()
}
