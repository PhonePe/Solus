# Aerospike Backend

Use `AerospikeStorageContext` when your workload needs low-latency deduplication operations backed by Aerospike's in-memory storage.

## Configuration

```java
AerospikeStorageContext storageContext = AerospikeStorageContext.builder()
    .aerospikeClient(aerospikeClient)  // IAerospikeClient instance
    .namespace("your-namespace")       // Aerospike namespace
    .setName("deduper-set")            // Aerospike set name suffix
    .farm("dc1")                       // Datacenter identifier
    .build();
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `aerospikeClient` | `IAerospikeClient` | An already-connected Aerospike client. Solus does not manage the client lifecycle. |
| `namespace` | `String` | The Aerospike namespace where deduper records are stored. Must already exist on the cluster. |
| `setName` | `String` | Base set name for storing Bloom filter data. |
| `farm` | `String` | Datacenter / farm identifier. Used in key and bin name construction. |

## How deduplication works

Aerospike records represent individual Bloom filter shards. Each bit position in the shard is stored as a separate bin with a TTL.

### Data storage

1. An entity is hashed to determine its shard ID via Murmur3-128.
2. Multiple hash functions (MD5-based) compute the bit positions within the shard.
3. Each bit position is written as an Aerospike bin with the requested TTL.
4. To check absence, the bins for all computed bit positions are read — if any are missing, the entity is considered absent.

### Key format

Aerospike record keys follow this format:

```
<shardId>|<clientId>|<deDuperName>
```

### Bin naming

Solus supports three bin naming versions for backward compatibility:

| Version | Format | Direction |
|---------|--------|-----------|
| V0 (legacy) | `b_<position>_ttl` | Read only |
| V1 (legacy) | `<farm>##b_<position>_ttl` | Read only |
| V2 (current) | `<farm>#b<position>` | Read and write |

All three formats are read during `checkAbsence`, but only V2 is used for writes. This ensures seamless backward compatibility during rolling upgrades.

## Set naming

The Aerospike set name varies based on deduplication level:

| Level | Set name |
|-------|----------|
| `XDC` | `<setName>` |
| `DC` | `<farm>_<setName>` |

DC-level dedupers use farm-specific sets, providing natural isolation between datacenters.

### Meta store set

Deduper metadata is stored in a separate set: `<clientId>_solus_DEDUPER`. This set stores registration information (name, config, active status) and never expires (`TTL = -1`).

## Batch operations

Solus uses Aerospike's native batch APIs for efficient bulk operations:

- **Batch reads** (`BatchRead`) — used by `checkAbsence` for batch entity lookups. Entities are grouped by shard, and all bit positions for each shard are read in a single batch call.
- **Batch writes** (`BatchWrite`) — used by `add` for batch entity inserts. Multiple shards are written in a single batch call.

## Retry behavior

All Aerospike operations are wrapped in a `guava-retrying` retryer:

| Setting | Value |
|---------|-------|
| Retry on | `AerospikeException` |
| Max attempts | 5 |
| Wait between attempts | 80 ms (fixed) |
| Block strategy | Thread sleep |

If all retries are exhausted, a `SolusException` with `ErrorCode.AEROSPIKE_ERROR` is thrown.

## Secondary indexes

The meta store creates a secondary index on the `active` bin to support efficient `getAllActive` queries using Aerospike's query API.

## Initialization

`AerospikeStorageContext` does not perform any I/O on construction — Aerospike sets are created on first write automatically. Secondary indexes for the meta store are created during the first `register` call.

## Multi-farm support

Aerospike bin names are farm-prefixed, allowing multiple farms to write to the same record. The `getLatestUpdatedFarm()` utility determines which farm has the most recent data by comparing update timestamps across farm-specific bins.
