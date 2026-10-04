export function dateTime(value: string) { return new Date(value).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' }); }
export function dateOnly(value: string) { return new Date(value).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }); }
export function initials(name: string) { return name.trim().split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase(); }
