package cashu.wallet.proto.tasks;

import cashu.common.annotation.Nut;
import cashu.common.model.BlindSignature;
import cashu.common.model.Proof;
import cashu.common.model.PublicKey;
import cashu.common.model.Secret;
import cashu.common.util.Task;
import cashu.wallet.proto.nut.NUT04;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.extern.java.Log;

import java.math.BigInteger;

@Nut(4)
@Log
@AllArgsConstructor
@ToString
public class UnblindSignatureTask implements Task<Proof> {

    private final BlindSignature blindSignature;
    private final BigInteger r;
    private final PublicKey K;
    private final Secret secret;

    @Override
    public Proof execute() {
        //log.log(Level.INFO, "UnblindSsignature: {0}", this);
        return NUT04.unblindingSignature(blindSignature, r, K, secret);
    }
}
