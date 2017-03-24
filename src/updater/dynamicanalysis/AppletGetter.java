package updater.dynamicanalysis;


import java.applet.Applet;
import java.applet.AppletContext;
import java.applet.AppletStub;
import java.applet.AudioClip;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;

import org.armanious.io.IOUtils;

import sun.applet.AppletAudioClip;

public class AppletGetter {

	private AppletGetter(){}

	public static Applet getApplet() throws IOException {
		final Map<String, InputStream> streams = new HashMap<>();
		final Map<String, String> parameters = new HashMap<>();
		final Map<String, String> nonParams = new HashMap<>();
		final Map<String, String> messages = new HashMap<>();

		final URL documentBase = new URL("http://world2.runescape.com");
		System.out.println("Document base: " + documentBase);

		final URL codeBase;
		final String[] lines = new String(IOUtils.readURL(new URL(documentBase + "/jav_config.ws"))).split("\n");
		for (final String line : lines) {
			if (line.isEmpty())
				continue;
			final String[] parts = line.split("=");
			if (line.startsWith("msg")) {
				messages.put(parts[0], parts.length == 1 ? null : parts[1]);
			} else if (line.startsWith("param")) {
				parameters.put(parts[1], parts.length == 2 ? "" : parts[2]);
			} else {
				nonParams.put(parts[0], parts.length == 1 ? "" : parts[1]);
			}
		}

		System.out.println(parameters);
		System.out.println(nonParams);
		System.out.println(messages);


		codeBase = new URL(documentBase + "/" + nonParams.get("initial_jar"));

		try(final JarFile jar = ((JarURLConnection) new URL("jar:" + codeBase + "!/").openConnection()).getJarFile()){
			System.out.print("Got jar file loaded in memory: ");
			final Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				final JarEntry entry = entries.nextElement();
				try(final InputStream in = jar.getInputStream(entry)){
					final ByteArrayOutputStream out = new ByteArrayOutputStream();
					IOUtils.writeToStream(in, out);
					if(entry.getName().equals("inner.pack.gz")){
						try{
							final byte[] d = MessageDigest.getInstance("SHA-1").digest(out.toByteArray());
							for (int k : d)
								System.out.print(Integer.toString((k & 0xFF) + 256, 16).substring(1));
							System.out.println();
						}catch(Exception e){
							System.err.println("Could not generate hash");
							e.printStackTrace();
						}
					}
					streams.put(entry.getName(), new ByteArrayInputStream(out.toByteArray()));
				}
			}

			System.out.println("Loading instance");
			final JarClassLoader jcl = new JarClassLoader(streams);
			System.out.println("Got custom ClassLoader");
			Applet app = null;
			try {
				System.out.println("Creating new instance of applet");
				app = (Applet) jcl.loadClass(nonParams.get("initial_class").replace(".class", "")).newInstance();
				System.out.println("Done creating new instance of applet");
			} catch (final Exception e) {
				System.err.println("Error loading applet class or creating new instance");
				e.printStackTrace();
			}
			assert app != null;
			final Applet applet = app;
			applet.setStub(new AppletStub() {
				private final AppletContext context = new AppletContext() {
					@Override
					public InputStream getStream(final String key) {
						return streams.get(key);
					}

					@Override
					public Iterator<String> getStreamKeys() {
						return streams.keySet().iterator();
					}

					@Override
					public void setStream(final String key, final InputStream stream) throws IOException {
						streams.put(key, stream);
					}

					@Override
					public void showDocument(final URL url) {
						showDocument(url, null);
					}

					@Override
					public void showDocument(final URL url, final String target) {
						System.out.println("Attempting to show document: " + url + "\n\tTarget: " + target);
					}

					@Override
					public void showStatus(final String msgKey) {
						final String msg = messages.get(msgKey);
						JOptionPane.showMessageDialog(applet, msg == null ? msgKey : msg);
					}

					@Override
					public Applet getApplet(final String name) {
						if (applet.getName().equals(name))
							return applet;
						return null;
					}

					@Override
					public Enumeration<Applet> getApplets() {
						return new Enumeration<Applet>() {
							private boolean shown;

							@Override
							public boolean hasMoreElements() {
								return !shown;
							}

							@Override
							public Applet nextElement() {
								if (shown)
									return null;
								shown = true;
								return applet;
							}
						};
					}

					@Override
					public AudioClip getAudioClip(final URL url) {
						return new AppletAudioClip(url);
					}

					@Override
					public Image getImage(final URL url) {
						try {
							return ImageIO.read(url);
						} catch (final IOException e) {
							e.printStackTrace();
							return null;
						}
					}
				};

				@Override
				public void appletResize(final int width, final int height) {
					// TODO
				}

				@Override
				public AppletContext getAppletContext() {
					return context;
				}

				@Override
				public URL getCodeBase() {
					return codeBase;
				}

				@Override
				public URL getDocumentBase() {
					return documentBase;
				}

				@Override
				public String getParameter(final String name) {
					return parameters.get(name);
				}

				@Override
				public boolean isActive() {
					return true;// TODO
				}
			});
			System.out.println("Set stub");
			return applet;
		}
	}

}
