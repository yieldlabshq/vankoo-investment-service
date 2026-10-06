# Marketplace read model

## Ownership and boundary

`investment-service` owns the Marketplace read model, its Oracle tables, its Kafka consumer and
`GET /api/v1/auctions/marketplace`. The write-side `Auction` aggregate remains the source of
business truth. Marketplace is an eventually consistent projection and never mutates `Auction`.

The projection consumes versioned integration contracts. It never consumes Java domain-event
classes, and the REST query never falls back to `AuctionRepository` when projection data is stale
or unavailable.

## Visible data

Each projected auction exposes:

- Auction, invoice and MYPE identifiers.
- Payer RUC and display name.
- Target, current and available funding, progress percentage and currency.
- Investor TEA, term rate and quoted term in days.
- Risk grade, lifecycle status and green certification.
- Due date, publication time and funding expiration time.

Rows may be created before publication, but `publishedAt IS NOT NULL` is required for every public
Marketplace result. Without an explicit status filter, only `PUBLISHED` and `FUNDING` are visible.
Explicit filters may retrieve `FULLY_FUNDED`, `EXPIRED`, `CANCELLED` and `CLOSED` rows. Auctions
cancelled before publication are never public.

## Query contract

`GET /api/v1/auctions/marketplace` supports combinable filters for lifecycle status, currency and
green certification. It uses zero-based pagination, defaults to 20 rows and rejects sizes greater
than 100. The stable default order is `expiresAt ASC, auctionId ASC`. Supported primary sort fields
are `expiresAt`, `dueDate`, `publishedAt` and `targetAmount`; `auctionId ASC` is always the final
tie-breaker.

The response contains `content`, `page`, `size`, `totalElements`, `totalPages` and `sort`. Query
results may be cached for at most 30 seconds. Projection updates invalidate all Marketplace query
entries after their database transaction commits.

Example:

```http
GET /api/v1/auctions/marketplace?status=PUBLISHED&status=FUNDING&currency=PEN&greenCertified=true&page=0&size=20&sort=expiresAt,asc
```

`page` must be zero or greater, `size` must be between 1 and 100, and `sort` accepts exactly one
of `expiresAt`, `dueDate`, `publishedAt` or `targetAmount` followed by `asc` or `desc`. The service
always appends `auctionId ASC` as a deterministic tie-breaker. Invalid status, currency, page,
size or sort values return HTTP 400.

## Event stream

`investment-service` owns `investment.auction-lifecycle.v1`. Every message uses `auctionId` as the
Kafka key so all lifecycle events for one auction remain in a single partition and in publication
order. The consumer group is `investment-marketplace-v1`; invalid messages are routed after three
attempts to `investment.auction-lifecycle.v1.dlq`.

The stream contains `AuctionCreated`, `AuctionPublished`, `PartitionAdded`,
`AuctionFullyFunded`, `AuctionExpired`, `AuctionCancelled` and `AuctionClosed`. Contracts use an
envelope with `eventId`, `eventType`, `schemaVersion`, `aggregateId`, `sequence`, `occurredAt` and a
type-specific `data` payload. Delivery is at-least-once, so consumers deduplicate by `eventId` and
must not regress an aggregate to an older `sequence`.

The launch policy is `cleanup.policy=delete` with indefinite retention (`retention.ms=-1`) so the
projection can be rebuilt completely. Partition-count changes require a coordinated rebuild because
they can change key-to-partition assignment. Compatible fields may be added within schema version 1;
removal, renaming or semantic changes require a new schema version and topic with a dual-publish
migration window.

## Replay

A controlled replay uses this sequence:

1. Stop the `investment-marketplace-v1` consumer.
2. Clear only the Marketplace projection and processed-event inbox tables.
3. Reset the consumer-group offsets to the beginning of `investment.auction-lifecycle.v1`.
4. Restart the consumer and monitor the DLT and projection lag until caught up.

The Auction write model and outbox are never deleted during a Marketplace replay. Events received
out of order are retained as deferred inbox records and retried after their prerequisites arrive.

## Materialization and idempotency

Flyway migration `V3__create_marketplace_projection.sql` creates only two additive tables:

- `auction_marketplace_views` stores one current row per `auctionId`, including the last applied
  sequence and event identifier.
- `marketplace_processed_events` is the durable inbox keyed by `eventId`; it retains `APPLIED` and
  `DEFERRED` messages together with the original envelope so deferred work is replayable.

The consumer validates schema version and event type before opening the projection transaction.
Inside one transaction it records the inbox item and updates the view. Duplicate `eventId` values
are ignored, sequences at or below the view watermark cannot regress it, and incremental events
received before `AuctionPublished` remain `DEFERRED`. A successful `AuctionPublished` then applies
the pending events in ascending sequence. Unknown types, unsupported versions and invalid payloads
escape the consumer so the binder performs three deliveries before routing the original record to
the DLT.
