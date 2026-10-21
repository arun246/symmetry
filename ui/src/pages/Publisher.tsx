import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, type User } from "../api";
import { formatCents, formatDate, formatNumber } from "../format";
import { Bars, DataTableFallback, Donut, LineChart } from "../components/charts";
import {
  Badge,
  Card,
  DataTable,
  ErrorBox,
  Kpi,
  Loading,
  Notice,
  PageHead,
  Tabs,
  stateTone,
  type Column,
} from "../components/ui";

export function WorkersPage() {
  const workers = useQuery({ queryKey: ["org-workers"], queryFn: () => api.orgWorkers() });

  const columns: Column<User>[] = [
    { header: "Name", cell: (row) => row.displayName },
    { header: "Email", cell: (row) => <span className="mono">{row.email}</span> },
    { header: "Role", cell: (row) => <Badge plain>{row.role}</Badge> },
    {
      header: "Status",
      cell: (row) => <Badge tone={stateTone(row.status.toLowerCase())}>{row.status}</Badge>,
    },
    { header: "Joined", cell: (row) => <span className="small muted">{formatDate(row.createdAt)}</span> },
  ];

  return (
    <>
      <PageHead
        title="Workers"
        subtitle="People attached to your organization. Worker identities are separate from publisher accounts."
      />
      <Card title="Members" subtitle={`${workers.data?.length ?? 0} member(s)`}>
        {workers.isLoading ? (
          <Loading />
        ) : workers.isError ? (
          <ErrorBox>{workers.error.message}</ErrorBox>
        ) : (
          <DataTable
            columns={columns}
            rows={workers.data ?? []}
            rowKey={(row) => row.id}
            empty="No members in this organization yet."
          />
        )}
      </Card>
    </>
  );
}

export function EvaluationsPage() {
  const evaluations = useQuery({
    queryKey: ["org-evaluations"],
    queryFn: () => api.orgEvaluations(),
  });

  const overview = (evaluations.data?.overview ?? {}) as Record<string, number>;
  const verification = (evaluations.data?.verification ?? []) as { label: string; value: number }[];
  const funnel = (evaluations.data?.funnel ?? []) as { label: string; value: number }[];

  return (
    <>
      <PageHead
        title="Evaluations"
        subtitle="Correctness gates first; only passing submissions are ranked on resource metrics."
      />
      {evaluations.isLoading ? (
        <Loading />
      ) : evaluations.isError ? (
        <ErrorBox>{evaluations.error.message}</ErrorBox>
      ) : (
        <>
          <div className="grid grid-4">
            <Kpi label="Contracts" value={formatNumber(overview.contracts)} />
            <Kpi label="Accepted" value={formatNumber(overview.acceptances)} />
            <Kpi label="Verified" value={formatNumber(overview.verified)} />
            <Kpi label="Evaluator" value="offline" meta="sandbox not connected in this phase" />
          </div>
          <div className="grid grid-2" style={{ marginTop: 14 }}>
            <Card title="Verification" subtitle="Acceptances whose signature check passed.">
              <Donut data={verification} label="Acceptances" />
              <DataTableFallback data={verification} />
            </Card>
            <Card title="Funnel" subtitle="Published → accepted → verified.">
              <Bars data={funnel} />
              <DataTableFallback data={funnel} />
            </Card>
          </div>
          <Notice>
            Ranking, holdout scoring and resource metrics appear when the evaluator is online. The
            platform already stores the gate inputs and refusals.
          </Notice>
        </>
      )}
    </>
  );
}

export function AnalyticsPage() {
  const [days, setDays] = useState("30");
  const analytics = useQuery({
    queryKey: ["org-analytics", Number(days)],
    queryFn: () => api.orgAnalytics(Number(days)),
  });

  return (
    <>
      <PageHead
        title="Analytics"
        subtitle="Small, pre-aggregated charts. Every chart has a data table for screen readers."
        actions={
          <Tabs
            value={days}
            onValueChange={setDays}
            items={[
              { value: "7", label: "7 days" },
              { value: "30", label: "30 days" },
              { value: "90", label: "90 days" },
            ]}
          />
        }
      />
      {analytics.isLoading ? (
        <Loading />
      ) : analytics.isError ? (
        <ErrorBox>{analytics.error.message}</ErrorBox>
      ) : (
        <>
          <div className="grid grid-4">
            <Kpi label="Contracts" value={formatNumber(analytics.data?.overview.contracts)} />
            <Kpi label="Acceptances" value={formatNumber(analytics.data?.overview.acceptances)} />
            <Kpi label="Verified" value={formatNumber(analytics.data?.overview.verified)} />
            <Kpi label="Workers" value={formatNumber(analytics.data?.overview.workers)} />
          </div>
          <div className="grid grid-2" style={{ marginTop: 14 }}>
            <Card title="Contract states" subtitle="Distribution of job-card lifecycles.">
              <Donut data={analytics.data?.contractsByState ?? []} label="Contracts" />
              <DataTableFallback data={analytics.data?.contractsByState ?? []} />
            </Card>
            <Card title="Acceptances by day" subtitle={`Last ${days} days.`}>
              <LineChart data={analytics.data?.acceptancesByDay ?? []} />
              <DataTableFallback data={analytics.data?.acceptancesByDay ?? []} />
            </Card>
            <Card title="Top modules" subtitle="Where publishing is concentrated.">
              <Bars data={analytics.data?.topModules ?? []} />
              <DataTableFallback data={analytics.data?.topModules ?? []} />
            </Card>
            <Card title="Verification" subtitle="Signature check outcomes.">
              <Donut data={analytics.data?.verification ?? []} label="Checks" />
              <DataTableFallback data={analytics.data?.verification ?? []} />
            </Card>
          </div>
        </>
      )}
    </>
  );
}

export function PaymentsPage() {
  const payments = useQuery({ queryKey: ["org-payments"], queryFn: () => api.orgPayments() });

  return (
    <>
      <PageHead
        title="Payments"
        subtitle="Payouts and invoices. The settlement ledger is not connected in this phase."
      />
      {payments.isLoading ? (
        <Loading />
      ) : payments.isError ? (
        <ErrorBox>{payments.error.message}</ErrorBox>
      ) : (
        <>
          <div className="grid grid-3">
            <Kpi label="Pending" value={formatCents(payments.data?.pendingCents, payments.data?.currency)} />
            <Kpi label="Paid" value={formatCents(payments.data?.paidCents, payments.data?.currency)} />
            <Kpi label="Currency" value={payments.data?.currency ?? "USD"} />
          </div>
          <Card title="Invoices" subtitle="No invoices yet.">
            <Notice>{payments.data?.note}</Notice>
            <div className="empty" style={{ marginTop: 12 }}>
              <strong>No payment activity</strong>
              Payments will attach to verified, accepted work once settlement is online.
            </div>
          </Card>
        </>
      )}
    </>
  );
}
