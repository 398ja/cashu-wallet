package xyz.tcheeric.cashu.wallet.proto.service.impl;

import xyz.tcheeric.cashu.crypto.BDHKEUtils;
import xyz.tcheeric.cashu.wallet.proto.service.BDHKEUtilsService;

public class BDHKEUtilsServiceImpl implements BDHKEUtilsService {
    @Override
    public byte[] unblindSignature(byte[] blindedSignature, byte[] r, byte[] publicKey) {
        return BDHKEUtils.unblindSignature(blindedSignature, r, publicKey);
    }
}
