package com.richreach.codef.client;

import com.richreach.codef.config.CodefProperties;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 카드사 비밀번호 같은 민감정보를 CODEF 공개키(RSA)로 암호화한다.
 * CODEF 요구 방식: X.509 공개키(Base64) + RSA(PKCS1 패딩) 암호화 후 Base64 인코딩.
 * 평문은 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class CodefRsaEncryptor {

    private static final String TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    private final CodefProperties properties;

    public CodefRsaEncryptor(CodefProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plainText) {
        if (!properties.hasPublicKey()) {
            // 설정 안내는 서버 로그에만 남긴다. 응답에는 내부 설정 이름을 내보내지 않는다.
            log.error("CODEF RSA 공개키가 설정되지 않았습니다. CODEF_PUBLIC_KEY를 확인하세요.");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        try {
            byte[] keyBytes = Base64.getMimeDecoder().decode(stripPemHeaders(properties.publicKey()));
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 예외 객체와 평문은 로그에 남기지 않는다.
            log.error("RSA 암호화에 실패했습니다. CODEF_PUBLIC_KEY 형식을 확인하세요. ({})", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /** PEM 형식(-----BEGIN ...-----)으로 복사해도 동작하도록 머리말과 공백을 제거한다. */
    private String stripPemHeaders(String key) {
        return key.replaceAll("-----(BEGIN|END)[A-Z ]*-----", "").replaceAll("\\s+", "");
    }

}
