export interface User { id: number; name: string; email: string; role: 'USER'; createdAt: string }
export interface AuthResponse { accessToken: string; tokenType: string; expiresIn: number; user: User }
export interface Project { id: number; name: string; key: string; description: string | null; ownerId: number; createdAt: string; updatedAt: string }
export interface Environment { id: number; projectId: number; name: string; key: string; createdAt: string }
export interface FeatureFlag { id: number; projectId: number; key: string; name: string; description: string | null; createdAt: string; updatedAt: string }
export interface FlagConfiguration { id: number; featureFlagId: number; environmentId: number; enabled: boolean; rolloutPercentage: number; updatedAt: string }
export interface ApiKey { id: number; environmentId: number; name: string; prefix: string; createdAt: string; revokedAt: string | null }
export interface ApiKeyCreated { key: ApiKey; apiKey: string }
export type AuditAction = 'PROJECT_CREATED' | 'PROJECT_UPDATED' | 'PROJECT_DELETED' | 'FLAG_CREATED' | 'FLAG_UPDATED' | 'FLAG_DELETED' | 'FLAG_CONFIGURATION_UPDATED' | 'API_KEY_CREATED' | 'API_KEY_REVOKED';
export interface AuditEvent { id: number; actorUserId: number; projectId: number; action: AuditAction; entityType: string; entityId: number; metadata: Record<string, unknown>; createdAt: string }
export interface AuditPage { content: AuditEvent[]; page: number; size: number; totalElements: number }
export interface ApiError { timestamp: string; status: number; code: string; message: string; path: string; fieldErrors: Record<string, string> }
export interface ResourceInput { name: string; key: string; description: string | null }
export type ResourceUpdate = Pick<ResourceInput, 'name' | 'description'>;
