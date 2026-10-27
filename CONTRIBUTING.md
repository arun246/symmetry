# Contributing

## Licence of contributions

Symmetry uses **one licence: AGPL-3.0**, for every path. By contributing, you
agree that your contribution is licensed under AGPL-3.0.

See [LICENSING.md](LICENSING.md) and
[ADR-0008](docs/adr/0008-single-agpl-licence.md) for the decision.

## Developer Certificate of Origin

We use the **DCO**, not a CLA, for normal contributions. Add a sign-off line to
each commit:

```text
Signed-off-by: Your Name <you@example.com>
```

Use `git commit -s` to add it. The sign-off means you wrote the change or have
the right to submit it under the path licence.

A commercial exception or a corporate CLA is a separate written agreement. Do not
assume one exists.

## Before you send a change

- Keep the change focused.
- Add or update tests where the change affects behaviour.
- Update the design docs when a decision changes.
- Add an entry to `docs/past_final_commit_comments.md`.
- Do not commit secrets, signing keys, holdout data or publisher code.
- Run the verify commands in `AGENTS.md` that apply to the path you changed.

## Security issues

Do not open a public issue for a vulnerability. Report it privately to the owner
so a fix and disclosure plan can be prepared.
