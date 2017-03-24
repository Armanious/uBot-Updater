package updater;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.GZIPInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.armanious.io.IOUtils;

public class RunescapeInnerFiles {

	private static final int[] g = new int[Byte.MAX_VALUE + 1];
	static
	{
		int var0;
		for (var0 = 0; g.length > var0; var0++) {
			g[var0] = -1;
		}

		for (var0 = 65; 90 >= var0; var0++) {
			g[var0] = (var0 - 65);
		}

		for (var0 = 97; var0 <= 122; var0++) {
			g[var0] = (var0 - 71);
		}

		for (var0 = 48; var0 <= 57; var0++) {
			g[var0] = (4 + var0);
		}

		int[] var2 = g;
		g[43] = 62;
		var2[42] = 62;
		g[47] = 63;
		int[] var1 = g;
		var1[45] = 63;
	}
	
	public static JarFile get() throws IOException {


		long start = System.currentTimeMillis();

		final Map<String, String> parameters = new HashMap<>();
		final Map<String, String> nonParams = new HashMap<>();

		final URL documentBase = new URL("http://world2.runescape.com");

		final String[] lines = new String(IOUtils.readURL(new URL(documentBase + "/jav_config.ws"))).split("\n");
		for (final String line : lines) {
			if (line.isEmpty())
				continue;
			final String[] parts = line.split("=");
			if (line.startsWith("msg")) {

			} else if (line.startsWith("param")) {
				parameters.put(parts[1], parts.length == 2 ? "" : parts[2]);
			} else {
				nonParams.put(parts[0], parts.length == 1 ? "" : parts[1]);
			}
		}

		final URL codeBase = new URL(documentBase + "/" + nonParams.get("initial_jar"));

		final JarFile jar = ((JarURLConnection) new URL("jar:" + codeBase + "!/").openConnection()).getJarFile();
		System.out.println("Got jar file loaded in memory");
		int var27 = 0;
		try
		{
			//k += 1;
			String var3 = parameters.get("0");
			int var4 = var3.length();
			byte[] var2;
			int var5 = 0xFFFFFFFC & 3 + var4;
			int var6 = var5 / 4 * 3;
			if (var5 - 2 < var4 && g[var3.charAt(var5 - 2) ^ 0xFFFFFFFF] != 0) {
				if (var5 - 1 >= var4){
					var6--;
				}
			}else{
				var6 -= 2;
			}

			byte[] var7 = new byte[var6];
			int var8 = 0;
			var2 = var7;
			int var9 = var3.length();
			int var10 = 0;

			while (var10 < var9) {
				int var11 = g[var3.charAt(var10)];
				int var12 = var9 <= var10 + 1 ? -1 : g[var3.charAt(var10 + 1)];
				int var13 = var10 + 2 < var9 ? g[var3.charAt(2 + var10)] : -1;
				int var14 = 3 + var10 < var9 ? g[var3.charAt(3 + var10)] : -1;
				var7[(var8++)] = (byte)(var11 << 2 | var12 >>> 4);
				if (var13 == -1){
					break;
				}
				var7[(var8++)] = (byte)((var12 & 0xF) << 4 | var13 >>> 2);
				if ((var14 ^ 0xFFFFFFFF) == 0){
					break;
				}
				var7[(var8++)] = (byte)((0x3 & var13) << 6 | var14);
				var10 += 4;
				if (var27 != 0){
					break;
				}
			}

			String var43 = parameters.get("-1");
			int var42 = var43.length();
			byte[] var41;
			if (var42 == 0) {
				var41 = new byte[0];
			}
			else
			{
				var8 = 0xFFFFFFFC & 3 + var42;
				var9 = 3 * (var8 / 4);
				if ((var42 <= var8 - 2) || ((g[var43.charAt(var8 - 2)] ^ 0xFFFFFFFF) == 0)){
					var9 -= 2;
					if (var27 == 0);
				}
				else if ((var42 <= var8 - 1)) {
					var9--;
				}

				int var11 = 0;
				var41 = new byte[var9];
				int var12 = var43.length();
				int var13 = 0;

				while (var12 > var13) {
					int var14 = g[var43.charAt(var13)];
					int streamIndex = 1 + var13 >= var12 ? -1 : g[var43.charAt(1 + var13)];
					int bytesRead = var12 > var13 + 2 ? g[var43.charAt(var13 + 2)] : -1;
					int var17 = var12 <= var13 + 3 ? -1 : g[var43.charAt(3 + var13)];
					var41[(var11++)] = (byte)(streamIndex >>> 4 | var14 << 2);
					if (bytesRead == -1)
					{
						break;
					}
					var41[(var11++)] = (byte)(0xF0 & streamIndex << 4 | bytesRead >>> 2);
					if (((var17 ^ 0xFFFFFFFF) == 0) && (var27 == 0))
					{
						break;
					}
					var41[(var11++)] = (byte)(bytesRead << 6 & 0xC0 | var17);
					var13 += 4;
					if (var27 != 0)
					{
						break;
					}
				}
			}

			final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(var2, "AES"), new IvParameterSpec(var41));


			InputStream encryptedClient = jar.getInputStream(jar.getEntry("inner.pack.gz"));
			assert encryptedClient != null;

			System.out.println("Decrypting inner.pack.gz...");

			Pack200.Unpacker jarUnpacker = Pack200.newUnpacker();
			File file = new File("C:\\Users\\David\\Desktop\\runescape_decrypted.jar");
			try
			{
				FileOutputStream outputStream = new FileOutputStream(file);
				JarOutputStream decryptedClient = new JarOutputStream(outputStream);
				GZIPInputStream zipClient = new GZIPInputStream(new ByteArrayInputStream(cipher.doFinal(IOUtils.readAllBytes(encryptedClient))));
				jarUnpacker.unpack(zipClient, decryptedClient);
				decryptedClient.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("Decrypted and saved inner.pack.gz in : " + (System.currentTimeMillis() - start) + "ms");
			return new JarFile(file);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
