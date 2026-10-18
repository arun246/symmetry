"""Symmetry AI service.

Two assistants, one gateway:

* role="org"    -> helps a company WRITE THE TASK (requirements, acceptance
                   criteria, property tests). May see private context.
* role="worker" -> helps a worker WRITE A SUBMISSION against the PUBLIC task
                   document and sample data. MUST NOT see hidden tests/holdout.

The model is reached through an OpenAI-compatible gateway (LiteLLM) so it can be
swapped between a hosted provider and a self-hosted vLLM without code changes.
"""

from __future__ import annotations

import logging
import os
from typing import Literal, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

GATEWAY_BASE_URL = os.getenv("SYMMETRY_AI_BASE_URL", "http://localhost:4000/v1")
GATEWAY_API_KEY = os.getenv("SYMMETRY_AI_API_KEY", "not-needed")
MODEL = os.getenv("SYMMETRY_AI_MODEL", "deepseek-chat")
REQUEST_TIMEOUT_SECONDS = float(os.getenv("SYMMETRY_AI_TIMEOUT_SECONDS", "60"))

logging.basicConfig(level=logging.INFO)
audit = logging.getLogger("symmetry.ai.audit")

app = FastAPI(title="Symmetry AI Service", version="0.0.1")

SYSTEM_PROMPTS = {
    "org": (
        "You help a company turn a software idea into a clear, checkable task "
        "document: numbered must-have requirements, non-functional targets, and "
        "acceptance criteria. Return a draft the company must approve. Never "
        "invent hidden test data."
    ),
    "worker": (
        "You help a worker implement a module against the PUBLIC task document "
        "and sample data only. You have no access to hidden tests or the "
        "organization's private code. Suggest an approach and code, but the "
        "worker owns correctness."
    ),
}


class AssistRequest(BaseModel):
    role: Literal["org", "worker"]
    prompt: str = Field(min_length=1)
    context: Optional[str] = None


class AssistResponse(BaseModel):
    role: str
    model: str
    content: str


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/assist", response_model=AssistResponse)
def assist(request: AssistRequest) -> AssistResponse:
    # Guardrail: worker help is public-context only. Hidden tests/holdout must
    # never be placed in `context` for role="worker".
    if request.role == "worker" and request.context and "holdout" in request.context.lower():
        raise HTTPException(
            status_code=400,
            detail="Hidden test data must never be sent to the worker assistant.",
        )

    try:
        from openai import OpenAI
    except ImportError as exc:  # pragma: no cover - dependency guard
        raise HTTPException(status_code=503, detail="openai package is not installed") from exc

    # Audit trail (required by docs/STACK.md section 6): record that AI was used,
    # without logging the prompt/response bodies.
    audit.info(
        "ai.assist role=%s model=%s prompt_chars=%d has_context=%s",
        request.role,
        MODEL,
        len(request.prompt),
        request.context is not None,
    )

    client = OpenAI(base_url=GATEWAY_BASE_URL, api_key=GATEWAY_API_KEY, timeout=REQUEST_TIMEOUT_SECONDS)
    messages = [
        {"role": "system", "content": SYSTEM_PROMPTS[request.role]},
    ]
    if request.context:
        messages.append({"role": "system", "content": f"Context:\n{request.context}"})
    messages.append({"role": "user", "content": request.prompt})

    try:
        completion = client.chat.completions.create(model=MODEL, messages=messages)
    except Exception as exc:  # noqa: BLE001 - surface gateway failures as 502
        raise HTTPException(status_code=502, detail="model gateway request failed") from exc

    content = completion.choices[0].message.content or ""
    return AssistResponse(role=request.role, model=MODEL, content=content)
