# HBase Backend

Use `HBaseStorageContext` when your platform standard is Apache HBase and you need deduplication operations backed by HBase's atomic `checkAndMutate` semantics and native TTL support.

## Configuration

```java
HBaseTableConnection tableConnection = new HBaseTableConnection(false, hbaseConnection);

HBaseStorageContext storageContext = HBaseStorageContext.builder()
    .connection(tableConnection)   // HBaseTableConnection wrapper
    .tableName("solus_deduper")    // HBase table name
    .farm("dc1")                   // Datacenter identifier
    .build();
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `connection` | `HBaseTableConnection` | A wrapper around the HBase `Connection`. Handles Kerberos re-login if the cluster uses secure authentication. |
| `tableName` | `String` | Name of the HBase table used for Bloom filter data storage. Created automatically if it does not exist. |
| `farm` | `String` | Datacenter / farm identifier. Used for DC-level table naming. |

## Initialization — auto table creation

When you construct an `HBaseStorageContext`, the constructor calls `ensureTableExists()` which checks whether the table exists and creates it if needed:

```java
TableDescriptor tableDescriptor = TableDescriptorBuilder
    .newBuilder(TableName.valueOf(tableName))
    .setColumnFamily(ColumnFamilyDescriptorBuilder
        .newBuilder("S")
        .setCompressionType(Compression.Algorithm.GZ)
        .setMaxVersions(1)
        .build())
    .build();
```

The table is pre-split using a 256-bucket one-byte hash prefix (`OneByteSimpleHash`) to distribute writes evenly across regions.

!!! warning
    For best performance, do **not** pre-create the HBase table manually. Let Solus create it with the correct schema, column family, compression, and pre-split configuration.

If table creation fails, a `SolusException` with `ErrorCode.TABLE_CREATION_ERROR` is thrown.

## How deduplication works

HBase columns represent individual Bloom filter bit positions. Each bit position stores a logical `expireTime` timestamp (in ms) as the cell value. Each cell expires after the deduper-level TTL configured in `DeDuperConfig.ttlInMs`.

### Data storage

1. An entity is hashed to determine its shard ID via Murmur3-128.
2. Multiple hash functions (MD5-based) compute the bit positions within the shard.
3. Each bit position is written as a column in the `S` column family. The column value is `now + perEntityTtl`, where `perEntityTtl` is the TTL passed to `add(...)` (defaults to `DeDuperConfig.ttlInMs` when not supplied).
4. To check absence, all computed bit position columns are read — if any are missing or the stored `expireTime` has passed, the entity is considered absent.

### TTL behavior

Two TTL values are in play:

- **Per-entity TTL** — passed to `add(..., ttlInMs)`. If no TTL is passed, `DeDuperConfig.ttlInMs` is used. The value is limited to `DeDuperConfig.ttlInMs` and determines the logical `expireTime` stored in the cell value.
- **Deduper-level TTL** — `DeDuperConfig.ttlInMs`. It is set as the cell-level TTL on every `Put` and drives when each cell expires.

```java
new Put(rowKey, System.currentTimeMillis())
    .setTTL(deduperTtl)
    .addColumn(COLUMN_FAMILY, columnName, Bytes.toBytes(expireTime));
```

After the cell expires, HBase removes it and the bit position becomes available for reuse.

### Backward compatibility

Reads support two cell value formats:

- **Current format** — a `long` containing the absolute `expireTime`. The bit is considered set only if `expireTime >= currentTime`.
- **Legacy format** — a single-byte boolean marker (`true` means set, `false`/absent means not set). This allows rolling upgrades to coexist with data written by older releases.

!!! note
    HBase cell TTL depends on the region server's compaction cycle. In practice, the cell becomes invisible to reads immediately after TTL expiry, but physical deletion happens during the next major compaction.

## Row key design

Row keys are hash-prefixed using `RowKeyDistributorByHashPrefix` with a `OneByteSimpleHash(256)` hasher to prevent hotspotting:

```
<1-byte-hash-prefix> + <shardId>|<clientId>|<deDuperName>
```

## Table naming

The HBase table name varies based on deduplication level:

| Level | Table name |
|-------|-----------|
| `XDC` | `<tableName>` |
| `DC` | `<farm>_<tableName>` |

DC-level dedupers use farm-specific tables, providing natural isolation between datacenters.

### Meta store table

Deduper metadata is stored in a separate table: `<clientId>_dedupers`. This table uses:

- Column family: `C`
- Columns: `name`, `hf` (hash functions), `sh` (shards), `bps` (bits per shard), `a` (active), `level`, `ctime`, `utime`
- Row key: `deDuperConfig|<clientId>|<deDuperName>` with hash prefix

The `getAllActive` query uses a `SingleColumnValueFilter` scan on the `a` (active) column.

## Column layout

### Data table

| Column Family | Column | Value |
|--------------|--------|-------|
| `S` | `<bitPosition>` | `long` expireTime (legacy: boolean marker) |

### Meta table

| Column Family | Column | Value |
|--------------|--------|-------|
| `C` | `name` | Deduper name |
| `C` | `hf` | Number of hash functions |
| `C` | `sh` | Number of shards |
| `C` | `bps` | Bits per shard |
| `C` | `a` | Active status (boolean) |
| `C` | `level` | Deduplication level |
| `C` | `ctime` | Creation timestamp |
| `C` | `utime` | Last update timestamp |

## Batch operations

Solus uses HBase's native batch APIs for efficient bulk operations:

- **Batch gets** — used by `checkAbsence` for batch entity lookups. All bit positions for each shard are read in a single batch call.
- **Batch puts** — used by `add` for batch entity inserts. Multiple shards are written in a single batch call.

Both are implemented via the `HBaseBatchGetCommand` and `HBaseBatchPutCommand` command classes.

## HBase command pattern

All HBase operations are abstracted into command classes extending `GenericHBaseCommand<T>`:

| Command | Description |
|---------|-------------|
| `HBaseGetCommand` | Single-row get with specific columns/filters. |
| `HBasePutCommand` | Single-row put. |
| `HBaseBatchGetCommand` | Batch get (`List<Get>`), returns `Result[]`. |
| `HBaseBatchPutCommand` | Batch put (`List<Put>`). |
| `HBaseScanCommand` | Table scan with filters, returns `List<Result>`. |

## Kerberos support

`HBaseTableConnection` handles Kerberos authentication transparently. When `isSecure` is set to `true`, it performs a `UserGroupInformation.getLoginUser().reloginFromKeytab()` before each table access to ensure the Kerberos ticket is valid.
