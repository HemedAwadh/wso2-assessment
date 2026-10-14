# Jamii Savings – WSO2 Integration Assignment

[![ci](https://github.com/paulodhiambo/ws02-assessment/actions/workflows/ci.yml/badge.svg)](https://github.com/paulodhiambo/ws02-assessment/actions/workflows/ci.yml)

Three integrations on **WSO2 Micro Integrator 4.6**, managed in **API Manager 4.6**, and built, tested and deployed by one **Jenkins** pipeline (mirrored in GitHub Actions). Both bonus tasks are included. Code: `mi/` (integrations), `apim/` (API Manager projects), `Jenkinsfile` and `scripts/` (pipeline).

More detail: [full write-up](docs/write-up.md) · [API design and error catalogue](docs/api-design.md) · [architecture](docs/architecture.md) · [deployment](docs/deployment.md) · [slides](docs/jamii-wso2-integration.pptx)

## Architecture decisions and trade-offs

- **MI integrates; API Manager governs.** MI owns validation, protocol mediation, timeouts and error mapping, so the APIs behave the same with or without the gateway (CI tests both). API Manager owns OAuth2 and API keys, throttling, policies and the Developer Portal.
- **One error contract.** A single shared sequence builds every error (`error.code`, `message`, `status`, `requestId`, `correlationId`, `timestamp`) for database, REST and SOAP failures. Malformed input gets 400 or 415, never 500. Logs show masked IDs (`******0001`), never payloads.
- **1a Account balance:** an MI data service with a bound parameter. An unknown account returns 404; a database outage returns a distinct 503 `ACCOUNT_DB_UNAVAILABLE`.
- **1b Customer proxy** over JSONPlaceholder (public, realistic data with PII, real 404s, plenty of CDN headers to strip). Adds a correlation ID, strips credential and internal headers in both directions, applies a 5 s timeout with a circuit breaker, and maps failures to 502, 503 or 504.
- **1c Loan eligibility** over the DNE Online SOAP calculator: `Divide` computes the installment and MI applies a 40% debt-to-income rule. SOAP faults (including a real .NET stack trace) are logged and mapped to 422 or 502, never returned.
- **Security and tiers:** OAuth2 for accounts and customers (financial data, PII). An API key for loan eligibility (no PII), on the lower **Bronze tier (10K/min)**, because every call costs an external SOAP round trip. The **API Product** `JamiiCoreBankingProduct` bundles all three APIs behind one subscription.
- **All-or-nothing deploys:** MI CARs and API Manager APIs are backed up, deployed and verified, and both tiers are restored if any step fails (tested with deliberately broken artifacts).
- **Bonus A dashboard:** calls both backends in parallel. If one fails, it returns `partial: true` with the failed section flagged rather than an error, because a read-only view is still useful.
- **Bonus B events:** a RabbitMQ consumer calls the 1c API. Permanent failures (bad JSON, schema errors, 4xx) go straight to a dead-letter queue with the reason. Transient failures are retried 3 times by the broker, then parked. No caller is waiting, so MI absorbs outages instead of failing fast.

**Trade-offs**

| Choice | Gained | Cost |
|---|---|---|
| Small Go tool writes the CARs instead of the WSO2 Maven plugins | Simple, readable layout and build-time artifact checks | Custom tooling to maintain |
| Public backends for demos, contract-compatible mocks in CI | Realistic demos and deterministic failure tests | Mocks can drift from the real services |
| Prod modelled as an approval-gated dry run | The full promotion flow, using the same artifacts | No live production target |

## What I would do differently with more time

- Synapse unit tests and OpenAPI contract tests, in addition to the static artifact tests.
- Secrets in a vault, and mutual TLS from the gateway to MI.
- Central logs, Prometheus metrics, and alerts on 5xx rates and dead-letter queue depth.
- Carry the publisher's correlation ID through the event flow (MI currently generates a new one), and add an AsyncAPI definition for the events.
- A real production target (Kubernetes with Helm), and interest charges in the eligibility calculation.

## Assumptions

- Jamii Savings and all its data are fictional. Amounts are in KES unless the account says otherwise, and account numbers are exactly ten digits.
- Eligibility is indicative: principal only, whole-shilling installments, and a 40% debt-to-income threshold that I chose.
- MI is reachable only from the gateway network, so it does no client authentication itself.
- Only dev is wired up. Prod is an approval-gated dry run that promotes the same artifacts.

## How to run everything locally

**Prerequisites:** Docker with Compose (give it about 6 GB of RAM; API Manager alone needs about 3 GB), JDK 17+ and Maven 3.9+. Go 1.24+ is optional: without it, the Go builds and tests run in Docker.

```bash
cp .env.example .env                                     # optional; the defaults work
scripts/build.sh                                         # CARs + API Manager packages, with tests
docker compose --profile apim up -d --build --wait       # MySQL, mocks, RabbitMQ, MI, API Manager (~3 min)
scripts/deploy-mi.sh dev                                 # deploy the CARs, all-or-nothing
scripts/install-apictl.sh && scripts/deploy-apim.sh dev  # import the APIs and the product
eval "$(scripts/apim-demo-consumer.sh | grep '^export ')" # Dev Portal app + keys: sets GW, TOKEN, APIKEY (1 h)
```

Try it:

```bash
curl -sk "$GW/accounts/v1/0100000001/balance" -H "Authorization: Bearer $TOKEN"
curl -sk "$GW/customers/v1/1/dashboard"       -H "Authorization: Bearer $TOKEN"
curl -sk "$GW/loans/v1/eligibility" -H "apikey: $APIKEY" -H 'Content-Type: application/json' \
  -d '{"customerId":"1","monthlyIncome":120000,"requestedAmount":300000,"tenureMonths":12}'
scripts/test.sh --all                                    # unit, API, outage and event tests
```

MI is also directly reachable at `http://localhost:8290` (for example `/accounts/0100000001/balance`). The Developer Portal is at `https://localhost:9443/devportal` (admin / admin) and RabbitMQ at `http://localhost:15672` (jamii / jamii-dev-only). Stop everything with `docker compose --profile apim down` (add `-v` to reset the data).
