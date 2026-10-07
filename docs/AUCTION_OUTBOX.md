# Auction lifecycle outbox

## Delivery flow

Every confirmed lifecycle transition is registered by `Auction` as a domain event. Spring Data
publishes that event when the command service saves the aggregate. A transactional listener maps it
to the v1 integration contract and inserts `investment_outbox_events` during the same Oracle
transaction.

The scheduled publisher reads due `PENDING` rows by `sequence_no`, sends their immutable JSON to
the `auctionLifecycle-out-0` binding with `aggregate_id` as the Kafka key, and changes the row to
`PUBLISHED` only after the synchronous producer call succeeds.

The two halves of that message do not have the same type. The payload is a `byte[]`, which is what
`projectAuctionMarketplace-in-0` consumes, but the key is the `aggregate_id` string. Left alone the
binder would pick one serializer for both, so the binding declares
`key.serializer: StringSerializer` explicitly. Without it every send fails on a cast and the
Marketplace projection receives nothing at all, while the auctions themselves stay correct in
Oracle — a silent empty marketplace rather than an error anyone would notice. A failed send keeps the row pending,
increments `attempt_count`, records `last_error` and schedules an exponential retry capped at five
minutes. Published rows are retained for seven days for operational traceability and then removed.

The guarantee is at-least-once. A process failure after Kafka acknowledges a message but before
Oracle commits `PUBLISHED` can produce redelivery. `event_id` and the serialized payload remain
unchanged across retries, so consumers must deduplicate by `eventId`.

## Operations

The production topic is provisioned as `investment.auction-lifecycle.v1` with
`cleanup.policy=delete`, `retention.ms=-1` and a fixed partition count agreed before launch. Its DLT
is `investment.auction-lifecycle.v1.dlq`. Development may use binder topic autocreation; production
must provision both topics outside the application with the environment's replication policy.

Monitor at minimum:

- Count and age of `PENDING` rows.
- Growth in `attempt_count` and non-null `last_error`.
- Kafka producer errors and DLT records.
- Time between `created_at` and `published_at`.

If Kafka is unavailable, do not delete pending rows. Restore broker connectivity and allow the
scheduled publisher to retry. The rollback for migration V2, only when the feature itself is being
removed and no pending event is needed, is to stop the service, drop `investment_outbox_events` and
then drop `investment_outbox_sequence`. This rollback is intentionally not automated.
