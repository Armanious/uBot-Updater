package updater.dynamicanalysis;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.armanious.io.IOUtils;
import org.objectweb.asm.ClassReader;

public class JarClassLoader extends ClassLoader {

	private final Map<String, InputStream> streams;

	protected JarClassLoader(Map<String, InputStream> entriesAsStreams) {
		this.streams = entriesAsStreams;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		if (streams.containsKey(name + ".class")) {
			byte[] data = null;
			try {
				data = IOUtils.readAllBytes(streams.get(name + ".class"));
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			if (data == null) {
				return super.findClass(name);
			}
			return super.defineClass(name, data, 0, data.length);
		}
		return super.findClass(name);
	}
	
	@Override
	public InputStream getResourceAsStream(String name) {
		final InputStream is = streams.get(name);
		return is != null ? is : super.getResourceAsStream(name);
	}

	private static final File PARENT_FILE = new File("C:\\Users\\David\\Desktop\\RSFiles");
	static {
		if(!PARENT_FILE.exists()){
			PARENT_FILE.mkdirs();
		}else{
			for(File child : PARENT_FILE.listFiles()){
				if(!child.isDirectory()){
					child.delete();
				}
			}
		}
	}

	public static void saveClass(byte[] data) throws IOException{
		if(data != null){
			final ClassReader cr = new ClassReader(data);
			IOUtils.writeToFile(data, new File(PARENT_FILE, cr.getClassName() + ".class"));
		}
	}

}
