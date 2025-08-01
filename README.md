# Cashu Wallet

This project contains an experimental Java wallet for the [Cashu](https://github.com/cashubtc/cashu) eCash system.  It focuses on the protocol around minting tokens as described in [NUT‑04](https://github.com/cashubtc/nuts/blob/main/04.md).

## Minting Flow

Minting new tokens is a two step process:

1. **Request a mint quote** using `POST /v1/mint/quote/{method}`.  The wallet specifies the payment method (e.g. `bolt11`) and the `unit` it wishes to mint.  The mint responds with a unique `quote` id and a payment `request`.
2. **Execute the quote** after paying the request.  The wallet calls `POST /v1/mint/{method}` with the `quote` id and the blinded `outputs` that sum to the requested amount.  The mint verifies the payment and returns blind signatures.

The wallet can check the state of a quote via `GET /v1/mint/quote/{method}/{quote_id}`.  Once the blind signatures are received, they are unblinded to produce spendable proofs.

The mint's info endpoint (see NUT‑06) exposes which method‑unit pairs are supported and their limits.  New payment methods follow this pattern by defining their specific request/response fields and providing the three endpoints above.

## Building

The project uses Java 21 and Maven.  Run tests with:

```bash
mvn -q verify
```

This will also generate code coverage reports using JaCoCo in `target/site/jacoco` for each module.

Note that the build requires additional Cashu libraries which are not included in this repository.
