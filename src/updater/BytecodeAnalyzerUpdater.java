package updater;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.armanious.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import updater.relationships.RelationshipSet;
import updater.relationships.RelationshipViewer;
import updater.stages.ExceptionRemoverStage;

public class BytecodeAnalyzerUpdater {

	private final ClassNode[] classNodes;
	private final RelationshipSet relationships;

	private BytecodeAnalyzerUpdater(byte[][] classes){
		classNodes = new ClassNode[classes.length];
		relationships = new RelationshipSet();
		System.out.print("Loaded classes: ");
		for(int i = 0; i < classes.length; i++){
			final ClassReader cr = new ClassReader(classes[i]);
			System.out.print(cr.getClassName() + ',');
			cr.accept(classNodes[i] = new ClassNode(), 0);
			relationships.add(classNodes[i]);
		}
		System.out.println();
	}

	public void update(){
		ExceptionRemoverStage.removeAllExceptions(classNodes);

		SwingUtilities.invokeLater(new Runnable(){
			@Override
			public void run() {
				System.out.println("Displaying hooks data");
				final JFrame frame = new RelationshipViewer(relationships, true);/*new RelationshipViewer(relationships, false, new Filter<FieldNode>(){
						@Override
						public boolean accept(FieldNode fn){
							return hooks.containsValue(fn.toString().split(" ")[0]);
						}
					});*/
				frame.setTitle("Hooks Viewer");
				frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				frame.setVisible(true);
			}
		});
	}

	public RelationshipSet getRelationshipSet(){
		return relationships;
	}

	public ClassNode[] getClassNodes(){
		return classNodes;
	}

	public void save(final File parentFile) throws IOException {
		if(!parentFile.exists())
			parentFile.mkdirs();
		else
			deleteAll(parentFile.listFiles());
		for(ClassNode cn : classNodes){
			final ClassWriter cw = new ClassWriter(0);
			cn.accept(cw);
			final int idx = cn.name.lastIndexOf('/');
			if(idx != -1){
				new File(parentFile, cn.name.substring(0, idx)).mkdirs();
			}
			IOUtils.writeToFile(cw.toByteArray(), new File(parentFile, cn.name + ".class"));
		}
	}

	private static void deleteAll(File[] files){
		for(File file : files){
			if(file.isDirectory()){
				deleteAll(file.listFiles());
			}else{
				file.delete();
			}
		}
	}

	public static BytecodeAnalyzerUpdater loadFromParentFile(File file) throws IOException {
		final ArrayList<byte[]> list = new ArrayList<>();
		for(String child : file.list()){
			if(child.endsWith(".class")){
				final File f = new File(file, child);
				if(!f.isDirectory()){
					list.add(IOUtils.readFile(f));
				}
			}
		}
		return new BytecodeAnalyzerUpdater(list.toArray(new byte[list.size()][]));
	}

	public static BytecodeAnalyzerUpdater loadFromJarFile(JarFile jar) throws IOException {
		final ArrayList<byte[]> list = new ArrayList<>();
		final Enumeration<JarEntry> entries = jar.entries();
		while(entries.hasMoreElements()){
			final JarEntry entry = entries.nextElement();
			if(entry.getName().endsWith(".class")){
				list.add(IOUtils.readAllBytes(jar.getInputStream(entry)));
			}
		}
		return new BytecodeAnalyzerUpdater(list.toArray(new byte[list.size()][]));
	}

}
