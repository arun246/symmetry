import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { roleHome, useMe } from "./auth";
import { Loading } from "./components/ui";
import { Shell, RequireRole } from "./components/Shell";
import { Login } from "./pages/Login";
import { OverviewPage } from "./pages/Overview";
import { ContractDetailPage, ContractsPage, WorkbenchPage } from "./pages/Contracts";
import { AcceptancesPage, EarningsPage, SubmissionsPage } from "./pages/Worker";
import { AnalyticsPage, EvaluationsPage, PaymentsPage, WorkersPage } from "./pages/Publisher";
import { SettingsPage } from "./pages/Settings";
import {
  AdminAnalyticsPage,
  AdminOverviewPage,
  AuditPage,
  ConfigurationPage,
  OrganizationsPage,
  RequestsPage,
  TroubleshootingPage,
  UsersPage,
} from "./pages/Admin";

function Landing() {
  const me = useMe();
  if (me.isLoading) {
    return (
      <div className="window">
        <div className="login-shell">
          <Loading label="Starting Symmetry" />
        </div>
      </div>
    );
  }
  if (me.data) {
    return <Navigate to={roleHome[me.data.role]} replace />;
  }
  return <Navigate to="/user/login" replace />;
}

function NotFound() {
  return (
    <div className="empty">
      <strong>Page not found</strong>
      Use the sidebar to return to a page that exists.
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/user/login" element={<Login />} />
        <Route path="/user/admin/login" element={<Login admin />} />
        <Route path="/user" element={<Shell />}>
          <Route index element={<Navigate to="overview" replace />} />
          <Route path="overview" element={<OverviewPage />} />
          <Route path="contracts" element={<ContractsPage />} />
          <Route path="contracts/:moduleId/:version" element={<ContractDetailPage />} />
          <Route
            path="contracts/:moduleId/:version/workbench"
            element={
              <RequireRole allow={["WORKER"]}>
                <WorkbenchPage />
              </RequireRole>
            }
          />
          <Route
            path="acceptances"
            element={
              <RequireRole allow={["WORKER"]}>
                <AcceptancesPage />
              </RequireRole>
            }
          />
          <Route
            path="submissions"
            element={
              <RequireRole allow={["WORKER"]}>
                <SubmissionsPage />
              </RequireRole>
            }
          />
          <Route
            path="earnings"
            element={
              <RequireRole allow={["WORKER"]}>
                <EarningsPage />
              </RequireRole>
            }
          />
          <Route
            path="workers"
            element={
              <RequireRole allow={["PUBLISHER"]}>
                <WorkersPage />
              </RequireRole>
            }
          />
          <Route
            path="evaluations"
            element={
              <RequireRole allow={["PUBLISHER"]}>
                <EvaluationsPage />
              </RequireRole>
            }
          />
          <Route
            path="analytics"
            element={
              <RequireRole allow={["PUBLISHER"]}>
                <AnalyticsPage />
              </RequireRole>
            }
          />
          <Route
            path="payments"
            element={
              <RequireRole allow={["PUBLISHER"]}>
                <PaymentsPage />
              </RequireRole>
            }
          />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="admin" element={<Navigate to="/user/admin/overview" replace />} />
          <Route
            path="admin/overview"
            element={
              <RequireRole allow={["ADMIN"]}>
                <AdminOverviewPage />
              </RequireRole>
            }
          />
          <Route
            path="admin/analytics"
            element={
              <RequireRole allow={["ADMIN"]}>
                <AdminAnalyticsPage />
              </RequireRole>
            }
          />
          <Route
            path="admin/orgs"
            element={
              <RequireRole allow={["ADMIN"]}>
                <OrganizationsPage />
              </RequireRole>
            }
          />
          <Route
            path="admin/users"
            element={
              <RequireRole allow={["ADMIN"]}>
                <UsersPage />
              </RequireRole>
            }
          />
          <Route
            path="admin/troubleshooting"
            element={
              <RequireRole allow={["ADMIN"]}>
                <TroubleshootingPage />
              </RequireRole>
            }
          />
          <Route
            path="admin/config"
            element={
              <RequireRole allow={["ADMIN"]}>
                <ConfigurationPage />
              </RequireRole>
            }
          />
          <Route
            path="admin/requests"
            element={
              <RequireRole allow={["ADMIN"]}>
                <RequestsPage />
              </RequireRole>
            }
          />
          <Route
            path="admin/audit"
            element={
              <RequireRole allow={["ADMIN"]}>
                <AuditPage />
              </RequireRole>
            }
          />
          <Route path="*" element={<NotFound />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
