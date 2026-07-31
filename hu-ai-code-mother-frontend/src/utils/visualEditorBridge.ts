/**
 * 可视化编辑桥接工具
 *
 * 通过直接访问同源 iframe 的 document，实现元素悬浮高亮和点选功能。
 * 主网站和展示网站必须同域名才能使用此工具。
 */

export interface SelectedElement {
  uid: string
  tagName: string
  id: string
  className: string
  textContent: string
  selector: string
}

/** 元素被选中时的回调 */
export type ElementSelectCallback = (element: SelectedElement) => void
/** 元素被取消选中时的回调 */
export type ElementDeselectCallback = (uid: string) => void

// -- 注入到 iframe 中的 CSS 类名 / 属性名 --
const HOVER_CLASS = '__ve-hover'
const SELECTED_CLASS = '__ve-selected'
const SELECTED_ATTR = 'data-ve-uid'

// -- 模块级状态 --
let isActive = false
let targetDocument: Document | null = null
let styleElement: HTMLStyleElement | null = null
let onSelect: ElementSelectCallback | null = null
let onDeselect: ElementDeselectCallback | null = null
let uidCounter = 0
let currentHoverElement: Element | null = null

/** 已选中元素的映射，uid → SelectedElement */
const selectedMap = new Map<string, SelectedElement>()

// ---------------------------------------------------------------------------
// 内部工具函数
// ---------------------------------------------------------------------------

/** 生成唯一标识 */
function uid(): string {
  return `ve-${Date.now()}-${++uidCounter}`
}

/** 构建元素的 CSS 选择器（尽量可读且唯一） */
function buildSelector(el: Element): string {
  // 如果有 id，直接使用
  if (el.id) return `#${el.id}`

  const parts: string[] = []
  let cur: Element | null = el

  while (cur && cur !== targetDocument?.body && cur !== targetDocument?.documentElement) {
    let seg = cur.tagName.toLowerCase()

    if (cur.id) {
      parts.unshift(`#${cur.id}`)
      break
    }

    // 拼接前两个非 ve- 的 CSS 类名
    const cls =
      typeof cur.className === 'string'
        ? cur.className
            .trim()
            .split(/\s+/)
            .filter((c) => c && !c.startsWith('__ve-'))
            .slice(0, 2)
            .join('.')
        : ''
    if (cls) seg += `.${cls}`

    parts.unshift(seg)
    cur = cur.parentElement
  }

  return parts.join(' > ')
}

/** 提取元素的描述信息 */
function getInfo(el: Element, id: string): SelectedElement {
  return {
    uid: id,
    tagName: el.tagName.toLowerCase(),
    id: el.id || '',
    className:
      typeof el.className === 'string'
        ? el.className.replace(/__ve-\S+/g, '').trim()
        : '',
    textContent: (el.textContent || '').trim().substring(0, 100),
    selector: buildSelector(el),
  }
}

/** 向 iframe 注入样式 */
function injectStyles(doc: Document): HTMLStyleElement {
  const style = doc.createElement('style')
  style.setAttribute('data-ve-style', 'true')
  style.textContent = `
    .${HOVER_CLASS} {
      outline: 2px dashed rgba(24, 144, 255, 0.6) !important;
      outline-offset: 2px !important;
      cursor: crosshair !important;
    }
    .${SELECTED_CLASS} {
      outline: 3px solid #1890ff !important;
      outline-offset: 2px !important;
    }
  `
  doc.head.appendChild(style)
  return style
}

// ---------------------------------------------------------------------------
// iframe 内的事件处理器（通过 addEventListener 绑定）
// ---------------------------------------------------------------------------

function onMouseOver(e: MouseEvent): void {
  const target = e.target as Element
  if (!target || target === targetDocument?.body || target === targetDocument?.documentElement) return
  // 已选中的元素不再展示悬浮样式
  if (target.hasAttribute(SELECTED_ATTR)) return
  if (currentHoverElement === target) return

  if (currentHoverElement) {
    currentHoverElement.classList.remove(HOVER_CLASS)
  }
  currentHoverElement = target
  target.classList.add(HOVER_CLASS)
}

function onMouseOut(e: MouseEvent): void {
  const target = e.target as Element
  if (target) {
    target.classList.remove(HOVER_CLASS)
  }
  if (currentHoverElement === target) {
    currentHoverElement = null
  }
}

function onClick(e: MouseEvent): void {
  // 阻止默认行为，避免编辑模式下触发链接跳转等
  e.preventDefault()
  e.stopPropagation()

  const target = e.target as Element
  if (!target || target === targetDocument?.body || target === targetDocument?.documentElement) return

  // 如果点击的是已经选中的元素 → 取消选中
  const existingUid = target.getAttribute(SELECTED_ATTR)
  if (existingUid) {
    deselectInDom(existingUid)
    return
  }

  // 选中元素
  const id = uid()
  target.setAttribute(SELECTED_ATTR, id)
  target.classList.add(SELECTED_CLASS)
  target.classList.remove(HOVER_CLASS)

  const info = getInfo(target, id)
  selectedMap.set(id, info)
  onSelect?.(info)
}

/** 在 iframe 中取消某个元素的选中状态（不清理整个模式） */
function deselectInDom(id: string): void {
  if (!targetDocument) return

  const el = targetDocument.querySelector(`[${SELECTED_ATTR}="${id}"]`)
  if (el) {
    el.classList.remove(SELECTED_CLASS)
    el.removeAttribute(SELECTED_ATTR)
  }
  selectedMap.delete(id)
  onDeselect?.(id)
}

/** 完全清理 iframe 中的所有注入内容 */
function cleanup(): void {
  if (!targetDocument) return

  targetDocument.removeEventListener('mouseover', onMouseOver, true)
  targetDocument.removeEventListener('mouseout', onMouseOut, true)
  targetDocument.removeEventListener('click', onClick, true)

  if (styleElement?.parentNode) {
    styleElement.parentNode.removeChild(styleElement)
  }

  // 清除所有悬浮和选中的类
  const hovered = targetDocument.querySelectorAll(`.${HOVER_CLASS}`)
  hovered.forEach((el) => el.classList.remove(HOVER_CLASS))

  const selected = targetDocument.querySelectorAll(`.${SELECTED_CLASS}`)
  selected.forEach((el) => {
    el.classList.remove(SELECTED_CLASS)
    el.removeAttribute(SELECTED_ATTR)
  })

  styleElement = null
  targetDocument = null
  onSelect = null
  onDeselect = null
  currentHoverElement = null
  selectedMap.clear()
  uidCounter = 0
  isActive = false
}

// ---------------------------------------------------------------------------
// 对外导出的 API
// ---------------------------------------------------------------------------

/**
 * 启动编辑模式
 * @param iframe  目标 iframe 元素
 * @param selectCallback   元素被选中时的回调
 * @param deselectCallback 元素被取消选中时的回调
 * @returns 是否启动成功
 */
export function startEditMode(
  iframe: HTMLIFrameElement,
  selectCallback: ElementSelectCallback,
  deselectCallback: ElementDeselectCallback,
): boolean {
  if (isActive) stopEditMode()

  try {
    const doc = iframe.contentDocument || iframe.contentWindow?.document
    if (!doc) {
      console.error('无法访问 iframe 文档，请确保主网站与展示网站同源')
      return false
    }

    targetDocument = doc
    onSelect = selectCallback
    onDeselect = deselectCallback
    isActive = true

    styleElement = injectStyles(doc)
    doc.addEventListener('mouseover', onMouseOver, true)
    doc.addEventListener('mouseout', onMouseOut, true)
    doc.addEventListener('click', onClick, true)

    return true
  } catch (err) {
    console.error('启动编辑模式失败：', err)
    return false
  }
}

/** 退出编辑模式，清理 iframe 中的所有注入 */
export function stopEditMode(): void {
  cleanup()
}

/**
 * 根据 uid 移除单个选中元素（同时取消 iframe 中的高亮）
 * 通常在用户关闭 alert 标签时调用
 */
export function removeSelectedElement(uid: string): void {
  deselectInDom(uid)
}

/** 清除所有选中元素（但不退出编辑模式） */
export function clearAllSelections(): void {
  if (!targetDocument) return
  const selected = targetDocument.querySelectorAll(`.${SELECTED_CLASS}`)
  selected.forEach((el) => {
    el.classList.remove(SELECTED_CLASS)
    el.removeAttribute(SELECTED_ATTR)
  })
  selectedMap.clear()
}

/** 获取当前所有选中元素的信息 */
export function getSelectedElements(): SelectedElement[] {
  return Array.from(selectedMap.values())
}

/** 查询编辑模式是否开启 */
export function isEditModeActive(): boolean {
  return isActive
}

/**
 * 将选中的元素信息拼接为提示词，附加到用户输入前
 */
export function buildElementPrompt(elements: SelectedElement[]): string {
  if (elements.length === 0) return ''

  const lines = elements.map((el, i) => {
    const parts: string[] = [`元素${i + 1}：<${el.tagName}>`]
    if (el.id) parts.push(`#${el.id}`)
    if (el.className) parts.push(`.${el.className}`)
    if (el.textContent) parts.push(`— "${el.textContent}"`)
    parts.push(`\n  选择器：${el.selector}`)
    return parts.join(' ')
  })

  return `以下是用户选中的网页元素信息，请根据以下信息对相应元素进行修改：\n${lines.join('\n')}`
}
