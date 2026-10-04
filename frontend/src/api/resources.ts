import { apiClient as client } from './index';
import type { ApiKey, ApiKeyCreated, AuditPage, AuthResponse, Environment, FeatureFlag, FlagConfiguration, Project, ResourceInput, ResourceUpdate, User } from '../types/api';
const projectPath = (id: number) => `/api/v1/projects/${id}`;
const flagsPath = (project: number) => `${projectPath(project)}/flags`;
const keysPath = (project: number, environment: number) => `${projectPath(project)}/environments/${environment}/api-keys`;
export const authApi = {
  register: (name: string, email: string, password: string) => client.post<User>('/api/v1/auth/register', { name, email, password }, { public: true }),
  login: (email: string, password: string) => client.post<AuthResponse>('/api/v1/auth/login', { email, password }, { public: true }),
  me: (signal?: AbortSignal) => client.get<User>('/api/v1/auth/me', { signal }),
};
export const projectsApi = {
  list: (signal?: AbortSignal) => client.get<Project[]>('/api/v1/projects', { signal }),
  get: (id: number, signal?: AbortSignal) => client.get<Project>(projectPath(id), { signal }),
  create: (input: ResourceInput) => client.post<Project>('/api/v1/projects', input),
  update: (id: number, input: ResourceUpdate) => client.put<Project>(projectPath(id), input),
  delete: (id: number) => client.delete(projectPath(id)),
  environments: (id: number, signal?: AbortSignal) => client.get<Environment[]>(`${projectPath(id)}/environments`, { signal }),
};
export const flagsApi = {
  list: (project: number, signal?: AbortSignal) => client.get<FeatureFlag[]>(flagsPath(project), { signal }),
  get: (project: number, id: number, signal?: AbortSignal) => client.get<FeatureFlag>(`${flagsPath(project)}/${id}`, { signal }),
  create: (project: number, input: ResourceInput) => client.post<FeatureFlag>(flagsPath(project), input),
  update: (project: number, id: number, input: ResourceUpdate) => client.put<FeatureFlag>(`${flagsPath(project)}/${id}`, input),
  delete: (project: number, id: number) => client.delete(`${flagsPath(project)}/${id}`),
  configurations: (project: number, id: number, signal?: AbortSignal) => client.get<FlagConfiguration[]>(`${flagsPath(project)}/${id}/configurations`, { signal }),
  configure: (flag: number, environment: number, enabled: boolean, rolloutPercentage: number) => client.patch<FlagConfiguration>(`/api/v1/flags/${flag}/environments/${environment}`, { enabled, rolloutPercentage }),
};
export const keysApi = {
  list: (project: number, environment: number, signal?: AbortSignal) => client.get<ApiKey[]>(keysPath(project, environment), { signal }),
  create: (project: number, environment: number, name: string) => client.post<ApiKeyCreated>(keysPath(project, environment), { name }),
  revoke: (project: number, environment: number, key: number) => client.delete(`${keysPath(project, environment)}/${key}`),
};
export const auditApi = {
  list: (project: number, page: number, signal?: AbortSignal) => client.get<AuditPage>(`${projectPath(project)}/audit?page=${page}&size=20`, { signal }),
};
