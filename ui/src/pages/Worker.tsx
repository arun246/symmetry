import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api";
import { formatCents, formatDate } from "../format";
import {
  Badge,
  Card,
  DataTable,
  ErrorBox,
  Kpi,
  Loading,
  Notice,
  PageHead,
  stateTone,
  type Column,
} from "../components/ui";
import type { WorkerAcceptance } from "../api";

export function AcceptancesPage() {
  const acceptances = useQuery({
    queryKey: ["worker-acceptances"],
    queryFn: () => api.workerAcceptances(),
  });

  const columns: Column<WorkerAcceptance>[] = [
    {
      header: "Module",
      cell: (row) => (
        <Link to={`/user/contracts/${encodeURIComponent(row.moduleId)}/${encodeURIComponent(row.version)}`}>
          {row.moduleId}
        </Link>
      ),
    },
    { header: "Version", cell: (row) => <span className="mono">{row.version}</span> },
    {
      header: "Document hash",
      cell: (row) => <span className="mono">{row.taskDocumentHash.slice(0, 16)}…</span>,
    },
    {
      header: "Verified",
      cell: (row) => <Badge tone={row.verified ? "success" : "danger"}>{row.verified ? "verified" : "failed"}</Badge>,
    },
    {
      header: "Lifecycle",
      cell: (row) => <Badge tone={stateTone(row.lifecycleState)}>{row.lifecycleState}</Badge>,
    },
    {
      header: "Status",
      cell: (row) => (
        <Badge tone={row.status === "WITHDRAWN" ? "warn" : "success"}>
          {row.status === "WITHDRAWN" ? "withdrawn" : "active"}
        </Badge>
      ),
    },
    {
      header: "Accepted",
      cell: (row) => (
        <span className="small muted">
          {formatDate(row.acceptedAt)}
          {row.withdrawnAt && <> · nullified {formatDate(row.withdrawnAt)}</>}
        </span>
      ),
    },
  ];

  return (
    <>
      <PageHead
        title="My acceptances"
        subtitle="Every signed task document is bound to a contract version and a hash of the exact text you saw."
      />
      {acceptances.isLoading ? (
        <Loading />
      ) : acceptances.isError ? (
        <ErrorBox>{acceptances.error.message}</ErrorBox>
      ) : (
        <Card title="Signed task documents" subtitle={`${acceptances.data?.length ?? 0} record(s)`}>
          <DataTable
            columns={columns}
            rows={acceptances.data ?? []}
            rowKey={(row) => row.id}
            empty="You have not accepted a contract yet. Browse the registry to start."
          />
        </Card>
      )}
    </>
  );
}

export function SubmissionsPage() {
  const submissions = useQuery({
    queryKey: ["worker-submissions"],
    queryFn: () => api.workerSubmissions(),
  });

  return (
    <>
      <PageHead
        title="Submissions"
        subtitle="Work you send to the evaluator, with gate results and resource metrics."
      />
      <Card title="Evaluator queue" subtitle="The evaluator is offline in this phase.">
        {submissions.isLoading ? (
          <Loading />
        ) : submissions.isError ? (
          <ErrorBox>{submissions.error.message}</ErrorBox>
        ) : (
          <>
            <Notice>{submissions.data?.note}</Notice>
            <div className="empty" style={{ marginTop: 12 }}>
              <strong>No submissions yet</strong>
              A submission is eligible only when it has a matching signed acceptance.
            </div>
          </>
        )}
      </Card>
    </>
  );
}

export function EarningsPage() {
  const earnings = useQuery({
    queryKey: ["worker-earnings"],
    queryFn: () => api.workerEarnings(),
  });

  return (
    <>
      <PageHead
        title="Earnings"
        subtitle="Payouts attach to verified work. Payments are not connected in this phase."
      />
      {earnings.isLoading ? (
        <Loading />
      ) : earnings.isError ? (
        <ErrorBox>{earnings.error.message}</ErrorBox>
      ) : (
        <>
          <div className="grid grid-3">
            <Kpi label="Pending" value={formatCents(earnings.data?.pendingCents, earnings.data?.currency)} />
            <Kpi label="Paid" value={formatCents(earnings.data?.paidCents, earnings.data?.currency)} />
            <Kpi label="Currency" value={earnings.data?.currency ?? "USD"} />
          </div>
          <Card title="Payout history" subtitle="No ledger entries yet.">
            <div className="empty">
              <strong>Nothing paid out</strong>
              The payment ledger arrives with the evaluation and settlement phase.
            </div>
          </Card>
        </>
      )}
    </>
  );
}
