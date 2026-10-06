# Project Summary Prompt

Use this prompt with any LLM/assistant (paste it into a fresh session, or feed it the repo). It is designed to produce a complete, consistent summary of this project.

---

```
You are given access to a codebase called "financial-dashboard". Read it and produce a comprehensive project summary for a technical audience.

## What to analyze

- README.md and docs/ARCHITECTURE.md for intent and design
- backend/ — the Spring Boot application: controllers, services, clients, DTOs, config, tests
- frontend/ — the Vite + React + TypeScript app: components, state flow, API calls, tests

## What to produce

Structure the summary into these sections:

1. **Purpose** — one paragraph: what the app does and who it's for.
2. **Feature list** — every user-facing capability, described at feature level (not file level).
3. **Architecture** — the split: frontend, backend layers, external APIs, the AI agent; how a request flows end-to-end (chart load, autocomplete, top-5, agent chat).
4. **API surface** — every endpoint with method, params, response shape.
5. **External integrations** — Yahoo Finance endpoints used (v8 chart, v7 quote + cookie/crumb, v1 search), the User-Agent requirement, and the LLM provider.
6. **AI agent** — how LangChain4j tool calling works here: which tools exist, what the LLM sees vs. what the UI receives, graceful degradation without an API key.
7. **Data model** — the DTOs and their fields.
8. **Testing strategy** — test layers, what each covers, coverage tooling.
9. **Security posture** — CORS policy, input validation, error-message hardening, secret handling.
10. **Known limitations / trade-offs** — be honest: hardcoded ticker candidates, unofficial Yahoo endpoints (fragile, may break), rate limits, single-user assumptions, no persistence.
11. **Development history** — summarize the iteration log from the README.

## Rules

- Be concrete: cite real class/component names and endpoints.
- Do not speculate about intent beyond what's in the code.
- Call out anything that looks like a bug, tech debt, or risk.
- Keep it under 800 words; a new engineer should be able to onboard from it.
```
