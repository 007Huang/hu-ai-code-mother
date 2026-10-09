const internalOrigin = 'https://internal.invalid'

/** 只接受当前应用的站内路径，防止登录后跳转到外部网站。 */
export function getSafeRedirect(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) {
    return '/'
  }

  try {
    const decoded = decodeURIComponent(value)
    if (
      !decoded.startsWith('/') ||
      decoded.startsWith('//') ||
      /[\\\u0000- \u007f]/.test(decoded) ||
      /[\\\u0000- \u007f]/.test(value)
    ) {
      return '/'
    }
    const url = new URL(value, internalOrigin)
    const decodedUrl = new URL(decoded, internalOrigin)
    if (url.origin !== internalOrigin || decodedUrl.origin !== internalOrigin) {
      return '/'
    }
    if ([url.pathname, decodedUrl.pathname].some((path) => /^\/user\/(login|register)(\/|$)/.test(path))) {
      return '/'
    }
    return `${url.pathname}${url.search}${url.hash}`
  } catch {
    return '/'
  }
}

export function getLoginRedirect(path: string) {
  return `/user/login?${new URLSearchParams({ redirect: getSafeRedirect(path) })}`
}
