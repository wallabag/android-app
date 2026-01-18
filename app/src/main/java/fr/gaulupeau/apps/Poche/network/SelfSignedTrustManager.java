package fr.gaulupeau.apps.Poche.network;

import javax.net.ssl.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class SelfSignedTrustManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // Optionnel : implémenter selon tes besoins
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null || chain.length == 0) throw new IllegalArgumentException("Empty or null certificate chain");
        X509Certificate cert = chain[0];
        // Vérifie si le certificat est auto-signé
        if (isSelfSigned(cert)) {
           return;
        }
        // accepter le certificat s'il est valide
        cert.checkValidity();
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    // Vérifie si le certificat est auto-signé
    private boolean isSelfSigned(X509Certificate cert) {
        try {
            // Un certificat est auto-signé si l’émetteur et le sujet sont identiques et si la signature est valide avec sa propre clé publique
            cert.verify(cert.getPublicKey());
            return cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal());
        } catch (Exception e) {
            return false;
        }
    }
}
