# Project Summary Prompt

Use this prompt with any LLM/assistant (paste it into a fresh session, or feed it the repo). It is designed to produce a complete, consistent summary of this project.

---

```
You are given access to a codebase called "financial-dashboard" (the Stocks Explorer app). Read it and produce a comprehensive project summary for a technical audience.

## What to analyze

- README.md, docs/ARCHITECTURE.md and docs/SOLUTION_REFERENCE.html for intent and design
- backend/ — the Spring Boot application: controllers, services, agents, clients, DTOs, config, tests
- frontend/ — the Vite + React + TypeScript app: components, state flow, API calls, SSE handling, tests
- deploy/ — aws-setup.sh/.md, ec2-userdata.sh, dist-config.json, github-oidc-trust.json, github-deploy-policy.json, docker-compose.yml
- .github/workflows/deploy.yml — the CI/CD pipeline

## What to produce

Structure the summary into these sections:

1. **Purpose** — one paragraph: what the app does and who it's for.
2. **Feature list** — every user-facing capability, described at feature level (not file level). Include regional watchlists, the Virtual Agent, market-open/closed awareness, and mobile responsiveness.
3. **Architecture** — frontend, backend layers, external APIs, the Markets Agent (per-region workers, shared cache, adaptive cadence), the agent mesh (gateway filter → orchestrator → task queue → workers → evaluator → audit store), and how a request flows end-to-end (chart load, autocomplete, agent chat, market stream).
4. **API surface** — every endpoint with method, params, response shape, including `/api/markets/stream` (SSE) and `/api/agent/tasks` (mesh).
5. **External integrations** — Yahoo Finance endpoints used (v8 chart, v7 quote + cookie/crumb, v1 search, FX pairs for currency normalization), the User-Agent requirement, and the LLM provider.
6. **AI components** — two distinct pieces: (a) the LangChain4j Virtual Agent with `@Tool`s over `StockService`, per-session memory, charts-on-demand; (b) the deterministic agent mesh (task lifecycle PENDING→RUNNING→PASS/FAIL, single requeue, audit trail).
7. **Data model** — the DTOs (Candle, QuoteSummary, MarketQuote, RegionSnapshot, MarketUpdate, AgentTask, TaskResult, TaskAuditEntry) and their fields.
8. **Deployment** — CloudFront + private S3 (OAC) for the SPA, `/api/*` to the Dockerized backend on EC2 (ECR images, SSM SecureString secrets, security group locked to the CloudFront prefix list), GitHub OIDC CI/CD, and what `deploy/aws-setup.sh` automates.
9. **Testing strategy** — test layers, what each covers, coverage tooling (JaCoCo, Vitest).
10. **Security posture** — CORS policy, input validation, error-message hardening, secret handling (SSM/env, never in repo), gateway auth on `/api/agent/**`.
11. **Known limitations / trade-offs** — be honest: curated candidate pools, unofficial Yahoo endpoints, no holiday calendars, in-memory state (single replica), single-EC2 SPOF, non-streaming chat, large JS bundle.
12. **Development history** — summarize the iteration log from the README.

## Rules

- Be concrete: cite real class/component names and endpoints.
- Do not speculate about intent beyond what's in the code.
- Call out anything that looks like a bug, tech debt, or risk.
- Keep it under 900 words; a new engineer should be able to onboard from it.
```
