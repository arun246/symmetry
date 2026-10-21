export type Role = "WORKER" | "PUBLISHER" | "ADMIN";

export interface Me {
  id: string;
  email: string;
  name: string;
  role: Role;
  orgId: string | null;
  orgSlug: string | null;
  settings: Record<string, unknown>;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Contract {
  id: string;
  moduleId: string;
  version: string;
  taskKind: string;
  language: string;
  workKind: string;
  lifecycleState: string;
  disclosureTier: string;
  taskDocument: string;
  payload: unknown;
  orgId: string | null;
  createdBy: string | null;
  createdAt: string;
}

export interface Workbench {
  id: string;
  moduleId: string;
  version: string;
  language: string;
  taskKind: string;
  workKind: string;
  disclosureTier: string;
  taskDocument: string;
  payload: unknown;
}

export interface Acceptance {
  id: string;
  contractId: string;
  workerId: string;
  taskDocumentHash: string;
  acceptedAt: string;
  verified: boolean;
}

export interface WorkerAcceptance {
  id: string;
  contractId: string;
  moduleId: string;
  version: string;
  lifecycleState: string;
  taskDocumentHash: string;
  verified: boolean;
  acceptedAt: string;
}

export interface MetricPoint {
  label: string;
  value: number;
}

export interface AnalyticsBundle {
  overview: Record<string, number>;
  contractsByState: MetricPoint[];
  acceptancesByDay: MetricPoint[];
  topModules: MetricPoint[];
  verification: MetricPoint[];
  funnel: MetricPoint[];
}

export interface Organization {
  id: string;
  slug: string;
  name: string;
  status: string;
  contracts: number;
  workers: number;
  createdAt: string;
}

export interface User {
  id: string;
  email: string;
  displayName: string;
  role: Role;
  orgId: string | null;
  status: string;
  createdAt: string;
}

export interface ConfigRequest {
  id: string;
  orgId: string;
  contractId: string | null;
  requestedBy: string;
  key: string;
  value: unknown;
  reason: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "APPLIED";
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewNote: string | null;
  createdAt: string;
}

export interface AuditEntry {
  id: number;
  actorId: string | null;
  actorEmail: string | null;
  actorRole: string | null;
  action: string;
  targetType: string;
  targetId: string | null;
  before: unknown;
  after: unknown;
  ip: string | null;
  userAgent: string | null;
  createdAt: string;
}

export interface SettingRow {
  id: string;
  scope: string;
  scopeId: string | null;
  key: string;
  value: unknown;
  version: number;
  updatedBy: string | null;
  updatedAt: string;
}

const USER_KEY = "symmetry.devUser";
export const DEFAULT_USER = "publisher@symmetry.local";

export function getDevUser(): string {
  return localStorage.getItem(USER_KEY) ?? DEFAULT_USER;
}

export function setDevUser(email: string): void {
  localStorage.setItem(USER_KEY, email);
}

export function clearDevUser(): void {
  localStorage.removeItem(USER_KEY);
}

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = "ApiError";
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "X-Symmetry-User": getDevUser(),
      ...(options.headers ?? {}),
    },
  });
  if (!response.ok) {
    let message = response.statusText;
    try {
      const body = (await response.json()) as { detail?: string; title?: string };
      message = body.detail ?? body.title ?? message;
    } catch {
      message = response.statusText;
    }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

function body(value: unknown): RequestInit {
  return { body: JSON.stringify(value) };
}

export const api = {
  me: () => request<Me>("/me"),
  updateSettings: (values: Record<string, unknown>) =>
    request<Record<string, unknown>>("/me/settings", { method: "PATCH", ...body(values) }),

  listContracts: (page = 0, size = 50, moduleId?: string) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (moduleId) params.set("moduleId", moduleId);
    return request<Page<Contract>>(`/contracts?${params.toString()}`);
  },
  getContract: (moduleId: string, version: string) =>
    request<Contract>(`/contracts/${encodeURIComponent(moduleId)}/${encodeURIComponent(version)}`),
  workbench: (moduleId: string, version: string) =>
    request<Workbench>(
      `/contracts/${encodeURIComponent(moduleId)}/${encodeURIComponent(version)}/workbench`,
    ),
  publishContract: (input: Record<string, unknown>) =>
    request<Contract>("/contracts", { method: "POST", ...body(input) }),
  acceptContract: (id: string, input: Record<string, unknown>) =>
    request<Acceptance>(`/contracts/${id}/accept`, { method: "POST", ...body(input) }),

  workerSummary: () => request<Record<string, number | string>>("/worker/summary"),
  workerAcceptances: () => request<WorkerAcceptance[]>("/worker/acceptances"),
  workerSubmissions: () =>
    request<{ items: unknown[]; note: string }>("/worker/submissions"),
  workerEarnings: () =>
    request<{ currency: string; pendingCents: number; paidCents: number; items: unknown[] }>(
      "/worker/earnings",
    ),

  orgSummary: () =>
    request<Record<string, number | string> & { org: { id: string; slug: string; name: string } }>(
      "/org/summary",
    ),
  orgContracts: (page = 0, size = 50) =>
    request<Page<Contract>>(`/org/contracts?page=${page}&size=${size}`),
  orgWorkers: () => request<User[]>("/org/workers"),
  orgEvaluations: () => request<Record<string, unknown>>("/org/evaluations"),
  orgPayments: () =>
    request<{ currency: string; pendingCents: number; paidCents: number; note: string }>(
      "/org/payments",
    ),
  orgAnalytics: (days = 30) =>
    request<AnalyticsBundle>(`/org/analytics/overview?days=${days}`),
  orgSettings: () => request<Record<string, unknown>>("/org/settings/effective"),
  orgConfigRequests: (status?: string) =>
    request<Page<ConfigRequest>>(
      `/org/config-requests${status ? `?status=${status}` : ""}`,
    ),
  requestConfig: (input: { contractId?: string | null; key: string; value: unknown; reason: string }) =>
    request<ConfigRequest>("/org/config-requests", { method: "POST", ...body(input) }),

  adminSummary: () =>
    request<Record<string, number | Record<string, string>>>("/admin/summary"),
  adminAnalytics: (days = 30) =>
    request<AnalyticsBundle>(`/admin/analytics/overview?days=${days}`),
  adminOrganizations: (page = 0, size = 50) =>
    request<Page<Organization>>(`/admin/organizations?page=${page}&size=${size}`),
  adminUsers: (params: { role?: string; orgId?: string; page?: number; size?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.role) query.set("role", params.role);
    if (params.orgId) query.set("orgId", params.orgId);
    query.set("page", String(params.page ?? 0));
    query.set("size", String(params.size ?? 50));
    return request<Page<User>>(`/admin/users?${query.toString()}`);
  },
  adminHealth: () =>
    request<{ status: string; database: string; services: Record<string, string>; checkedAt: string }>(
      "/admin/troubleshooting/health",
    ),
  adminQueues: () =>
    request<Record<string, number | string>>("/admin/troubleshooting/queues"),
  adminErrors: () => request<{ items: unknown[]; note: string }>("/admin/troubleshooting/errors"),
  adminConfig: () =>
    request<{ keys: string[]; global: SettingRow[] }>("/admin/config"),
  setGlobalSetting: (key: string, value: unknown) =>
    request<SettingRow>(`/admin/config/global/${encodeURIComponent(key)}`, {
      method: "PUT",
      ...body({ value }),
    }),
  adminConfigRequests: (status?: string) =>
    request<Page<ConfigRequest>>(
      `/admin/config/requests${status ? `?status=${status}` : ""}`,
    ),
  approveConfigRequest: (id: string, note: string) =>
    request<ConfigRequest>(`/admin/config/requests/${id}/approve`, {
      method: "POST",
      ...body({ note }),
    }),
  rejectConfigRequest: (id: string, note: string) =>
    request<ConfigRequest>(`/admin/config/requests/${id}/reject`, {
      method: "POST",
      ...body({ note }),
    }),
  adminAudit: (params: { action?: string; actorId?: string; page?: number; size?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.action) query.set("action", params.action);
    if (params.actorId) query.set("actorId", params.actorId);
    query.set("page", String(params.page ?? 0));
    query.set("size", String(params.size ?? 50));
    return request<Page<AuditEntry>>(`/admin/audit?${query.toString()}`);
  },
};
