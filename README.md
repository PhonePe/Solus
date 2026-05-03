# Solus

> A distributed de-duper with TTL capability based on Bloom Filters

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=PhonePe_Solus&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=PhonePe_Solus)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=PhonePe_Solus&metric=coverage)](https://sonarcloud.io/summary/new_code?id=PhonePe_Solus)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=PhonePe_Solus&metric=bugs)](https://sonarcloud.io/summary/new_code?id=PhonePe_Solus)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=PhonePe_Solus&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=PhonePe_Solus)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=PhonePe_Solus&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=PhonePe_Solus)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=PhonePe_Solus&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=PhonePe_Solus)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=PhonePe_Solus&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=PhonePe_Solus)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=PhonePe_Solus&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=PhonePe_Solus)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=PhonePe_Solus&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=PhonePe_Solus)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=PhonePe_Solus&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=PhonePe_Solus)

## Overview

**Solus** is a high-performance, TTL-based deduplication library designed for (hundred+) billion scale operations. It
uses probabilistic data structures (Bloom filters) with sharding to provide memory-efficient duplicate detection with
configurable accuracy.

## Features

- **Massive Scale** — Handle billions of unique entities using sharded Bloom filters
- **TTL Support** — Automatic expiration of entries based on time-to-live
- **Pluggable Storage Backends** — Aerospike and HBase out of the box
- **Multi-Datacenter Support** — `DC` (datacenter-local) and `XDC` (cross-datacenter) deduplication levels
- **Configurable Accuracy** — Tune false positive rates by adjusting hash functions, shards, and bits per shard
- **Batch Operations** — Efficient bulk add and check operations
- **Atomic Operations** — `addIfAbsent` for atomic check-and-add patterns

## Documentation

Detailed documentation is available at **[https://phonepe.github.io/Solus](https://phonepe.github.io/Solus)**

- [Getting Started](docs/docs/getting-started.md)
- [Usage Guide](docs/docs/usage.md)
- [Deduplication Semantics](docs/docs/deduplication.md)
- [Aerospike Backend](docs/docs/storages/aerospike.md)
- [HBase Backend](docs/docs/storages/hbase.md)

## Getting Started

### Maven

```xml

<dependency>
    <groupId>com.phonepe</groupId>
    <artifactId>solus</artifactId>
    <version>${solus.version}</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.phonepe:solus:${solus.version}'
```

### Requirements

- Java 17 or higher
- One of the supported storage backends:
    - Aerospike 6.x+
    - HBase 2.x+

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

## License

Solus is licensed under the [Apache License 2.0](LICENSE).

```
Copyright 2026 PhonePe India Pvt. Ltd.

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
