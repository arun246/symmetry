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
  status: "ACTIVE" | "WITHDRAWN" | string;
  withdrawnAt: string | null;
}

export interface Withdrawal {
  acceptanceId: string;
  contractId: string;
  status: string;
  reasons: string[];
  comment: string | null;
  withdrawnAt: string;
}

export interface Exercise {
  id: string;
  title: string;
  summary: string;
  language: string;
  status: string;
  version: number;
  complexity: string;
  priceCents: number;
  currency: string;
  deadlineDays: number;
  publicTestCount: number;
  stubCount: number;
  parentExerciseId: string | null;
  publishedAt: string | null;
  createdAt: string;
}

export interface ExerciseSource {
  repoUrl: string;
  repoOwner: string;
  repoName: string;
  commitSha: string;
  filePath: string;
  blobSha: string | null;
  license: string | null;
  language: string;
  contentHash: string;
  content: string | null;
  findings: string[];
}

export interface ExerciseStub {
  id: string;
  symbolName: string;
  symbolKind: string;
  startLine: number;
  endLine: number;
  stubContent: string;
  originalHash: string;
}

export interface ExerciseTest {
  id: string;
  stubId: string | null;
  name: string;
  visibility: string;
  content: string;
  timeoutMs: number;
}

export interface ExerciseSideCase {
  id: string;
  testId: string | null;
  name: string;
  visibility: string;
  input: unknown;
  expected: unknown;
}

export interface ExerciseDetail {
  id: string;
  title: string;
  summary: string;
  language: string;
  status: string;
  version: number;
  complexity: string;
  priceCents: number;
  currency: string;
  deadlineDays: number;
  taskDocument: string;
  source: ExerciseSource | null;
  stubs: ExerciseStub[];
  tests: ExerciseTest[];
  sideCases: ExerciseSideCase[];
  parentExerciseId: string | null;
  createdBy: string;
  publishedAt: string | null;
  createdAt: string;
}

export interface RepoTree {
  repoUrl: string;
  repoOwner: string;
  repoName: string;
  commitSha: string;
  license: string | null;
  files: { path: string; sha: string; size: number }[];
}

export interface SymbolInfo {
  name: string;
  kind: string;
  startLine: number;
  endLine: number;
  bodyStartLine: number;
  bodyEndLine: number;
  signature: string;
}

export interface ExerciseAcceptance {
  id: string;
  exerciseId: string;
  workerId: string;
  taskDocumentHash: string;
  verified: boolean;
  status: string;
  acceptedAt: string;
  deadlineAt: string;
}

export interface Workspace {
  exercise: ExerciseDetail;
  sessionId: string;
  acceptanceId: string;
  status: string;
  deadlineAt: string;
  executionEnabled: boolean;
}

export interface StaticCheckResult {
  passed: boolean;
  findings: string[];
}

export interface ExerciseSubmission {
  id: string;
  exerciseId: string;
  workerId: string;
  status: string;
  contentHash: string;
  staticFindings: unknown;
  submittedAt: string;
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
export const DEFAULT_USER = "worker@symmetry.local";

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
  publishContractRecord: (id: string) =>
    request<Contract>(`/contracts/${id}/publish`, { method: "POST" }),
  acceptContract: (id: string, input: Record<string, unknown>) =>
    request<Acceptance>(`/contracts/${id}/accept`, { method: "POST", ...body(input) }),
  withdrawContract: (id: string, input: { reasons: string[]; comment?: string | null }) =>
    request<Withdrawal>(`/contracts/${id}/withdraw`, { method: "POST", ...body(input) }),

  listExercises: (page = 0, size = 50) =>
    request<Page<Exercise>>(`/exercises?page=${page}&size=${size}`),
  listMyExercises: (page = 0, size = 50) =>
    request<Page<Exercise>>(`/exercises/mine?page=${page}&size=${size}`),
  getExercise: (id: string) => request<ExerciseDetail>(`/exercises/${id}`),
  createExercise: (input: Record<string, unknown>) =>
    request<ExerciseDetail>("/exercises", { method: "POST", ...body(input) }),
  createSubtask: (parentId: string, input: Record<string, unknown>) =>
    request<ExerciseDetail>(`/exercises/${parentId}/subtasks`, { method: "POST", ...body(input) }),
  updateExercise: (id: string, input: Record<string, unknown>) =>
    request<ExerciseDetail>(`/exercises/${id}`, { method: "PATCH", ...body(input) }),
  githubTree: (repoUrl: string) =>
    request<RepoTree>(`/exercises/github/tree?repoUrl=${encodeURIComponent(repoUrl)}`),
  importExerciseSource: (id: string, repoUrl: string, filePath: string) =>
    request<ExerciseSource>(`/exercises/${id}/source`, {
      method: "POST",
      ...body({ repoUrl, filePath }),
    }),
  exerciseSymbols: (id: string) => request<SymbolInfo[]>(`/exercises/${id}/symbols`),
  addExerciseStub: (id: string, symbolName: string, startLine: number) =>
    request<ExerciseStub>(`/exercises/${id}/stubs`, {
      method: "POST",
      ...body({ symbolName, startLine }),
    }),
  saveExerciseTests: (id: string, tests: unknown[], sideCases: unknown[]) =>
    request<ExerciseTest[]>(`/exercises/${id}/tests`, {
      method: "PUT",
      ...body({ tests, sideCases }),
    }),
  publishExercise: (id: string) =>
    request<ExerciseDetail>(`/exercises/${id}/publish`, { method: "POST" }),
  acceptExercise: (id: string, input: Record<string, unknown>) =>
    request<ExerciseAcceptance>(`/exercises/${id}/accept`, { method: "POST", ...body(input) }),
  exerciseWorkspace: (id: string) => request<Workspace>(`/exercises/${id}/workspace`),
  staticCheckExercise: (id: string, content: string) =>
    request<StaticCheckResult>(`/exercises/${id}/static-check`, {
      method: "POST",
      ...body({ content }),
    }),
  submitExercise: (id: string, content: string) =>
    request<ExerciseSubmission>(`/exercises/${id}/submissions`, {
      method: "POST",
      ...body({ content }),
    }),
  exerciseSubmissions: (id: string) =>
    request<ExerciseSubmission[]>(`/exercises/${id}/submissions`),
  acceptedExercises: () => request<ExerciseAcceptance[]>("/exercises/accepted"),

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
