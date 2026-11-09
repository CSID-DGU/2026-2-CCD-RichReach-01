package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.codef.config.CodefProperties;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import javax.crypto.Cipher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodefRsaEncryptorTest {

    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    private static String base64PublicKey() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    private static CodefRsaEncryptor encryptorWith(String publicKey) {
        return new CodefRsaEncryptor(new CodefProperties(null, null, publicKey, null, null, null, null, null, null));
    }

    private static String decrypt(String encrypted) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("암호화한 값은 개인키로 풀면 원래 값이 되고, 평문과는 다르다")
    void encryptsWithPublicKey() throws Exception {
        String encrypted = encryptorWith(base64PublicKey()).encrypt("test-card-password");

        assertThat(encrypted).isNotEqualTo("test-card-password");
        assertThat(decrypt(encrypted)).isEqualTo("test-card-password");
    }

    @Test
    @DisplayName("같은 값을 암호화해도 매번 다른 결과가 나온다(패딩이 무작위)")
    void producesDifferentCiphertextEachTime() {
        CodefRsaEncryptor encryptor = encryptorWith(base64PublicKey());

        assertThat(encryptor.encrypt("same")).isNotEqualTo(encryptor.encrypt("same"));
    }

    @Test
    @DisplayName("PEM 머리말과 줄바꿈이 있는 공개키도 사용할 수 있다")
    void acceptsPemFormat() throws Exception {
        String base64 = base64PublicKey();
        String pem = "-----BEGIN PUBLIC KEY-----\n" + base64.substring(0, 64) + "\n" + base64.substring(64)
            + "\n-----END PUBLIC KEY-----";

        assertThat(decrypt(encryptorWith(pem).encrypt("pw"))).isEqualTo("pw");
    }

    @Test
    @DisplayName("공개키가 없으면 500이고, 응답 메시지에 내부 설정 이름이 나오지 않는다")
    void failsWithoutPublicKeyAndHidesConfigName() {
        assertThatThrownBy(() -> encryptorWith("").encrypt("pw"))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
                assertThat(e.getMessage()).doesNotContain("CODEF_PUBLIC_KEY").doesNotContain(".env");
            });
    }

    @Test
    @DisplayName("공개키 형식이 잘못되면 500이고, 응답 메시지에 평문과 설정 이름이 나오지 않는다")
    void failsWithInvalidKeyAndHidesPlainText() {
        assertThatThrownBy(() -> encryptorWith("not-a-key").encrypt("secret-card-password"))
            .isInstanceOfSatisfying(BusinessException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
                assertThat(e.getMessage()).doesNotContain("secret-card-password").doesNotContain("CODEF_PUBLIC_KEY");
            });
    }

}
