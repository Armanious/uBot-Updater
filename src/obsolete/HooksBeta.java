package obsolete;

import updater.AppletGetter;
import updater.BytecodeAnalyzerUpdater;
import updater.RunescapeInnerFiles;
import updater.RuntimeUpdater;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;

import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.armanious.io.IOUtils;
import org.armanious.synchronization.DependentRunnable;

public class HooksBeta {

	public static void main(String...args){
		if(Premain.inst == null){
			System.err.println("Make sure to run the Jar File to indicate the premain class.");
			System.exit(1);
		}
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}
		new HooksBeta();
	}

	private final HooksFinderUtil hfu;
	
	private String currentSelection;

	private final JTextArea infoDetails;
	
	public HooksBeta() {
		try {
			hfu = new HooksFinderUtil(new RuntimeUpdater(AppletGetter.getApplet()), BytecodeAnalyzerUpdater.loadFromJarFile(RunescapeInnerFiles.get()));
		} catch (ReflectiveOperationException | IOException e) {
			throw new RuntimeException(e);
		}
		reloadAllHookFinders();

		JFrame frame = new JFrame("HooksBeta");

		JPanel listPanel = new JPanel(new BorderLayout());
		JList<String> list = new JList<>();
		list.setModel(new AbstractListModel<String>() {
			private static final long serialVersionUID = 7763893309508732179L;
			@Override
			public int getSize() {
				return finders.size();
			}
			@Override
			public String getElementAt(int index) {
				return finders.get(index).getClass().getSimpleName();
			}
		});
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener(lse -> showInformationFor(list.getSelectedValue()));
		listPanel.add(new JScrollPane(list), BorderLayout.CENTER);
		JButton reloadAllButton = new JButton("Reload all");
		reloadAllButton.addActionListener(ae -> reloadAllHookFinders());
		listPanel.add(reloadAllButton, BorderLayout.SOUTH);

		JPanel infoPanel = new JPanel(new BorderLayout());
		infoDetails = new JTextArea();
		//JPanel infoDetailsPanel = new JPanel(new GridBagLayout());
		infoPanel.add(new JScrollPane(infoDetails), BorderLayout.CENTER);
		JButton reloadCurrent = new JButton("Reload current");
		reloadCurrent.addActionListener(ae -> reloadHookFinder(finders.stream().filter(hf -> hf.getClass().getSimpleName().equals(currentSelection)).findFirst().get()));
		//reloadCurrent.setEnabled(false);
		infoPanel.add(reloadCurrent, BorderLayout.SOUTH);

		JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, infoPanel);
		frame.setContentPane(sp);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
	
	private void showInformationFor(String hookFinderName){
		currentSelection = hookFinderName;
		infoDetails.setText(hookFinderName);
	}

	private final ArrayList<HookFinder> finders = new ArrayList<>();
	private final ArrayList<DependentRunnable> runnables = new ArrayList<>();

	private void generateDependenciesNetwork(){
		runnables.clear();
		int len = finders.size();
		for(int i = 0; i < len; i++){
			runnables.add(new DependentRunnable(finders.get(i)));
		}

		for(int i = 0; i < len; i++){
			DependentRunnable dr = runnables.get(i);
			HookFinderAnnotations hfa = finders.get(i).getClass().getAnnotation(HookFinderAnnotations.class);
			if(hfa.requiredHooks() != null){
				requiredHooksLoop: for(String requiredHook : hfa.requiredHooks()){
					for(int j = 0; j < len; j++){
						HookFinderAnnotations annot = finders.get(j).getClass().getAnnotation(HookFinderAnnotations.class);
						if(annot.hookKeys() != null){
							for(String hook : annot.hookKeys()){
								if(requiredHook.equals(hook)){
									dr.addDependency(runnables.get(j));
									continue requiredHooksLoop;
								}
							}
						}
					}
				}
			}
			if(hfa.requiredWrappers() != null){
				requiredWrappersLoop: for(String requiredWrapper : hfa.requiredWrappers()){
					for(int j = 0; j < len; j++){
						HookFinderAnnotations annot = finders.get(j).getClass().getAnnotation(HookFinderAnnotations.class);
						if(annot.wrapperKeys() != null){
							for(String wrapper : annot.wrapperKeys()){
								if(requiredWrapper.equals(wrapper)){
									dr.addDependency(runnables.get(j));
									continue requiredWrappersLoop;
								}
							}
						}
					}
				}
			}
			if(hfa.requiredMultipliers() != null){
				requiredMultipliersLoop: for(String requiredMultiplier : hfa.requiredMultipliers()){
					for(int j = 0; j < len; j++){
						HookFinderAnnotations annot = finders.get(j).getClass().getAnnotation(HookFinderAnnotations.class);
						if(annot.multiplierKeys() != null){
							for(String multiplier : annot.multiplierKeys()){
								if(requiredMultiplier.equals(multiplier)){
									dr.addDependency(runnables.get(j));
									continue requiredMultipliersLoop;
								}
							}
						}
					}
				}
			}
		}

		//checking for dead-lock conditions
		for(int i = 0; i < len; i++){
			DependentRunnable dr = runnables.get(i);
			for(DependentRunnable dependency : dr.getDependencies()){
				for(DependentRunnable dependencysDependency : dependency.getDependencies()){
					if(dependencysDependency == dr){
						throw new RuntimeException("Dead-lock condition with runnable: " + finders.get(i).getClass().getSimpleName());
					}
				}
			}
		}
		
		for(DependentRunnable dr : runnables){
			dr.getAssociatedThread().start();
		}
		
	}

	private void reloadAllHookFinders() {
		System.out.println("Reloading all hook finders.");
		try{
			for(DependentRunnable runnable : runnables){
				runnable.getAssociatedThread().interrupt();
			}

			File[] hookFinderFiles = new File("C:\\Users\\David\\workspace\\uBot Updater\\bin\\hooksbeta\\hookfinders\\").listFiles();
			ArrayList<ClassDefinition> redefs = new ArrayList<>();
			for(int i = 0; i < hookFinderFiles.length; i++){
				if(hookFinderFiles[i].getName().indexOf('$') != -1){
					continue;
				}
				byte[] data = IOUtils.readFile(hookFinderFiles[i]);
				Class<?> clazz;
				try{
					clazz = ClassLoader.getSystemClassLoader().loadClass("hooksbeta.hookfinders." + hookFinderFiles[i].getName().replace(".class", ""));
				}catch(Exception e){
					Method m = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
					m.setAccessible(true);
					clazz = (Class<?>) m.invoke(HookFinder.class.getClassLoader(), "hooksbeta.hookfinders." + hookFinderFiles[i].getName().replace(".class", ""), data, 0, data.length);
				}
				redefs.add(new ClassDefinition(clazz, data));
			}
			Premain.inst.redefineClasses(redefs.toArray(new ClassDefinition[redefs.size()]));

			redefLoop: for(ClassDefinition redef : redefs){
				for(HookFinder finder : finders){
					if(finder.getClass() == redef.getDefinitionClass()){
						continue redefLoop;
					}
				}
				final HookFinder hf = (HookFinder) redef.getDefinitionClass().newInstance();
				hf.setHooksFinderUtil(hfu);
				finders.add(hf);
			}

			generateDependenciesNetwork();

		}catch(Exception e){
			e.printStackTrace();
		}

	}
	
	private void recursivelyPopulateDependents(HashSet<DependentRunnable> dependents, DependentRunnable dr){
		for(int i = 0; i < runnables.size(); i++){
			for(DependentRunnable dependency : runnables.get(i).getDependencies()){
				if(dependency == dr){
					dependents.add(runnables.get(i));
					recursivelyPopulateDependents(dependents, runnables.get(i));
				}
			}
		}
	}

	private void reloadHookFinder(HookFinder hf){
		System.out.println("Reloading HookFinder: " + hf.getClass().getSimpleName());
		DependentRunnable dr = null;
		for(DependentRunnable runnable : runnables){
			if(runnable.getAssociatedThread().getName().equals(hf.getClass().getSimpleName())){
				dr = runnable;
			}
		}
		if(dr == null){
			throw new RuntimeException("HookFinder: " + hf.getClass().getSimpleName() + " is not already in the runnables list...");
		}
		HashSet<DependentRunnable> dependents = new HashSet<>();
		recursivelyPopulateDependents(dependents, dr);
		dr.getAssociatedThread().interrupt();
		for(DependentRunnable dependent : dependents){
			dependent.getAssociatedThread().interrupt();
		}
		try {
			ClassDefinition cd = new ClassDefinition(hf.getClass(), IOUtils.readFile(new File("C:\\Users\\David\\workspace\\uBot Updater\\bin\\hooksbeta\\hookfinders\\" + hf.getClass().getSimpleName() + ".class")));
			Premain.inst.redefineClasses(cd);
		} catch (Exception e) {
			e.printStackTrace();
		}
		dr.reset();
		dr.getAssociatedThread().start();
		for(DependentRunnable dependent : dependents){
			dependent.reset();
			dependent.getAssociatedThread().start();
		}
	}

}
