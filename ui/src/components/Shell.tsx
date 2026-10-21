import { useEffect, useState, type ReactNode } from "react";
import { Navigate, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { clearDevUser, type Role } from "../api";
import { roleLabel, useMe } from "../auth";
import { applyTheme, getTheme, type Theme } from "../theme";
import {
  AlertIcon,
  ChartIcon,
  CheckIcon,
  FileIcon,
  FlaskIcon,
  GearIcon,
  GridIcon,
  InboxIcon,
  ListIcon,
  MoonIcon,
  ShieldIcon,
  SlidersIcon,
  SunIcon,
  UploadIcon,
  UsersIcon,
  WalletIcon,
} from "./icons";
import { Badge, Loading } from "./ui";

type NavItem = {
  to: string;
  label: string;
  icon: ReactNode;
};

const workerNav: NavItem[] = [
  { to: "/user/overview", label: "Overview", icon: <GridIcon /> },
  { to: "/user/contracts", label: "Browse contracts", icon: <FileIcon /> },
  { to: "/user/acceptances", label: "My acceptances", icon: <CheckIcon /> },
  { to: "/user/submissions", label: "Submissions", icon: <UploadIcon /> },
  { to: "/user/earnings", label: "Earnings", icon: <WalletIcon /> },
  { to: "/user/settings", label: "Settings", icon: <GearIcon /> },
];

const publisherNav: NavItem[] = [
  { to: "/user/overview", label: "Overview", icon: <GridIcon /> },
  { to: "/user/contracts", label: "Contracts", icon: <FileIcon /> },
  { to: "/user/workers", label: "Workers", icon: <UsersIcon /> },
  { to: "/user/evaluations", label: "Evaluations", icon: <FlaskIcon /> },
  { to: "/user/analytics", label: "Analytics", icon: <ChartIcon /> },
  { to: "/user/payments", label: "Payments", icon: <WalletIcon /> },
  { to: "/user/settings", label: "Settings", icon: <GearIcon /> },
];

const adminNav: NavItem[] = [
  { to: "/user/admin/overview", label: "Overview", icon: <GridIcon /> },
  { to: "/user/admin/analytics", label: "Analytics", icon: <ChartIcon /> },
  { to: "/user/admin/orgs", label: "Organizations", icon: <ShieldIcon /> },
  { to: "/user/admin/users", label: "Users", icon: <UsersIcon /> },
  { to: "/user/admin/troubleshooting", label: "Troubleshooting", icon: <AlertIcon /> },
  { to: "/user/admin/config", label: "Configuration", icon: <SlidersIcon /> },
  { to: "/user/admin/requests", label: "Requests", icon: <InboxIcon /> },
  { to: "/user/admin/audit", label: "Audit", icon: <ListIcon /> },
];

function navFor(role: Role): NavItem[] {
  if (role === "ADMIN") return adminNav;
  if (role === "PUBLISHER") return publisherNav;
  return workerNav;
}

function isActive(item: NavItem, path: string): boolean {
  if (item.to.endsWith("/overview")) {
    return path === item.to;
  }
  return path === item.to || path.startsWith(`${item.to}/`);
}

export function Shell() {
  const me = useMe();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [theme, setTheme] = useState<Theme>(getTheme());

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  useEffect(() => {
    document.title = "Symmetry";
  }, []);

  if (me.isLoading) {
    return (
      <div className="window">
        <div className="login-shell">
          <Loading label="Opening workspace" />
        </div>
      </div>
    );
  }

  if (me.isError || !me.data) {
    const adminArea = location.pathname.startsWith("/user/admin");
    return <Navigate to={adminArea ? "/user/admin/login" : "/user/login"} replace />;
  }

  const role = me.data.role;
  const nav = navFor(role);
  const current = nav.find((item) => isActive(item, location.pathname));

  const signOut = () => {
    clearDevUser();
    queryClient.clear();
    navigate(role === "ADMIN" ? "/user/admin/login" : "/user/login");
  };

  return (
    <div className="window">
      <div className="titlebar">
        <span className="titlebar-title">Symmetry</span>
        <span className="titlebar-sep" />
        <span className="titlebar-crumb">{current?.label ?? "Workspace"}</span>
        <div className="titlebar-right">
          <button
            type="button"
            className="btn ghost small"
            aria-label={theme === "dark" ? "Switch to light appearance" : "Switch to dark appearance"}
            onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
          >
            {theme === "dark" ? <SunIcon /> : <MoonIcon />}
          </button>
          <Badge plain>{roleLabel[role]}</Badge>
          <span className="small muted">{me.data.email}</span>
          <button type="button" className="btn small" onClick={signOut}>
            Sign out
          </button>
        </div>
      </div>
      <div className="body">
        <nav className="sidebar" aria-label="Primary">
          <div className="brand">
            <span className="brand-mark">S</span>
            <span>{role === "ADMIN" ? "Admin" : "Symmetry"}</span>
          </div>
          <div className="nav-group">
            <div className="nav-label">{role === "ADMIN" ? "Platform" : "Workspace"}</div>
            {nav
              .filter((item) => !item.to.endsWith("/settings"))
              .map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={() => `nav-item${isActive(item, location.pathname) ? " active" : ""}`}
                >
                  {item.icon}
                  <span>{item.label}</span>
                </NavLink>
              ))}
          </div>
          {role !== "ADMIN" && (
            <div className="nav-group">
              <div className="nav-label">Account</div>
              <NavLink
                to="/user/settings"
                className={() =>
                  `nav-item${isActive(nav[nav.length - 1], location.pathname) ? " active" : ""}`
                }
              >
                <GearIcon />
                <span>Settings</span>
              </NavLink>
            </div>
          )}
        </nav>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export function RequireRole({ allow, children }: { allow: Role[]; children: ReactNode }) {
  const me = useMe();
  if (me.isLoading) {
    return <Loading label="Checking access" />;
  }
  if (!me.data) {
    return null;
  }
  if (!allow.includes(me.data.role)) {
    return (
      <div className="empty">
        <strong>You do not have access to this page</strong>
        Your account role is {roleLabel[me.data.role]}. Choose a page in the sidebar that matches
        your role.
      </div>
    );
  }
  return <>{children}</>;
}
