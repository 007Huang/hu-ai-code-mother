/** 仅用于数量与分页，不用于 ID；拒绝无法精确表示的值。 */
export function toSafeCount(value: string | number | undefined | null): number {
  if (value === undefined || value === null) {
    return 0
  }
  if (typeof value === 'string' && !/^\d+$/.test(value)) {
    throw new Error('返回的数量格式不正确')
  }
  const count = Number(value)
  if (!Number.isSafeInteger(count) || count < 0) {
    throw new Error('返回的数量超出安全范围')
  }
  return count
}
