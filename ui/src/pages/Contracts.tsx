import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api";
import { useMe } from "../auth";
import { formatDate, formatJson } from "../format";
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
  stateTone,
  type Column,
} from "../components/ui";

const DEFAULT_PAYLOAD = `{
  "signature": {"name": "resize"},
  "invariants": ["dims_match", "alpha_preserved"],
  "resourceBudget": {"timeMs": 12, "peakMemMb": 8, "artifactKb": 64},
  "oracle": {"holdout": "hidden", "properties": "ref"},
  "scoring": {"gate": "all_pass"}
}`;

export function ContractsPage() {
  const me = useMe();
  const publisher = me.data?.role === "PUBLISHER";
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [applied, setApplied] = useState("");
  const [publishing, setPublishing] = useState(false);

  const contracts = useQuery({
    queryKey: ["contracts", publisher ? "org" : "all", page, applied],
    queryFn: () =>
      publisher ? api.orgContracts(page, 50) : api.listContracts(page, 50, applied || undefined),
  });

  const rows = useMemo(() => {
    const content = contracts.data?.content ?? [];
    if (!publisher || !applied) return content;
    const needle = applied.toLowerCase();
    return content.filter((contract) => contract.moduleId.toLowerCase().includes(needle));
  }, [contracts.data, publisher, applied]);

  const columns: Column<(typeof rows)[number]>[] = [
    {
      header: "Module",
      cell: (contract) => (
        <Link
          to={`/user/contracts/${encodeURIComponent(contract.moduleId)}/${encodeURIComponent(contract.version)}`}
        >
          {contract.moduleId}
        </Link>
      ),
    },
    { header: "Version", cell: (contract) => <span className="mono">{contract.version}</span> },
    { header: "Kind", cell: (contract) => <Badge plain>{contract.taskKind}</Badge> },
    { header: "Language", cell: (contract) => contract.language },
    { header: "Tier", cell: (contract) => contract.disclosureTier },
    {
      header: "State",
      cell: (contract) => (
        <Badge tone={stateTone(contract.lifecycleState)}>{contract.lifecycleState}</Badge>
      ),
    },
    {
      header: "Created",
      cell: (contract) => <span className="small muted">{formatDate(contract.createdAt)}</span>,
    },
    {
      header: "",
      align: "right",
      cell: (contract) => (
        <Link
          className="btn small"
          to={`/user/contracts/${encodeURIComponent(contract.moduleId)}/${encodeURIComponent(contract.version)}`}
        >
          Open
        </Link>
      ),
    },
  ];

  return (
    <>
      <PageHead
        title={publisher ? "Contracts" : "Browse contracts"}
        subtitle={
          publisher
            ? "Job cards owned by your organization. Publishing is audited and bound to your account."
            : "Public job cards. Open one to read the task document at its disclosure tier."
        }
        actions={
          publisher ? (
            <Button variant="primary" onClick={() => setPublishing(true)}>
              Publish contract
            </Button>
          ) : undefined
        }
      />

      <Card
        title="Registry"
        subtitle={`${contracts.data?.totalElements ?? 0} contract(s)`}
        actions={
          <form
            className="row"
            onSubmit={(event) => {
              event.preventDefault();
              setPage(0);
              setApplied(search.trim());
            }}
          >
            <input
              className="input"
              style={{ width: 240 }}
              placeholder="Filter by module id"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
            <Button small type="submit">
              Filter
            </Button>
            {applied && (
              <Button
                small
                variant="ghost"
                onClick={() => {
                  setSearch("");
                  setApplied("");
                }}
              >
                Clear
              </Button>
            )}
          </form>
        }
      >
        {contracts.isLoading ? (
          <Loading label="Loading contracts" />
        ) : contracts.isError ? (
          <ErrorBox>{contracts.error.message}</ErrorBox>
        ) : (
          <>
            <DataTable
              columns={columns}
              rows={rows}
              rowKey={(contract) => contract.id}
              empty="No contracts match this filter yet."
            />
            <div className="pager">
              <Button small disabled={page === 0} onClick={() => setPage(page - 1)}>
                Previous
              </Button>
              <span>
                Page {(contracts.data?.page ?? 0) + 1} of {Math.max(contracts.data?.totalPages ?? 1, 1)}
              </span>
              <Button
                small
                disabled={page + 1 >= (contracts.data?.totalPages ?? 1)}
                onClick={() => setPage(page + 1)}
              >
                Next
              </Button>
            </div>
          </>
        )}
      </Card>

      {publisher && <PublishDialog open={publishing} onOpenChange={setPublishing} />}
    </>
  );
}

function PublishDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const [moduleId, setModuleId] = useState("module.image.resize");
  const [version, setVersion] = useState("1.0.0");
  const [taskKind, setTaskKind] = useState("optimize");
  const [language, setLanguage] = useState("java");
  const [tier, setTier] = useState("L1");
  const [workKind, setWorkKind] = useState("compute");
  const [taskDocument, setTaskDocument] = useState("Describe the task, the exact signature and the acceptance criteria.");
  const [payload, setPayload] = useState(DEFAULT_PAYLOAD);
  const [error, setError] = useState<string | null>(null);

  const publish = useMutation({
    mutationFn: () => {
      let parsed: unknown;
      try {
        parsed = JSON.parse(payload);
      } catch {
        throw new Error("Payload is not valid JSON");
      }
      return api.publishContract({
        moduleId,
        version,
        taskKind,
        language,
        disclosureTier: tier,
        workKind,
        taskDocument,
        payload: parsed,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["contracts"] });
      queryClient.invalidateQueries({ queryKey: ["org-contracts"] });
      onOpenChange(false);
    },
    onError: (mutationError: Error) => setError(mutationError.message),
  });

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title="Publish a contract"
      subtitle="The task document is immutable for this version. A worker signs exactly this text."
      footer={
        <>
          <Button onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button variant="primary" disabled={publish.isPending} onClick={() => publish.mutate()}>
            {publish.isPending ? "Publishing" : "Publish"}
          </Button>
        </>
      }
    >
      {error && <ErrorBox>{error}</ErrorBox>}
      <div className="grid grid-2">
        <Field label="Module id">
          <input className="input" value={moduleId} onChange={(event) => setModuleId(event.target.value)} />
        </Field>
        <Field label="Version">
          <input className="input" value={version} onChange={(event) => setVersion(event.target.value)} />
        </Field>
        <Field label="Task kind">
          <select className="select" value={taskKind} onChange={(event) => setTaskKind(event.target.value)}>
            <option value="optimize">optimize</option>
            <option value="build">build</option>
          </select>
        </Field>
        <Field label="Language">
          <select className="select" value={language} onChange={(event) => setLanguage(event.target.value)}>
            <option value="java">java</option>
            <option value="rust">rust</option>
          </select>
        </Field>
        <Field label="Disclosure tier">
          <select className="select" value={tier} onChange={(event) => setTier(event.target.value)}>
            <option value="L0">L0 — shape only</option>
            <option value="L1">L1 — samples</option>
            <option value="L2">L2 — redacted examples</option>
          </select>
        </Field>
        <Field label="Work kind">
          <select className="select" value={workKind} onChange={(event) => setWorkKind(event.target.value)}>
            <option value="compute">compute</option>
            <option value="service">service</option>
          </select>
        </Field>
      </div>
      <Field label="Task document">
        <textarea
          className="textarea"
          value={taskDocument}
          onChange={(event) => setTaskDocument(event.target.value)}
        />
      </Field>
      <Field label="Payload (JSON)" hint="Must include signature, invariants, resourceBudget, oracle and scoring.">
        <textarea className="textarea" value={payload} onChange={(event) => setPayload(event.target.value)} />
      </Field>
    </Dialog>
  );
}

export function ContractDetailPage() {
  const params = useParams();
  const moduleId = params.moduleId ?? "";
  const version = params.version ?? "";
  const me = useMe();
  const contract = useQuery({
    queryKey: ["contract", moduleId, version],
    queryFn: () => api.getContract(moduleId, version),
    enabled: Boolean(moduleId && version),
  });

  if (contract.isLoading) {
    return <Loading label="Loading contract" />;
  }
  if (contract.isError || !contract.data) {
    return <ErrorBox>{contract.error?.message ?? "Contract not found"}</ErrorBox>;
  }
  const data = contract.data;

  return (
    <>
      <PageHead
        title={`${data.moduleId}@${data.version}`}
        subtitle={`${data.taskKind} · ${data.language} · ${data.workKind} · disclosure ${data.disclosureTier}`}
        actions={
          <div className="row">
            <Badge tone={stateTone(data.lifecycleState)}>{data.lifecycleState}</Badge>
            <Link className="btn small" to="/user/contracts">
              Back
            </Link>
            {me.data?.role === "WORKER" && (
              <Link
                className="btn small primary"
                to={`/user/contracts/${encodeURIComponent(data.moduleId)}/${encodeURIComponent(data.version)}/workbench`}
              >
                Open workbench
              </Link>
            )}
          </div>
        }
      />
      <div className="grid grid-4">
        <Kpi label="Task kind" value={data.taskKind} />
        <Kpi label="Language" value={data.language} />
        <Kpi label="Tier" value={data.disclosureTier} />
        <Kpi label="Created" value={<span style={{ fontSize: 16 }}>{formatDate(data.createdAt)}</span>} />
      </div>

      <div className="grid grid-2" style={{ marginTop: 14 }}>
        <Card title="Task document" subtitle="The exact text a worker signs.">
          <pre className="code">{data.taskDocument}</pre>
        </Card>
        <Card title="Metadata" subtitle="Ownership and identifiers.">
          <dl className="kv">
            <dt>Contract id</dt>
            <dd className="mono">{data.id}</dd>
            <dt>Organization</dt>
            <dd className="mono">{data.orgId ?? "—"}</dd>
            <dt>Created by</dt>
            <dd className="mono">{data.createdBy ?? "—"}</dd>
            <dt>Lifecycle</dt>
            <dd>{data.lifecycleState}</dd>
          </dl>
          <Notice>
            Workers should read the tier-filtered workbench view. The payload below is the publisher
            view and may contain data the tier hides from workers.
          </Notice>
        </Card>
      </div>

      <Card title="Payload" subtitle="Machine-readable task description.">
        <pre className="code">{formatJson(data.payload)}</pre>
      </Card>
    </>
  );
}

const AGREEMENTS = [
  { key: "honesty", label: "Honesty", description: "The work and measurements I submit are my own and accurate." },
  { key: "scope", label: "Scope", description: "I will work only within the published task and its interfaces." },
  { key: "ai-declaration", label: "AI declaration", description: "I will declare AI assistance where it was used." },
  { key: "confidentiality", label: "Confidentiality", description: "I will not disclose holdouts or secret examples." },
  { key: "responsibility", label: "Responsibility", description: "I am accountable for the code and its licenses." },
  { key: "license", label: "License", description: "I accept the license terms attached to the contract." },
];

function toBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

async function signLocally(moduleId: string, version: string, taskDocument: string) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(taskDocument));
  const hash = Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
  const message = `accept:${moduleId}@${version}:${hash}`;
  const keyPair = (await crypto.subtle.generateKey({ name: "Ed25519" }, true, [
    "sign",
    "verify",
  ])) as CryptoKeyPair;
  const signature = await crypto.subtle.sign(
    { name: "Ed25519" },
    keyPair.privateKey,
    new TextEncoder().encode(message),
  );
  const publicKey = await crypto.subtle.exportKey("spki", keyPair.publicKey);
  return { hash, message, publicKey: toBase64(publicKey), signature: toBase64(signature) };
}

export function WorkbenchPage() {
  const params = useParams();
  const moduleId = params.moduleId ?? "";
  const version = params.version ?? "";
  const me = useMe();
  const workbench = useQuery({
    queryKey: ["workbench", moduleId, version],
    queryFn: () => api.workbench(moduleId, version),
    enabled: Boolean(moduleId && version),
  });
  const [selected, setSelected] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<string | null>(null);

  const accept = useMutation({
    mutationFn: async () => {
      if (!workbench.data || !me.data) throw new Error("Contract is not ready");
      if (selected.length !== AGREEMENTS.length) throw new Error("Accept every agreement first");
      if (!crypto.subtle) throw new Error("This browser does not expose WebCrypto");
      let signed;
      try {
        signed = await signLocally(workbench.data.moduleId, workbench.data.version, workbench.data.taskDocument);
      } catch {
        throw new Error(
          "This browser could not create an Ed25519 signature. Use a signing tool and paste the public key and signature.",
        );
      }
      const result = await api.acceptContract(workbench.data.id, {
        workerId: me.data.email,
        agreements: selected,
        publicKey: signed.publicKey,
        signature: signed.signature,
      });
      return { result, signed };
    },
    onSuccess: ({ result, signed }) => {
      setReceipt(`Acceptance ${result.id} verified. Task document hash ${signed.hash}.`);
      setError(null);
    },
    onError: (mutationError: Error) => setError(mutationError.message),
  });

  if (workbench.isLoading) return <Loading label="Loading workbench" />;
  if (workbench.isError || !workbench.data) {
    return <ErrorBox>{workbench.error?.message ?? "Workbench unavailable"}</ErrorBox>;
  }
  const data = workbench.data;

  return (
    <>
      <PageHead
        title={`${data.moduleId}@${data.version} workbench`}
        subtitle={`Tier ${data.disclosureTier} · ${data.language} · ${data.workKind}`}
        actions={
          <Link className="btn small" to={`/user/contracts/${encodeURIComponent(data.moduleId)}/${encodeURIComponent(data.version)}`}>
            Contract
          </Link>
        }
      />
      {receipt && <Notice>{receipt}</Notice>}
      {error && <ErrorBox>{error}</ErrorBox>}

      <div className="grid grid-2">
        <Card title="Task document" subtitle="This exact text is bound to your signature.">
          <pre className="code">{data.taskDocument}</pre>
        </Card>
        <Card title="Tier-filtered payload" subtitle="Holdouts never travel to the workbench.">
          <pre className="code">{formatJson(data.payload)}</pre>
        </Card>
      </div>

      <Card
        title="Accept this task"
        subtitle="Six agreements are mandatory before a submission counts."
        actions={
          <Button
            variant="primary"
            disabled={accept.isPending || selected.length !== AGREEMENTS.length}
            onClick={() => {
              setError(null);
              accept.mutate();
            }}
          >
            {accept.isPending ? "Signing" : "Sign and accept"}
          </Button>
        }
      >
        <div className="grid grid-2">
          {AGREEMENTS.map((agreement) => (
            <label key={agreement.key} className="row" style={{ alignItems: "flex-start", gap: 8 }}>
              <input
                type="checkbox"
                checked={selected.includes(agreement.key)}
                onChange={(event) => {
                  setSelected((current) =>
                    event.target.checked
                      ? [...current, agreement.key]
                      : current.filter((key) => key !== agreement.key),
                  );
                }}
              />
              <span>
                <strong style={{ fontSize: 13 }}>{agreement.label}</strong>
                <span className="small muted" style={{ display: "block" }}>
                  {agreement.description}
                </span>
              </span>
            </label>
          ))}
        </div>
        <div className="row" style={{ marginTop: 12 }}>
          <Button
            small
            onClick={() => setSelected(AGREEMENTS.map((agreement) => agreement.key))}
          >
            Select all
          </Button>
          <Button small variant="ghost" onClick={() => setSelected([])}>
            Clear
          </Button>
          <span className="small muted">
            Worker identity: <span className="mono">{me.data?.email}</span>
          </span>
        </div>
        <Notice>
          Signing generates a fresh Ed25519 key in this browser and sends only the public key and
          signature. The canonical message is <span className="mono">accept:module@version:sha256(taskDocument)</span>.
        </Notice>
      </Card>
    </>
  );
}
