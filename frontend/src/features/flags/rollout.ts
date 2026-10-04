export function shouldCommitRollout(confirmed: number, draft: number): boolean {
  return Number.isInteger(draft) && draft >= 0 && draft <= 100 && draft !== confirmed;
}
