# Auction lifecycle integration contract v1

## Ownership and transport

`investment-service` owns this contract and publishes every event to
`investment.auction-lifecycle.v1`. The Kafka record key is the `aggregateId` (`auctionId`), not
the `eventId`. Consumers therefore receive the events for one auction in publication order while
the partition count remains unchanged.

The topic uses `cleanup.policy=delete` and `retention.ms=-1` at launch so a consumer can rebuild a
projection from the beginning. A partition-count change requires coordination with consumers. The
dead-letter topic is `investment.auction-lifecycle.v1.dlq`.

The machine-readable contract is
[`auction-lifecycle-event.schema.json`](auction-lifecycle-event.schema.json). The `examples/`
directory contains one valid message for every event type.

## Envelope

Every message has the same required fields:

| Field | Format | Meaning |
| --- | --- | --- |
| `eventId` | UUID string | Stable identity of the fact; it remains unchanged on every retry. |
| `eventType` | String enum | One of the seven event names documented below. |
| `schemaVersion` | Integer constant `1` | Version of this wire contract. |
| `aggregateId` | Non-empty string | Auction identifier and Kafka record key. |
| `sequence` | Positive integer | Monotonic publication sequence; it is compared only with events for the same auction. |
| `occurredAt` | ISO-8601 UTC instant | Time when the business transition occurred. |
| `data` | Object | Payload selected by `eventType`. |

Amounts are JSON numbers expressed in `currency`. Rates are decimal fractions: `0.15` means 15%,
not 0.15%. Calendar dates use `YYYY-MM-DD`; instants use ISO-8601 UTC. Lifecycle states, currencies
and risk grades are strings in the wire contract and are not Java domain enums.

## Event payloads

| `eventType` | Required `data` fields | Meaning |
| --- | --- | --- |
| `AuctionCreated` | `invoiceId`, `mypeId`, `payerRuc`, `payerName`, `dueDate`, `invoiceAmount`, `currency`, `status`, `greenCertified` | Creates a preliminary, non-public projection row. `status` is `PENDING_VERIFICATION_RISK`. |
| `AuctionPublished` | Invoice/MYPE/payer fields, `dueDate`, `invoiceAmount`, `fundableAmount`, `targetAmount`, `currentFunding`, `currency`, `investorTea`, `investorTermRate`, `quotedTermDays`, `riskGrade`, `status`, `greenCertified`, `publishedAt`, `expiresAt` | Is self-contained and makes the auction visible. `status` is `PUBLISHED`. |
| `PartitionAdded` | `partitionId`, `addedAmount`, `currentFunding` | Advances the projected funding using the aggregate-calculated total. |
| `AuctionFullyFunded` | Empty object | Changes the lifecycle state to `FULLY_FUNDED`; the target was established by `AuctionPublished`. |
| `AuctionExpired` | `releasedInvestmentTransactionIds` | Changes the state to `EXPIRED` and reports reservations released by the aggregate. |
| `AuctionCancelled` | `reason`, `releasedInvestmentTransactionIds` | Changes the state to `CANCELLED` and explains the administrative cancellation. |
| `AuctionClosed` | `closingTransactionId` | Changes the state to `CLOSED` and correlates the settlement transaction. |

`occurredAt` is also the authoritative published, expired, cancelled, fully-funded or closed time
when an event represents that transition. Consumers must defer an incremental event when its
`AuctionPublished` prerequisite has not yet been projected, then retry it in `sequence` order.

## Delivery and compatibility

Delivery is **at least once**. A consumer must persist processed `eventId` values atomically with
its projection update and ignore redelivery. It must also refuse to regress an auction when an
event has a lower or equal already-applied `sequence`. `eventId` handles duplicate delivery;
`sequence` handles ordering. They are not interchangeable.

Version 1 is backward compatible only under these rules:

- Producers may add an optional field without changing existing meaning.
- Producers may not remove or rename a required field, change its type/unit, reuse an event name,
  or change the meaning of an existing value.
- A new required field, incompatible semantic change, or removal requires schema version 2 and a
  new topic such as `investment.auction-lifecycle.v2`.
- An incompatible rollout uses a time-boxed dual-publish window. Consumers migrate and validate
  v2 before v1 publication is retired.
- Unknown `eventType` or `schemaVersion` values are invalid for this schema and must follow the
  consumer retry/DLT policy; they must not be silently projected.

Before freezing a new version, producer and Marketplace owners review the schema, all examples,
Kafka key, amount/rate units, replay procedure and the compatibility classification of the change.
