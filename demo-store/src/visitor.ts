const storageKey = 'morrow.visitor';
type Storage = Pick<globalThis.Storage, 'getItem' | 'setItem'>;
// Access localStorage lazily so browsers blocking its getter are also handled.
export const browserStorage: Storage = {
  getItem: key => localStorage.getItem(key),
  setItem: (key, value) => localStorage.setItem(key, value),
};
export function newVisitor(storage: Storage, uuid: () => string = () => crypto.randomUUID()): string {
  const id = `demo-user-${uuid()}`;
  try { storage.setItem(storageKey, id); } catch { /* Session stays usable if storage is blocked. */ }
  return id;
}
export function getVisitor(storage: Storage, uuid?: () => string): string {
  try {
    const saved = storage.getItem(storageKey);
    if (saved && /^demo-user-[a-zA-Z0-9-]{1,100}$/.test(saved)) return saved;
  } catch { /* Use an in-memory visitor when storage is unavailable. */ }
  return newVisitor(storage, uuid);
}
