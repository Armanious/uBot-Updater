package obsolete;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;

import org.armanious.Filter;
import org.armanious.MapUtils;
import org.armanious.Tuple;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import updater.dynamicanalysis.RuntimeUpdater;
import updater.staticanalysis.BytecodeAnalyzerUpdater;

public class HooksFinderUtil {

	private final RuntimeUpdater runtime;
	private final BytecodeAnalyzerUpdater bytecode;

	private final Map<String, String> wrappers = Collections.synchronizedMap(new TreeMap<String, String>());
	private final Map<String, String> hooks = Collections.synchronizedMap(new TreeMap<String, String>());
	private final Map<String, Number> multipliers = Collections.synchronizedMap(new TreeMap<String, Number>());

	public HooksFinderUtil(RuntimeUpdater runtime, BytecodeAnalyzerUpdater bytecode){
		this.runtime = runtime;
		this.bytecode = bytecode;
	}

	public String getInput(String prompt) {
		final JDialog dialog = new JDialog(null, Thread.currentThread().getName(), Dialog.ModalityType.MODELESS);
		final JPanel content = new JPanel(new BorderLayout());
		final JTextField text = new JTextField();
		text.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if(e.getKeyCode() == KeyEvent.VK_ENTER){
					dialog.dispatchEvent(new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING));
				}
			}
		});
		text.setColumns(40);

		final JTextArea message = new JTextArea(prompt);
		message.setFont(UIManager.getFont("Label.font"));
		message.setEditable(false);
		message.setOpaque(false);
		message.setWrapStyleWord(true);
		message.setLineWrap(true);
		message.setColumns(20);

		content.add(message, BorderLayout.WEST);
		content.add(text, BorderLayout.CENTER);
		dialog.setContentPane(content);
		final Object lock = new Object();
		dialog.addWindowListener(new WindowAdapter(){
			@Override
			public void windowClosing(WindowEvent e) {
				dialog.setVisible(false);
				dialog.dispose();
				synchronized(lock){
					lock.notify();
				}
			}
		});
		dialog.pack();
		dialog.setVisible(true);
		synchronized(lock){
			try {
				lock.wait();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		}
		return text.getText();
	}

	public RuntimeUpdater getRuntime() {
		return runtime;
	}

	public void addWrapper(String name, String wrapper) {
		wrappers.put(name, wrapper);
	}

	public void addHook(String name, String hook) {
		hooks.put(name, hook);
	}

	public void addMultiplier(String nameOfHook, Number multiplier) {
		multipliers.put(nameOfHook, multiplier);
	}

	public Object getHookObject(String hook, Object instance) throws IllegalArgumentException, IllegalAccessException {
		final String s = hooks.get(hook);
		final int idx = s.indexOf('.');
		for(Class<?> clazz : runtime.getAllClasses()){
			if(clazz.getName().equals(s.substring(0, idx))){
				for(Field field : clazz.getDeclaredFields()){
					if(field.getName().equals(s.substring(idx + 1))){
						if(!field.isAccessible())
							field.setAccessible(true);
						return field.get(instance);
					}
				}

			}
		}
		return null;
	}
	
	/**
	 * 
	 * @param filter what FieldInsnNodes to accept for being multiplied on; or null to accept all
	 * @param nodes ClassNodes to search for
	 * @return an array of Numbers so that the lower the index in the array, the more occurrences of that multiplier found in the given class nodes.
	 */
	public static Number[] getPossibleMultipliers(Filter<FieldInsnNode> filter, ClassNode...nodes){
		//Number[] other = getPossibleMultipliersBeta(filter, nodes);
		if(filter == null){
			filter = new Filter<FieldInsnNode>(){
				@Override
				public boolean accept(FieldInsnNode t) {
					return true;
				}

			};
		}
		final Map<Number, Integer> multipliersCount = new HashMap<>();
		for(ClassNode node : nodes){
			for(MethodNode mn : node.methods){
				AbstractInsnNode insn = mn.instructions.getFirst();
				while(insn != null){
					if(insn.getOpcode() >= Opcodes.IMUL && insn.getOpcode() <= Opcodes.DMUL){
						boolean prevInsnIsLdcInsn = insn.getPrevious() instanceof LdcInsnNode;
						if(prevInsnIsLdcInsn){
							if(insn.getPrevious().getPrevious().getOpcode() == Opcodes.GETFIELD ||
									insn.getPrevious().getPrevious().getOpcode() == Opcodes.GETSTATIC){
								final FieldInsnNode fin = (FieldInsnNode) insn.getPrevious().getPrevious();
								if(filter.accept(fin)){
									final Number n = (Number)((LdcInsnNode)insn.getPrevious()).cst;
									Integer i = multipliersCount.remove(n);
									if(i == null)
										i = 0;
									multipliersCount.put(n, i + 1);
								}
							}
						}else if(insn.getPrevious().getOpcode() == Opcodes.GETFIELD){
							if(insn.getPrevious().getPrevious().getPrevious() instanceof LdcInsnNode){
								final FieldInsnNode fin = (FieldInsnNode) insn.getPrevious();
								if(filter.accept(fin)){
									final Number n = (Number)((LdcInsnNode)insn.getPrevious().getPrevious().getPrevious()).cst;
									Integer i = multipliersCount.remove(n);
									if(i == null)
										i = 0;
									multipliersCount.put(n, i + 1);
								}
							}
						}else if(insn.getPrevious().getOpcode() == Opcodes.GETSTATIC){
							if(insn.getPrevious().getPrevious() instanceof LdcInsnNode){
								final FieldInsnNode fin = (FieldInsnNode) insn.getPrevious();
								if(filter.accept(fin)){
									final Number n = (Number)((LdcInsnNode)insn.getPrevious().getPrevious()).cst;
									Integer i = multipliersCount.remove(n);
									if(i == null)
										i = 0;
									multipliersCount.put(n, i + 1);
								}
							}
						}
					}
					insn = insn.getNext();
				}
			}
		}
		Number[] us = MapUtils.sortMapHighToLow(multipliersCount, new Number[multipliersCount.size()]);
		//System.err.println("US:" + Arrays.toString(us) + "\n\tOther:" + Arrays.toString(other));
		return us;
	}
	
	public Tuple<Field, Number> getMultipliedHookIntsOnly(Object instance, int expected) throws ReflectiveOperationException {
		Class<?> searching = instance.getClass();

		final HashMap<Tuple<Field, Number>, Integer> possibleMultipliedHooks = new HashMap<>();
		while(searching != Object.class){
			for(final Field field : searching.getDeclaredFields()){
				if(Modifier.isStatic(field.getModifiers())) continue;
				if(field.getType() == int.class){
					final Number[] multipliers = getPossibleMultipliers(
							t -> t.owner.equals(field.getDeclaringClass().getName()) && t.name.equals(field.getName()),
							bytecode.getClassNodes());
					if(!field.isAccessible()) field.setAccessible(true);

					final int fieldVal = field.getInt(instance);
					for(Number multiplier : multipliers){
						double d = fieldVal * multiplier.intValue();
						if((int)d == expected){
							final Tuple<Field, Number> t = new Tuple<>(field, multiplier);
							Integer i = possibleMultipliedHooks.remove(t);
							if(i == null){
								i = 1;
							}
							possibleMultipliedHooks.put(t, i);
						}
					}
				}
			}
			searching = searching.getSuperclass();
		}
		int highest = 0;
		Tuple<Field, Number> mostLikelyHook = null;
		for(Tuple<Field, Number> hook : possibleMultipliedHooks.keySet()){
			if(possibleMultipliedHooks.get(hook) > highest){
				highest = possibleMultipliedHooks.get(hook);
				mostLikelyHook = hook;
			}
		}
		return mostLikelyHook;
	}
	
	public Class<?> getClass(String name){
		if(wrappers.containsKey(name))
			name = wrappers.get(name);
		return runtime.loadClass(name);
	}

	public ClassNode getClassNode(String name){
		if(wrappers.containsKey(name))
			name = wrappers.get(name);
		for(ClassNode cn : bytecode.getClassNodes()){
			if(cn.name.equals(name)){
				return cn;
			}
		}
		return null;
	}

	public BytecodeAnalyzerUpdater getBytecode() {
		return bytecode;
	}

	public String getWrapper(String wrapper) {
		return wrappers.get(wrapper);
	}
	
	public String getHook(String hook){
		return hooks.get(hook);
	}
	
	public Number getMultiplier(String multiplier){
		return multipliers.get(multiplier);
	}

}
