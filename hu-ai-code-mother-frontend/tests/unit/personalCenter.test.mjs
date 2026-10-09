import { test, after } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, readFile, writeFile, rm, mkdir } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'
import path from 'node:path'
import ts from 'typescript'
import { createPinia, setActivePinia } from 'pinia'

// 复用项目已有 TypeScript/Pinia，不为少量状态测试增加一套测试框架。
const root = path.resolve(import.meta.dirname, '../..')
const temporaryRoot = path.join(root, 'node_modules/.tmp')
await mkdir(temporaryRoot, { recursive: true })
const directory = await mkdtemp(path.join(temporaryRoot, 'personal-center-'))
const files = [
  ['src/utils/authState.ts', 'authState.mjs'],
  ['src/utils/redirect.ts', 'redirect.mjs'],
  ['src/utils/count.ts', 'count.mjs'],
  ['src/stores/loginUser.ts', 'loginUser.mjs'],
  ['src/stores/appQuota.ts', 'appQuota.mjs'],
]
for (const [source, output] of files) {
  const text = await readFile(path.join(root, source), 'utf8')
  const result = ts.transpileModule(text, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
  }).outputText
    .replaceAll("'@/api/userController'", "'./api.mjs'")
    .replaceAll("'@/api/appController'", "'./api.mjs'")
    .replaceAll("'@/utils/authState'", "'./authState.mjs'")
    .replaceAll("'@/stores/loginUser'", "'./loginUser.mjs'")
  await writeFile(path.join(directory, output), result)
}
await writeFile(path.join(directory, 'api.mjs'), `
export const loginRequests = []
export const quotaRequests = []
export const getLoginUser = () => new Promise((resolve, reject) => loginRequests.push({ resolve, reject }))
export const getMyAppQuota = () => new Promise((resolve, reject) => quotaRequests.push({ resolve, reject }))
`)
const load = (name) => import(pathToFileURL(path.join(directory, name)).href)
const { getSafeRedirect, getLoginRedirect } = await load('redirect.mjs')
const { toSafeCount } = await load('count.mjs')
const { getAuthRevision, notifySessionExpired } = await load('authState.mjs')
const { useLoginUserStore } = await load('loginUser.mjs')
const { useAppQuotaStore } = await load('appQuota.mjs')
const { loginRequests, quotaRequests } = await load('api.mjs')
after(() => rm(directory, { recursive: true, force: true }))

const success = (data) => ({ data: { code: 0, data } })
const quotaData = { usedCount: 2, limitCount: 10, remainingCount: 8, canCreate: true, overLimit: false }
const userA = { id: '1999999999999999999', userName: '用户甲' }
const userB = { id: '2999999999999999999', userName: '用户乙' }

function setup() {
  setActivePinia(createPinia())
  loginRequests.length = 0
  quotaRequests.length = 0
  const login = useLoginUserStore()
  login.setLoginUser(userA)
  return { login, quota: useAppQuotaStore() }
}

test('安全 redirect 允许站内路径且 query 正确编码', () => {
  assert.equal(getSafeRedirect('/user/center?tab=works#title'), '/user/center?tab=works#title')
  assert.equal(getLoginRedirect('/user/center?tab=works'), '/user/login?redirect=%2Fuser%2Fcenter%3Ftab%3Dworks')
})

test('拒绝外部、控制字符、反斜杠、畸形编码和登录回环', () => {
  for (const value of [
    'https://evil.example', '//evil.example', '/\\evil.example', '/%2f%2fevil.example',
    '/%5cevil.example', '/%0aevil.example', '/broken%', '/user/login', '/user/register?redirect=/foo',
    '/foo/../user/login', '/user/%6cogin', '/user/login%2fextra', null, [],
  ]) {
    assert.equal(getSafeRedirect(value), '/', String(value))
  }
})

test('数量可安全归一化，大整数和非法输入被拒绝', () => {
  assert.equal(toSafeCount('20'), 20)
  assert.equal(toSafeCount(0), 0)
  assert.equal(toSafeCount(undefined), 0)
  for (const value of ['1999999999999999999', '-1', '1e2', 'abc', 1.5, -1]) {
    assert.throws(() => toSafeCount(value))
  }
})

test('登出后旧登录响应不能回填资料', async () => {
  const { login } = setup()
  const request = login.fetchLoginUser()
  login.clearLoginUser()
  loginRequests[0].resolve(success(userA))
  await request
  assert.equal(login.loginUser.id, undefined)
  assert.equal(login.loading, false)
})

test('网络故障保留身份，确认会话失效才退出', async () => {
  const { login } = setup()
  const request = login.fetchLoginUser()
  loginRequests[0].reject(new Error('offline'))
  await request
  assert.equal(login.loginUser.id, userA.id)
  assert.ok(login.error)
  const retry = login.fetchLoginUser()
  loginRequests[1].resolve({ data: { code: 40100 } })
  await retry
  assert.equal(login.loginUser.id, undefined)
})

test('切换用户时旧额度响应不得覆盖新用户', async () => {
  const { login, quota } = setup()
  const oldRequest = quota.fetchQuota()
  login.setLoginUser(userB)
  const newRequest = quota.fetchQuota()
  quotaRequests[1].resolve(success({ ...quotaData, usedCount: 0, remainingCount: 10 }))
  await newRequest
  quotaRequests[0].resolve(success(quotaData))
  await oldRequest
  assert.equal(quota.quota.usedCount, 0)
  assert.equal(quota.loading, false)
})

test('额度查询去重，失败不伪装成零，重试支持真实满额', async () => {
  const { quota } = setup()
  const first = quota.fetchQuota()
  const duplicate = quota.fetchQuota()
  assert.equal(quotaRequests.length, 1)
  quotaRequests[0].reject(new Error('offline'))
  await Promise.all([first, duplicate])
  assert.equal(quota.quota, null)
  assert.ok(quota.error)
  const retry = quota.fetchQuota()
  quotaRequests[1].resolve(success({ ...quotaData, usedCount: 10, remainingCount: 0, canCreate: false }))
  await retry
  assert.equal(quota.quota.canCreate, false)
  assert.equal(quota.error, '')
})

test('登出重登同一账号，旧会话响应仍不能回填', async () => {
  const { login, quota } = setup()
  const request = quota.fetchQuota()
  login.clearLoginUser()
  login.setLoginUser(userA)
  quotaRequests[0].resolve(success(quotaData))
  await request
  assert.equal(quota.quota, null)
})

test('旧请求的未登录结果不能清理新账号；资料编辑不变更身份版本', () => {
  const { login } = setup()
  const revision = getAuthRevision()
  login.setLoginUser({ ...userA, userName: '新昵称' })
  assert.equal(getAuthRevision(), revision)
  login.setLoginUser(userB)
  assert.equal(notifySessionExpired(revision), false)
  assert.equal(login.loginUser.id, userB.id)
})
