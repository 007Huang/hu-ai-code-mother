let authRevision = 0
const sessionExpiredHandlers = new Set<() => void>()

export function getAuthRevision() {
  return authRevision
}

export function advanceAuthRevision() {
  authRevision += 1
  return authRevision
}

export function onSessionExpired(handler: () => void) {
  sessionExpiredHandlers.add(handler)
  return () => sessionExpiredHandlers.delete(handler)
}

export function notifySessionExpired(requestRevision: number) {
  if (requestRevision !== authRevision) {
    return false
  }
  sessionExpiredHandlers.forEach((handler) => handler())
  return true
}
