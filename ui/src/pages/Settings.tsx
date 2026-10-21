import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "../api";
import { roleLabel, useMe } from "../auth";
import { applyTheme, getTheme, type Theme } from "../theme";
import { formatJson } from "../format";
import {
  Badge,
  Button,
  Card,
  DataTable,
  Field,
  Notice,
  PageHead,
  Toggle,
  type Column,
} from "../components/ui";

export function SettingsPage() {
  const me = useMe();
  const queryClient = useQueryClient();
  const [theme, setTheme] = useState<Theme>(getTheme());
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [aiAssist, setAiAssist] = useState(false);
  const [maxConcurrent, setMaxConcurrent] = useState("4");
  const [saved, setSaved] = useState<string | null>(null);

  const settings = me.data?.settings ?? {};

  useEffect(() => {
    setEmailNotifications(Boolean(settings["notifications.email"] ?? true));
    setAiAssist(Boolean(settings["ai.assistEnabled"] ?? false));
    setMaxConcurrent(String(settings["worker.maxConcurrent"] ?? 4));
  }, [me.data]);

  const save = useMutation({
    mutationFn: (values: Record<string, unknown>) => api.updateSettings(values),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["me"] });
      setSaved("Preferences saved.");
    },
    onError: (error: Error) => setSaved(error.message),
  });

  const changeTheme = (next: Theme) => {
    setTheme(next);
    applyTheme(next);
  };

  const effectiveColumns: Column<[string, unknown]>[] = [
    { header: "Key", cell: ([key]) => <span className="mono">{key}</span> },
    { header: "Effective value", cell: ([, value]) => <span className="mono">{formatJson(value)}</span> },
  ];

  return (
    <>
      <PageHead
        title="Settings"
        subtitle="Account, appearance and personal preferences. Evaluation limits are controlled by the platform."
      />
      {saved && <Notice>{saved}</Notice>}

      <div className="grid grid-2">
        <Card title="Account" subtitle="Identity comes from the identity provider; the platform mirrors role and organization.">
          <dl className="kv">
            <dt>Name</dt>
            <dd>{me.data?.name}</dd>
            <dt>Email</dt>
            <dd className="mono">{me.data?.email}</dd>
            <dt>Role</dt>
            <dd>
              <Badge plain>{me.data ? roleLabel[me.data.role] : "—"}</Badge>
            </dd>
            <dt>Organization</dt>
            <dd className="mono">{me.data?.orgSlug ?? "—"}</dd>
          </dl>
        </Card>

        <Card title="Appearance" subtitle="Solid Harbor surfaces, light or dark. No translucency.">
          <div className="row spread" style={{ marginBottom: 14 }}>
            <div>
              <strong style={{ display: "block", fontSize: 13 }}>Dark appearance</strong>
              <span className="small muted">Follows your choice, stored in this browser.</span>
            </div>
            <Toggle
              checked={theme === "dark"}
              onCheckedChange={(checked) => changeTheme(checked ? "dark" : "light")}
              label={theme === "dark" ? "Dark" : "Light"}
            />
          </div>
          <div className="row">
            <button
              type="button"
              className="chip-btn"
              onClick={() => changeTheme("light")}
              aria-pressed={theme === "light"}
              style={{ background: "#edf0f5", color: "#1c2330" }}
            >
              Harbor light
            </button>
            <button
              type="button"
              className="chip-btn"
              onClick={() => changeTheme("dark")}
              aria-pressed={theme === "dark"}
              style={{ background: "#222831", color: "#e6eaf0" }}
            >
              Harbor dark
            </button>
          </div>
        </Card>
      </div>

      <div className="grid grid-3" style={{ marginTop: 14 }}>
        <Card title="Notifications" subtitle="Platform email, never marketing.">
          <Toggle
            checked={emailNotifications}
            onCheckedChange={(checked) => {
              setEmailNotifications(checked);
              save.mutate({ "notifications.email": checked });
            }}
            label="Email notifications"
          />
        </Card>

        <Card title="AI helper" subtitle="Drafting and explanation assistance for workers.">
          <Toggle
            checked={aiAssist}
            onCheckedChange={(checked) => {
              setAiAssist(checked);
              save.mutate({ "ai.assistEnabled": checked });
            }}
            label="Enable AI assistance"
          />
          <p className="small muted" style={{ marginBottom: 0 }}>
            The AI service is offline in this phase; the preference is stored now and honoured when
            the helper is connected.
          </p>
        </Card>

        <Card title="Work pace" subtitle="How many contracts you intend to hold at once.">
          <Field label="Maximum concurrent contracts">
            <input
              className="input"
              type="number"
              min={1}
              max={50}
              value={maxConcurrent}
              onChange={(event) => setMaxConcurrent(event.target.value)}
            />
          </Field>
          <Button
            small
            variant="primary"
            disabled={save.isPending}
            onClick={() => save.mutate({ "worker.maxConcurrent": Number(maxConcurrent) })}
          >
            Save
          </Button>
        </Card>
      </div>

      <Card title="Effective settings" subtitle="Resolved USER → CONTRACT → ORG → GLOBAL.">
        <DataTable
          columns={effectiveColumns}
          rows={Object.entries(settings)}
          rowKey={([key]) => key}
          empty="No settings are set yet. Admins can publish global defaults."
        />
      </Card>
    </>
  );
}
