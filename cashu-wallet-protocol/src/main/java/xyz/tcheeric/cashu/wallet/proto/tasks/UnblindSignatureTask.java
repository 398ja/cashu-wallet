package xyz.tcheeric.cashu.wallet.proto.tasks;

import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.extern.java.Log;
import xyz.tcheeric.cashu.common.BlindSignature;
import xyz.tcheeric.cashu.common.Proof;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.common.Signature;
import xyz.tcheeric.cashu.common.util.Task;
import xyz.tcheeric.cashu.crypto.BDHKEUtils;
import xyz.tcheeric.cashu.crypto.util.Utils;
import xyz.tcheeric.cashu.entities.annotation.Nut;
import xyz.tcheeric.cashu.wallet.proto.nut.NUT04;

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
        Signature C_ = blindSignature.getBlindedSignature();
        byte[] C = BDHKEUtils.unblindSignature(C_.getBytes(), Utils.bytesFromBigInteger(r), K.getBytes());
        return Proof.<T>builder().build().<T>builder().secret(secret).amount(blindSignature.getAmount()).keySetId(blindSignature.getKeySetId()).unblindedSignature(Signature.fromBytes(C)).build();
    }
}
