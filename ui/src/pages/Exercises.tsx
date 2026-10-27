import { useState, type Dispatch, type SetStateAction } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  api,
  type Exercise,
  type ExerciseAcceptance,
  type ExerciseDetail,
  type ExerciseStub,
  type ExerciseTest,
  type RepoTree,
  type SymbolInfo,
} from "../api";
import { useMe } from "../auth";
import { DockPane } from "../components/DockPane";
import { formatCents, formatDate, formatJson } from "../format";
import {
  Badge,
  Button,
  Card,
  ErrorBox,
  Field,
  Kpi,
  Loading,
  Notice,
  PageHead,
  stateTone,
} from "../components/ui";

const EXERCISE_AGREEMENTS = [
  { key: "rights", label: "Rights", description: "I have the right to publish this code and its tests." },
  { key: "confidentiality", label: "Confidentiality", description: "I will not disclose hidden tests or source beyond the task." },
  { key: "ai-declaration", label: "AI declaration", description: "I will declare material AI assistance." },
  { key: "responsibility", label: "Responsibility", description: "I am accountable for the code and its licenses." },
  { key: "license", label: "License", description: "I accept the license terms attached to this task." },
];

function complexityTone(value: string): "success" | "warn" | "danger" | "info" | undefined {
  if (value === "TRIVIAL" || value === "EASY") return "success";
  if (value === "MEDIUM") return "info";
  if (value === "HARD") return "warn";
  if (value === "EXPERT") return "danger";
  return undefined;
}

function priceTier(complexity: string): string {
  switch (complexity) {
    case "MEDIUM":
      return "$$";
    case "HARD":
      return "$$$";
    case "EXPERT":
      return "$$$$";
    default:
      return "$";
  }
}

function PublicHeader() {
  const me = useMe();
  return (
    <div className="titlebar">
      <span className="brand-mark">S</span>
      <span className="titlebar-title">Symmetry tasks</span>
      <span className="titlebar-sep" />
      <span className="titlebar-crumb">Open work, clearly priced</span>
      <div className="titlebar-right">
        {me.data ? (
          <Link className="btn small" to="/user/overview">
            Open workspace
          </Link>
        ) : (
          <Link className="btn small primary" to="/user/login">
            Sign in
          </Link>
        )}
      </div>
    </div>
  );
}

function TaskCard({ exercise }: { exercise: Exercise }) {
  return (
    <Link className="task-card" to={`/tasks/${exercise.id}`}>
      <div className="row spread">
        <Badge tone={complexityTone(exercise.complexity)}>{exercise.complexity.toLowerCase()}</Badge>
        <span className="row" style={{ gap: 6 }}>
          {exercise.parentExerciseId && <Badge plain>subtask</Badge>}
          <span className="small muted">{exercise.language}</span>
        </span>
      </div>
      <h3>{exercise.title}</h3>
      <p>{exercise.summary}</p>
      <div className="task-card-meta">
        <span>
          <strong className="price-tier" aria-label={`$${priceTier(exercise.complexity).length} price tier`}>
            {priceTier(exercise.complexity)}
          </strong>
          <span className="small muted"> task tier</span>
        </span>
        <span className="small muted">
          {exercise.stubCount} stub{exercise.stubCount === 1 ? "" : "s"} ·{" "}
          {exercise.deadlineDays} day{exercise.deadlineDays === 1 ? "" : "s"}
        </span>
      </div>
    </Link>
  );
}

function TaskGrid({ exercises }: { exercises: Exercise[] }) {
  if (exercises.length === 0) {
    return (
      <div className="empty">
        <strong>No tasks published yet</strong>
        Publishers can create a task from a public GitHub file and remove a function body.
      </div>
    );
  }
  return (
    <div className="task-grid">
      {exercises.map((exercise) => (
        <TaskCard key={exercise.id} exercise={exercise} />
      ))}
    </div>
  );
}

export function PublicTasksPage() {
  const exercises = useQuery({
    queryKey: ["exercises", "public"],
    queryFn: () => api.listExercises(0, 100),
  });

  return (
    <div className="window public-window">
      <PublicHeader />
      <main className="content public-content">
        <section className="board-hero">
          <div>
            <span className="eyebrow">The index</span>
            <h1>Small, real tasks from real code.</h1>
          </div>
          <div className="board-hero-stat">
            <strong>{exercises.data?.totalElements ?? 0}</strong>
            <span>open task{exercises.data?.totalElements === 1 ? "" : "s"}</span>
          </div>
        </section>

        {exercises.isLoading ? (
          <Loading label="Loading the task board" />
        ) : exercises.isError ? (
          <ErrorBox>{exercises.error.message}</ErrorBox>
        ) : (
          <TaskGrid exercises={exercises.data?.content ?? []} />
        )}

        <div className="board-foot">
          The board shows a $ to $$$$ tier from complexity, not an amount. Payments and escrow are
          not connected in this milestone.
        </div>
      </main>
    </div>
  );
}

export function ExercisesPage() {
  const me = useMe();
  const publisher = me.data?.role === "PUBLISHER";
  const exercises = useQuery({
    queryKey: ["exercises", publisher ? "mine" : "public"],
    queryFn: () => (publisher ? api.listMyExercises(0, 100) : api.listExercises(0, 100)),
  });

  return (
    <>
      <PageHead
        title={publisher ? "Your tasks" : "Task board"}
        subtitle={
          publisher
            ? "Drafts and published exercises owned by your organization."
            : "Published tasks with a symbolic $ tier, complexity and deadline."
        }
        actions={
          publisher ? (
            <Link className="btn primary" to="/user/exercises/new">
              Create task
            </Link>
          ) : undefined
        }
      />
      {exercises.isLoading ? (
        <Loading label="Loading tasks" />
      ) : exercises.isError ? (
        <ErrorBox>{exercises.error.message}</ErrorBox>
      ) : (
        <TaskGrid exercises={exercises.data?.content ?? []} />
      )}
      <Notice>
        {publisher
          ? "Published tasks are immutable. Create a new task to change the source or tests."
          : "Only the stub and public tests are shown. Hidden tests stay on the platform."}
      </Notice>
    </>
  );
}

export function MyTasksPage() {
  const accepted = useQuery({
    queryKey: ["exercises", "accepted"],
    queryFn: () => api.acceptedExercises(),
  });
  if (accepted.isLoading) return <Loading label="Loading your tasks" />;
  if (accepted.isError) return <ErrorBox>{accepted.error.message}</ErrorBox>;
  const items = accepted.data ?? [];
  return (
    <>
      <PageHead
        title="My tasks"
        subtitle="Accepted exercises, their deadline and the workspace entry point."
      />
      {items.length === 0 ? (
        <div className="empty">
          <strong>No accepted tasks yet</strong>
          Browse the task board and sign an exercise to start.
        </div>
      ) : (
        <div className="grid grid-2">
          {items.map((acceptance) => (
            <Card
              key={acceptance.id}
              title={`Exercise ${acceptance.exerciseId.slice(0, 8)}`}
              subtitle={`Accepted ${formatDate(acceptance.acceptedAt)}`}
              actions={<Badge tone={stateTone(acceptance.status.toLowerCase())}>{acceptance.status}</Badge>}
            >
              <dl className="kv">
                <dt>Deadline</dt>
                <dd>{formatDate(acceptance.deadlineAt)}</dd>
                <dt>Document hash</dt>
                <dd className="mono">{acceptance.taskDocumentHash.slice(0, 18)}…</dd>
              </dl>
              <div className="row" style={{ marginTop: 12 }}>
                <Link className="btn small primary" to={`/user/workspace/${acceptance.exerciseId}`}>
                  Open workspace
                </Link>
                <Link className="btn small" to={`/user/exercises/${acceptance.exerciseId}`}>
                  Read task
                </Link>
                <Link className="btn small" to={`/user/exercises/new?parent=${acceptance.exerciseId}`}>
                  Publish subtask
                </Link>
              </div>
            </Card>
          ))}
        </div>
      )}
    </>
  );
}

function toBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

async function signExercise(exercise: ExerciseDetail) {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(exercise.taskDocument),
  );
  const hash = Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
  const message = `accept:exercise:${exercise.id}:${exercise.version}:${hash}`;
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
  return { hash, publicKey: toBase64(publicKey), signature: toBase64(signature) };
}

function AcceptExerciseCard({ exercise }: { exercise: ExerciseDetail }) {
  const me = useMe();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const accepted = useQuery({
    queryKey: ["exercises", "accepted"],
    queryFn: () => api.acceptedExercises(),
    enabled: me.data?.role === "WORKER",
  });
  const existing = (accepted.data ?? []).find((item) => item.exerciseId === exercise.id);
  const [selected, setSelected] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const accept = useMutation({
    mutationFn: async () => {
      if (selected.length !== EXERCISE_AGREEMENTS.length) {
        throw new Error("Accept every agreement first");
      }
      if (!crypto.subtle) throw new Error("This browser does not expose WebCrypto");
      const signed = await signExercise(exercise);
      return api.acceptExercise(exercise.id, {
        agreements: selected,
        publicKey: signed.publicKey,
        signature: signed.signature,
      });
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["exercises", "accepted"] });
      navigate(`/user/workspace/${result.exerciseId}`);
    },
    onError: (mutationError: Error) => setError(mutationError.message),
  });

  const isSubtaskAuthor = me.data?.role === "WORKER" && Boolean(exercise.parentExerciseId)
    && exercise.createdBy === me.data.id;

  if (isSubtaskAuthor) {
    return (
      <Card title="Subtask author controls" subtitle="You published this subtask; another worker implements it.">
        <div className="row">
          <Link className="btn small primary" to={`/user/exercises/${exercise.id}/submissions`}>
            View submissions
          </Link>
          <Link className="btn small" to={`/user/exercises/new?parent=${exercise.parentExerciseId}`}>
            Publish another subtask
          </Link>
          {exercise.parentExerciseId && (
            <Link className="btn small" to={`/user/exercises/${exercise.parentExerciseId}`}>
              Parent task
            </Link>
          )}
        </div>
      </Card>
    );
  }

  if (me.data?.role === "PUBLISHER" || me.data?.role === "ADMIN") {
    return (
      <Card title="Publisher controls" subtitle="Only your organization can edit this draft.">
        <div className="row">
          <Link className="btn small primary" to={`/user/exercises/${exercise.id}/submissions`}>
            View submissions
          </Link>
          {exercise.status === "DRAFT" && (
            <Link className="btn small" to="/user/exercises/new">
              Create another task
            </Link>
          )}
        </div>
      </Card>
    );
  }

  if (existing && existing.status !== "EXPIRED" && existing.status !== "WITHDRAWN") {
    return (
      <Card title="You accepted this task" subtitle={`Deadline ${formatDate(existing.deadlineAt)}`}>
        <div className="row">
          <Link className="btn primary" to={`/user/workspace/${exercise.id}`}>
            Open workspace
          </Link>
          <Link className="btn small" to={`/user/exercises/new?parent=${exercise.id}`}>
            Publish a subtask
          </Link>
        </div>
      </Card>
    );
  }

  return (
    <Card
      title="Accept this task"
      subtitle="An acceptance is a signed promise to work within the deadline."
      actions={
        <Button
          variant="primary"
          disabled={accept.isPending || selected.length !== EXERCISE_AGREEMENTS.length}
          title={selected.length === EXERCISE_AGREEMENTS.length
            ? "Sign the task document and accept"
            : `Select all ${EXERCISE_AGREEMENTS.length} agreements first`}
          onClick={() => {
            setError(null);
            accept.mutate();
          }}
        >
          {accept.isPending ? "Signing" : "Sign and accept"}
        </Button>
      }
    >
      {error && <ErrorBox>{error}</ErrorBox>}
      <div className="grid grid-2">
        {EXERCISE_AGREEMENTS.map((agreement) => (
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
        <Button small onClick={() => setSelected(EXERCISE_AGREEMENTS.map((item) => item.key))}>
          Select all
        </Button>
        <Button small variant="ghost" onClick={() => setSelected([])}>
          Clear
        </Button>
        <span className="small muted" aria-live="polite">
          {selected.length} of {EXERCISE_AGREEMENTS.length} selected
        </span>
      </div>
      <Notice>
        The browser creates an Ed25519 key locally and sends only the public key and signature.
      </Notice>
    </Card>
  );
}

export function ExerciseDetailPage({ publicView = false }: { publicView?: boolean }) {
  const params = useParams();
  const me = useMe();
  const id = params.id ?? "";
  const isPublisher = me.data?.role === "PUBLISHER" || me.data?.role === "ADMIN";
  const exercise = useQuery({
    queryKey: ["exercise", id],
    queryFn: () => api.getExercise(id),
    enabled: Boolean(id),
  });

  if (exercise.isLoading) return <Loading label="Loading task" />;
  if (exercise.isError || !exercise.data) {
    return <ErrorBox>{exercise.error?.message ?? "Task not found"}</ErrorBox>;
  }
  const data = exercise.data;

  const body = (
    <>
      <PageHead
        title={data.title}
        subtitle={`${data.parentExerciseId ? "subtask · " : ""}${data.language} · ${data.complexity.toLowerCase()} · ${data.stubs.length} stub(s) · ${data.deadlineDays} day deadline`}
        actions={
          <div className="row">
            {data.parentExerciseId && <Badge plain>subtask</Badge>}
            <Badge tone={complexityTone(data.complexity)}>{data.complexity.toLowerCase()}</Badge>
            <Badge tone={stateTone(data.status.toLowerCase())}>{data.status.toLowerCase()}</Badge>
            <Link className="btn small" to={publicView ? "/" : "/user/exercises"}>
              Back
            </Link>
          </div>
        }
      />
      <div className={`grid ${isPublisher ? "grid-4" : "grid-3"}`}>
        <Kpi label="Price tier" value={priceTier(data.complexity)} meta="from complexity" />
        <Kpi label="Public tests" value={data.tests.filter((test) => test.visibility === "PUBLIC").length} meta="visible to workers" />
        <Kpi label="Deadline" value={`${data.deadlineDays} days`} meta="after acceptance" />
        {isPublisher && (
          <Kpi label="Internal amount" value={formatCents(data.priceCents, data.currency)} meta="not shown on the board" />
        )}
      </div>

      <div className="grid grid-2" style={{ marginTop: 14 }}>
        <Card title="The task" subtitle={data.summary}>
          {data.parentExerciseId && (
            <Notice>
              This is a subtask of{" "}
              <Link to={`/user/exercises/${data.parentExerciseId}`}>the parent task</Link>. It was
              published by a worker who accepted the parent.
            </Notice>
          )}
          <pre className="code">{data.taskDocument}</pre>
        </Card>
        <Card title="Source provenance" subtitle="Pinned to a commit; never a moving branch.">
          {data.source ? (
            <dl className="kv">
              <dt>Repository</dt>
              <dd>
                <a href={data.source.repoUrl} target="_blank" rel="noreferrer">
                  {data.source.repoOwner}/{data.source.repoName}
                </a>
              </dd>
              <dt>Commit</dt>
              <dd className="mono">{data.source.commitSha.slice(0, 12)}…</dd>
              <dt>File</dt>
              <dd className="mono">{data.source.filePath}</dd>
              <dt>License</dt>
              <dd>{data.source.license ?? "Not detected — publisher confirmed rights"}</dd>
            </dl>
          ) : (
            <Notice>The publisher-only source view is hidden on the public page.</Notice>
          )}
        </Card>
      </div>

      <div className="section-title">Stubs to implement</div>
      {data.stubs.map((stub) => (
        <Card
          key={stub.id}
          title={`${stub.symbolName} · ${stub.symbolKind.toLowerCase()}`}
          subtitle={`Lines ${stub.startLine}–${stub.endLine}`}
        >
          <pre className="code">{stub.stubContent}</pre>
        </Card>
      ))}

      <div className="section-title">Public tests</div>
      <Card title="Visible checks" subtitle="Hidden tests and the original implementation are not shown.">
        {data.tests.filter((test) => test.visibility === "PUBLIC").length === 0 ? (
          <div className="empty">No public tests were attached.</div>
        ) : (
          <div className="test-list">
            {data.tests
              .filter((test) => test.visibility === "PUBLIC")
              .map((test) => (
                <div key={test.id} className="test-row">
                  <strong>{test.name}</strong>
                  <pre className="code">{test.content}</pre>
                </div>
              ))}
          </div>
        )}
      </Card>

      {!publicView && data.status === "PUBLISHED" && <AcceptExerciseCard exercise={data} />}
      {publicView && data.status === "PUBLISHED" && (
        <Card title="Want to implement it?" subtitle="Sign in as a worker to accept the task.">
          <Link className="btn primary" to="/user/login">
            Sign in
          </Link>
        </Card>
      )}
    </>
  );

  if (publicView) {
    return (
      <div className="window public-window">
        <PublicHeader />
        <main className="content public-content">{body}</main>
      </div>
    );
  }
  return body;
}

type DraftTest = {
  name: string;
  visibility: "PUBLIC" | "HIDDEN";
  content: string;
  timeoutMs: number;
};

type DraftSideCase = {
  name: string;
  visibility: "PUBLIC" | "HIDDEN";
  input: string;
  expected: string;
};

export function TaskCreatorPage() {
  const navigate = useNavigate();
  const me = useMe();
  const [searchParams] = useSearchParams();
  const parentId = searchParams.get("parent");
  const isSubtask = Boolean(parentId);
  const parent = useQuery({
    queryKey: ["exercise", parentId],
    queryFn: () => api.getExercise(parentId as string),
    enabled: Boolean(parentId),
  });
  const [exerciseId, setExerciseId] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [summary, setSummary] = useState("");
  const [complexity, setComplexity] = useState("MEDIUM");
  const [price, setPrice] = useState("250");
  const [deadlineDays, setDeadlineDays] = useState(7);
  const [repoUrl, setRepoUrl] = useState("");
  const [tree, setTree] = useState<RepoTree | null>(null);
  const [filePath, setFilePath] = useState("");
  const [source, setSource] = useState<Awaited<ReturnType<typeof api.importExerciseSource>> | null>(null);
  const [symbols, setSymbols] = useState<SymbolInfo[]>([]);
  const [selectedSymbol, setSelectedSymbol] = useState<SymbolInfo | null>(null);
  const [stubs, setStubs] = useState<ExerciseStub[]>([]);
  const [tests, setTests] = useState<DraftTest[]>([
    { name: "basic behavior", visibility: "PUBLIC", content: "assert the documented behavior", timeoutMs: 5000 },
  ]);
  const [sideCases, setSideCases] = useState<DraftSideCase[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () => {
      if (parentId) {
        return api.createSubtask(parentId, {
          title,
          summary,
          complexity,
          deadlineDays,
          taskDocument: "",
        });
      }
      return api.createExercise({
        title,
        summary,
        language: "java",
        complexity,
        priceCents: Math.round(Number(price) * 100),
        currency: "USD",
        deadlineDays,
        taskDocument: "",
      });
    },
    onSuccess: (created) => {
      setExerciseId(created.id);
      setNotice(isSubtask
        ? "Subtask draft created. Import the source and choose the part you want help with."
        : "Draft created. Import a public GitHub file next.");
      setError(null);
    },
    onError: (mutationError: Error) => setError(mutationError.message),
  });

  const loadTree = useMutation({
    mutationFn: () => api.githubTree(repoUrl),
    onSuccess: (loaded) => {
      setTree(loaded);
      setFilePath(loaded.files.find((file) => file.path.endsWith(".java"))?.path ?? loaded.files[0]?.path ?? "");
      setError(null);
    },
    onError: (mutationError: Error) => setError(mutationError.message),
  });

  const importSource = useMutation({
    mutationFn: () => {
      if (!exerciseId) throw new Error("Create the draft first");
      return api.importExerciseSource(exerciseId, repoUrl, filePath);
    },
    onSuccess: (imported) => {
      setSource(imported);
      setSymbols([]);
      setSelectedSymbol(null);
      setNotice("Source pinned. Load symbols and choose one to remove.");
      setError(null);
    },
    onError: (mutationError: Error) => setError(mutationError.message),
  });

  const loadSymbols = useMutation({
    mutationFn: () => {
      if (!exerciseId) throw new Error("Create the draft first");
      return api.exerciseSymbols(exerciseId);
    },
    onSuccess: (loaded) => {
      setSymbols(loaded);
      setSelectedSymbol(loaded[0] ?? null);
      setError(null);
    },
    onError: (mutationError: Error) => setError(mutationError.message),
  });

  const addStub = useMutation({
    mutationFn: () => {
      if (!exerciseId || !selectedSymbol) throw new Error("Choose a symbol first");
      return api.addExerciseStub(exerciseId, selectedSymbol.name, selectedSymbol.startLine);
    },
    onSuccess: (stub) => {
      setStubs((current) => [...current.filter((item) => item.id !== stub.id), stub]);
      setNotice(`${stub.symbolName} is stubbed. Add another symbol or publish when ready.`);
      setError(null);
    },
    onError: (mutationError: Error) => setError(mutationError.message),
  });

  const saveTests = useMutation({
    mutationFn: () => {
      if (!exerciseId) throw new Error("Create the draft first");
      const parsedSideCases = sideCases.map((item) => ({
        testId: null,
        name: item.name,
        visibility: item.visibility,
        input: parseJson(item.input),
        expected: parseJson(item.expected),
      }));
      return api.saveExerciseTests(
        exerciseId,
        tests.map((test) => ({
          stubId: stubs[0]?.id ?? null,
          name: test.name,
          visibility: test.visibility,
          content: test.content,
          timeoutMs: test.timeoutMs,
        })),
        parsedSideCases,
      );
    },
    onSuccess: (saved) => {
      setNotice(`${saved.length} test(s) saved.`);
      setError(null);
    },
    onError: (mutationError: Error) => setError(mutationError.message),
  });

  const publish = useMutation({
    mutationFn: () => {
      if (!exerciseId) throw new Error("Create the draft first");
      return api.publishExercise(exerciseId);
    },
    onSuccess: (published) => navigate(`/user/exercises/${published.id}`),
    onError: (mutationError: Error) => setError(mutationError.message),
  });

  const canCreate = Boolean(title.trim() && summary.trim() && (isSubtask || Number(price) >= 0));
  const canPublish = Boolean(exerciseId && source && stubs.length > 0
    && tests.some((test) => test.visibility === "PUBLIC"));

  if (me.data?.role === "WORKER" && !isSubtask) {
    return <ErrorBox>Workers publish subtasks from a task they have accepted. Open My tasks first.</ErrorBox>;
  }
  if (isSubtask && me.data?.role !== "WORKER") {
    return <ErrorBox>Only a worker who accepted the parent task can publish a subtask.</ErrorBox>;
  }
  if (me.data?.role !== "PUBLISHER" && me.data?.role !== "ADMIN" && me.data?.role !== "WORKER") {
    return <ErrorBox>Publisher or accepted worker role required to create tasks.</ErrorBox>;
  }

  return (
    <>
      <PageHead
        title={isSubtask ? "Publish a subtask" : "Create a stub task"}
        subtitle={isSubtask
          ? `Delegate part of ${parent.data?.title ?? "your accepted task"} to another worker.`
          : "Import a public GitHub file, remove a function body, attach tests, then publish."}
        actions={
          <Link className="btn small" to={parentId ? `/user/exercises/${parentId}` : "/user/exercises"}>
            {parentId ? "Back to parent task" : "Back to tasks"}
          </Link>
        }
      />
      {error && <ErrorBox>{error}</ErrorBox>}
      {notice && <Notice>{notice}</Notice>}

      <div className="wizard">
        <Card title="1. Task details" subtitle={isSubtask
          ? "The board shows a $ to $$$$ tier from complexity, never an amount."
          : "This becomes the public listing and the signed task document."}>
          <div className="grid grid-2">
            <Field label="Title">
              <input className="input" value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Implement interval merging" />
            </Field>
            <Field label="Complexity">
              <select className="select" value={complexity} onChange={(event) => setComplexity(event.target.value)}>
                {["TRIVIAL", "EASY", "MEDIUM", "HARD", "EXPERT"].map((value) => (
                  <option key={value} value={value}>
                    {value.toLowerCase()}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <Field label="Summary">
            <textarea className="textarea" value={summary} onChange={(event) => setSummary(event.target.value)} placeholder="What should the worker implement, and what is out of scope?" />
          </Field>
          <div className={`grid ${isSubtask ? "grid-2" : "grid-3"}`}>
            {!isSubtask && (
              <Field label="Internal offered price (USD)" hint="Stored for the publisher; never shown on the board.">
                <input className="input" type="number" min="0" step="1" value={price} onChange={(event) => setPrice(event.target.value)} />
              </Field>
            )}
            <Field label="Deadline (days)">
              <input className="input" type="number" min="1" max="30" value={deadlineDays} onChange={(event) => setDeadlineDays(Number(event.target.value))} />
            </Field>
            <Field label="Language" hint="Java is the first supported authoring path.">
              <input className="input" value="java" disabled />
            </Field>
          </div>
          <div className="row" style={{ marginTop: 8 }}>
            <span className="small muted">
              Board tier: <strong className="price-tier">{priceTier(complexity)}</strong> for {complexity.toLowerCase()}
            </span>
          </div>
          <Button variant="primary" disabled={!canCreate || create.isPending || Boolean(exerciseId)} onClick={() => create.mutate()}>
            {exerciseId ? "Draft created" : create.isPending ? "Creating" : isSubtask ? "Create subtask draft" : "Create draft"}
          </Button>
        </Card>

        <Card title="2. Import source" subtitle="Public GitHub only in this version. The commit SHA is pinned.">
          <div className="inline-form">
            <Field label="Repository URL">
              <input className="input" value={repoUrl} onChange={(event) => setRepoUrl(event.target.value)} placeholder="https://github.com/owner/repo" />
            </Field>
            <Field label="File">
              <select className="select" value={filePath} onChange={(event) => setFilePath(event.target.value)} disabled={!tree}>
                <option value="">{tree ? "Choose a file" : "Load the repository first"}</option>
                {(tree?.files ?? []).map((file) => (
                  <option key={file.path} value={file.path}>
                    {file.path}
                  </option>
                ))}
              </select>
            </Field>
            <div className="row">
              <Button disabled={!exerciseId || !repoUrl || loadTree.isPending} onClick={() => loadTree.mutate()}>
                {loadTree.isPending ? "Loading" : "Load files"}
              </Button>
              <Button variant="primary" disabled={!exerciseId || !filePath || importSource.isPending} onClick={() => importSource.mutate()}>
                {importSource.isPending ? "Importing" : "Import"}
              </Button>
            </div>
          </div>
          {tree && (
            <p className="small muted">
              Pinned commit <span className="mono">{tree.commitSha.slice(0, 12)}…</span> · license{" "}
              {tree.license ?? "not detected"}
            </p>
          )}
          {source && (
            <div className="source-summary">
              <Badge tone={source.findings.length ? "warn" : "success"}>
                {source.findings.length ? "Review warnings" : "Source pinned"}
              </Badge>
              <span className="mono">{source.filePath}</span>
              {source.findings.map((finding) => (
                <div key={finding} className="small" style={{ color: "var(--warning)" }}>
                  {finding}
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card title="3. Choose the stub" subtitle="Select a method or constructor; the body is replaced with a TODO throw.">
          <div className="row" style={{ marginBottom: 12 }}>
            <Button disabled={!source || loadSymbols.isPending} onClick={() => loadSymbols.mutate()}>
              {loadSymbols.isPending ? "Parsing" : "Load symbols"}
            </Button>
            <span className="small muted">{symbols.length} symbol(s)</span>
          </div>
          {symbols.length > 0 && (
            <div className="symbol-list">
              {symbols.map((symbol) => (
                <button
                  key={`${symbol.name}-${symbol.startLine}`}
                  type="button"
                  className={`symbol-row${selectedSymbol?.startLine === symbol.startLine && selectedSymbol.name === symbol.name ? " active" : ""}`}
                  onClick={() => setSelectedSymbol(symbol)}
                >
                  <span>
                    <strong>{symbol.name}</strong>
                    <span className="small muted"> {symbol.kind.toLowerCase()}</span>
                  </span>
                  <span className="mono small">lines {symbol.startLine}–{symbol.endLine}</span>
                </button>
              ))}
            </div>
          )}
          <div className="row" style={{ marginTop: 12 }}>
            <Button variant="primary" disabled={!selectedSymbol || addStub.isPending} onClick={() => addStub.mutate()}>
              {addStub.isPending ? "Stubbing" : "Create stub"}
            </Button>
            <span className="small muted">{stubs.length} stub(s) ready</span>
          </div>
          {stubs.map((stub) => (
            <details key={stub.id} className="stub-preview">
              <summary>
                {stub.symbolName} · lines {stub.startLine}–{stub.endLine}
              </summary>
              <pre className="code">{stub.stubContent}</pre>
            </details>
          ))}
        </Card>

        <Card title="4. Tests and edge cases" subtitle="Public tests are shown to workers; hidden tests stay private.">
          {tests.map((test, index) => (
            <div key={index} className="test-editor">
              <div className="grid grid-3">
                <Field label="Name">
                  <input className="input" value={test.name} onChange={(event) => updateAt(setTests, index, { name: event.target.value })} />
                </Field>
                <Field label="Visibility">
                  <select className="select" value={test.visibility} onChange={(event) => updateAt(setTests, index, { visibility: event.target.value as "PUBLIC" | "HIDDEN" })}>
                    <option value="PUBLIC">public</option>
                    <option value="HIDDEN">hidden</option>
                  </select>
                </Field>
                <Field label="Timeout (ms)">
                  <input className="input" type="number" min="100" max="60000" value={test.timeoutMs} onChange={(event) => updateAt(setTests, index, { timeoutMs: Number(event.target.value) })} />
                </Field>
              </div>
              <Field label="Test content">
                <textarea className="textarea" value={test.content} onChange={(event) => updateAt(setTests, index, { content: event.target.value })} />
              </Field>
            </div>
          ))}
          <Button small onClick={() => setTests((current) => [...current, { name: "edge case", visibility: "HIDDEN", content: "", timeoutMs: 5000 }])}>
            Add test
          </Button>

          <div className="section-title">Side cases</div>
          {sideCases.map((sideCase, index) => (
            <div key={index} className="test-editor">
              <div className="grid grid-3">
                <Field label="Name">
                  <input className="input" value={sideCase.name} onChange={(event) => updateAt(setSideCases, index, { name: event.target.value })} />
                </Field>
                <Field label="Visibility">
                  <select className="select" value={sideCase.visibility} onChange={(event) => updateAt(setSideCases, index, { visibility: event.target.value as "PUBLIC" | "HIDDEN" })}>
                    <option value="PUBLIC">public</option>
                    <option value="HIDDEN">hidden</option>
                  </select>
                </Field>
                <Field label="Input JSON">
                  <input className="input mono" value={sideCase.input} onChange={(event) => updateAt(setSideCases, index, { input: event.target.value })} />
                </Field>
              </div>
              <Field label="Expected JSON">
                <input className="input mono" value={sideCase.expected} onChange={(event) => updateAt(setSideCases, index, { expected: event.target.value })} />
              </Field>
            </div>
          ))}
          <Button small onClick={() => setSideCases((current) => [...current, { name: "empty input", visibility: "PUBLIC", input: "{}", expected: "{}" }])}>
            Add side case
          </Button>
          <div className="row" style={{ marginTop: 12 }}>
            <Button disabled={!exerciseId || saveTests.isPending} onClick={() => saveTests.mutate()}>
              {saveTests.isPending ? "Saving" : "Save tests"}
            </Button>
          </div>
        </Card>

        <Card title="5. Publish" subtitle={isSubtask
          ? "Publication opens the subtask to other workers on the public task board."
          : "Publication makes the task immutable and opens it to workers."}>
          <div className="row">
            <Button variant="primary" disabled={!canPublish || publish.isPending} onClick={() => publish.mutate()}>
              {publish.isPending ? "Publishing" : "Publish task"}
            </Button>
            <span className="small muted">
              {stubs.length} stub(s), {tests.filter((test) => test.visibility === "PUBLIC").length} public test(s), tier {priceTier(complexity)}
            </span>
          </div>
        </Card>
      </div>
    </>
  );
}

function updateAt<T>(setter: Dispatch<SetStateAction<T[]>>, index: number, patch: Partial<T>) {
  setter((current) => current.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)));
}

function parseJson(value: string): unknown {
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

export function WorkspacePage() {
  const params = useParams();
  const id = params.id ?? "";
  const workspace = useQuery({
    queryKey: ["exercise-workspace", id],
    queryFn: () => api.exerciseWorkspace(id),
    enabled: Boolean(id),
  });
  const [selectedStubId, setSelectedStubId] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [check, setCheck] = useState<{ passed: boolean; findings: string[] } | null>(null);
  const [submission, setSubmission] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [instructionsOpen, setInstructionsOpen] = useState(false);

  const data = workspace.data;
  const stubs = data?.exercise.stubs ?? [];
  const activeStub = stubs.find((stub) => stub.id === selectedStubId) ?? stubs[0] ?? null;
  const currentCode = code || activeStub?.stubContent || "";

  const runCheck = useMutation({
    mutationFn: () => api.staticCheckExercise(id, currentCode),
    onSuccess: (result) => {
      setCheck(result);
      setError(null);
    },
    onError: (mutationError: Error) => setError(mutationError.message),
  });

  const submit = useMutation({
    mutationFn: () => api.submitExercise(id, currentCode),
    onSuccess: (result) => {
      setSubmission(`Submission ${result.id} stored with status ${result.status}.`);
      setError(null);
    },
    onError: (mutationError: Error) => setError(mutationError.message),
  });

  if (workspace.isLoading) return <Loading label="Opening workspace" />;
  if (workspace.isError || !data) {
    return (
      <ErrorBox>
        {workspace.error?.message ?? "Workspace unavailable"}.{" "}
        <Link to={`/user/exercises/${id}`}>Read the task</Link>
      </ErrorBox>
    );
  }

  return (
    <div className="workspace-page">
      <PageHead
        title={data.exercise.title}
        subtitle={`Accepted · deadline ${formatDate(data.deadlineAt)} · execution disabled`}
        actions={
          <div className="row">
            <Badge tone={stateTone(data.status.toLowerCase())}>{data.status}</Badge>
            <Link className="btn small" to={`/user/exercises/${id}`}>
              Task
            </Link>
            <Link className="btn small primary" to={`/user/exercises/new?parent=${id}`}>
              Publish subtask
            </Link>
          </div>
        }
      />
      {error && <ErrorBox>{error}</ErrorBox>}
      {submission && <Notice>{submission}</Notice>}

      <div className="workspace-split">
        <Card
          className="workspace-editor-card"
          title="Editor"
          subtitle="The stub is a starting point; replace the TODO with your implementation."
        >
          {stubs.length > 1 && (
            <div className="row wrap" style={{ marginBottom: 8 }}>
              {stubs.map((stub) => (
                <button
                  key={stub.id}
                  type="button"
                  className={`chip-btn${activeStub?.id === stub.id ? " active" : ""}`}
                  onClick={() => {
                    setSelectedStubId(stub.id);
                    setCode(stub.stubContent);
                  }}
                >
                  {stub.symbolName}
                </button>
              ))}
            </div>
          )}
          <textarea
            className="textarea code-editor"
            value={currentCode}
            onChange={(event) => setCode(event.target.value)}
            spellCheck={false}
          />
          <div className="row workspace-actions">
            <Button disabled={runCheck.isPending} onClick={() => runCheck.mutate()}>
              {runCheck.isPending ? "Checking" : "Run static checks"}
            </Button>
            <Button variant="primary" disabled={submit.isPending} onClick={() => submit.mutate()}>
              {submit.isPending ? "Submitting" : "Submit"}
            </Button>
            <span className="small muted">{currentCode.split("\n").length} lines</span>
          </div>
          {check && (
            <div className={`check-result ${check.passed ? "pass" : "fail"}`}>
              <strong>
                {check.passed ? "Static checks passed" : "Static checks found problems"}
              </strong>
              {check.findings.map((finding) => (
                <div key={finding} className="small">
                  {finding}
                </div>
              ))}
            </div>
          )}
        </Card>

        <DockPane
          label="Instructions"
          open={instructionsOpen}
          onOpenChange={setInstructionsOpen}
        >
          <div className="instructions-panel">
            <div className="instructions-head">
              <span className="eyebrow">Task instructions</span>
              <h2>{data.exercise.title}</h2>
              <p className="small muted">{data.exercise.summary}</p>
              <div className="row wrap">
                <Badge tone={stateTone(data.status.toLowerCase())}>{data.status}</Badge>
                <span className="small muted">Deadline {formatDate(data.deadlineAt)}</span>
                <Link className="btn small" to={`/user/exercises/${id}`}>
                  Open full task
                </Link>
              </div>
            </div>
            <div className="instructions-scroll">
              <Notice>
                This workspace saves and statically checks your code. It does not compile or run
                untrusted code in this milestone, so test results are not produced here.
              </Notice>
              <Card title="Task document" subtitle="The exact text you accepted.">
                <pre className="code">{data.exercise.taskDocument}</pre>
              </Card>
              <Card title="Public tests" subtitle="Execution is disabled; these are for guidance.">
                {data.exercise.tests
                  .filter((test) => test.visibility === "PUBLIC")
                  .map((test) => (
                    <div key={test.id} className="test-row">
                      <strong>{test.name}</strong>
                      <pre className="code">{test.content}</pre>
                    </div>
                  ))}
                {data.exercise.tests.filter((test) => test.visibility === "PUBLIC").length ===
                  0 && <div className="small muted">No public tests were provided.</div>}
                {data.exercise.sideCases.filter((item) => item.visibility === "PUBLIC").length >
                  0 && (
                  <>
                    <div className="section-title">Side cases</div>
                    {data.exercise.sideCases
                      .filter((item) => item.visibility === "PUBLIC")
                      .map((item) => (
                        <div key={item.id} className="test-row">
                          <strong>{item.name}</strong>
                          <pre className="code">
                            {formatJson({ input: item.input, expected: item.expected })}
                          </pre>
                        </div>
                      ))}
                  </>
                )}
              </Card>
            </div>
          </div>
        </DockPane>
      </div>
    </div>
  );
}

export function ExerciseSubmissionsPage() {
  const params = useParams();
  const id = params.id ?? "";
  const submissions = useQuery({
    queryKey: ["exercise-submissions", id],
    queryFn: () => api.exerciseSubmissions(id),
    enabled: Boolean(id),
  });
  if (submissions.isLoading) return <Loading label="Loading submissions" />;
  if (submissions.isError) return <ErrorBox>{submissions.error.message}</ErrorBox>;
  const items = submissions.data ?? [];
  return (
    <>
      <PageHead title="Submissions" subtitle="Stored implementations and static-check findings." />
      {items.length === 0 ? (
        <div className="empty">No submissions yet.</div>
      ) : (
        <div className="grid">
          {items.map((item) => (
            <Card key={item.id} title={item.workerId} subtitle={`Status ${item.status} · ${formatDate(item.submittedAt)}`}>
              <div className="mono small">{item.contentHash}</div>
              <pre className="code">{formatJson(item.staticFindings)}</pre>
            </Card>
          ))}
        </div>
      )}
    </>
  );
}
