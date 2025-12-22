# How to build and test

Step-by-step instructions to build and verify cashu-wallet locally.

## Prerequisites
- Java 21 (Temurin recommended)
- Maven 3.9+
- Access to the configured repositories (see root `pom.xml`)

## Build and test everything
```bash
mvn -q verify
```
Runs unit + integration tests for all modules and aggregates JaCoCo reports.

## Build a single module
```bash
mvn -q -pl cashu-wallet-protocol -am verify
```
Use `-pl <module> -am` to include dependencies.

## Common test filters
- Run only unit tests:
  ```bash
  mvn -q -DskipITs verify
  ```
- Run a single test class:
  ```bash
  mvn -q -Dtest=ProofRecoveryServiceTest test
  ```

## Troubleshooting
- SLF4J multiple bindings warnings: ensure only one slf4j provider on the classpath when running custom test setups.
- Networked tests: current suite is offline; ensure no corporate proxy interferes with Maven downloads.
