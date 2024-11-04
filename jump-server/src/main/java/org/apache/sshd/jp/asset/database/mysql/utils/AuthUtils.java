package org.apache.sshd.jp.asset.database.mysql.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.sshd.jp.asset.database.mysql.common.MysqlPasswordAlgorithmEnums;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class AuthUtils {

    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AuthDigestHolder {
        String password;
        byte[] nonce;
    }

    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AuthVerifyHolder {
        String password;
        byte[] nonce;
        byte[] scrambledPassword;
    }

    public static final Map<MysqlPasswordAlgorithmEnums, Function<AuthDigestHolder, byte[]>> digestAlgorithmMap = new HashMap<>() {{
        put(MysqlPasswordAlgorithmEnums.mysql_native_password, password -> {

            return null;
        });

        put(MysqlPasswordAlgorithmEnums.caching_sha2_password, holder -> {
            // Scramble - XOR(SHA256(password), SHA256(SHA256(SHA256(password)), Nonce))
            String password = holder.getPassword();
            byte[] nonce = holder.getNonce();
            byte[] sha256Password = DigestUtils.sha256(password);
            byte[] doubleSha256Password = DigestUtils.sha256(sha256Password);
            byte[] concat = new byte[doubleSha256Password.length + 20];
            System.arraycopy(doubleSha256Password, 0, concat, 0, doubleSha256Password.length);
            System.arraycopy(nonce, 0, concat, doubleSha256Password.length, 20);
            byte[] sha256Concat = DigestUtils.sha256(concat);

            byte[] scramble = new byte[doubleSha256Password.length];
            for (int i = 0; i < scramble.length; i++) {
                scramble[i] = (byte) (sha256Password[i] ^ sha256Concat[i]);
            }

            return scramble;
        });
    }};

    public static final Map<MysqlPasswordAlgorithmEnums, Function<AuthVerifyHolder, Boolean>> verifyAlgorithmMap = new HashMap<>() {{
        put(MysqlPasswordAlgorithmEnums.mysql_native_password, holder -> {
            byte[] scrambledPassword = holder.getScrambledPassword();
            String password = holder.getPassword();
            byte[] databaseStoredPassword = DigestUtils.sha1(DigestUtils.sha1(password));

            byte[] nonce = holder.getNonce();
            byte[] concat = new byte[databaseStoredPassword.length + nonce.length - 1];
            System.arraycopy(nonce, 0, concat, 0, nonce.length - 1);
            System.arraycopy(databaseStoredPassword, 0, concat, databaseStoredPassword.length, databaseStoredPassword.length);
            byte[] concatSha1 = DigestUtils.sha1(concat);

            byte[] reverseXorSha1Password = new byte[concatSha1.length];
            for (int i = 0; i < reverseXorSha1Password.length; i++) {
                reverseXorSha1Password[i] = (byte) (scrambledPassword[i] ^ concatSha1[i]);
            }

            return Arrays.equals(DigestUtils.sha1(reverseXorSha1Password), databaseStoredPassword);
        });

        put(MysqlPasswordAlgorithmEnums.caching_sha2_password, holder -> {
            // Scramble - XOR(SHA256(password), SHA256(SHA256(SHA256(password)), Nonce))
            byte[] scrambledPassword = holder.getScrambledPassword();
            String password = holder.getPassword();
            byte[] nonce = holder.getNonce();

            byte[] databaseStoredPassword = DigestUtils.sha256(DigestUtils.sha256(password));

            byte[] concat = new byte[databaseStoredPassword.length + 20];
            System.arraycopy(databaseStoredPassword, 0, concat, 0, databaseStoredPassword.length);
            System.arraycopy(nonce, 0, concat, databaseStoredPassword.length, 20);
            byte[] sha256Concat = DigestUtils.sha256(concat);

            byte[] reverseXorSha256Password = new byte[databaseStoredPassword.length];
            for (int i = 0; i < reverseXorSha256Password.length; i++) {
                reverseXorSha256Password[i] = (byte) (scrambledPassword[i] ^ sha256Concat[i]);
            }

            return Arrays.equals(DigestUtils.sha256(reverseXorSha256Password), databaseStoredPassword);
        });
    }};

    public static byte[] digestPassword(AuthDigestHolder holder, MysqlPasswordAlgorithmEnums algorithm) {
        Function<AuthDigestHolder, byte[]> function = digestAlgorithmMap.get(algorithm);
        if (Objects.nonNull(function)) {
            return function.apply(holder);
        }
        throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
    }

    public static boolean verifyPassword(MysqlPasswordAlgorithmEnums algorithm, AuthVerifyHolder holder) {
        Function<AuthVerifyHolder, Boolean> function = verifyAlgorithmMap.get(algorithm);
        if (Objects.nonNull(function)) {
            return function.apply(holder);
        }
        throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
    }

    public static void main(String[] args) {

        //
        byte[] nonce = {1, 117, 106, 112, 44, 18, 88, 115, 4, 79, 53, 96, 33, 63, 94, 97, 29, 1, 90, 57, 0};
        byte[] bytes = digestPassword(new AuthDigestHolder("12121122.", nonce), MysqlPasswordAlgorithmEnums.caching_sha2_password);

        AuthVerifyHolder authVerifyHolder = new AuthVerifyHolder("12121122.", nonce, bytes);
        boolean b = verifyPassword(MysqlPasswordAlgorithmEnums.caching_sha2_password, authVerifyHolder);
        System.out.println();

    }
}
