# Usage

## Builder parameters

| Parameter        | Type             | Required | Description                                                                                           |
|------------------|------------------|----------|-------------------------------------------------------------------------------------------------------|
| `clientId`       | `String`         | Yes      | Unique identifier for the calling service. Used in key construction to scope deduper data per client. |
| `storageContext` | `StorageContext` | Yes      | The storage backend — wraps the connection details for Aerospike or HBase.                            |
| `validator`      | `Validator`      | No       | A `javax.validation.Validator` instance for config validation. A default is used if omitted.          |

## Initialize the engine

=== "Aerospike"

```java
AerospikeStorageContext storageContext = AerospikeStorageContext.builder()
        .aerospikeClient(aerospikeClient)
        .namespace("your-namespace")
        .setName("deduper-set")
        .farm("dc1")
        .build();

SolusEngine<String> solusEngine = SolusEngine.<String>builder()
        .clientId("my-service")
        .storageContext(storageContext)
        .build();
```

=== "HBase"

```java
HBaseTableConnection tableConnection = new HBaseTableConnection(false, hbaseConnection);

HBaseStorageContext storageContext = HBaseStorageContext.builder()
        .connection(tableConnection)
        .tableName("solus_deduper")
        .farm("dc1")
        .build();

SolusEngine<String> solusEngine = SolusEngine.<String>builder()
        .clientId("my-service")
        .storageContext(storageContext)
        .build();
```

!!! warning

    For HBase, the `HBaseStorageContext` constructor automatically creates the table if it does not exist, with GZ
    compression, a single column family, and pre-split regions for optimal distribution.

## Register a deduper

Before performing any data operations, you must register a deduper. This creates the metadata record in the store.

```java
// Register with default configuration
solusEngine.register("coupons");

// Register with custom configuration
DeDuperConfig config = DeDuperConfig.builder()
        .noOfHashFunctions(10)
        .noOfShards(50_000_000L)
        .bitsPerShard(20_000)
        .deDuperLevel(DeDuperLevel.XDC)
        .build();

solusEngine.register("high-volume-deduper", config);
```

!!! info

    If a deduper with the same name already exists and its configuration matches, registration succeeds silently. If the
    configuration differs, a `SolusException` with `ErrorCode.DEDUPER_CONFIG_MISMATCH` is thrown.

### Unregister

```java
solusEngine.unregister("coupons");
```

This marks the deduper as inactive in the metadata store. Existing data in the store is not deleted and will expire via
TTL.

### Query dedupers

```java
// Get a specific deduper
DeDuper deDuper = solusEngine.getDeDuper("coupons");

// Get from cache (faster, may be slightly stale — refreshes every 300s, expires after 3h)
DeDuper cached = solusEngine.getCachedDeDuper("coupons");

// Get all active dedupers
Map<String, DeDuper> active = solusEngine.getActiveDeDupers();
```

## Data operations

### Check absence

Checks whether an entity has been seen before. Returns `true` if the entity is **absent** (not a duplicate).

```java
// Single entity
boolean isAbsent = solusEngine.checkAbsence("coupons", "COUPON-ABC-123");

// Batch
Set<String> entities = Set.of("COUPON-1", "COUPON-2", "COUPON-3");
Map<String, Boolean> results = solusEngine.checkAbsence("coupons", entities);
```

!!! note "Probabilistic guarantees" 
    
    Due to the nature of Bloom filters, `checkAbsence()` returning `false` **guarantees** the entity was seen before.
    Returning `true` has a small probability of being a false positive (the entity was actually seen, but the filter missedit). 
    
See [False Positive Rates](deduplication.md#false-positive-rates) for details.

### Add entities

Adds an entity to the deduper with a TTL. After the TTL expires, the entity is automatically removed by the storage
backend.

```java
// Single entity — TTL in milliseconds
solusEngine.add("coupons", "COUPON-ABC-123", 86400000L); // 24-hour TTL

// Batch
Set<String> entities = Set.of("COUPON-1", "COUPON-2", "COUPON-3");
solusEngine.add("coupons", entities, 86400000L);
```

### Atomic add-if-absent

Checks if the entity is absent, and if so, adds it atomically. Returns `true` if the entity was added (was absent).

```java
// Single entity
boolean wasAdded = solusEngine.addIfAbsent("coupons", "COUPON-XYZ-789", 86400000L);

// Batch — returns a map of entity → whether it was added
Map<String, Boolean> results = solusEngine.addIfAbsent("coupons", entities, 86400000L);
```

## Error handling

All operations throw `SolusException`. Use `getErrorCode()` to distinguish failure reasons:

```java
try{
    solusEngine.getDeDuper("non-existent");
} catch(SolusException e){
        switch(e.getErrorCode()){
        case DEDUPER_NOT_FOUND -> log.warn("Deduper does not exist");
        case INVALID_CONFIG -> log.error("Configuration validation failed", e);
        case DEDUPER_CONFIG_MISMATCH -> log.error("Config mismatch on re-registration", e);
        case AEROSPIKE_ERROR -> log.error("Aerospike backend error", e);
        case HBASE_ERROR -> log.error("HBase backend error", e);
        case TABLE_CREATION_ERROR -> log.error("HBase table creation failed", e);
        case CACHE_ERROR -> log.error("Internal cache error", e);
        case INTERNAL_ERROR -> log.error("Unexpected error", e);
    }
}
```

See [Error Codes](deduplication.md#error-codes) for the full list.

## Complete lifecycle example

```java
// ── Setup (application startup) ──
AerospikeClient aerospikeClient = new AerospikeClient("localhost", 3000);

AerospikeStorageContext storageContext = AerospikeStorageContext.builder()
        .aerospikeClient(aerospikeClient)
        .namespace("dedup")
        .setName("solus_data")
        .farm("dc1")
        .build();

SolusEngine<String> solusEngine = SolusEngine.<String>builder()
        .clientId("coupon-service")
        .storageContext(storageContext)
        .build();

// ── Register (once per deduper) ──
DeDuperConfig config = DeDuperConfig.builder()
        .noOfHashFunctions(10)
        .noOfShards(50_000_000L)
        .bitsPerShard(20_000)
        .deDuperLevel(DeDuperLevel.XDC)
        .build();

solusEngine.register("coupon-deduper",config);

// ── Use (request handling) ──
String couponCode = "SUMMER-SALE-2026";
boolean wasAdded = solusEngine.addIfAbsent("coupon-deduper", couponCode, 86400000L);
if(wasAdded) {
    // First time — process the coupon
    processCoupon(couponCode);
} else {
    // Duplicate — reject
    throw new IllegalStateException("Coupon already redeemed");
}

// ── Teardown (application shutdown) ──
// Unregister if no longer needed
solusEngine.unregister("coupon-deduper");
```
