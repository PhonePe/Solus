# Contributing to Solus

First off, thank you for considering contributing to Solus! It's people like you that make Solus such a great tool.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Process](#development-process)
- [Style Guidelines](#style-guidelines)
- [Community](#community)

## Code of Conduct

This project and everyone participating in it is governed by our [Code of Conduct](CODE_OF_CONDUCT.md). By
participating, you are expected to uphold this code. Please report unacceptable behavior to the project maintainers.

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker (for running integration tests)

### Setting Up Your Development Environment

1. **Fork the repository** on GitHub

2. **Clone your fork locally**:
   ```bash
   git clone https://github.com/YOUR-USERNAME/Solus.git
   cd solus
   ```

3. **Add the upstream remote**:
   ```bash
   git remote add upstream https://github.com/PhonePe/Solus.git
   ```

4. **Build the project**:
   ```bash
   mvn clean install
   ```

5. **Run tests**:
   ```bash
   mvn test
   ```

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the [existing issues](https://github.com/PhonePe/Solus/issues) to avoid
duplicates.

When creating a bug report, please include:

- **A clear and descriptive title**
- **Steps to reproduce the issue**
- **Expected behavior** vs **actual behavior**
- **Environment details**: Java version, storage backend, OS
- **Relevant logs or stack traces**
- **Code samples** if applicable

Use the bug report template when creating an issue.

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, please include:

- **A clear and descriptive title**
- **Detailed description** of the proposed functionality
- **Use cases** that would benefit from this enhancement
- **Potential implementation approach** (if you have ideas)

### Pull Requests

1. **Create a branch** from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes** following our [style guidelines](#style-guidelines)

3. **Add tests** for your changes

4. **Ensure all tests pass**:
   ```bash
   mvn clean verify
   ```

5. **Commit your changes** with a clear message:
   ```bash
   git commit -m "Add feature: brief description of changes"
   ```

6. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Create a Pull Request** against the `main` branch

### Pull Request Guidelines

- Follow the PR template
- Include relevant issue numbers (e.g., "Fixes #123")
- Ensure CI checks pass
- Keep PRs focused - one feature/fix per PR
- Update documentation if needed
- Add tests for new functionality

## Development Process

### Branching Strategy

- `main` - Stable release branch
- `feature/*` - New features
- `fix/*` - Bug fixes
- `docs/*` - Documentation updates

### Commit Messages

Follow these conventions:

```
<type>: <subject>

<body>

<footer>
```

**Types:**

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Example:**

```
feat: Add support for Redis storage backend

- Implement RedisStorageContext
- Add Redis-specific data and meta stores
- Include integration tests

Closes #45
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=AerospikeDeDuperTest

# Run with coverage report
mvn verify
```

Integration tests require Docker to be running for Aerospike containers.

### Code Coverage

We maintain a minimum code coverage threshold. Run the following to generate a coverage report:

```bash
mvn jacoco:report
```

The report will be available at `target/site/jacoco/index.html`.

## Style Guidelines

### Java Code Style

- Follow standard Java naming conventions
- Use 4 spaces for indentation (no tabs)
- Maximum line length: 120 characters
- Use meaningful variable and method names
- Add Javadoc for public APIs

### Code Organization

```
src/
├── main/java/com/phonepe/solus/
│   ├── commands/          # Command interfaces and implementations
│   ├── config/            # Configuration classes
│   ├── exception/         # Custom exceptions
│   ├── filter/            # Bloom filter implementations
│   ├── hbase/             # HBase-specific code
│   ├── shard/             # Sharding logic
│   ├── store/             # Storage backends
│   └── util/              # Utility classes
└── test/java/com/phonepe/solus/
    └── ...                # Test classes mirror main structure
```

### Lombok Usage

We use Lombok to reduce boilerplate. Common annotations:

- `@Data` - Generates getters, setters, equals, hashCode, toString
- `@Builder` - Generates builder pattern
- `@Slf4j` - Generates logger field

### Documentation

- Add Javadoc to all public classes and methods
- Update README.md for user-facing changes
- Include code examples where helpful

## Adding New Storage Backends

To add support for a new storage backend:

1. Create a new `StorageContext` implementation:
   ```java
   public class NewStorageContext extends StorageContext {
       // Implementation
   }
   ```

2. Implement `IDeDuperDataStore` and `IDeDuperMetaStore` interfaces

3. Update the visitor pattern in `StorageContext.Visitor`

4. Add comprehensive tests

5. Update documentation

## Community

### Getting Help

- **GitHub Issues** - For bugs and feature requests
- **Discussions** - For questions and general discussion

### Recognition

Contributors will be recognized in:

- Release notes
- CONTRIBUTORS.md file
- GitHub contributors page

## License

By contributing to Solus, you agree that your contributions will be licensed under the Apache License 2.0.

---

Thank you for contributing to Solus!
