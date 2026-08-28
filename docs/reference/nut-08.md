# NUT-08 Lightning Fee Return

Describes how cashu-wallet recovers the unspent part of the Lightning fee reserve when
melting tokens, and the wallet-side types involved.

## Why the fee reserve comes back

Lightning routing fees cannot be known before the payment is attempted, so a mint quotes a
`fee_reserve` and the wallet funds `amount + fee_reserve`. When the actual routing fee is
lower, the difference belongs to the wallet. NUT-08 is the mechanism that returns it: the
wallet sends *blank outputs* with the melt, and the mint imprints the overpaid amount onto
them before signing.

A wallet that sends no blank outputs receives no change and silently forfeits the
difference on every melt.

## Blank output count

`BlankOutputBuilder.calculateBlankOutputCount(feeReserve)` implements the spec formula:

| Fee reserve | Blank outputs |
| --- | --- |
| 0 | 0 |
| 1 | 1 |
| 2 | 1 |
| 3 | 2 |
| 1000 | 10 |

That is `max(ceil(log2(fee_reserve)), 1)`, and zero when the reserve is zero. The count is
sufficient because any change amount below the reserve is a sum of distinct powers of two.

Each blank output is a `BlindedMessage` with `amount` set to `0`; per the spec the mint
ignores that value and imprints its own.

## Wallet flow

`WalletMeltServiceImpl.melt(...)` performs the whole sequence:

1. Derive `n_blank_outputs` deterministic secrets and blinding factors (NUT-13) from the
   wallet master key, starting at the supplied counter.
2. Blind them into zero-amount blank outputs via `BlankOutputBuilder`.
3. Post `PostMeltRequest` with `quote`, `inputs`, and `outputs` to `/v1/melt/{method}`.
4. Unblind the returned `change` via `MeltChangeService` into spendable proofs.
5. Zero the blinding factors and report the next counter to derive from.

Because the blank output secrets are deterministic, the change is also recoverable from the
mnemonic alone if the wallet is lost between the melt and the next save.

## Matching change to blank outputs

The mint omits zero-value signatures, so the returned `change` list is usually shorter than
the blank output list. The spec requires the surviving signatures to keep their original
relative order, so `DefaultMeltChangeService` matches them positionally against the leading
secrets. More signatures than blank outputs is a protocol violation and is rejected rather
than partially applied.

## Reference

- `xyz.tcheeric.cashu.wallet.proto.builders.BlankOutputBuilder`
- `xyz.tcheeric.cashu.wallet.proto.service.MeltChangeService`
- `xyz.tcheeric.cashu.wallet.client.service.WalletMeltService`
- `xyz.tcheeric.cashu.wallet.client.service.MeltResult`
- [NUT-08](https://github.com/cashubtc/nuts/blob/main/08.md), [NUT-05](https://github.com/cashubtc/nuts/blob/main/05.md)
