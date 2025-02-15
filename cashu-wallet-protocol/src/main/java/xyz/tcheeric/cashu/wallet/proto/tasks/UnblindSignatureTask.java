package xyz.tcheeric.cashu.wallet.proto.tasks;

import xyz.tcheeric.cashu.common.annotation.Nut;
import xyz.tcheeric.cashu.common.model.BlindSignature;
import xyz.tcheeric.cashu.common.model.Proof;
import xyz.tcheeric.cashu.common.model.PublicKey;
import xyz.tcheeric.cashu.common.model.Secret;
import xyz.tcheeric.cashu.common.util.Task;
import xyz.tcheeric.cashu.wallet.proto.nut.NUT04;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.extern.java.Log;

import java.math.BigInteger;

@Nut(4)
@Log
@AllArgsConstructor
@ToString
public class UnblindSignatureTask<T extends Secret> implements Task<Proof<T>> {

    private final BlindSignature blindSignature;
    private final BigInteger r;
    private final PublicKey K;
    private final T secret;

    @Override
    public Proof<T> execute() {
        return NUT04.unblindingSignature(blindSignature, r, K, secret);
    }
}
