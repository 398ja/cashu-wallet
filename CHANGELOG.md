# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

---

## [0.5.0] - 2026-01-21

### Added

- **Virtual Thread Support**: Full Virtual Thread (Project Loom) support for Java 21+
  - `VirtualThreadExecutors` utility class for creating VT-based executors
  - Environment variable toggle (`CASHU_WALLET_VIRTUAL_THREADS_ENABLED`) for VT configuration
  - Virtual Threads enabled by default for improved concurrency
  - VT compatibility documentation in `docs/explanation/virtual-thread-compatibility.md`
  - CI pinning detection with `-Djdk.tracePinnedThreads=short`
  - README section on Virtual Thread compatibility
- **Load Testing Scripts**: k6 load test scripts for Virtual Thread baseline metrics
  - `scripts/baseline-metrics.js` for baseline performance measurement
  - `scripts/load-test-wallet.js` for comprehensive load testing
- **Loom Documentation**: Comprehensive Virtual Thread rollout documentation
  - Baseline performance results (`docs/loom/baseline-results.md`)
  - Pilot comparison results (`docs/loom/pilot-results.md`)
  - JFR analysis confirming no VT pinning (`docs/loom/jfr-analysis.md`)
  - Staged production rollout plan (`docs/loom/rollout-plan.md`)
  - Production results summary (`docs/loom/production-results.md`)

### Changed

- Updated cashu-lib dependency from 0.11.1 to 0.12.0 (VT-ready with @ThreadSafe annotations)
- `ParallelRecoveryService` now uses `VirtualThreadExecutors` for improved concurrency

---

## [0.4.4] - 2026-01-10

### Changed

- Relocated `ProofRecoveryServiceImpl` to `impl` package
- Relocated `BDHKEUtilsServiceImpl` to `impl` package
- Updated cashu-lib dependency from 0.10.0 to 0.11.1
- Updated cashu-voucher dependency from 0.4.0 to 0.5.0

---

## [0.4.3] - 2026-01-07

### Changed

- Updated cashu-lib dependency from 0.9.1 to 0.10.0
- Updated cashu-voucher dependency from 0.3.7 to 0.4.0
- Expanded Clean Code and Clean Architecture guidelines in AGENTS.md

---

## [0.4.2] - 2025-12-28

### Changed

- Updated cashu-voucher dependency from 0.3.6 to 0.3.7

---

## [0.4.1] - 2025-12-23

### Changed

- Updated cashu-voucher dependency from 0.3.5 to 0.3.6

---

## [0.4.0] - 2025-12-22
### Added
- Completed NUT-12 wallet-side verification with a DLEQ verification service and default implementation for blind signatures and received proofs.
- Integrated DLEQ attachment into proof recovery, including validation of proofs with originating blinding factors.
- Added unit coverage for DLEQ verification flows and proof recovery DLEQ hooks.
- Added NUT-07 `/checkstate` integration in proof recovery to filter out spent proofs using mint-reported states.
