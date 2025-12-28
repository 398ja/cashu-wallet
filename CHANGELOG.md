# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

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
