# Past final-commit comments — code change audit log

This file is the **audit log** for accepted code changes. Each time a
change is accepted, we add one entry below. Later, anyone can read an entry and
understand **what changed**, **why the decision was made**, and **see the code
before and after**.

- **Written for:** future you (and any new developer) auditing decisions.
- **Rule:** every accepted change gets an entry — no change without a note.
- **Plain language:** this repo is explained simply; keep entries simple too.

---

## How to add an entry (procedure)

When a change is finished and accepted, add an entry **at the top** of
"Entries" (newest first) using this shape:

1. **Heading:** `## <date> — <short title> (<commit or "uncommitted">)`.
2. **What changed:** one or two sentences in plain words.
3. **Why (decision):** the reason the source code was changed — the problem it
   solves, the trade-off accepted, and any alternative we rejected. This is the
   part that makes the decision auditable later.
4. **Files touched:** list each file as `path:line`.
5. **Code — modified files:** show **Before** and **After** (the exact lines that
   changed).
6. **Code — new files:** paste the new code (or the key part) and add a short
   comment plus **line numbers**, so it can be found again.
7. **References:** link related docs, issues, or the previous entry.

> If a change only edits documentation (no `src` code), still add an entry, but
> you may skip the Before/After code blocks and just say which docs changed.

---

## Entries

## 2026-10-22 — Phase 1 runner protocol and control plane (uncommitted)

**What changed.** Added the control-plane side of the publisher-runner protocol:
runner registration with a one-time bearer token, heartbeat, lease of one accepted
contract, Ed25519-signed result submission, revocation/quarantine, and admin
listing. Flyway `V3` adds `runner`, `job_lease` and `runner_result`. The protocol is
covered by four Postgres/Testcontainers integration tests.

**Why (decision).** ADR-0004 requires deadline-bound leases, idempotent signed
results and lease-and-retry failover. This slice implements those rules in the
control plane without pretending the appliance or evaluator exists yet. The
runner authenticates with a bearer token whose SHA-256 hash is stored, and every
result is additionally signed with the runner's Ed25519 key. A stolen token alone
therefore cannot forge a verdict. The signed message binds the lease, nonce,
contract and the SHA-256 of the **exact JSON text** the runner sent, avoiding a
canonical-JSON implementation on both sides.

Trade-offs accepted: a completed lease stays completed when its runner is
revoked (its result is quarantined, so the job can be re-run); an expired lease
cannot submit late; registration is disabled until the operator sets
`SYMMETRY_RUNNER_REGISTRATION_TOKEN`; the lease currently returns only contract
identity and task-document hash because submissions/source binding do not exist
yet. Rejected: user JWTs for machine runners (no machine identity yet), signing
only selected parsed fields (tamperable metrics), accepting results after expiry
(unsafe), and a plain container as the runner boundary (ADR-0001).

**Files touched.**
- `platform/src/main/resources/db/migration/V3__runner_protocol.sql:11` (`runner`), `:65` (`job_lease`), `:97` (one active lease per contract), `:106` (`runner_result`).
- `platform/src/main/java/io/symmetry/runner/Runner.java:22`, `JobLease.java:13`, `RunnerResult.java:17` (entities).
- `platform/src/main/java/io/symmetry/runner/RunnerAuthService.java:25` (registration token and bearer auth).
- `platform/src/main/java/io/symmetry/runner/RunnerService.java:62` (register), `:114` (lease), `:146` (signed result), `:207` (revoke/quarantine).
- `platform/src/main/java/io/symmetry/runner/RunnerController.java:25` (HTTP surface).
- `platform/src/main/java/io/symmetry/runner/RunnerRepository.java`, `JobLeaseRepository.java`, `RunnerResultRepository.java` (persistence).
- `platform/src/main/java/io/symmetry/contract/ContractRepository.java:48` (lease query).
- `platform/src/main/java/io/symmetry/contract/MessageSigner.java:50` (result signature message and key check).
- `platform/src/main/java/io/symmetry/config/SecurityConfig.java:69` (machine POSTs bypass user JWT).
- `platform/src/main/resources/application.properties:23` (registration token and lease seconds).
- `platform/src/test/java/io/symmetry/runner/RunnerApiIntegrationTest.java:59` (register/heartbeat/lease/idempotent result), `:96` (tamper), `:115` (revocation/quarantine), `:157` (bad token).

**Code — modified files.**

Before (`MessageSigner.java`): only the acceptance message existed.
After (at `:50`):

```java
public static String canonicalResultMessage(UUID leaseId, String nonce, UUID contractId,
        String payloadHash) {
    return "result:" + leaseId + ":" + nonce + ":" + contractId + ":" + payloadHash;
}
```

Before (`SecurityConfig.java`): every non-health request needed a user JWT.
After (at `:69`):

```java
.requestMatchers(HttpMethod.POST, "/runners/register").permitAll()
.requestMatchers(HttpMethod.POST, "/runners/*/heartbeat",
        "/runners/*/lease", "/runners/*/results").permitAll()
```

The runner endpoints authenticate with the issued bearer token instead. Admin
`GET /runners*` and `POST /runners/{id}/revoke` still require a normal session.

Before (`ContractRepository.java`): ended at the module-version lookup.
After (at `:48`): a locked query that leases only accepted contracts without an
active/completed lease or a live result. `lock.timeout=-2` maps to
`FOR UPDATE SKIP LOCKED`, so two runners cannot take the same job.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
@Query("""
        select c from Contract c
         where c.lifecycleState = io.symmetry.task.LifecycleState.ACCEPTED
           and not exists (select l.id from JobLease l
                where l.contractId = c.id
                  and (l.status = io.symmetry.runner.LeaseStatus.ACTIVE
                       or l.status = io.symmetry.runner.LeaseStatus.COMPLETED))
           and not exists (select r.id from RunnerResult r
                where r.contractId = c.id and r.quarantined = false)
         order by c.createdAt asc
        """)
List<Contract> findLeasable(Pageable pageable);
```

**Code — new files (key parts).**

Registration stores only the token hash (`RunnerService.java:62`):

```java
RunnerAuthService.IssuedToken token = RunnerAuthService.issueToken();
Runner runner = new Runner(UUID.randomUUID(), request.name().trim(), ...,
        token.hash(), request.version().trim(), capabilities);
runners.saveAndFlush(runner);
return new RegisteredRunnerView(runner.getId(), token.raw(),
        runner.getStatus().name().toLowerCase(), runner.getRegisteredAt(), leaseSeconds);
```

The lease carries a deadline and nonce; expiry is retried instead of blocking
(`RunnerService.java:114`):

```java
leases.expireStale(now);
JobLease lease = new JobLease(UUID.randomUUID(), runner.getId(), contract.getId(),
        RunnerAuthService.newNonce(), now, now.plusSeconds(leaseSeconds));
```

The result is verified before storage, then marks the lease complete
(`RunnerService.java:146`):

```java
String payloadHash = MessageSigner.sha256(request.payload());
String message = MessageSigner.canonicalResultMessage(lease.getId(), lease.getNonce(),
        lease.getContractId(), payloadHash);
if (!MessageSigner.verify(runner.getPublicKey(), request.signature(), message)) {
    throw badRequest("Result signature does not verify with the runner public key");
}
...
lease.complete(now);
```

Revocation expires active leases and quarantines all of the runner's results, so
payment-critical work can be re-run elsewhere (`RunnerService.java:207`):

```java
runner.revoke(now);
leases.revokeActiveForRunner(runnerId);
results.quarantineForRunner(runnerId);
```

**Verification.** `sg docker -c './mvnw -B verify'` passed: **40 tests, 0 failures**,
including the four new runner integration tests. Flyway applied V1–V3 against
PostgreSQL 16.15.

**References.** [ADR-0001](../docs/adr/0001-runner-isolation.md),
[ADR-0004](../docs/adr/0004-updates-and-failover.md),
[API surface](../docs/DESIGN.md#11-the-buttons-and-doors-api-surface),
previous entry (uncommitted ADR change). Next: the appliance agent and the
submission/source binding that gives a lease real code to evaluate.

---

## 2026-10-22 — Runner isolation, licensing and AI payment ADRs (uncommitted)

**What changed.** Added six architecture decision records and a licence map, then
reconciled the design, stack, implementation and agent docs with them. The runner
is now a **publisher-hosted AlmaLinux 9 appliance** that runs untrusted code in
**KVM microVMs**. The JVM, Chicory/WASM and CPython tiers are inner layers, not the
host boundary. Added the split licence (Apache-2.0 agent/SDK, AGPL-3.0 server),
signed OTA plus offline bundles, AI-final payment with escrow and one human
appeal, and the AWS/GCP nested-virt support tiers.

**Why (decision).** The previous docs described the Java path as a locked-down
container and left the runner, update and payment decisions open. That was too
weak for hostile code and payment-critical evaluation, and it did not match the
owner's choices: publisher-owned hardware/electricity, software and updates from
Symmetry, x86-64 KVM, Java/Rust/Python, AI-final payment, open-source updates and
a Red Hat-style commercial model.

Trade-offs accepted: KVM needs `/dev/kvm`, so unsupported cloud hosts fail
preflight instead of silently falling back to Docker; three language runtimes add
image size and CVE work; AGPL may require a commercial exception for some
enterprises; AI-final needs escrow, an appeal and a liability cap to be defensible.
Rejected: Docker/chroot as the boundary, cloud-first hosting, RHEL qcow2
redistribution, unsigned updates, and AI-final with no appeal.

**Key decisions recorded.**

| Topic | Decision |
|---|---|
| Isolation | KVM microVM; Firecracker preferred; Kata on the neutral Kubernetes fleet |
| Host/guest | AlmaLinux 9 host; Alpine guests; glibc guests for Java/Python when needed |
| Images | qcow2/OVA; AWS AMI and GCP image only on nested-virt instance types |
| Licensing | Apache-2.0 `runner/` + `wasm-sdk/`; AGPL-3.0 `platform/` + `ai/` + `ui/` + `deploy/` |
| Payment | AI-final first decision; escrow; one evidence-based human appeal; liability cap |
| Updates | TUF-style signed OTA plus signed offline bundles; A/B rollback |
| Failover | Lease-and-retry; signed results; no live migration in V1 |

**Files touched.**
- `docs/adr/README.md:1` (ADR index).
- `docs/adr/0001-runner-isolation.md:1` (KVM microVM boundary).
- `docs/adr/0002-licensing.md:1` (Apache/AGPL split).
- `docs/adr/0003-ai-adjudication.md:1` (AI-final, escrow, one appeal).
- `docs/adr/0004-updates-and-failover.md:1` (signed OTA, leases).
- `docs/adr/0005-language-runtimes.md:1` (Java, Rust, Python).
- `docs/adr/0006-cloud-appliance.md:1` (qcow2, AMI, GCP image).
- `LICENSING.md:1` (path-by-path licence map).
- `LICENSE-APACHE:1`, `LICENSE-AGPL:1` (canonical licence texts).
- `CONTRIBUTING.md:1` (DCO and contribution licences), `NOTICE:1`.
- `docs/DESIGN.md:33` (services, microVM boundary, payment, data model, API, risks).
- `docs/STACK.md:12` (runner appliance, neutral evaluator, licensing).
- `docs/IMPLEMENTATION.md:24` (outer microVM and three runtime tiers).
- `AGENTS.md:3` (load-bearing decisions and verify rules).
- `README.md:105` (supported runner hosts and licensing summary).
- `docs/NEW-BUILDS.md:32` (Java/Rust/Python are the first three, not the limit).
- `ui/README.md:3` (stale UI-plan link fixed to the lightweight mock).

**Code — documentation only.** No application code changed. The key architectural
snippet is the boundary rule:

```text
AlmaLinux 9 host
  └── KVM microVM (Firecracker/Kata)
        ├── Java 21 (JVM tier)
        ├── Rust (wasm32 in Chicory)
        └── Python 3.13 (CPython)
```

**Verification.** Relative links checked across the changed docs; existing
`platform/`, `ui/` and `ai/` checks were not affected by this documentation-only
change. The `runner/` verify command does not exist yet and is explicitly marked
as unverified in `AGENTS.md`.

**References.** [ADR index](../docs/adr/README.md), [LICENSING.md](../LICENSING.md),
previous entry (`7951a45`).

---

## 2026-10-21 — Rename UI plan to lightweight mock (commit 7951a45)

**What changed.** Renamed `docs/UI-PLAN.md` to
`docs/UI_LightWeight_mock.md` and replaced the Apple-specific wording
("macOS-Settings-inspired", "macOS/Harbor", "macOS feel", "macOS-quiet") with
"lightweight UI". The Harbor tokens, routes, component rules and implementation
are unchanged; only the positioning and file name changed.

**Why (decision).** The owner wanted the UI documented as a lightweight mock
rather than an imitation of a specific operating system. Keeping the visual rules
but dropping the macOS label avoids implying platform cloning and keeps the
design language neutral. Trade-off: the old path is gone, so links that still say
`docs/UI-PLAN.md` must be updated when touched; the content itself did not change.

**Files touched.**
- `docs/UI_LightWeight_mock.md:1` (renamed; wording changes at lines 3, 18, 20, 21, 26, 405).

**Code — documentation only.** No before/after code blocks required.

**References.** Previous entry (`ffd8a75`); `docs/STACK.md:87`, `ui/README.md:5`
still mention the old filename.

---

## 2026-10-21 — 0.0.1 release artifacts: database backup and screenshots (commit ffd8a75)

**What changed.** Added a compressed PostgreSQL development snapshot under
`data/pgsql/0.0.1/`, with checksum and restore instructions. Captured real
screenshots of the three main pages (worker overview, publisher organization
overview, admin platform overview) into `docs/screenshots/0.0.1/` and embedded
them in the root `README.md`. The images were taken through the actual role
picker with Playwright/Chromium, not mocked.

**Why (decision).** The owner asked for a restorable database snapshot and visual
proof of the running build. A plain `pg_dump --clean --if-exists` is the simplest
restore path and keeps the file readable with only `gzip` + `psql`; a custom-format
dump would add `pg_restore` as a second tool. The snapshot is explicitly marked
**development only** because it contains seeded demo identities and demo data, not
production secrets. Screenshots are served from `docs/` so GitHub renders them in
the README without external hosting. Trade-off: binary artifacts grow the repo;
the alternative (external object storage) would make the 0.0.1 state less
self-contained.

**Files touched.**
- `data/pgsql/0.0.1/symmetry-0.0.1-20261021.sql.gz` (new snapshot).
- `data/pgsql/0.0.1/README.md:1` (checksum, contents, restore commands).
- `docs/screenshots/0.0.1/worker-overview.png` (new).
- `docs/screenshots/0.0.1/publisher-overview.png` (new).
- `docs/screenshots/0.0.1/admin-overview.png` (new).
- `README.md:31` (Screenshots section + backup link).
- `docs/past_final_commit_comments.md` (this entry).

**Verification.** `gzip -t` passed; SHA-256
`9b2a3eeaeab57bde83296d1754c6c89402114ea1d63eeb7e19289cb4b8daac3f`.
The screenshot script waited for each role-specific heading and for loading
spinners to clear; it reported zero console/page errors. Backend
`./mvnw -B verify` (36 tests) and `ui/npm run build` had already passed.

---

## 2026-10-21 — Harbor UI: all worker, publisher and admin pages (commit ffd8a75)

**What changed.** The React console is now the Harbor UI described in
`docs/UI-PLAN.md`. AWS Cloudscape is gone. A typed API client, dev auth, theme
switcher, role-specific shell and pages for worker (`Overview`, `Contracts`,
`Acceptances`, `Submissions`, `Earnings`, `Settings`), publisher (`Contracts`,
`Workers`, `Evaluations`, `Analytics`, `Payments`, `Settings`) and admin
(`Overview`, `Analytics`, `Organizations`, `Users`, `Troubleshooting`,
`Configuration`, `Requests`, `Audit`) are implemented. Charts are small inline
SVGs paired with data tables; dialogs/toggles/tabs use Radix primitives.

**Why (decision).** Cloudscape looks like AWS and cannot be themed into the
macOS-Settings/Harbor mock without heavy overrides, so we own a small token-based
design system instead. Pages are thin: every number comes from the API and every
mutation invalidates its query, so role scoping and audit stay server-side. The
workbench signs the exact canonical message
(`accept:<module>@<version>:sha256(taskDocument)`) with a fresh Ed25519 key in
the browser and sends only the public key + signature. Trade-offs: charts are
hand-built rather than a chart library (less code, full token control); sign-in
still uses `X-Symmetry-User` in dev until Keycloak/PKCE is wired; payments and
submissions explicitly say they are offline instead of faking data. Rejected:
keeping Cloudscape, a chart dependency for four simple charts, and client-side
role checks as the source of truth.

**Files touched.**
- `ui/package.json` (removed `@cloudscape-design/*`, added `react-router-dom` + Radix dialog/switch/tabs), `ui/src/main.tsx:1` (Harbor CSS + `initTheme` + QueryClient).
- `ui/src/App.tsx:1` (route tree + role guards), `ui/src/api.ts:1` (typed client, `X-Symmetry-User`, `ApiError`), `ui/src/auth.ts:1`, `ui/src/theme.ts:1`, `ui/src/format.ts:1`.
- `ui/src/styles.css:1` (tokens, components, dark mode, responsive, Radix dialog), `ui/src/components/`: `Shell.tsx:76`, `ui.tsx:1`, `charts.tsx:1`, `icons.tsx:1`.
- `ui/src/pages/`: `Login.tsx:1`, `Overview.tsx:1`, `Contracts.tsx:31,290,404`, `Worker.tsx:1`, `Publisher.tsx:1`, `Settings.tsx:19`, `Admin.tsx:28`.
- `ui/README.md:1`, `docs/UI-PLAN.md:3` (status now implemented).

**Code — modified (representative Before/After).**

`ui/src/App.tsx` replaced the Cloudscape shell with a router tree:

```tsx
// Before: <TopNavigation/> + <SideNavigation/> + <ContractsTable/>
// After (excerpt):
<Route path="/user" element={<Shell />}>
  <Route path="overview" element={<OverviewPage />} />
  <Route path="contracts/:moduleId/:version/workbench"
    element={<RequireRole allow={["WORKER"]}><WorkbenchPage /></RequireRole>} />
  <Route path="admin/overview"
    element={<RequireRole allow={["ADMIN"]}><AdminOverviewPage /></RequireRole>} />
</Route>
```

`ui/src/main.tsx` loads Harbor instead of Cloudscape:

```tsx
// Before
import "@cloudscape-design/global-styles/index.css";
// After
import { initTheme } from "./theme";
import "./styles.css";
initTheme();
```

**New code — signing the acceptance (key part).**

`ui/src/pages/Contracts.tsx:385`

```tsx
const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(taskDocument));
const message = `accept:${moduleId}@${version}:${hex(digest)}`;
const keyPair = await crypto.subtle.generateKey({ name: "Ed25519" }, true, ["sign", "verify"]);
const signature = await crypto.subtle.sign({ name: "Ed25519" }, keyPair.privateKey,
    new TextEncoder().encode(message));
// POST publicKey (SPKI/base64) + signature (raw/base64) + the six agreements
```

**Verification.** `cd ui && npm run build` → TypeScript + Vite build passed
(161 modules). Live API smoke tests with `worker|publisher|admin@symmetry.local`
confirmed the shape of `/me`, `/org/*`, `/worker/*`, `/admin/*`, publishing and
the tier-filtered workbench.

---

## 2026-10-21 — Config keys are restricted by scope (commit ffd8a75)

**What changed.** `ConfigKeys` now declares which keys may be set at `ORG` /
`CONTRACT` and which at `USER`; `SettingService` and `ConfigRequestService`
reject anything outside those sets. `GLOBAL` still accepts every known key and
is admin-only.

**Why (decision).** Settings are writable by their owner: `/me/settings` writes
`USER` scope. Without a scope check a worker could set
`evaluation.timeLimitSeconds` (or a publisher could raise `evaluation.fuelLimit`)
and bypass the correctness sandbox — a silent privilege escalation. The
allow-list keeps the existing typed keys but makes the dangerous ones
admin-only; org/contract requests are limited to evaluation, retention and
contract defaults. Trade-off: a new key must be classified in one place. Rejected:
trusting the endpoint that calls `SettingService`, and letting orgs request
evaluation keys that would apply to everyone.

**Files touched.**
- `platform/src/main/java/io/symmetry/settings/ConfigKeys.java:26,71`
- `platform/src/main/java/io/symmetry/settings/SettingService.java:56`
- `platform/src/main/java/io/symmetry/settings/ConfigRequestService.java:48`

**Code — modified (Before/After).**

```java
// Before: any known key could be written at any scope
ConfigKeys.validate(key, value);

// After
ConfigKeys.validate(key, value);
if (!ConfigKeys.allows(scope, key)) {
    throw new IllegalArgumentException("'" + key + "' cannot be set at " + scope + " scope");
}
```

**New code — the allow-list.** `ConfigKeys.java:26`

```java
private static final Set<String> ORG_KEYS = Set.of(
        WORKER_ACCEPTANCE_REQUIRED, EVALUATION_TIME_LIMIT_SECONDS,
        EVALUATION_FUEL_LIMIT, SCORING_PARETO_WEIGHTS,
        RETENTION_SUBMISSION_DAYS, CONTRACT_AUTO_PUBLISH);

private static final Set<String> USER_KEYS = Set.of(
        NOTIFICATIONS_EMAIL, AI_ASSIST_ENABLED, WORKER_MAX_CONCURRENT);

public static boolean allows(ConfigScope scope, String key) {
    return switch (scope) {
        case GLOBAL -> isKnown(key);
        case ORG, CONTRACT -> ORG_KEYS.contains(key);
        case USER -> USER_KEYS.contains(key);
    };
}
```

**Verification.** Live smoke test: `PATCH /me/settings` with
`{"evaluation.timeLimitSeconds":9999}` returns 400; the same request for
`notifications.email` succeeds. The existing 36-test suite is re-run after this
change.

---

## 2026-10-21 — Online backend: identity, scoped config, audit, analytics, admin APIs (commit ffd8a75)

**What changed.** The platform is now an online, role-aware API. V2 adds
`organization`, `app_user`, `setting`, `config_request`, `audit_log`, and
`contract.org_id/created_by`. New surfaces: `/me`, `/worker/*`, `/org/*`,
`/admin/*` (summary, analytics, organizations, users, troubleshooting, config,
config requests, audit, SSE). Publishing is publisher/admin-only, acceptance is
worker-only, and both are audited. Settings resolve `USER > CONTRACT > ORG >
GLOBAL`; organizations request tweaks and an admin approves them, which writes
the setting and two audit entries. Analytics are pre-aggregated in SQL.
`ai/` and `wasm-sdk/` stay **offline** and are reported as such by
`/admin/troubleshooting/*`.

**Why (decision).** The owner asked to finish the web backend, keep AI/WASM
offline for now, and make everything else online. Roles must be real, because
worker and publisher are different actors and admin is observability + audited
overrides. Dev sign-in is a header-driven filter (`X-Symmetry-User`) that only
exists while `symmetry.security.enabled=false`; in deployed mode Keycloak issues
the JWT and a converter maps `realm_access.roles` to `ROLE_*` so `hasRole`
works. Config is data, not code: an allow-list of typed keys prevents inert
typos, and **approval is explicit** so an org cannot silently change evaluation
rules. Trade-offs: SSE is in-process (one JVM) and is swapped for Redis pub/sub
later; submissions/payments/error-buffer are honest placeholders because the
evaluator is offline; analytics use SQL over the existing tables rather than a
warehouse. Rejected alternatives: trusting a client-supplied role, letting orgs
write settings directly, and putting Prometheus behind the browser.

**Files touched.**
- `platform/src/main/resources/db/migration/V2__identity_config_audit.sql:12` (new schema + trigger + indexes).
- `platform/src/main/java/io/symmetry/identity/`: `Role.java:1`, `Organization.java:1`, `AppUser.java:1`, `Actor.java:1`, `OrganizationRepository.java:1`, `AppUserRepository.java:1`, `CurrentUserService.java:28`, `DevAuthenticationFilter.java:26`, `DevDataSeeder.java:20`.
- `platform/src/main/java/io/symmetry/settings/`: `ConfigScope.java:1`, `ConfigRequestStatus.java:1`, `Setting.java:1`, `SettingRepository.java:1`, `ConfigRequest.java:1`, `ConfigRequestRepository.java:1`, `ConfigKeys.java:14`, `SettingService.java:34`, `ConfigRequestView.java:1`, `ConfigRequestService.java:72`.
- `platform/src/main/java/io/symmetry/audit/`: `AuditLog.java:1`, `AuditLogRepository.java:1`, `AuditService.java:20`.
- `platform/src/main/java/io/symmetry/analytics/`: `MetricPoint.java:1`, `AnalyticsService.java:17`.
- `platform/src/main/java/io/symmetry/events/EventService.java:21`.
- `platform/src/main/java/io/symmetry/api/`: `MeView.java:1`, `WorkerAcceptanceView.java:1`, `OrganizationView.java:1`, `UserView.java:1`, `MeController.java:21`, `ApiExceptionHandler.java:1`.
- `platform/src/main/java/io/symmetry/worker/WorkerController.java:24`.
- `platform/src/main/java/io/symmetry/org/OrgController.java:45`.
- `platform/src/main/java/io/symmetry/admin/AdminController.java:52`.
- `platform/src/main/java/io/symmetry/config/SecurityConfig.java:23,63,76`, `MethodSecurityConfig.java:1`.
- `platform/src/main/java/io/symmetry/contract/Contract.java:60`, `ContractView.java:9`, `ContractRepository.java:15`, `ContractService.java:61,65,100,155`, `ContractController.java:26,49,86`.
- `platform/src/test/java/io/symmetry/api/IdentityAdminApiIntegrationTest.java:32` (new, 6 tests).
- `platform/src/test/java/io/symmetry/contract/DisclosureServiceTest.java:37`, `ContractApiIntegrationTest.java:201`, `e2e/AcceptanceJourneyE2ETest.java:98`.
- `docs/UI-PLAN.md:255` (API list reconciled with what exists).

**Code — modified (representative Before/After).**

`ContractService.create` gained an actor so contracts carry an owner:

```java
// Before: public ContractView create(ContractRequest request) { ... }
// After:
public ContractView create(ContractRequest request) { return create(request, Actor.system()); }
public ContractView create(ContractRequest request, Actor actor) { ... }
```

`ContractController` now authorizes and audits:

```java
// Before
@PostMapping @ResponseStatus(HttpStatus.CREATED)
public ContractView create(@Valid @RequestBody ContractRequest request) { return service.create(request); }

// After
@PostMapping @ResponseStatus(HttpStatus.CREATED)
@PreAuthorize("hasAnyRole('PUBLISHER','ADMIN')")
public ContractView create(@Valid @RequestBody ContractRequest request) {
    Actor actor = currentUser.require();
    ContractView view = service.create(request, actor);
    audit.record(actor, "contract.publish", "contract", view.id().toString(), null, ...);
    events.publish(CHANNEL, "contract.published", ...);
    return view;
}
```

`SecurityConfig` keeps the dev filter inside the security chain and maps
Keycloak roles:

```java
// Before: .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
// After:
.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
        .jwtAuthenticationConverter(keycloakRealmRoles())));
```

**Code — new (key parts).**

The scope constraint is enforced by the database, not just the service
(`V2__identity_config_audit.sql:99`):

```sql
CONSTRAINT chk_setting_scope_id
    CHECK ((scope = 'GLOBAL' AND scope_id IS NULL)
        OR (scope <> 'GLOBAL' AND scope_id IS NOT NULL))
```

Precedence is one readable chain (`SettingService.java:34`):

```java
return find(ConfigScope.USER, userId, key)
        .or(() -> find(ConfigScope.CONTRACT, contractId, key))
        .or(() -> find(ConfigScope.ORG, orgId, key))
        .or(() -> find(ConfigScope.GLOBAL, null, key));
```

Approval applies the setting and writes the audit entry in one transaction
(`ConfigRequestService.java:72`):

```java
ConfigScope scope = request.getContractId() == null ? ConfigScope.ORG : ConfigScope.CONTRACT;
UUID scopeId = request.getContractId() == null ? request.getOrgId() : request.getContractId();
settings.set(scope, scopeId, request.getKey(), value, admin);
request.approve(admin.id(), note);
```

The dev identity only exists when security is off (`DevAuthenticationFilter.java:26`):

```java
@Component
@ConditionalOnProperty(name = "symmetry.security.enabled", havingValue = "false", matchIfMissing = true)
public class DevAuthenticationFilter extends OncePerRequestFilter {
    public static final String USER_HEADER = "X-Symmetry-User";
```

**Verification.** `sg docker -c './mvnw -B verify'` → 36 tests, 0 failures,
0 skipped. New coverage: `/me`, role 403s, global setting visibility, the
request → admin approve → scoped setting flow, admin users/analytics/audit, and
worker scoping.

**References.** `docs/UI-PLAN.md` §6 (UI↔backend contract); previous entry
(UI plan). UI phase starts next: remove Cloudscape, add Harbor primitives.

## 2026-10-19 — Finalize UI design + backend integration plan (uncommitted)

**What changed.** Added `docs/UI-PLAN.md`, which locks the Harbor/macOS design,
roles (`WORKER`/`PUBLISHER`/`ADMIN`), the route map (`/user/login`,
`/user/admin/login`, `/user/*`, `/user/admin/*`), the component inventory, the
REST contract, and the `V2` data model (`organization`, `app_user`, `setting`,
`config_request`, `audit_log`). Reconciled every doc that still said
"React + AWS Cloudscape" (`STACK.md`, `DESIGN.md`, `IMPLEMENTATION.md`,
`README.md`, `AGENTS.md`, the UML doc/diagram, `ui/README.md`).

**Why (decision).** The owner asked whether the current design is "AWS-flavored
macOS". It is not: the approved mock is hand-written CSS, while Cloudscape is
Amazon's AWS visual language and cannot be themed into it. So keeping the Harbor
design requires a **custom design system + Radix**; Cloudscape is dropped.
Trade-off: we lose vendor-tested tables/wizards and must own accessibility. The
rejected alternative was keeping Cloudscape with token overrides, which still
looks AWS and costs more than owning ~15 primitives. Roles are separate
(`WORKER` ≠ `PUBLISHER`), admin is **read-only** over users/orgs plus scoped
overrides, and orgs **request** task-specific tweaks through an audited workflow.
Config is data with precedence `USER > CONTRACT > ORG > GLOBAL`; analytics are
donut/bar/line on pre-aggregated SQL views, never browser-side Prometheus.

**Files touched.**
- `docs/UI-PLAN.md:1` (new — the finalized UI↔backend contract).
- `docs/STACK.md:8,17,38,49,86,94,198` (UI stack + rationale).
- `docs/DESIGN.md:34,185`, `docs/IMPLEMENTATION.md:8,29,74`,
  `README.md:93`, `AGENTS.md:22` (reconciled wording).
- `docs/diagrams/uml_diagrams.md:91,799,802,809` and
  `docs/diagrams/symmetry-uml.drawio:43` (UI label).
- `ui/README.md:3,22` (target design + migration note).

**Code.** Documentation-only change; no `src` code touched yet. Cloudscape is
removed from `ui/package.json` and `App.tsx` in phase P1.

**References.** `docs/UI-PLAN.md`; previous entry (draw.io diagrams).

## 2026-10-19 — Add draw.io visual diagrams (boxes, solid/dotted lines) (uncommitted)

**What changed.** Added `docs/diagrams/symmetry-uml.drawio`, an 11-page draw.io
document built from native draw.io objects: actor shapes, use-case ellipses, UML
class boxes (name/attributes/methods compartments), deployment and component
boxes, an ER entity pair with a crow's-foot edge, and sequence diagrams with
lifelines and message arrows. Connector convention: **solid = implemented**,
**dashed/dotted = planned**. The markdown doc now points to it.

**Why (decision).** The owner asked for the class and other diagrams to be
"boxes and dotted and solid lines" using draw.io objects, rather than text-style
Mermaid. A `.drawio` file was chosen because it opens and stays editable in
draw.io / diagrams.net, and diff-friendly plain XML (`<mxfile>` with one
`<diagram>` per page) keeps it in the repo next to the code. Rejected
alternatives: exported PNG/SVG (not editable, goes stale) and a screenshot from
the web app (no source). The Mermaid copy is kept because it renders on GitHub;
the draw.io file is the editable visual source.

**Files touched.**
- `docs/diagrams/symmetry-uml.drawio:1` (new — 11 pages).
- `docs/diagrams/uml_diagrams.md:15` (added pointer to the `.drawio`).

**Code.** Documentation/diagram-only change; Before/After blocks skipped per the
rule above.

**References.** Entry below (UML/system-interaction doc); `docs/DESIGN.md`,
`docs/IMPLEMENTATION.md`, `docs/STACK.md`.

## 2026-10-19 — Add UML / system-interaction design doc (uncommitted)

**What changed.** Added `docs/diagrams/uml_diagrams.md`: a class-first UML and
system-interaction reference. It contains a use-case catalog, context/deployment
and component diagrams, the domain class model, an ER diagram of the schema, a
per-class interaction diagram for **every** class (platform, UI, AI, WASM SDK)
showing its software interfaces, database access, and use case, plus use-case
sequence diagrams (implemented and planned) and a traceability matrix.

**Why (decision).** The owner asked for "senior system engineer"-level diagrams
that walk one class at a time, cover interactions with software interfaces,
database access, and stakeholder use cases. A single markdown file with Mermaid
was chosen because it renders directly on GitHub and next to the code it
describes (no external diagram tool to install or keep in sync). Rejected
alternative: generating PNG/SVG images or a heavyweight UML tool — harder to
review in pull requests and to keep current. Implemented (✅) and planned (🔜)
elements are marked separately so the diagram is honest about M1–M1b status.

**Files touched.**
- `docs/diagrams/uml_diagrams.md:1` (new — full document).

**Code.** Documentation-only change; Before/After blocks skipped per the rule
above.

**References.** `docs/DESIGN.md`, `docs/IMPLEMENTATION.md`, `docs/STACK.md`,
`AGENTS.md`; entry below (Postgres/schema hardening).

## 2026-10-19 — Run Postgres in Docker; squash the schema into one hardened baseline (uncommitted)

**What changed.** Started the dev Postgres from `deploy/docker-compose.yml` (tuned
for a ~2 GB machine), squashed the old `V1` + `V2` migrations into a single
well-designed `V1__baseline.sql`, and made one small code change that the new
table rules require.

**Why (decision).** The owner asked to run a real Postgres locally and to apply
senior DBA table-design practices. The earlier migrations had never been applied
to any persistent database (no `symmetry` container or volume existed), so
squashing into one clean baseline was **safe and clearer** than layering a `V3`
on top of already-weak definitions — accepted trade-off: this rewrites
migration history, which we only did because nothing was ever deployed. Rejected
alternative: keep `V1`/`V2` untouched and add `V3` (preserves history but leaves
the weak definitions in the schema forever).

Table-design rules applied (see the file's header comment): UUID PKs defaulted
with `gen_random_uuid()`; `TIMESTAMPTZ` everywhere; enums as `TEXT` + named
`CHECK`; `NOT NULL` plus "not blank" checks; JSONB shape-checked
(object/array); `ON DELETE CASCADE` on the acceptance→contract foreign key;
`updated_at` kept correct by a trigger; **only** indexes that match real queries
(no redundant ones). The new unique `(contract_id, worker_id)` rule means the
accept path needs to turn a duplicate into a `409`, so `ContractService.accept`
was updated to flush inside a `try` and translate the violation.

**Files touched.**
- `platform/src/main/resources/db/migration/V1__baseline.sql` (new; replaces
  `V1__contract_acceptance.sql` + `V2__work_kind_lifecycle_verified_acceptance.sql`)
- `platform/src/main/java/io/symmetry/contract/ContractService.java:111`
- `platform/src/test/java/io/symmetry/contract/ContractServiceTest.java:175`
- `platform/src/test/java/io/symmetry/e2e/AcceptanceJourneyE2ETest.java`
- `deploy/docker-compose.yml:15`
- `AGENTS.md` (new "Local Postgres (Docker)" section)
- `docs/STACK.md:185`, `docs/IMPLEMENTATION.md:72` (reconciled migration names)

**Code — modified files.**

`platform/src/main/java/io/symmetry/contract/ContractService.java`

Before (in `accept`, lines 130–134):
```java
        Acceptance acceptance = new Acceptance(UUID.randomUUID(), contract.getId(), request.workerId(),
                hash, request.signature(), request.publicKey(), true, writeJson(request.agreements()));
        contract.markAccepted();
        contracts.save(contract);
        return acceptances.save(acceptance);
    }
```
After (lines 130–142):
```java
        Acceptance acceptance = new Acceptance(UUID.randomUUID(), contract.getId(), request.workerId(),
                hash, request.signature(), request.publicKey(), true, writeJson(request.agreements()));
        contract.markAccepted();
        contracts.save(contract);
        try {
            // Flush now so the unique (contract_id, worker_id) constraint is checked
            // inside this transaction and can be reported as 409, not a 500.
            return acceptances.saveAndFlush(acceptance);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Worker " + request.workerId() + " already accepted contract " + contractId);
        }
    }
```

`platform/src/test/java/io/symmetry/contract/ContractServiceTest.java`

Before (line 175):
```java
        when(acceptances.save(any(Acceptance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
```
After (line 175):
```java
        when(acceptances.saveAndFlush(any(Acceptance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
```

`platform/src/test/java/io/symmetry/e2e/AcceptanceJourneyE2ETest.java` — replaced
`TestRestTemplate` (Boot 4 moved it and it needs an extra `spring-boot-restclient`
class that is not on the classpath) with Spring `RestClient` bound to
`@LocalServerPort`, configured with a no-op status handler so 4xx/5xx are
asserted instead of thrown. No new dependency added.

`deploy/docker-compose.yml` — the `postgres` service (line 15) now sets
`POSTGRES_INITDB_ARGS="--data-checksums"` (line 24), a low-RAM `command` block
(line 27: `shared_buffers=128MB`, `effective_cache_size=512MB`, `work_mem=4MB`,
`maintenance_work_mem=64MB`, `max_connections=40`, slow-query logging),
`shm_size: 128mb` (line 42), and `mem_limit: 512m` (line 44).

**Code — new files.**

`platform/src/main/resources/db/migration/V1__baseline.sql` (new). Key lines:
- `18` `CREATE TABLE contract` — UUID PK `DEFAULT gen_random_uuid()`; `payload JSONB`; `created_at`/`updated_at TIMESTAMPTZ`.
- `43` `uq_contract_module_version UNIQUE (module_id, version)` — also serves list-by-module (leading column), so no separate module index.
- `45`–`62` named `chk_contract_*` checks (task kind, tier, work kind, lifecycle, not-blank fields, payload is an object).
- `69` `idx_contract_payload` — `GIN (payload jsonb_path_ops)` for payload lookups.
- `71` `CREATE TABLE acceptance` — FK `contract_id` with `ON DELETE CASCADE` (line 88).
- `93` `uq_acceptance_contract_worker UNIQUE (contract_id, worker_id)` — one acceptance per worker per contract; also serves `findByContractId`.
- `102` `idx_acceptance_worker_id` — "everything this worker accepted".
- `105`–`115` `set_contract_updated_at()` function + `trg_contract_set_updated_at` trigger.
- `117`–`127` `COMMENT ON` table/column notes.

**References.** Supersedes the `V1__*.sql, V2__*.sql` row in the 2026-10-18 entry.
Verified: `sg docker -c './mvnw -B verify'` → BUILD SUCCESS, **30 tests, 0 skipped**
(with Docker, integration + E2E run for real). The dev stack applied `V1` on
startup, `\d contract`/`\d acceptance` showed every constraint/index, and the
`updated_at` trigger fired (`updated_at > created_at` after an `UPDATE`).

---

## 2026-10-19 — Introduce this audit log + record the change rule (uncommitted)

**What changed.** Created `docs/past_final_commit_comments.md` (this file) and
added a "Documenting changes (audit log)" section to `AGENTS.md`. Also fixed the
stale "Design-only / no code" text in `AGENTS.md`'s Repo state.

**Why (decision).** Decisions were being made in code with no durable, reviewable
record of *why*. The fix is a lightweight, mandatory change log written in plain
language so any future reader can audit a decision. Rejected alternative: relying
on git commit messages alone — they capture *what* but rarely the *why*, the
trade-off, or the rejected alternative.

**Files touched.**
- `docs/past_final_commit_comments.md:1` (new)
- `AGENTS.md` — new "Documenting changes (audit log)" section; Repo state updated.

**Code — modified files.** None (documentation only).

**Code — new files.** This document; the procedure is the "How to add an entry"
section above (`docs/past_final_commit_comments.md:14`).

**References.** `AGENTS.md` → "Documenting changes (audit log)".

---

## 2026-10-19 — Remove `FUTURE-PROOFING.md`; strip "socket" references from code (uncommitted)

**What changed.** Deleted `docs/FUTURE-PROOFING.md` and removed every reference
to it, including the "(socket N in `docs/FUTURE-PROOFING.md`)" notes in six Java
source files.

**Why (decision).** The product owner decided the future work will be **decided
when it is needed**, so keeping a standing "future-proofing" document risks
drifting out of date and implying commitments we have not made. The *ideas*
(keep interfaces cell-agnostic; add languages via a toolchain registry) are still
true and still in the code and in `docs/STACK.md` / `docs/NEW-BUILDS.md` — only
the separate document and the cross-references were removed. Rejected
alternative: leaving the file and marking it "draft", which would still leave a
stale document to maintain.

**Files touched.**
- `docs/FUTURE-PROOFING.md` (deleted)
- `README.md:107`, `AGENTS.md:14`, `AGENTS.md:30`, `docs/STACK.md:199`,
  `docs/NEW-BUILDS.md:46`
- `platform/src/main/java/io/symmetry/task/WorkKind.java:4`
- `platform/src/main/java/io/symmetry/task/LifecycleState.java:4`
- `platform/src/main/java/io/symmetry/scoring/MetricVector.java:9`
- `platform/src/main/java/io/symmetry/eval/Keycard.java:5`
- `platform/src/main/java/io/symmetry/eval/Checker.java:4`
- `platform/src/main/java/io/symmetry/build/Toolchain.java:4`

**Code — modified files (the intent of each class is unchanged; only the stray
file reference was dropped).**

`platform/src/main/java/io/symmetry/task/WorkKind.java`

Before (line 4):
```java
 * The kind of work a contract describes (socket 1 in {@code docs/FUTURE-PROOFING.md}).
```
After (line 4):
```java
 * The kind of work a contract describes.
```

`platform/src/main/java/io/symmetry/task/LifecycleState.java`

Before (line 4):
```java
 * The states a contract moves through (socket 7 in {@code docs/FUTURE-PROOFING.md}).
```
After (line 4):
```java
 * The states a contract moves through.
```

`platform/src/main/java/io/symmetry/scoring/MetricVector.java`

Before (lines 9–10):
```java
 * A flexible bundle of named measurements (socket 4 in
 * {@code docs/FUTURE-PROOFING.md}).
```
After (line 9):
```java
 * A flexible bundle of named measurements.
```

`platform/src/main/java/io/symmetry/eval/Keycard.java`

Before (lines 5–6):
```java
 * The capabilities a module is allowed to use while running (socket 5 in
 * {@code docs/FUTURE-PROOFING.md}).
```
After (line 5):
```java
 * The capabilities a module is allowed to use while running.
```

`platform/src/main/java/io/symmetry/eval/Checker.java`

Before (line 4):
```java
 * A single checkpoint in the gate (socket 3 in {@code docs/FUTURE-PROOFING.md}).
```
After (line 4):
```java
 * A single checkpoint in the gate.
```

`platform/src/main/java/io/symmetry/build/Toolchain.java`

Before (line 4):
```java
 * A compiler connector (socket 6 in {@code docs/FUTURE-PROOFING.md}).
```
After (line 4):
```java
 * A compiler connector.
```

**Code — new files.** None.

**References.** Decision recorded in `AGENTS.md` ("future kinds of work are
decided when needed"). Reconciled per the AGENTS.md rule "when you change one,
reconcile the others".

---

## 2026-10-18 — M1 walking skeleton: initial backend + UI + tests (commit `0b62a45`)

**What changed.** First working slice: the `platform/` Spring Boot API (publish a
contract, browse it, read a tier-filtered workbench, sign and accept a task
document), a Cloudscape `ui/` contracts table, an `ai/` FastAPI stub, a
`wasm-sdk/` ABI crate + example module, plus a Postgres/Testcontainers test suite.

**Why (decision).** This is the smallest end-to-end proof of the core promise —
"verify without leaking" — so the money-and-trust path (signed acceptance) is
real before we build more. Key choices and reasons:

- **Postgres + Testcontainers, never H2:** the schema needs `jsonb`, exact money,
  and timezone-correct timestamps that H2 cannot faithfully model (see
  `AGENTS.md`).
- **Signed acceptance of the task document:** a submission is ineligible without
  it, so the hash and the worker's Ed25519 signature are bound together.
- **Security off by default:** local development should not require an OAuth2
  server; turn it on with `symmetry.security.enabled=true`.
- **Interfaces stay cell-agnostic:** V1 only does compute, but the work kind,
  lifecycle, toolchain registry, keycard, checker list, and flexible metrics
  are already types, so a new kind of work plugs in instead of rewriting the
  centre.

**Files touched (all new in this commit).** The decision-bearing code:

| File | Key lines | Comment (what it does) |
|---|---|---|
| `config/SecurityConfig.java` | `21`, `23` | Toggleable security: permit-all by default, JWT when enabled. |
| `contract/ContractController.java` | `32`, `38`, `49`, `54`, `59` | The five REST endpoints (`POST/GET /contracts`, get, workbench, accept). |
| `contract/ContractService.java` | `28`, `29`, `112` | Task-kind + tier allow-lists; `accept(...)` is the signed, gated flow. |
| `contract/ContractService.java` | `123`–`124` | Binds the task-document hash into the canonical accept message. |
| `contract/MessageSigner.java` | `29`, `40`, `52` | `sha256`, `canonicalAcceptMessage`, and Ed25519 `verify`. |
| `contract/ContractDocumentValidator.java` | `1`–`69` | Rejects documents missing sections / with bad numbers. |
| `contract/DisclosureService.java` | `1`–`49` | Per-tier view: secrets are stripped before leaving the server. |
| `contract/Acceptance.java` | `20`, `53` | Stored acceptance (hash, public key, signature, verified flag). |
| `task/WorkKind.java` | `10`–`15` | Work kinds; V1 uses `COMPUTE` only. |
| `task/LifecycleState.java` | `10`–`16` | Full state sequence; V1 walks `DRAFT → ACCEPTED`. |
| `eval/Keycard.java` | `18` | `EMPTY` keycard for V1 (no network/filesystem/clock/threads). |
| `eval/Checker.java` | `10` | The gate is a list of checkers, not a fixed sequence. |
| `build/Toolchain.java`, `build/ToolchainRegistry.java` | `11`, `1`–`47` | Languages are registered, not hardcoded. |
| `scoring/MetricVector.java` | `16` | Metrics are a named map, so new domains need no schema change. |
| `resources/db/migration/V1__*.sql`, `V2__*.sql` | `1`–`27`, `1`–`16` | Schema for contracts and acceptances. |

**Code — modified files.** None (everything here was new).

**Code — new files with line numbers and comments.** See the table above; the
`socket` doc-comments these files pointed to were removed in the 2026-10-19 entry.

**References.** `docs/STACK.md` §7 (build status), `docs/IMPLEMENTATION.md`
(M1/M1b). Test suite: `platform/src/test/java/io/symmetry/` (unit, integration,
and `e2e/AcceptanceJourneyE2ETest.java`).
