# Simulated risk evaluation (temporary)

> **This is a stand-in, not a feature.** Remove it once a real risk service evaluates auctions.

## Why it exists

An auction is born in `PENDING_VERIFICATION_RISK` and only moves to `DRAFT` when someone calls the
evaluation with a risk grade. Nothing does that yet: the risk service is not part of this sprint.
Without an evaluation an auction cannot be quoted, the MYPE cannot accept a quote, and nothing ever
reaches the marketplace — so the invoice → auction → marketplace flow cannot be shown end to end.

`SimulatedRiskEvaluationHandler` fills that gap. When enabled, it listens to `AuctionCreatedEvent`
and, right after the creating transaction commits, runs `EvaluateAuctionCommand` in a transaction of
its own — the same command `PUT /api/v1/internal/auctions/{auctionId}/evaluation` runs.

## What it decides

| Field | Value | Why |
| --- | --- | --- |
| `riskGrade` | A, B or C, derived from the auction id | The marketplace shows varied TEAs, and a redelivered event yields the same grade (`Auction.evaluate` rejects a repeated assessment id with a different grade) |
| `assessmentId` | `simulated-<auctionId>` | Makes simulated evaluations recognisable in the data |
| `fullBalanceOutstanding` | `true` | The pilot only accepts invoices whose full balance is outstanding |
| `assessedAt` | Current time | — |

**The grade says nothing about the invoice.** Every log line it writes starts with `SIMULATED` so it
is not mistaken for a real assessment. If the evaluation fails, the auction stays pending, exactly as
it would without the simulation, and can still be evaluated by hand.

## Configuration

| Profile | Default | Override |
| --- | --- | --- |
| base (`application.yaml`) | off | `INVESTMENT_RISK_SIMULATION_ENABLED` |
| `dev` | on | `INVESTMENT_RISK_SIMULATION_ENABLED` |
| `docker` | on | `INVESTMENT_RISK_SIMULATION_ENABLED` |
| `test` | off | the test that covers it enables it |

## Evaluating by hand instead

With the simulation off, evaluate an auction directly against the service (the gateway does not
route `/internal/**`):

```bash
curl -X PUT http://localhost:8082/api/v1/internal/auctions/<auctionId>/evaluation \
  -H 'Content-Type: application/json' \
  -d '{"assessmentId":"manual-1","riskGrade":"B","fullBalanceOutstanding":true,"assessedAt":"2026-09-16T12:00:00Z"}'
```

## Removing it

When the risk service calls the evaluation:

1. Delete `application/internal/eventhandlers/SimulatedRiskEvaluationHandler.java` and its test.
2. Delete the `vankoo.investment.risk-simulation` block from `application.yaml`, `application-dev.yaml`
   and `application-docker.yaml`.
3. Delete this document and its link in `ARCHITECTURE.md`.
4. Auctions already evaluated this way keep their `simulated-` assessment id; decide whether to
   re-evaluate them before going live.
