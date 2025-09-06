package xyz.tcheeric.cashu.wallet.proto.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.tcheeric.cashu.common.BlindSignature;
import xyz.tcheeric.cashu.common.PublicKey;
import xyz.tcheeric.cashu.common.Secret;
import xyz.tcheeric.cashu.common.Signature;
import xyz.tcheeric.cashu.wallet.proto.service.BDHKEUtilsService;

import java.math.BigInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnblindSignatureTaskIT {

    @Mock
    private BDHKEUtilsService utilsService;
    @Mock
    private BlindSignature blindSignature;
    @Mock
    private PublicKey publicKey;
    @Mock
    private Signature blindedSignature;
    @Mock
    private Secret secret;

    @Test
    void executeUsesService() {
        when(blindSignature.getBlindedSignature()).thenReturn(blindedSignature);
        when(blindedSignature.getBytes()).thenReturn(new byte[] {1});
        when(publicKey.getBytes()).thenReturn(new byte[] {2});
        when(utilsService.unblindSignature(any(), any(), any())).thenReturn(new byte[] {3});

        UnblindSignatureTask<Secret> task = new UnblindSignatureTask<>(blindSignature, BigInteger.ONE, publicKey, secret, utilsService);
        task.execute();

        verify(utilsService, times(1)).unblindSignature(any(), any(), any());
    }
}
