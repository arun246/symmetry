import { Link, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { setDevUser } from "../api";

export function Login({ admin = false }: { admin?: boolean }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const choose = (email: string, home: string) => {
    setDevUser(email);
    queryClient.clear();
    navigate(home, { replace: true });
  };

  return (
    <div className="window" style={{ gridTemplateRows: "1fr" }}>
      <div className="login-shell">
        <div className="login-card">
          <h1 className="login-title">{admin ? "Admin sign-in" : "Sign in to Symmetry"}</h1>
          <p className="login-sub">
            {admin
              ? "The admin console is an observability and configuration surface. It cannot act as a worker or publisher."
              : "Choose a role to open its workspace. Each role has its own pages, navigation and data scope."}
          </p>

          <div className="role-grid">
            {!admin && (
              <button
                type="button"
                className="role-card"
                onClick={() => choose("worker@symmetry.local", "/user/overview")}
              >
                <strong>Worker</strong>
                <span>Browse contracts, sign the task document, track acceptances and submissions.</span>
              </button>
            )}
            {!admin && (
              <button
                type="button"
                className="role-card"
                onClick={() => choose("publisher@symmetry.local", "/user/overview")}
              >
                <strong>Publisher</strong>
                <span>Publish job cards, watch evaluations, read analytics and manage payouts.</span>
              </button>
            )}
            {admin && (
              <button
                type="button"
                className="role-card"
                onClick={() => choose("admin@symmetry.local", "/user/admin/overview")}
              >
                <strong>Admin</strong>
                <span>Platform health, organizations, users, configuration requests and the audit log.</span>
              </button>
            )}
          </div>

          <div className="dev-note">
            Development sign-in. The platform runs with security off locally, so this picker sets the
            <span className="mono"> X-Symmetry-User </span>
            header. In production this page hands off to the themed Keycloak login and no demo
            identities exist.
          </div>

          <p className="small muted" style={{ marginTop: 14 }}>
            {admin ? (
              <Link to="/user/login">Worker or publisher sign-in</Link>
            ) : (
              <Link to="/user/admin/login">Admin sign-in</Link>
            )}
          </p>
        </div>
      </div>
    </div>
  );
}
