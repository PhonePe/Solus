# Deduplication Semantics

## Configuration

Each deduper is created with a `DeDuperConfig` that controls the Bloom filter parameters:

| Parameter | Type | Default | Min | Max | Description |
|-----------|------|---------|-----|-----|-------------|
| `noOfHashFunctions` | `int` | 7 | 7 | 13 | Number of hash functions for the Bloom filter. More functions reduce false positives but increase write cost. |
| `noOfShards` | `long` | 10,000,000 | 10,000,000 | 150,000,000 | Number of shards for distributing data. Each entity is assigned to one shard via Murmur3 hashing. |
| `bitsPerShard` | `int` | 1,000 | 1,000 | 30,000 | Number of bit positions in each shard's Bloom filter. |
| `deDuperLevel` | `DeDuperLevel` | `XDC` | — | — | `DC` (datacenter-local) or `XDC` (cross-datacenter). |

```java
DeDuperConfig config = DeDuperConfig.builder()
    .noOfHashFunctions(10)
    .noOfShards(50_000_000L)
    .bitsPerShard(20_000)
    .deDuperLevel(DeDuperLevel.XDC)
    .build();
```

!!! warning "Immutable after registration"
    
    Once a deduper is registered, its configuration cannot be changed. Attempting to re-register with a different config throws `SolusException` with `ErrorCode.DEDUPER_CONFIG_MISMATCH`.

## Deduplication levels

Solus supports two deduplication levels that control the scope of duplicate detection:

### `XDC` — Cross Datacenter

Deduplication is shared across all datacenters. An entity added in DC1 will be detected as a duplicate in DC2. The storage key omits the farm identifier:

```
<shardId>|<clientId>|<deDuperName>
```

This is the **default** and recommended mode for most workloads.

### `DC` — Datacenter Local

Deduplication is isolated to each datacenter. Each farm maintains independent Bloom filter data. The storage backend uses farm-specific sets/tables:

| Backend | Key structure |
|---------|--------------|
| Aerospike | Set name: `<farm>_<setName>` |
| HBase | Table name: `<farm>_<tableName>` |

Use `DC` when you need independent deduplication per region or when cross-DC replication introduces unacceptable latency.

!!! warning "DC consistency"
    
    With `DC` level, the same entity can be added independently in different datacenters without conflict. This is by design — each DC maintains its own Bloom filter state.

## Capacity planning

The maximum key space is calculated as:

```
maxKeys = noOfShards × bitsPerShard
```

With maximum configuration (150M shards × 30K bits), Solus can handle up to **4.5 trillion** unique keys.

### False positive rates

The false positive probability for a Bloom filter is approximately:

```
P(false positive) ≈ (1 - e^(-kn/m))^k
```

Where:

- `k` = number of hash functions (`noOfHashFunctions`)
- `n` = number of elements inserted
- `m` = total number of bits (`noOfShards × bitsPerShard`)

The optimal number of hash functions to minimize false positives:

```
k_opt = (m / n) × ln(2)
```

### Recommended configurations

=== "Small Scale (up to 100M keys)"

    ```java
    DeDuperConfig.builder()
        .noOfHashFunctions(7)
        .noOfShards(10_000_000L)
        .bitsPerShard(1_000)
        .build();
    // Key space: 10 billion | False positive rate: ~0.8%
    ```

=== "Medium Scale (up to 1B keys)"

    ```java
    DeDuperConfig.builder()
        .noOfHashFunctions(7)
        .noOfShards(50_000_000L)
        .bitsPerShard(5_000)
        .build();
    // Key space: 250 billion | False positive rate: ~0.6%
    ```

=== "Large Scale (up to 10B keys)"

    ```java
    DeDuperConfig.builder()
        .noOfHashFunctions(10)
        .noOfShards(100_000_000L)
        .bitsPerShard(20_000)
        .build();
    // Key space: 2 trillion | False positive rate: ~0.1%
    ```

### Performance benchmarks

| Configuration | Keys | False Positive Rate |
|--------------|------|---------------------|
| 7 hash functions, 5K bits, 200K shards | 100M | ~0.8% |
| 7 hash functions, 20K bits, 500K shards | 1B | ~0.6% |

## API reference

### `checkAbsence` — single entity

| Method | Description |
|--------|-------------|
| `checkAbsence(String deDuperName, T entity)` | Returns `true` if the entity is absent (not seen before). Returns `false` if the entity was previously added. |

### `checkAbsence` — batch

| Method | Description |
|--------|-------------|
| `checkAbsence(String deDuperName, Set<T> entities)` | Returns a `Map<T, Boolean>` — `true` for absent entities, `false` for seen entities. Entities are grouped by shard for efficient batch reads. |

### `add` — single entity

| Method | Description |
|--------|-------------|
| `add(String deDuperName, T entity, long ttlInMs)` | Adds the entity to the Bloom filter with the specified TTL in milliseconds. |

### `add` — batch

| Method | Description |
|--------|-------------|
| `add(String deDuperName, Set<T> entities, long ttlInMs)` | Adds all entities to the Bloom filter. Entities are grouped by shard for efficient batch writes. |

### `addIfAbsent` — single entity

| Method | Description |
|--------|-------------|
| `addIfAbsent(String deDuperName, T entity, long ttlInMs)` | Checks if the entity is absent, and if so, adds it. Returns `true` if the entity was added. |

### `addIfAbsent` — batch

| Method | Description |
|--------|-------------|
| `addIfAbsent(String deDuperName, Set<T> entities, long ttlInMs)` | Returns a `Map<T, Boolean>` — `true` for entities that were added (were absent). |

### Registration methods

| Method | Description |
|--------|-------------|
| `register(String name)` | Register a deduper with default configuration. |
| `register(String name, DeDuperConfig config)` | Register a deduper with custom configuration. |
| `unregister(String name)` | Mark a deduper as inactive. |
| `getDeDuper(String name)` | Retrieve deduper metadata from the store. |
| `getCachedDeDuper(String name)` | Retrieve deduper metadata from the Caffeine cache. |
| `getActiveDeDupers()` | Retrieve all active dedupers as `Map<String, DeDuper>`. |

## Error codes

All errors are surfaced as `SolusException` with one of the following codes:

| Code | Description |
|------|-------------|
| `DEDUPER_NOT_FOUND` | The requested deduper does not exist or is inactive. |
| `INVALID_CONFIG` | `DeDuperConfig` validation failed (values out of allowed range). |
| `DEDUPER_CONFIG_MISMATCH` | Attempting to re-register an existing deduper with a different configuration. |
| `AEROSPIKE_ERROR` | Aerospike backend returned an error during a data operation. |
| `HBASE_ERROR` | HBase backend returned an error during a data operation. |
| `TABLE_CREATION_ERROR` | HBase table creation failed during storage context initialization. |
| `CACHE_ERROR` | Internal Caffeine cache error. |
| `INTERNAL_ERROR` | Catch-all for unexpected failures. |

### Exception propagation

`SolusException.propagate(throwable)` unwraps nested `SolusException` instances so you always receive the original error code rather than a wrapped `INTERNAL_ERROR`.

## Thread safety

- `SolusEngine` is thread-safe — it can be shared across threads.
- The underlying `DeDuperCrudCommands` uses a Caffeine `AsyncLoadingCache` for thread-safe cached reads.
- `ShardCalculator` and Bloom filter hash computations are stateless and thread-safe.
