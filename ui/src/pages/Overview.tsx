import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api";
import { useMe } from "../auth";
import { formatDate, formatNumber } from "../format";
import { Bars, DataTableFallback, Donut, LineChart } from "../components/charts";
import {
  Badge,
  Button,
  Card,
  ErrorBox,
  Kpi,
  Loading,
  Notice,
  PageHead,
  stateTone,
} from "../components/ui";

function WorkerOverview() {
  const me = useMe();
  const summary = useQuery({ queryKey: ["worker-summary"], queryFn: () => api.workerSummary() });
  const contracts = useQuery({
    queryKey: ["contracts", 0, 5],
    queryFn: () => api.listContracts(0, 5),
  });

  return (
    <>
      <PageHead
        title={`Welcome, ${me.data?.name ?? "worker"}`}
        subtitle="Your signed acceptances, current work and the contracts that are open to you."
        actions={
          <Link to="/user/contracts">
            <Button variant="primary">Browse contracts</Button>
          </Link>
        }
      />
      {summary.isError && <ErrorBox>{summary.error.message}</ErrorBox>}
      <div className="grid grid-4">
        <Kpi label="Acceptances" value={formatNumber(summary.data?.acceptances)} meta="signed task documents" />
        <Kpi label="Verified" value={formatNumber(summary.data?.verified)} meta="signature checks passed" />
        <Kpi label="Pending submissions" value={formatNumber(summary.data?.pendingSubmissions)} meta="evaluator is offline" />
        <Kpi label="Earnings pending" value={formatNumber(summary.data?.earningsPendingCents)} meta="payments are not connected in V1" />
      </div>

      <div className="grid grid-2" style={{ marginTop: 14 }}>
        <Card
          title="Open contracts"
          subtitle="The newest published job cards."
          actions={
            <Link to="/user/contracts" className="small">
              View all
            </Link>
          }
        >
          {contracts.isLoading ? (
            <Loading />
          ) : contracts.isError ? (
            <ErrorBox>{contracts.error.message}</ErrorBox>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Module</th>
                    <th>Version</th>
                    <th>Tier</th>
                    <th>State</th>
                  </tr>
                </thead>
                <tbody>
                  {(contracts.data?.content ?? []).map((contract) => (
                    <tr key={contract.id}>
                      <td>
                        <Link to={`/user/contracts/${encodeURIComponent(contract.moduleId)}/${encodeURIComponent(contract.version)}`}>
                          {contract.moduleId}
                        </Link>
                      </td>
                      <td className="mono">{contract.version}</td>
                      <td>{contract.disclosureTier}</td>
                      <td>
                        <Badge tone={stateTone(contract.lifecycleState)}>{contract.lifecycleState}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        <Card title="How acceptance works" subtitle="Correctness is a gate; the evaluator ranks only what passes.">
          <ol className="small" style={{ margin: 0, paddingLeft: 18, lineHeight: 1.7 }}>
            <li>Open a contract and read the task document at the disclosure tier you are allowed.</li>
            <li>Accept the six agreements and sign the canonical message with your Ed25519 key.</li>
            <li>The platform records a verified acceptance bound to this exact version.</li>
            <li>Submit work; a submission without a matching acceptance is ineligible.</li>
          </ol>
          <Notice>
            The evaluator and AI helper are offline in this phase. Acceptance, auditing and the
            dashboard are live.
          </Notice>
        </Card>
      </div>
    </>
  );
}

function PublisherOverview() {
  const me = useMe();
  const summary = useQuery({ queryKey: ["org-summary"], queryFn: () => api.orgSummary() });
  const analytics = useQuery({
    queryKey: ["org-analytics", 30],
    queryFn: () => api.orgAnalytics(30),
  });
  const contracts = useQuery({
    queryKey: ["org-contracts", 0, 5],
    queryFn: () => api.orgContracts(0, 5),
  });

  const org = summary.data?.org;

  return (
    <>
      <PageHead
        title={`${org?.name ?? "Organization"} overview`}
        subtitle="Published contracts, worker activity and the correctness gate at a glance."
        actions={
          <Link to="/user/contracts">
            <Button variant="primary">Publish contract</Button>
          </Link>
        }
      />
      {summary.isError && <ErrorBox>{summary.error.message}</ErrorBox>}
      <div className="grid grid-4">
        <Kpi label="Contracts" value={formatNumber(summary.data?.contracts)} meta="owned by this organization" />
        <Kpi label="Acceptances" value={formatNumber(summary.data?.acceptances)} meta="signed task documents" />
        <Kpi label="Verified" value={formatNumber(summary.data?.verified)} meta="contracts with a passing acceptance" />
        <Kpi label="Members" value={formatNumber(summary.data?.workers)} meta={`organization ${me.data?.orgSlug ?? ""}`} />
      </div>

      <div className="grid grid-2" style={{ marginTop: 14 }}>
        <Card title="Contract states" subtitle="Every job card in this organization.">
          {analytics.isLoading ? (
            <Loading />
          ) : (
            <>
              <Donut data={analytics.data?.contractsByState ?? []} label="Contracts" />
              <DataTableFallback data={analytics.data?.contractsByState ?? []} />
            </>
          )}
        </Card>
        <Card title="Acceptances by day" subtitle="Last 30 days, grouped in SQL.">
          {analytics.isLoading ? <Loading /> : <LineChart data={analytics.data?.acceptancesByDay ?? []} />}
        </Card>
      </div>

      <div className="grid grid-2" style={{ marginTop: 14 }}>
        <Card title="Top modules" subtitle="Where this organization publishes most.">
          {analytics.isLoading ? (
            <Loading />
          ) : (
            <>
              <Bars data={analytics.data?.topModules ?? []} />
              <DataTableFallback data={analytics.data?.topModules ?? []} />
            </>
          )}
        </Card>
        <Card
          title="Recent contracts"
          subtitle="Newest job cards."
          actions={
            <Link to="/user/contracts" className="small">
              View all
            </Link>
          }
        >
          {contracts.isLoading ? (
            <Loading />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Module</th>
                    <th>Version</th>
                    <th>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {(contracts.data?.content ?? []).map((contract) => (
                    <tr key={contract.id}>
                      <td>
                        <Link to={`/user/contracts/${encodeURIComponent(contract.moduleId)}/${encodeURIComponent(contract.version)}`}>
                          {contract.moduleId}
                        </Link>
                      </td>
                      <td className="mono">{contract.version}</td>
                      <td className="small muted">{formatDate(contract.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>
    </>
  );
}

export function OverviewPage() {
  const me = useMe();
  return me.data?.role === "PUBLISHER" ? <PublisherOverview /> : <WorkerOverview />;
}
