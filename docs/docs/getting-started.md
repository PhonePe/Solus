# Getting Started

## Requirements

- Java 17 or later.
- One of the supported storage backends:
    - An Aerospike cluster (6.x+) reachable from application nodes, or
    - An HBase cluster (2.x+) reachable from application nodes.

## Add dependency

```xml
<dependency>
    <groupId>com.phonepe</groupId>
    <artifactId>solus</artifactId>
    <version>${solus.version}</version>
</dependency>
```

Replace `${solus.version}` with the latest version from [Maven Central](https://central.sonatype.com/artifact/com.phonepe/solus) or [GitHub Releases](https://github.com/PhonePe/solus/releases).

## Build locally

```bash
git clone https://github.com/PhonePe/solus.git
cd solus
mvn clean install
```

To run the tests:

```bash
mvn clean test
```

## Minimal example

```java
// 1. Create Aerospike client
AerospikeClient aerospikeClient = new AerospikeClient("localhost", 3000);

// 2. Build the storage context
AerospikeStorageContext storageContext = AerospikeStorageContext.builder()
    .aerospikeClient(aerospikeClient)
    .namespace("your-namespace")
    .setName("deduper-set")
    .farm("dc1")
    .build();

// 3. Build the Solus engine
SolusEngine<String> solusEngine = SolusEngine.<String>builder()
    .clientId("my-service")
    .storageContext(storageContext)
    .build();

// 4. Register a deduper (creates metadata in the store)
solusEngine.register("coupons");

// 5. Add an entity with TTL
solusEngine.add("coupons", "COUPON-ABC-123", 86400000L); // 24-hour TTL

// 6. Check if entity exists
boolean isAbsent = solusEngine.checkAbsence("coupons", "COUPON-ABC-123");
// isAbsent = false (entity was already added)

// 7. Atomic add-if-absent
boolean wasAdded = solusEngine.addIfAbsent("coupons", "COUPON-XYZ-789", 86400000L);
// wasAdded = true (entity was absent, now added)
```

!!! tip
    The example above uses all default configuration values (7 hash functions, 10M shards, 1000 bits per shard, XDC level). To customise these, pass a `DeDuperConfig` to the `register()` call. See [Deduplication Semantics](deduplication.md#configuration) for details.

## What's next

- [Usage](usage.md) — initialization, registration, data operations, error handling.
- [Deduplication Semantics](deduplication.md) — configuration, capacity planning, deduplication levels.
- [Storage Backends](storages/aerospike.md) — Aerospike and HBase details.
