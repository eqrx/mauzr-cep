package net.eqrx.mauzr.cep;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;

/**
 * TLS crutch for java.
 *
 * @author Alexander Sowitzki
 */
public class TLS {
	/**
	 * Create {@link SocketFactory} for TLS.
	 * 
	 * @param caFile CA file path
	 * @return The factory
	 * @throws IOException IO and parsing errors
	 */
	public static SSLSocketFactory getSocketFactory(final String caFile) throws IOException {
		// Setup Bouncy Castle
		Security.addProvider(new BouncyCastleProvider());

		// Get CA from file
		PEMReader reader = new PEMReader(
				new InputStreamReader(new ByteArrayInputStream(Files.readAllBytes(Paths.get(caFile)))));
		X509Certificate caCert = (X509Certificate) reader.readObject();
		reader.close();

		try {
			// Get keystore
			KeyStore caKs = KeyStore.getInstance(KeyStore.getDefaultType());
			caKs.load(null, null);
			// Put CA in
			caKs.setCertificateEntry("ca-certificate", caCert);
			// Create trust factory and put keystore in it
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(caKs);

			// Fetch TLS context
			SSLContext context = SSLContext.getInstance("TLSv1.2");
			// Put trust manager in
			context.init(null, tmf.getTrustManagers(), null);
			// Wow, a factory
			return context.getSocketFactory();
		} catch (KeyManagementException | NoSuchAlgorithmException | CertificateException | KeyStoreException e) {
			// Don't care that much about the specififc error
			throw new IOException(e);
		}
	}
}