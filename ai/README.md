# Symmetry AI Service

A small **FastAPI** service offering two assistants behind one OpenAI-compatible
gateway (LiteLLM):

- `role="org"` — helps a company write the task document and acceptance criteria.
- `role="worker"` — helps a worker write a submission against the **public** task
  document only.

It never gives a worker the hidden tests/holdout; that is enforced in code.

## Run

```sh
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8090
```

## Configure

| Env var | Default | Meaning |
|---|---|---|
| `SYMMETRY_AI_BASE_URL` | `http://localhost:4000/v1` | Gateway (LiteLLM) or any OpenAI-compatible endpoint |
| `SYMMETRY_AI_API_KEY` | `not-needed` | API key for the gateway |
| `SYMMETRY_AI_MODEL` | `deepseek-chat` | Model name at the gateway |

## Endpoints

- `GET /health` — liveness.
- `POST /assist` — `{ role, prompt, context? }` → `{ role, model, content }`.

## Hosting note

This host has **no GPU**, so start with a hosted DeepSeek endpoint (open-weights,
MIT). Self-host later with **vLLM** once GPUs are available; no client code
changes because both speak the OpenAI API.
