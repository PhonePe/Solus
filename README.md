# Solus

[![Maven Central](https://img.shields.io/maven-central/v/com.phonepe/solus.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.phonepe%22%20AND%20a:%22solus%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://github.com/PhonePe/solus/workflows/Build/badge.svg)](https://github.com/PhonePe/solus/actions)

**Solus** is a high-performance, TTL-based deduplication library designed for (hundred+) billion scale operations. It uses probabilistic data structures (Bloom filters) with sharding to provide memory-efficient duplicate detection with configurable accuracy.

## Table of Contents

- [Features](#features)
- [Use Cases](#use-cases)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Storage Backends](#storage-backends)
- [API Reference](#api-reference)
- [Architecture](#architecture)
- [Performance Tuning](#performance-tuning)
- [Error Handling](#error-handling)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Massive Scale**: Handle billions of unique entities with minimal memory footprint
- **TTL Support**: Automatic expiration of entries based on time-to-live
- **Multiple Storage Backends**: Support for Aerospike and HBase
- **Multi-Datacenter Support**: Built-in support for cross-datacenter (XDC) and datacenter-local (DC) deduplication
- **Configurable Accuracy**: Tune false positive rates based on your requirements
- **Batch Operations**: Efficient bulk add and check operations
- **Thread-Safe**: Safe for concurrent use in multi-threaded applications
- **Atomic Operations**: `addIfAbsent` for atomic check-and-add patterns

## Use Cases

- **Coupon/Voucher Redemption**: Prevent duplicate redemption of one-time use codes
- **Event Deduplication**: Filter duplicate events in streaming pipelines
- **Request Idempotency**: Ensure API requests are processed only once
- **Notification Throttling**: Prevent sending duplicate notifications to users
- **Click Fraud Detection**: Identify and filter duplicate ad clicks

## Installation

### Maven

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.phonepe</groupId>
    <artifactId>solus</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.phonepe:solus:1.0.0'
```

### Requirements

- Java 17 or higher
- One of the supported storage backends:
  - Aerospike 6.x+
  - HBase 2.x+

## Quick Start

### Using Aerospike

```java
import com.aerospike.client.AerospikeClient;
import com.phonepe.solus.SolusEngine;
import com.phonepe.solus.config.DeDuperConfig;
import com.phonepe.solus.store.context.impl.AerospikeStorageContext;

// 1. Create Aerospike client
AerospikeClient aerospikeClient = new AerospikeClient("localhost", 3000);

// 2. Create storage context
AerospikeStorageContext storageContext = AerospikeStorageContext.builder()
        .aerospikeClient(aerospikeClient)
        .namespace("your-namespace")
        .setName("deduper-set")
        .farm("dc1")  // Optional: datacenter identifier
        .build();

// 3. Create Solus Engine
SolusEngine<String> solusEngine = SolusEngine.<String>builder()
        .clientId("my-service")
        .storageContext(storageContext)
        .build();

// 4. Register a DeDuper (with default config)
solusEngine.register("coupons");

// 5. Add an entity with TTL (in milliseconds)
solusEngine.add("coupons", "COUPON-ABC-123", 86400000L);  // 24 hours TTL

// 6. Check if entity exists
boolean isAbsent = solusEngine.checkAbsence("coupons", "COUPON-ABC-123");
// isAbsent = false (entity exists)

// 7. Atomic add-if-absent pattern
boolean wasAdded = solusEngine.addIfAbsent("coupons", "COUPON-XYZ-789", 86400000L);
// wasAdded = true (entity was added because it didn't exist)
```

### Using HBase

```java
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import com.phonepe.solus.SolusEngine;
import com.phonepe.solus.hbase.HBaseTableConnection;
import com.phonepe.solus.store.context.impl.HBaseStorageContext;

// 1. Create HBase connection
Configuration config = HBaseConfiguration.create();
config.set("hbase.zookeeper.quorum", "zk1.example.com,zk2.example.com");
Connection hbaseConnection = ConnectionFactory.createConnection(config);

// 2. Create HBase table connection
HBaseTableConnection tableConnection = new HBaseTableConnection(false, hbaseConnection);

// 3. Create storage context (table will be created automatically if it doesn't exist)
HBaseStorageContext storageContext = HBaseStorageContext.builder()
        .connection(tableConnection)
        .tableName("solus_deduper")
        .farm("dc1")
        .build();

// 4. Create Solus Engine
SolusEngine<String> solusEngine = SolusEngine.<String>builder()
        .clientId("my-service")
        .storageContext(storageContext)
        .build();

// Rest of the usage is identical to Aerospike
solusEngine.register("events");
solusEngine.add("events", "event-12345", 3600000L);  // 1 hour TTL
```

## Configuration

### DeDuperConfig

Each DeDuper can be configured with the following parameters:

| Parameter | Default | Min | Max | Description |
|-----------|---------|-----|-----|-------------|
| `noOfHashFunctions` | 7 | 7 | 13 | Number of hash functions for the Bloom filter |
| `noOfShards` | 10,000,000 | 10,000,000 | 150,000,000 | Number of shards for distributing data |
| `bitsPerShard` | 1,000 | 1,000 | 30,000 | Number of bits in each shard's Bloom filter |
| `deDuperLevel` | XDC | - | - | `DC` (datacenter-local) or `XDC` (cross-datacenter) |

```java
// Custom configuration for high-scale use case
DeDuperConfig config = DeDuperConfig.builder()
        .noOfHashFunctions(10)
        .noOfShards(50_000_000L)
        .bitsPerShard(20_000)
        .deDuperLevel(DeDuperLevel.XDC)
        .build();

solusEngine.register("high-volume-deduper", config);
```

### Capacity Planning

The maximum key space is calculated as: `noOfShards × bitsPerShard`

With maximum configuration, Solus can handle up to **4.5 trillion** unique keys.

**Formula for optimal configuration:**

The false positive probability is approximately: $(1-e^{-kn/m})^k$

Where:
- `k` = number of hash functions
- `n` = number of elements inserted
- `m` = total number of bits (noOfShards × bitsPerShard)

The optimal number of hash functions to minimize false positives: $k = (m/n) \times \ln(2)$

## Storage Backends

### Aerospike

Best for:
- Low-latency requirements (sub-millisecond reads)
- High throughput scenarios
- Cloud-native deployments

```java
AerospikeStorageContext.builder()
        .aerospikeClient(aerospikeClient)  // Required: Aerospike client instance
        .namespace("namespace")             // Required: Aerospike namespace
        .setName("set-name")               // Required: Aerospike set name
        .farm("datacenter-id")             // Optional: Datacenter identifier
        .build();
```

### HBase

Best for:
- Integration with Hadoop ecosystem
- Very large-scale deployments
- When data locality is important

```java
HBaseStorageContext.builder()
        .connection(hbaseTableConnection)  // Required: HBase connection wrapper
        .tableName("table-name")           // Required: HBase table name
        .farm("datacenter-id")             // Optional: Datacenter identifier
        .build();
```

The HBase table is automatically created with:
- GZ compression enabled
- Single column family
- Pre-split regions for optimal distribution

## API Reference

### SolusEngine

The main entry point for all deduplication operations.

#### Registration Methods

```java
// Register with default configuration
void register(String name)

// Register with custom configuration
void register(String name, DeDuperConfig config)

// Unregister a DeDuper
void unregister(String name)

// Get DeDuper details
DeDuper getDeDuper(String name)

// Get cached DeDuper (faster, may be slightly stale)
DeDuper getCachedDeDuper(String name)

// Get all active DeDupers
Map<String, DeDuper> getActiveDeDupers()
```

#### Data Operations

```java
// Check if single entity is absent
boolean checkAbsence(String deDuperName, T entity)

// Check absence for multiple entities (batch)
Map<T, Boolean> checkAbsence(String deDuperName, Set<T> entities)

// Add single entity with TTL
void add(String deDuperName, T entity, long ttlInMs)

// Add multiple entities with TTL (batch)
void add(String deDuperName, Set<T> entities, long ttlInMs)

// Atomic add-if-absent for single entity
boolean addIfAbsent(String deDuperName, T entity, long ttlInMs)

// Atomic add-if-absent for multiple entities (batch)
Map<T, Boolean> addIfAbsent(String deDuperName, Set<T> entities, long ttlInMs)
```

### Return Values

| Method | Returns `true` when |
|--------|---------------------|
| `checkAbsence()` | Entity is **not** in the DeDuper (absent) |
| `addIfAbsent()` | Entity was **added** (was absent before) |

**Note**: Due to the probabilistic nature of Bloom filters, `checkAbsence()` returning `false` guarantees the entity was seen before, but returning `true` has a small probability of being a false positive.

## Architecture

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

### How It Works

1. **Sharding**: Entities are hashed and distributed across millions of shards
2. **Bloom Filters**: Each shard maintains a space-efficient Bloom filter
3. **TTL Management**: Storage backend handles automatic expiration
4. **Hash Functions**: Multiple hash functions reduce false positive rates

## Performance Tuning

### Recommended Configurations

#### Small Scale (up to 100M keys)
```java
DeDuperConfig.builder()
        .noOfHashFunctions(7)
        .noOfShards(10_000_000L)
        .bitsPerShard(1_000)
        .build();
// False positive rate: ~0.8%
```

#### Medium Scale (up to 1B keys)
```java
DeDuperConfig.builder()
        .noOfHashFunctions(7)
        .noOfShards(50_000_000L)
        .bitsPerShard(5_000)
        .build();
// False positive rate: ~0.6%
```

#### Large Scale (up to 10B keys)
```java
DeDuperConfig.builder()
        .noOfHashFunctions(10)
        .noOfShards(100_000_000L)
        .bitsPerShard(20_000)
        .build();
// False positive rate: ~0.1%
```

### Performance Test Results

| Configuration | Keys | False Positive Rate |
|--------------|------|---------------------|
| 7 hash functions, 5k bits, 200k shards | 100M | ~0.8% |
| 7 hash functions, 20k bits, 500k shards | 1B | ~0.6% |

## Error Handling

Solus uses `SolusException` for all error conditions:

```java
try {
    solusEngine.getDeDuper("non-existent");
} catch (SolusException e) {
    switch (e.getErrorCode()) {
        case DEDUPER_NOT_FOUND:
            // Handle missing DeDuper
            break;
        case INVALID_CONFIG:
            // Configuration validation failed
            break;
        case DEDUPER_CONFIG_MISMATCH:
            // Trying to register existing DeDuper with different config
            break;
        case AEROSPIKE_ERROR:
        case HBASE_ERROR:
            // Storage backend error
            break;
        case TABLE_CREATION_ERROR:
            // HBase table creation failed
            break;
        case CACHE_ERROR:
            // Internal cache error
            break;
        case INTERNAL_ERROR:
            // Unexpected internal error
            break;
    }
}
```

## Multi-Datacenter Support

Solus supports two deduplication levels:

### XDC (Cross-Datacenter) - Default

Deduplication is shared across all datacenters. An entity added in DC1 will be detected as duplicate in DC2.

```java
DeDuperConfig.builder()
        .deDuperLevel(DeDuperLevel.XDC)
        .build();
```

### DC (Datacenter-Local)

Deduplication is isolated to each datacenter. Useful when you need independent deduplication per region.

```java
DeDuperConfig.builder()
        .deDuperLevel(DeDuperLevel.DC)
        .build();
```

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Development Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/PhonePe/solus.git
   cd solus
   ```

2. Build the project:
   ```bash
   mvn clean install
   ```

3. Run tests:
   ```bash
   mvn test
   ```

### Reporting Issues

Please report issues on [GitHub Issues](https://github.com/PhonePe/solus/issues).

## License

Solus is licensed under the [Apache License 2.0](LICENSE).

```
Copyright 2025 PhonePe India Pvt. Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Documentation Site (Zensical)

This repository now includes Zensical-based docs under `docs/`.

- Config: `docs/zensical.toml`
- Content: `docs/docs/`
- Python dependencies: `docs/requirements.txt`

Build docs locally:

```bash
cd docs
pip install -r requirements.txt
zensical build --clean
```

Generated site output is available at `docs/site`.

## Acknowledgments

Built with love by the PhonePe Engineering Team.

---

**Need help?** Open an issue or reach out to the maintainers.
