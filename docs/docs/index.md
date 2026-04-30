# Solus

Solus is a high-performance, TTL-based deduplication library designed for (hundred+) billion scale operations. It uses probabilistic data structures (Bloom filters) with sharding to provide memory-efficient duplicate detection with configurable accuracy.

## Why Solus?

In large-scale distributed systems, duplicate detection is a fundamental challenge — whether it is coupon redemption, event deduplication, request idempotency, or notification throttling. Solus provides a lightweight, pluggable deduplication engine that handles billions of unique entities with minimal memory footprint and automatic TTL-based expiration.

## Key features

- **Massive scale** — handle billions of unique entities using sharded Bloom filters.
- **TTL support** — automatic expiration of entries based on time-to-live.
- **Pluggable storage backends** — Aerospike and HBase out of the box.
- **Multi-datacenter support** — `DC` (datacenter-local) and `XDC` (cross-datacenter) deduplication levels.
- **Configurable accuracy** — tune false positive rates by adjusting hash functions, shards, and bits per shard.
- **Batch operations** — efficient bulk `add` and `checkAbsence` operations.
- **Thread-safe** — safe for concurrent use in multi-threaded applications.
- **Atomic operations** — `addIfAbsent` for atomic check-and-add patterns.
- **Built-in caching** — Caffeine-based async cache for deduper metadata lookups.
- **Built-in retry** — configurable retry with backoff on transient Aerospike failures.

## How it works

```
┌─────────────────────────────────────────────────────────────────┐
│                        SolusEngine<T>                           │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐     ┌─────────────────────┐           │
│  │ DeDuperCrudCommands │     │ DeDuperDataCommands │           │
│  └──────────┬──────────┘     └──────────┬──────────┘           │
│             │                           │                       │
│             ▼                           ▼                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    StorageContext                        │   │
│  │  ┌─────────────────────┐  ┌─────────────────────────┐   │   │
│  │  │ AerospikeStorage    │  │    HBaseStorage         │   │   │
│  │  │ Context             │  │    Context              │   │   │
│  │  └─────────┬───────────┘  └───────────┬─────────────┘   │   │
│  └────────────┼──────────────────────────┼─────────────────┘   │
└───────────────┼──────────────────────────┼─────────────────────┘
                │                          │
                ▼                          ▼
        ┌───────────────┐          ┌───────────────┐
        │   Aerospike   │          │     HBase     │
        │   Cluster     │          │    Cluster    │
        └───────────────┘          └───────────────┘
```

### Deduplication pipeline

1. **Sharding** — entities are hashed (Murmur3-128) and distributed across millions of shards.
2. **Bloom filters** — each shard maintains a space-efficient Bloom filter with configurable bit positions.
3. **TTL management** — the storage backend handles automatic expiration of entries.
4. **Hash functions** — multiple MD5-based hash functions reduce false positive rates.

## What to read next

- [Getting Started](getting-started.md) — dependency setup, prerequisites, building locally.
- [Usage](usage.md) — initialization, registration, data operations, error handling.
- [Deduplication Semantics](deduplication.md) — configuration, capacity planning, deduplication levels, false positive rates.
- [Storage Backends](storages/aerospike.md) — Aerospike and HBase internals.
