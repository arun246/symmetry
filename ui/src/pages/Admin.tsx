import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type AuditEntry, type ConfigRequest, type Organization, type User } from "../api";
import { formatDate, formatJson, formatNumber } from "../format";
import { Bars, DataTableFallback, Donut, LineChart } from "../components/charts";
import {
  Badge,
  Button,
  Card,
  DataTable,
  Dialog,
  ErrorBox,
  Field,
  Kpi,
  Loading,
  Notice,
  PageHead,
  Tabs,
  stateTone,
  type Column,
} from "../components/ui";

function numberFrom(record: Record<string, unknown> | undefined, key: string): number {
  return Number(record?.[key] ?? 0);
}

export function AdminOverviewPage() {
  const summary = useQuery({ queryKey: ["admin-summary"], queryFn: () => api.adminSummary() });
  const queues = useQuery({ queryKey: ["admin-queues"], queryFn: () => api.adminQueues() });

  if (summary.isLoading) return <Loading label="Loading platform summary" />;
  if (summary.isError) return <ErrorBox>{summary.error.message}</ErrorBox>;

  const services = (summary.data?.services ?? {}) as Record<string, string>;

  return (
    <>
      <PageHead
        title="Platform overview"
        subtitle="Live totals and service health. Admin is read-only except for audited configuration overrides."
        actions={
          <Link className="btn small" to="/user/admin/troubleshooting">
            Troubleshooting
          </Link>
        }
      />
      <div className="grid grid-4">
        <Kpi label="Contracts" value={formatNumber(numberFrom(summary.data, "contracts"))} />
        <Kpi label="Acceptances" value={formatNumber(numberFrom(summary.data, "acceptances"))} />
        <Kpi label="Verified" value={formatNumber(numberFrom(summary.data, "verified"))} />
        <Kpi label="Workers" value={formatNumber(numberFrom(summary.data, "workers"))} />
        <Kpi label="Organizations" value={formatNumber(numberFrom(summary.data, "organizations"))} />
        <Kpi label="Pending config" value={formatNumber(numberFrom(summary.data, "pendingConfigRequests"))} />
        <Kpi label="Audit events" value={formatNumber(numberFrom(summary.data, "auditEvents"))} />
        <Kpi label="Queues" value={formatNumber(queues.data?.submissions)} meta="evaluator offline" />
      </div>

      <div className="grid grid-2" style={{ marginTop: 14 }}>
        <Card title="Services" subtitle="What is online right now.">
          <div className="grid grid-2">
            {Object.entries(services).map(([name, status]) => (
              <div key={name} className="row spread" style={{ padding: "7px 0" }}>
                <span style={{ textTransform: "capitalize" }}>{name}</span>
                <Badge tone={stateTone(status)}>{status}</Badge>
              </div>
            ))}
          </div>
        </Card>
        <Card title="Queues" subtitle="Backlog across the pipeline.">
          <dl className="kv">
            <dt>Submissions</dt>
            <dd>{formatNumber(queues.data?.submissions)}</dd>
            <dt>Evaluations</dt>
            <dd>{formatNumber(queues.data?.evaluations)}</dd>
            <dt>Pending config requests</dt>
            <dd>{formatNumber(queues.data?.pendingConfigRequests)}</dd>
            <dt>Evaluator</dt>
            <dd>
              <Badge tone="danger">{String(queues.data?.evaluator ?? "offline")}</Badge>
            </dd>
          </dl>
        </Card>
      </div>

      <Notice>
        AI and WASM evaluation are intentionally offline in this phase. The platform still records
        acceptances, publishes contracts and enforces roles.
      </Notice>
    </>
  );
}

function AnalyticsPanel() {
  const [days, setDays] = useState("30");
  const analytics = useQuery({
    queryKey: ["admin-analytics", Number(days)],
    queryFn: () => api.adminAnalytics(Number(days)),
  });

  if (analytics.isLoading) return <Loading />;
  if (analytics.isError) return <ErrorBox>{analytics.error.message}</ErrorBox>;

  return (
    <>
      <div className="row spread" style={{ marginBottom: 14 }}>
        <span className="small muted">Aggregates are computed in PostgreSQL.</span>
        <Tabs
          value={days}
          onValueChange={setDays}
          items={[
            { value: "7", label: "7 days" },
            { value: "30", label: "30 days" },
            { value: "90", label: "90 days" },
          ]}
        />
      </div>
      <div className="grid grid-4">
        <Kpi label="Contracts" value={formatNumber(analytics.data?.overview.contracts)} />
        <Kpi label="Acceptances" value={formatNumber(analytics.data?.overview.acceptances)} />
        <Kpi label="Verified" value={formatNumber(analytics.data?.overview.verified)} />
        <Kpi label="Workers" value={formatNumber(analytics.data?.overview.workers)} />
      </div>
      <div className="grid grid-2" style={{ marginTop: 14 }}>
        <Card title="Contract states" subtitle="All organizations.">
          <Donut data={analytics.data?.contractsByState ?? []} label="Contracts" />
          <DataTableFallback data={analytics.data?.contractsByState ?? []} />
        </Card>
        <Card title="Acceptances by day" subtitle={`Last ${days} days.`}>
          <LineChart data={analytics.data?.acceptancesByDay ?? []} />
          <DataTableFallback data={analytics.data?.acceptancesByDay ?? []} />
        </Card>
        <Card title="Top modules" subtitle="Platform-wide publishing concentration.">
          <Bars data={analytics.data?.topModules ?? []} />
          <DataTableFallback data={analytics.data?.topModules ?? []} />
        </Card>
        <Card title="Funnel" subtitle="Published → accepted → verified.">
          <Bars data={analytics.data?.funnel ?? []} />
          <DataTableFallback data={analytics.data?.funnel ?? []} />
        </Card>
      </div>
    </>
  );
}

export function AdminAnalyticsPage() {
  return (
    <>
      <PageHead title="Platform analytics" subtitle="Donut, bar and line only. Each chart is paired with a data table." />
      <AnalyticsPanel />
    </>
  );
}

export function OrganizationsPage() {
  const organizations = useQuery({
    queryKey: ["admin-organizations"],
    queryFn: () => api.adminOrganizations(0, 50),
  });

  const columns: Column<Organization>[] = [
    { header: "Name", cell: (row) => row.name },
    { header: "Slug", cell: (row) => <span className="mono">{row.slug}</span> },
    {
      header: "Status",
      cell: (row) => <Badge tone={stateTone(row.status.toLowerCase())}>{row.status}</Badge>,
    },
    { header: "Contracts", cell: (row) => <span className="num">{row.contracts}</span>, align: "right" },
    { header: "Members", cell: (row) => <span className="num">{row.workers}</span>, align: "right" },
    { header: "Created", cell: (row) => <span className="small muted">{formatDate(row.createdAt)}</span> },
  ];

  return (
    <>
      <PageHead
        title="Organizations"
        subtitle="Read-only directory. Organizations can request scoped configuration changes; admins review them."
      />
      {organizations.isLoading ? (
        <Loading />
      ) : organizations.isError ? (
        <ErrorBox>{organizations.error.message}</ErrorBox>
      ) : (
        <Card title="Directory" subtitle={`${organizations.data?.totalElements ?? 0} organization(s)`}>
          <DataTable
            columns={columns}
            rows={organizations.data?.content ?? []}
            rowKey={(row) => row.id}
            empty="No organizations yet."
          />
        </Card>
      )}
    </>
  );
}

export function UsersPage() {
  const [role, setRole] = useState("ALL");
  const users = useQuery({
    queryKey: ["admin-users", role],
    queryFn: () => api.adminUsers(role === "ALL" ? {} : { role }),
  });

  const columns: Column<User>[] = [
    { header: "Name", cell: (row) => row.displayName },
    { header: "Email", cell: (row) => <span className="mono">{row.email}</span> },
    { header: "Role", cell: (row) => <Badge plain>{row.role}</Badge> },
    { header: "Organization", cell: (row) => <span className="mono">{row.orgId ?? "—"}</span> },
    {
      header: "Status",
      cell: (row) => <Badge tone={stateTone(row.status.toLowerCase())}>{row.status}</Badge>,
    },
    { header: "Created", cell: (row) => <span className="small muted">{formatDate(row.createdAt)}</span> },
  ];

  return (
    <>
      <PageHead
        title="Users"
        subtitle="Read-only identities mirrored from Keycloak. Credentials and MFA stay in Keycloak."
        actions={
          <select className="select" value={role} onChange={(event) => setRole(event.target.value)}>
            <option value="ALL">All roles</option>
            <option value="WORKER">Workers</option>
            <option value="PUBLISHER">Publishers</option>
            <option value="ADMIN">Admins</option>
          </select>
        }
      />
      {users.isLoading ? (
        <Loading />
      ) : users.isError ? (
        <ErrorBox>{users.error.message}</ErrorBox>
      ) : (
        <Card title="Directory" subtitle={`${users.data?.totalElements ?? 0} account(s)`}>
          <DataTable
            columns={columns}
            rows={users.data?.content ?? []}
            rowKey={(row) => row.id}
            empty="No accounts match this filter."
          />
        </Card>
      )}
    </>
  );
}

export function TroubleshootingPage() {
  const queryClient = useQueryClient();
  const health = useQuery({ queryKey: ["admin-health"], queryFn: () => api.adminHealth() });
  const queues = useQuery({ queryKey: ["admin-queues"], queryFn: () => api.adminQueues() });
  const errors = useQuery({ queryKey: ["admin-errors"], queryFn: () => api.adminErrors() });

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ["admin-health"] });
    queryClient.invalidateQueries({ queryKey: ["admin-queues"] });
    queryClient.invalidateQueries({ queryKey: ["admin-errors"] });
  };

  return (
    <>
      <PageHead
        title="Troubleshooting"
        subtitle="Live health, queues and recent errors. Realtime updates use SSE when connected."
        actions={<Button onClick={refresh}>Refresh</Button>}
      />
      {health.isLoading ? (
        <Loading />
      ) : health.isError ? (
        <ErrorBox>{health.error.message}</ErrorBox>
      ) : (
        <div className="grid grid-3">
          <Card title="Health" subtitle={`Checked ${formatDate(health.data?.checkedAt)}`}>
            <div className="row spread">
              <span>Overall</span>
              <Badge tone={stateTone(String(health.data?.status).toLowerCase())}>{health.data?.status}</Badge>
            </div>
            <div className="row spread" style={{ marginTop: 8 }}>
              <span>Database</span>
              <Badge tone={stateTone(String(health.data?.database).toLowerCase())}>{health.data?.database}</Badge>
            </div>
          </Card>
          <Card title="Services" subtitle="What the API can reach.">
            {Object.entries((health.data?.services ?? {}) as Record<string, string>).map(([name, status]) => (
              <div key={name} className="row spread" style={{ padding: "5px 0" }}>
                <span style={{ textTransform: "capitalize" }}>{name}</span>
                <Badge tone={stateTone(status)}>{status}</Badge>
              </div>
            ))}
          </Card>
          <Card title="Queues" subtitle="Evaluator backlog.">
            <dl className="kv">
              <dt>Submissions</dt>
              <dd>{formatNumber(queues.data?.submissions)}</dd>
              <dt>Evaluations</dt>
              <dd>{formatNumber(queues.data?.evaluations)}</dd>
              <dt>Pending config</dt>
              <dd>{formatNumber(queues.data?.pendingConfigRequests)}</dd>
            </dl>
          </Card>
        </div>
      )}

      <Card title="Recent errors" subtitle="In-process buffer.">
        {errors.isLoading ? (
          <Loading />
        ) : errors.isError ? (
          <ErrorBox>{errors.error.message}</ErrorBox>
        ) : (errors.data?.items ?? []).length === 0 ? (
          <Notice>{errors.data?.note}</Notice>
        ) : (
          <pre className="code">{formatJson(errors.data?.items)}</pre>
        )}
      </Card>
    </>
  );
}

export function ConfigurationPage() {
  const queryClient = useQueryClient();
  const config = useQuery({ queryKey: ["admin-config"], queryFn: () => api.adminConfig() });
  const [key, setKey] = useState("worker.maxConcurrent");
  const [value, setValue] = useState("8");
  const [message, setMessage] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () => {
      let parsed: unknown;
      try {
        parsed = JSON.parse(value);
      } catch {
        throw new Error("Value must be valid JSON, for example 8, true, or {\"time\": 0.5}");
      }
      return api.setGlobalSetting(key, parsed);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-config"] });
      setMessage(`Saved ${key}.`);
    },
    onError: (error: Error) => setMessage(error.message),
  });

  const globalColumns: Column<{ key: string; value: unknown; updatedAt: string }>[] = [
    { header: "Key", cell: (row) => <span className="mono">{row.key}</span> },
    { header: "Value", cell: (row) => <span className="mono">{formatJson(row.value)}</span> },
    { header: "Updated", cell: (row) => <span className="small muted">{formatDate(row.updatedAt)}</span> },
  ];

  return (
    <>
      <PageHead
        title="Configuration"
        subtitle="Typed, allow-listed keys. Every write is audited with actor and before/after values."
      />
      {message && <Notice>{message}</Notice>}
      <div className="grid grid-2">
        <Card title="Set a global value" subtitle="Global is the fallback when no org, contract or user override exists.">
          <Field label="Key">
            <select className="select" value={key} onChange={(event) => setKey(event.target.value)}>
              {(config.data?.keys ?? []).map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Value (JSON)" hint="Numbers, booleans, strings and objects are all valid.">
            <textarea className="textarea" value={value} onChange={(event) => setValue(event.target.value)} />
          </Field>
          <Button variant="primary" disabled={save.isPending} onClick={() => save.mutate()}>
            {save.isPending ? "Saving" : "Save global value"}
          </Button>
        </Card>

        <Card title="Known keys" subtitle="Anything else is rejected so typos cannot look applied.">
          <div className="row wrap">
            {(config.data?.keys ?? []).map((item) => (
              <span key={item} className="badge plain mono">
                {item}
              </span>
            ))}
          </div>
          <p className="small muted" style={{ marginBottom: 0, marginTop: 12 }}>
            Organization and contract overrides arrive through the Requests queue, not this form.
          </p>
        </Card>
      </div>

      <Card title="Global settings" subtitle="Current fallback values.">
        {config.isLoading ? (
          <Loading />
        ) : config.isError ? (
          <ErrorBox>{config.error.message}</ErrorBox>
        ) : (
          <DataTable
            columns={globalColumns}
            rows={(config.data?.global ?? []).map((row) => ({
              key: row.key,
              value: row.value,
              updatedAt: row.updatedAt,
            }))}
            rowKey={(row) => row.key}
            empty="No global overrides. Built-in defaults apply."
          />
        )}
      </Card>
    </>
  );
}

export function RequestsPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState("PENDING");
  const requests = useQuery({
    queryKey: ["admin-requests", status],
    queryFn: () => api.adminConfigRequests(status === "ALL" ? undefined : status),
  });
  const [decision, setDecision] = useState<{ request: ConfigRequest; approve: boolean } | null>(null);
  const [note, setNote] = useState("");

  const review = useMutation({
    mutationFn: () => {
      if (!decision) throw new Error("No request selected");
      return decision.approve
        ? api.approveConfigRequest(decision.request.id, note)
        : api.rejectConfigRequest(decision.request.id, note);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-requests"] });
      queryClient.invalidateQueries({ queryKey: ["admin-summary"] });
      setDecision(null);
      setNote("");
    },
  });

  const columns: Column<ConfigRequest>[] = [
    { header: "Key", cell: (row) => <span className="mono">{row.key}</span> },
    { header: "Value", cell: (row) => <span className="mono">{formatJson(row.value)}</span> },
    { header: "Reason", cell: (row) => <span className="small">{row.reason}</span> },
    {
      header: "Scope",
      cell: (row) => <Badge plain>{row.contractId ? "CONTRACT" : "ORG"}</Badge>,
    },
    {
      header: "Status",
      cell: (row) => <Badge tone={stateTone(row.status.toLowerCase())}>{row.status}</Badge>,
    },
    { header: "Requested", cell: (row) => <span className="small muted">{formatDate(row.createdAt)}</span> },
    {
      header: "",
      align: "right",
      cell: (row) =>
        row.status === "PENDING" ? (
          <div className="row" style={{ justifyContent: "flex-end" }}>
            <Button small variant="primary" onClick={() => setDecision({ request: row, approve: true })}>
              Approve
            </Button>
            <Button small variant="danger" onClick={() => setDecision({ request: row, approve: false })}>
              Reject
            </Button>
          </div>
        ) : (
          <span className="small muted">{row.reviewNote ?? "—"}</span>
        ),
    },
  ];

  return (
    <>
      <PageHead
        title="Configuration requests"
        subtitle="Organizations ask for a task-specific tweak. Approval applies the value and writes two audit entries."
        actions={
          <select className="select" value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="PENDING">Pending</option>
            <option value="APPLIED">Applied</option>
            <option value="REJECTED">Rejected</option>
            <option value="ALL">All</option>
          </select>
        }
      />
      {requests.isLoading ? (
        <Loading />
      ) : requests.isError ? (
        <ErrorBox>{requests.error.message}</ErrorBox>
      ) : (
        <Card title="Queue" subtitle={`${requests.data?.totalElements ?? 0} request(s)`}>
          <DataTable
            columns={columns}
            rows={requests.data?.content ?? []}
            rowKey={(row) => row.id}
            empty="No requests in this state."
          />
        </Card>
      )}

      <Dialog
        open={decision !== null}
        onOpenChange={(open) => {
          if (!open) setDecision(null);
        }}
        title={decision?.approve ? "Approve configuration request" : "Reject configuration request"}
        subtitle={decision ? `${decision.request.key} · ${decision.request.reason}` : undefined}
        footer={
          <>
            <Button onClick={() => setDecision(null)}>Cancel</Button>
            <Button
              variant={decision?.approve ? "primary" : "danger"}
              disabled={review.isPending || (decision?.approve === false && note.trim() === "")}
              onClick={() => review.mutate()}
            >
              {review.isPending ? "Saving" : decision?.approve ? "Approve and apply" : "Reject"}
            </Button>
          </>
        }
      >
        {review.isError && <ErrorBox>{review.error.message}</ErrorBox>}
        <Field
          label={decision?.approve ? "Review note" : "Reason (required)"}
          hint={decision?.approve ? "Stored with the setting's audit trail." : "Tell the organization why."}
        >
          <textarea className="textarea" value={note} onChange={(event) => setNote(event.target.value)} />
        </Field>
      </Dialog>
    </>
  );
}

export function AuditPage() {
  const [action, setAction] = useState("");
  const [applied, setApplied] = useState("");
  const audit = useQuery({
    queryKey: ["admin-audit", applied],
    queryFn: () => api.adminAudit({ action: applied || undefined }),
  });

  const columns: Column<AuditEntry>[] = [
    { header: "Time", cell: (row) => <span className="small muted">{formatDate(row.createdAt)}</span> },
    {
      header: "Actor",
      cell: (row) => (
        <span>
          <span className="mono">{row.actorEmail ?? "system"}</span>
          <span className="small muted"> · {row.actorRole ?? "SYSTEM"}</span>
        </span>
      ),
    },
    { header: "Action", cell: (row) => <Badge plain>{row.action}</Badge> },
    {
      header: "Target",
      cell: (row) => (
        <span className="mono">
          {row.targetType}:{row.targetId ?? "—"}
        </span>
      ),
    },
    {
      header: "Details",
      cell: (row) => (
        <details className="small">
          <summary>Before / after</summary>
          <pre className="code" style={{ marginTop: 6 }}>
            {formatJson({ before: row.before, after: row.after })}
          </pre>
        </details>
      ),
    },
  ];

  return (
    <>
      <PageHead
        title="Audit log"
        subtitle="Append-only. There is no update or delete path; the API only inserts and reads."
        actions={
          <form
            className="row"
            onSubmit={(event) => {
              event.preventDefault();
              setApplied(action.trim());
            }}
          >
            <input
              className="input"
              style={{ width: 220 }}
              placeholder="Filter by action"
              value={action}
              onChange={(event) => setAction(event.target.value)}
            />
            <Button small type="submit">
              Filter
            </Button>
          </form>
        }
      />
      {audit.isLoading ? (
        <Loading />
      ) : audit.isError ? (
        <ErrorBox>{audit.error.message}</ErrorBox>
      ) : (
        <Card title="Entries" subtitle={`${audit.data?.totalElements ?? 0} event(s)`}>
          <DataTable
            columns={columns}
            rows={audit.data?.content ?? []}
            rowKey={(row) => String(row.id)}
            empty="No audit entries match this filter."
          />
        </Card>
      )}
    </>
  );
}
