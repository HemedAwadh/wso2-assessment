# Jamii Savings – WSO2 Integration Assignment

[![ci](https://github.com/paulodhiambo/ws02-assessment/actions/workflows/ci.yml/badge.svg)](https://github.com/paulodhiambo/ws02-assessment/actions/workflows/ci.yml)

Three integrations on **WSO2 Micro Integrator 4.6**, managed in **API Manager 4.6** and built, tested and deployed by one **Jenkins** pipeline. Both bonus tasks are included: Bonus A (`mi/customer-proxy`) and Bonus B (`mi/loan-events`). The MI integrations are in `mi/`, the API Manager projects in `apim/`, and the pipeline in `Jenkinsfile`, mirrored in `.github/workflows/ci.yml`.

More detail: [full write-up](docs/write-up.md) · [API design and error catalogue](docs/api-design.md) · [architecture](docs/architecture.md) · [deployment](docs/deployment.md) · [slides](docs/jamii-wso2-integration.pptx)

## Run it locally

Needs Docker (about 3 GB of RAM for API Manager), JDK 17+ and Maven. Go 1.24+ is optional: without it, the Go builds and tests run in Docker.

```bash
scripts/build.sh                                      # CARs + API Manager packages, with tests
docker compose --profile apim up -d --build --wait    # MySQL, mocks, RabbitMQ, MI, API Manager
scripts/deploy-mi.sh dev && scripts/install-apictl.sh && scripts/deploy-apim.sh dev
eval "$(scripts/apim-demo-consumer.sh | grep '^export ')"   # Dev Portal app, subscriptions, keys
curl -sk "$GW/accounts/v1/0100000001/balance" -H "Authorization: Bearer $TOKEN"
scripts/test.sh --all                                 # unit, API, outage and event tests
```

## Architecture decisions and trade-offs

- **MI does the integration; API Manager governs access.** MI handles validation, protocol mediation, timeouts and error mapping, so the APIs behave the same with or without the gateway (CI tests both). API Manager handles OAuth2 and API keys, throttling, policies and the Developer Portal.
- **One error format** (`error.code`, `message`, `status`, plus `requestId`, `correlationId`, `timestamp`) is built by a single shared sequence for database, REST and SOAP failures. Malformed input gets 400 or 415, never 500. Logs show masked IDs (`******0001`) and never payloads.
- **1a:** an MI data service with a bound parameter. An unknown account gives 404; a database failure gives a distinct 503 `ACCOUNT_DB_UNAVAILABLE`.
- **1b: JSONPlaceholder**, chosen because it is public, returns a realistic profile with PII and real 404s, and sends plenty of CDN headers to strip. The proxy adds a correlation ID, strips credential and internal headers in both directions, enforces a 5 s timeout with a circuit breaker, and maps failures to 502/503/504.
- **1c: DNE Online SOAP calculator.** No public loan SOAP service exists, so the SOAP `Divide` computes the installment and MI applies a 40% debt-to-income rule. SOAP faults (a real .NET stack trace) are logged and mapped to 422/502, never returned.
- **Security and tiers:** OAuth2 for accounts and customers (financial data and PII); an API key for loan eligibility (no PII). **Loan eligibility gets the lower Bronze / 10K-per-minute tier because every call costs an external SOAP round trip.**
- **API Product** `JamiiCoreBankingProduct` bundles all three APIs, so a channel app needs one subscription. Consumers discover the APIs in the Developer Portal, create an application, subscribe (to the product or individual APIs), generate OAuth2 keys or an API key, and call the gateway.
- **All-or-nothing deploys:** MI CARs and API Manager APIs are backed up, deployed and verified, then restored if any step fails, across both tiers. This was tested with deliberately broken artifacts.
- **Bonus A:** the dashboard calls both backends in parallel. If one fails it returns **partial data** (`partial: true`, with the failed section flagged) rather than an error, because a read-only view is still useful.
- **Bonus B:** a RabbitMQ consumer calls the 1c API. Permanent failures (bad JSON, schema errors, 4xx) go straight to a DLQ with their reason. Transient failures are retried 3 times by the broker, then parked. Unlike the request/response APIs, nobody waits on the answer: MI owns the retries and absorbs outages instead of failing fast.
- **Trade-offs:** a small Go tool writes standard CARs instead of using the WSO2 Maven plugins (simpler layout, but custom tooling). Contract-compatible local mocks make CI deterministic while the public services serve demos.

## What I would do differently with more time

Synapse unit tests and OpenAPI contract tests; secrets in a vault with mutual TLS from gateway to MI; central logs and Prometheus alerts on 5xx errors and DLQ depth; a real production target (Kubernetes with Helm); interest in the eligibility rule; and an AsyncAPI definition for the events.

## Assumptions

Jamii Savings and all its data are fictional. Amounts are KES unless the account says otherwise, and account numbers are exactly ten digits. Eligibility is indicative (principal only, whole-shilling installments). MI is reachable only from the gateway network, so it does no client authentication itself. Only dev is wired up; prod is an approval-gated dry run that promotes the same artifacts.
