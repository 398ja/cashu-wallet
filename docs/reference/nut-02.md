# NUT-02 Keysets and Fees

How the wallet handles the two MUST-level obligations NUT-02 places on it, and the SHOULD that
keeps value from being stranded.

## The rule the mint enforces

A mint checks one equation on every transaction that spends ecash:

```
sum(inputs) - fees == sum(outputs)
```

Get it wrong in either direction and the swap is refused with `transaction_not_balanced`. Asking
for too much is the mint giving away value; asking for too little is the wallet burning its own.
There is no tolerance, so the wallet's arithmetic has to match the mint's exactly.

The fee is charged per input, not per amount:

```
fee = (sum(input_fee_ppk of each input's keyset) + 999) / 1000
```

Each input is priced against the keyset that issued it, which need not be the keyset the outputs
are built on. A swap that drains a rotated keyset mixes two rates in one request.

## What the wallet does

| Concern | Type |
| --- | --- |
| Which keysets are active, and what each charges | `KeysetCache` |
| Keeping that view current from `GET /v1/keysets` | `KeysetDirectory` |
| Pricing a set of inputs | `WalletFeeCalculator` |
| Choosing which proofs to spend | `InactiveFirstProofSelector` |
| Assembling a balanced transaction | `SwapPlanner`, `SwapPlan` |

### Fees come out of the outputs

`SwapPlanner.planSwap` prices the chosen inputs, subtracts the fee from the total, and splits
what is left into denominations. The result is a `SwapPlan` whose `isBalanced()` states the
mint's equation directly.

```java
KeysetDirectory directory = new KeysetDirectory(mintUrl);
directory.refresh();

SwapPlanner planner = new SwapPlanner(directory.getKeysetCache());
SwapPlan<DeterministicSecret> plan = planner.planSwap(proofs, "sat", denominations);

// 16 sats in, 2 of fee on a keyset priced at 1000 ppk, 14 sats of outputs.
plan.getOutputAmounts();   // [8, 4, 2]
```

The arithmetic itself is `cashu-lib`'s `InputFeeCalculator`, the same class the mint's
`VerifyFeesTask` uses. A wallet that reimplements the rounding rule is a wallet that eventually
disagrees with the mint.

### Outputs only against an active keyset

`SwapPlanner.activeKeysetFor(unit)` picks the keyset outputs are addressed to, and fails when the
mint publishes no active keyset for that unit. There is no fallback to a cached inactive keyset:
a mint refuses to sign against one, so falling back only turns a clear failure into a confusing
one.

A keyset the wallet has never seen is treated as inactive rather than assumed usable.

### Inputs from inactive keysets first

NUT-02 keeps a rotated keyset's proofs spendable but never issues new ones against it. Value left
there moves only when a wallet deliberately spends it, so `InactiveFirstProofSelector` sorts
inactive-keyset proofs to the front of every selection. A rotation then drains over ordinary
traffic rather than waiting on a consolidation run that may never happen.

Within each group, proofs are taken largest first. Fewer inputs means a smaller fee, since the fee
is per input.

### Unknown keysets are a hard failure

An input naming a keyset the cache does not hold cannot be priced. Assuming zero would build an
unbalanced request whose rejection names the balance, not the missing keyset, so
`WalletFeeCalculator` throws instead. The remedy is a `KeysetDirectory.refresh()`.

## Refreshing

`refresh()` replaces the cached listing wholesale rather than merging into it. This is what makes
a rotation visible: `active` flipping to false, or a keyset being withdrawn, are both expressed
only by their absence from, or altered state in, the new listing.

Refresh before building a transaction. A stale fee produces a request the mint refuses; a stale
`active` flag produces outputs it will not sign.

## References

- [NUT-02](https://github.com/cashubtc/nuts/blob/main/02.md)
- [NUT-03: Swap](https://github.com/cashubtc/nuts/blob/main/03.md)
