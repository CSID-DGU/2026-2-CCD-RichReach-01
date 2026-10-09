package com.richreach.codef.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.codef.config.CodefProperties;
import com.richreach.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
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

    private CodefRsaEncryptor encryptor(String publicKey) {
        CodefProperties properties = new CodefProperties("id", "secret", publicKey, "https://oauth.codef.io",
            "https://development.codef.io", Duration.ofSeconds(3), Duration.ofSeconds(10),
            Duration.ofSeconds(120), Duration.ofHours(1));
        return new CodefRsaEncryptor(properties);
    }

    private String base64PublicKey() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    private String decrypt(String encrypted) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("공개키로 암호화하면 개인키로 복호화했을 때 원문이 나온다 (Base64 결과)")
    void encryptAndDecrypt() throws Exception {
        String encrypted = encryptor(base64PublicKey()).encrypt("my-password-1!");

        assertThat(encrypted).isNotEqualTo("my-password-1!");
        assertThat(decrypt(encrypted)).isEqualTo("my-password-1!");
    }

    @Test
    @DisplayName("PEM 머리말이나 줄바꿈이 섞인 공개키도 처리한다")
    void acceptsPemFormattedKey() throws Exception {
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + base64PublicKey().replaceAll("(.{64})", "$1\n")
            + "\n-----END PUBLIC KEY-----";

        String encrypted = encryptor(pem).encrypt("pw");

        assertThat(decrypt(encrypted)).isEqualTo("pw");
    }

    @Test
    @DisplayName("같은 평문도 암호화할 때마다 결과가 달라진다 (패딩)")
    void resultIsRandomized() {
        CodefRsaEncryptor encryptor = encryptor(base64PublicKey());

        assertThat(encryptor.encrypt("pw")).isNotEqualTo(encryptor.encrypt("pw"));
    }

    @Test
    @DisplayName("공개키가 설정되지 않았으면 안내 오류를 던진다")
    void missingKey() {
        assertThatThrownBy(() -> encryptor("").encrypt("pw"))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getMessage()).contains("CODEF_PUBLIC_KEY"));
    }

    @Test
    @DisplayName("공개키 형식이 잘못되면 평문을 노출하지 않고 오류를 던진다")
    void invalidKey() {
        assertThatThrownBy(() -> encryptor("not-a-valid-key").encrypt("secret-plain"))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getMessage()).doesNotContain("secret-plain"));
    }

}
