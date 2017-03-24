package updater;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import updater.dynamicanalysis.RuntimeUpdater;
import updater.staticanalysis.BytecodeAnalyzerUpdater;

public class Hooks {

	/*SubMenuX, MinimapOffset, SkillExperiences, LoginIndex, CamPosY, RSItemHashTable, IsMenuCollapsed, 
	CollapsedMenuItems, PlayerCount, MenuHeight, MinimapSetting, SkillLevelMaxes, 
	SkillExperienceMaxes, MenuWidth, CamPosX, DestX, SubMenuY, 
	MenuX, RSNPCCount, RSItemDefLoader, LoopCycle, DestY, IsSpellSelected, 
	CurrentMenuGroupNode, MenuOptionsCount, DetailInfoNode, SkillLevels, 
	MenuOptionsCountCollapsed, IsItemSelected, SubMenuWidth, ValidRSInterfaceArray, CameraPitch, 
	RSInterfaceCamPosZ, GUIRSInterfaceIndex, MinimapAngle, MinimapScale, MenuItems, 
	CameraYaw, IsMenuOpen, Plane, MenuY, RSInterfaceBoundsArray, 
	Indices1, YPoints, Indices3, XPoints, Indices2, ZPoints, RSItemID, RSItemStackSize, 
	NodeListCacheNodeList, AbsoluteX, XMultiplier, AbsoluteY, Viewport, YMultiplier, 
	MenuGroupNodeItems, MenuGroupNodeSize, MenuGroupNodeOptions, 
 	DetailInfoLevel,  
	RSObjectDefActions, RSObjectDefID, RSObjectDefName, 
	CombatStatusDataHPRatio, CombatStatusDataLoopCycleStatus, NodeDequeTail, CombatStatusData,
	RSMessageDataMessage, NodeSubQueueTail, MenuItemNodeOption, MenuItemNodeAction, 
	RSPlayerCompositeNPCID, RSPlayerCompositeEquipment, 
	RSCharacterHeight, 
	RSCharacterCombatStatusList, RSCharacterMessageData, RSCharacterIsMoving, 
	RSPlayerComposite, 
	RSPlayerSkullIcon, RSPlayerTeam, RSPlayerPrayerIcon, 
	RSInterfaceBaseComponents, 
	RSItemDefLoaderCache, RSItemDefLoaderIsMembers, RSInterfaceVerticalScrollbarThumbSize, 
	RSInterfaceHorizontalScrollbarPosition, RSInterfaceTooltip, RSInterfaceBoundsArrayIndex, 
	RSInterfaceComponentStackSize, RSInterfaceVerticalScrollbarSize, RSInterfaceIsInventoryInterface, 
	RSInterfaceComponentID, RSInterfaceYRotation, RSInterfaceHeight, RSInterfaceHorizontalScrollbarSize, 
	RSInterfaceIsHorizontallyFlipped, RSInterfaceBorderThinkness, RSInterfaceIsVisible, RSInterfaceSelectedActionName, 
	RSInterfaceType, RSInterfaceTextColor, RSInterfaceZRotation, RSInterfaceIsVerticallyFlipped, RSInterfaceParentID, 
	RSInterfaceText, RSInterfaceX, RSInterfaceComponents, RSInterfaceComponentName, RSInterfaceY, RSInterfaceModelType,
	RSInterfaceShadowColor, RSInterfaceXRotation, RSInterfaceModelID, RSInterfaceWidth, 
	RSInterfaceVerticalScrollbarPosition, RSInterfaceHorizontalScrollbarThumbSize, RSInterfaceID, RSInterfaceIsHidden, 
	RSInterfaceComponentIndex, RSInterfaceActions, RSInterfaceTextureID, RSInterfaceModelZoom, RSInterfaceSpecialType, 
	RSItemDefLoader2, 
	RSItemDefGroundActions, RSItemDefID, RSItemDefName, RSItemDefIsMembersObject, RSItemDefActions, 
	RSInterfaceNodeMainID, RenderDataFloats, DetailInfoNodeDetailInfo, Render;*/

	private final Map<Class<?>, Object> hooksLocks = Collections.synchronizedMap(new HashMap<Class<?>, Object>());
	private final ArrayList<Thread> threads = new ArrayList<>();
	private Object completionLock = new Object();

	private final RuntimeUpdater runtime;
	private final BytecodeAnalyzerUpdater bytecode;

	private final Map<String, String> wrappers = Collections.synchronizedMap(new TreeMap<String, String>());
	private final Map<String, String> hooks = Collections.synchronizedMap(new TreeMap<String, String>());
	private final Map<String, Number> multipliers = Collections.synchronizedMap(new TreeMap<String, Number>());

	public Hooks(RuntimeUpdater runtime, BytecodeAnalyzerUpdater bytecode){
		this.runtime = runtime;
		this.bytecode = bytecode;
	}

	private void run(final Runnable...rs){

		try{
			for(final Runnable r : rs){ //initialize all hooks in case for example rs[0] is waiting for rs[1]
				if(hooksLocks.put(r.getClass(), new Object()) != null)
					throw new IllegalStateException();
			}
			for(final Runnable r : rs){
				final Thread t = new Thread(){
					@Override
					public void run() {
						try{
							r.run();
							System.out.println("Found hook(s): " + r.getClass().getSimpleName());
							synchronized(hooksLocks){
								final Object lock = hooksLocks.remove(r.getClass());
								synchronized(lock){
									lock.notifyAll();
								}
								if(hooksLocks.isEmpty()){
									synchronized(completionLock){
										completionLock.notifyAll();
									}
								}
							}
						}catch(Exception e){
							System.err.println("Thread " + getName() + " failed. Will not remove lock.");
						}
					}
				};
				t.setName(r.getClass().getSimpleName());
				t.start();
				threads.add(t);
			}
		}catch(Exception e){
			throw new RuntimeException(e);
		}
	}

	private void waitFor(Class<?> clazz){
		final Object lock = hooksLocks.get(clazz);
		if(lock != null){
			synchronized(lock){
				try {
					lock.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void waitFor(Class<?>...classes){
		for(Class<?> clazz : classes){
			waitFor(clazz);
		}
	}


	private Object getHookObject(String hook, Object instance) throws ReflectiveOperationException {
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

	private Class<?> getClass(String name){
		if(wrappers.containsKey(name))
			name = wrappers.get(name);
		return runtime.loadClass(name);
	}

	private ClassNode getClassNode(String name){
		if(wrappers.containsKey(name))
			name = wrappers.get(name);
		for(ClassNode cn : bytecode.getClassNodes()){
			if(cn.name.equals(name)){
				return cn;
			}
		}
		return null;
	}
	
	private static Number[] getPossibleMultipliersBeta(Filter<FieldInsnNode> filter, ClassNode...nodes){
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
				instructionSearch: while(insn != null){
					if(insn.getOpcode() >= Opcodes.IMUL && insn.getOpcode() <= Opcodes.DMUL){
						boolean foundLdcInsn = false;
						Number ldcNum = null;
						boolean foundFieldInsn = false;
						int i = 0;
						for(AbstractInsnNode ain = insn.getPrevious(); ain != null && i++ < 6; ain = ain.getPrevious()){
							if(ain instanceof LdcInsnNode){
								if(foundLdcInsn){
									continue instructionSearch;
								}
								if(((LdcInsnNode)ain).cst instanceof Number){
									foundLdcInsn = true;
									ldcNum = (Number) ((LdcInsnNode)ain).cst;
								}
							}else if(ain instanceof FieldInsnNode && filter.accept((FieldInsnNode)ain)){
								foundFieldInsn = true;
							}
							
							if(foundLdcInsn && foundFieldInsn){
								Integer count = multipliersCount.remove(ldcNum);
								if(count == null)
									count = 0;
								multipliersCount.put(ldcNum, count + 1);
							}
							
						}
					}
				}
			}
		}
		
		return MapUtils.sortMapHighToLow(multipliersCount, new Number[multipliersCount.size()]);
	}

	/**
	 * 
	 * @param filter what FieldInsnNodes to accept for being multiplied on; or null to accept all
	 * @param nodes ClassNodes to search for
	 * @return an array of Numbers so that the lower the index in the array, the more occurrences of that multiplier found in the given class nodes.
	 */
	private static Number[] getPossibleMultipliers(Filter<FieldInsnNode> filter, ClassNode...nodes){
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

	private Tuple<Field, Number> getMultipliedHookIntsOnly(Object instance, int expected) throws ReflectiveOperationException {
		Class<?> searching = instance.getClass();

		final HashMap<Tuple<Field, Number>, Integer> possibleMultipliedHooks = new HashMap<>();
		while(searching != Object.class){
			for(final Field field : searching.getDeclaredFields()){
				if(Modifier.isStatic(field.getModifiers())) continue;
				if(field.getType() == int.class){
					final Number[] multipliers = getPossibleMultipliers(new Filter<FieldInsnNode>(){
						@Override
						public boolean accept(FieldInsnNode t) {
							return t.owner.equals(field.getDeclaringClass().getName()) && t.name.equals(field.getName());
						}
					}, bytecode.getClassNodes());
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

	private static String getInput(String label){
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

		final JTextArea message = new JTextArea(label);
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
		dialog.setResizable(false);
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

	public class CURRENTDISPLAYNAME implements Runnable {
		@Override
		public void run(){
			try{
				String playerName = getInput("Player's display name (make sure you're logged in): ");
				outer: for(Class<?> clazz : runtime.getAllClasses()){
					for(Field field : clazz.getDeclaredFields()){
						if(Modifier.isStatic(field.getModifiers()) && field.getType().equals(String.class)){
							if(!field.isAccessible())
								field.setAccessible(true);
							final String s = (String) field.get(null);
							if(playerName.equals(s)){
								hooks.put("CurrentDisplayName", clazz.getName() + '.' + field.getName());
								break outer;
							}
						}
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public class MYRSPLAYER_RSPLAYERNAME implements Runnable {

		@Override
		public void run() {
			try{
				waitFor(CURRENTDISPLAYNAME.class);
				final String playerName = (String) getHookObject("CurrentDisplayName", null);
				outer: for(Class<?> clazz : runtime.getAllClasses()){
					for(Field field : clazz.getDeclaredFields()){
						if(Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()){
							if(!field.isAccessible())
								field.setAccessible(true);
							final Object possibleMyPlayer = field.get(null);
							if(possibleMyPlayer != null){

								for(Field p_field : possibleMyPlayer.getClass().getDeclaredFields()){
									if(!Modifier.isStatic(p_field.getModifiers()) && p_field.getType().equals(String.class)){
										if(!p_field.isAccessible())
											p_field.setAccessible(true);
										final String s = (String) p_field.get(possibleMyPlayer);
										if(playerName.equals(s)){
											hooks.put("MyRSPlayer", clazz.getName() + '.' + field.getName());
											wrappers.put("RSPlayer", possibleMyPlayer.getClass().getName());
											hooks.put("RSPlayer_Name", possibleMyPlayer.getClass().getName() + '.' + p_field.getName());
											break outer;
										}
									}
								}

							}
						}
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}

	}

	public class RSPLAYERLEVEL implements Runnable {
		@Override
		public void run() {
			try{
				waitFor(MYRSPLAYER_RSPLAYERNAME.class);
				final Object myPlayer = getHookObject("MyRSPlayer", null);

				int curLevel = Integer.parseInt(getInput("Current combat level: "));
				Tuple<Field, Number> hook = getMultipliedHookIntsOnly(myPlayer, curLevel);
				hooks.put("RSPlayer_Level", hook.val1.getDeclaringClass().getName() + '.' + hook.val1.getName());
				multipliers.put("RSPlayer_Level", hook.val2);
			}catch(Exception e){
				e.printStackTrace();
			}
		}

	}

	public class NODEID_LINKEDLISTTAIL_LINKEDLISTNEXT implements Runnable {

		@Override
		public void run() { outer:
			for(final ClassNode cn : bytecode.getClassNodes()){
				final boolean hasNodeWrapper = wrappers.get("Node") != null;
				final boolean hasLinkedListNodeWrapper = wrappers.get("LinkedListNode") != null;
				if(hasNodeWrapper && hasLinkedListNodeWrapper) //already found everything
					break;
				final String expectedDesc = 'L' + cn.name + ';';
				if(cn.superName.equals("java/lang/Object")){
					int count = 0;
					for(FieldNode fn : cn.fields){
						if(Modifier.isStatic(fn.access)) continue;
						if(fn.desc.equals(expectedDesc)){
							count++;
						}
					}
					if(count == 2){
						if(!hasNodeWrapper){
							for(final FieldNode fn : cn.fields){
								if(fn.desc.equals("J")){
									final Number nodeIdMultiplier = getPossibleMultipliers(new Filter<FieldInsnNode>(){
										@Override
										public boolean accept(FieldInsnNode fin){
											return fin.owner.equals(cn.name) && fin.name.equals(fn.name);
										}
									}, bytecode.getClassNodes())[0];
									if(nodeIdMultiplier instanceof Long){
										wrappers.put("Node", cn.name);
										hooks.put("Node_ID", cn.name + '.' + fn.name);
										multipliers.put("Node_ID", nodeIdMultiplier);
										continue outer;
									}
								}
							}
						}
						//not Node; should be LinkedListNode
						if(!hasLinkedListNodeWrapper){
							wrappers.put("LinkedListNode", cn.name);
							continue outer;
						}
					}
				}
			}
		}
	}

	public class NODENEXT_NODEPREVIOUS implements Runnable {

		@Override
		public void run() {
			waitFor(NODEID_LINKEDLISTTAIL_LINKEDLISTNEXT.class);
			final ClassNode nodeCn = getClassNode("Node");
			final String nodeDesc = 'L' + nodeCn.name + ';';

			int idx = 0;
			final FieldNode[] nextAndPrevious = new FieldNode[2];
			for(FieldNode fn : nodeCn.fields){
				if(Modifier.isStatic(fn.access) || !fn.desc.equals(nodeDesc)) continue;
				nextAndPrevious[idx++] = fn; //just throw exception and return from the method when 3 are present
			}
			final Map<FieldNode, ArrayList<MethodNode>> accesses = bytecode.getRelationshipSet().getFieldAccessorsMap();
			final int count0;
			final int count1;
			synchronized(accesses){
				count0 = accesses.get(nextAndPrevious[0]).size();
				count1 = accesses.get(nextAndPrevious[1]).size();
			}
			if(count0 == count1)
				throw new IllegalStateException();
			final String next;
			final String previous;
			if(count0 > count1){
				next = nextAndPrevious[0].name;
				previous = nextAndPrevious[1].name;
			}else{
				next = nextAndPrevious[1].name;
				previous = nextAndPrevious[0].name;
			}
			hooks.put("Node_Next", nodeCn.name + '.' + next);
			hooks.put("Node_Previous", nodeCn.name + '.' + previous);
		}

	}

	public class LINKEDLISTTAIL_LINKEDLISTNODENEXT_LINKEDLISTNODEPREVIOUS implements Runnable {

		@Override
		public void run() {
			waitFor(NODEID_LINKEDLISTTAIL_LINKEDLISTNEXT.class);
			ClassNode cn = getClassNode("LinkedListNode");
			final String expectedDesc = 'L' + cn.name + ';';
			final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();
			final Map<String, Integer> counter = new HashMap<>();
			synchronized(map){
				for(FieldNode fn : cn.fields){
					if(Modifier.isStatic(fn.access) || !fn.desc.equals(expectedDesc)) continue;
					final ArrayList<MethodNode> mns = map.get(fn);
					for(MethodNode mn : mns){
						if(Modifier.isStatic(mn.access)) continue;
						final String methodOwner = mn.toString().substring(0, mn.toString().indexOf('.'));
						if(methodOwner.equals(cn.name)) continue;
						Integer i = counter.remove(methodOwner);
						if(i == null)
							i = 0;
						counter.put(methodOwner, i + 1);
					}
				}
			}
			String linkedList = null;
			for(String owner : counter.keySet()){
				if(linkedList == null || counter.get(owner) > counter.get(linkedList)){
					linkedList = owner;
				}
			}
			wrappers.put("LinkedList", linkedList);
			cn = getClassNode(linkedList);
			for(MethodNode mn : cn.methods){
				if(mn.name.equals("<init>")){
					AbstractInsnNode insn = mn.instructions.getFirst();
					while((insn = insn.getNext()).getOpcode() != Opcodes.NEW);
					while((insn = insn.getNext()).getOpcode() != Opcodes.PUTFIELD);
					hooks.put("LinkedList_Tail", linkedList + '.' + ((FieldInsnNode) insn).name);
					break;
				}
			}
			synchronized(map){
				final String linkedListTailHook = hooks.get("LinkedList_Tail");
				final String linkedListTailFieldName = linkedListTailHook.substring(linkedListTailHook.indexOf('.') + 1);
				for(FieldNode fn : cn.fields){
					if(fn.name.equals(linkedListTailFieldName)){
						ArrayList<MethodNode> accesses = map.get(fn);
						counter.clear();
						for(MethodNode mn : accesses){
							AbstractInsnNode insn = mn.instructions.getFirst();
							while(insn != null){
								if(insn.getOpcode() == Opcodes.GETFIELD){
									if(insn.getPrevious().getOpcode() == Opcodes.ALOAD && ((VarInsnNode)insn.getPrevious()).var == 0){
										FieldInsnNode fin = (FieldInsnNode) insn;
										if(fin.name.equals(linkedListTailFieldName)){
											if(insn.getNext().getOpcode() == Opcodes.GETFIELD){
												fin = (FieldInsnNode) insn.getNext();
												Integer i = counter.remove(fin.name);
												if(i == null)
													i = 0;
												counter.put(fin.name, i + 1);
											}
										}
									}
								}
								insn = insn.getNext();
							}
						}
						assert counter.size() == 2;
						final String[] nextAndPrevious = new String[2];
						int idx = 0;
						for(String key : counter.keySet()){
							nextAndPrevious[idx++] = key;
						}
						final int count0 = counter.get(nextAndPrevious[0]);
						final int count1 = counter.get(nextAndPrevious[1]);
						if(count0 == count1)
							throw new IllegalStateException();
						final String next;
						final String previous;
						if(count0 > count1){
							next = nextAndPrevious[0];
							previous = nextAndPrevious[1];
						}else{
							next = nextAndPrevious[1];
							previous = nextAndPrevious[0];
						}
						hooks.put("LinkedListNode_Next", wrappers.get("LinkedListNode") + '.' + next);
						hooks.put("LinkedListNode_Previous", wrappers.get("LinkedListNode") + '.' + previous);
					}
				}
			}
		}

	}

	public class RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA implements Runnable {
		@Override
		public void run() {
			try{
				waitFor(MYRSPLAYER_RSPLAYERNAME.class);
				ClassNode cn = getClassNode("RSPlayer");
				wrappers.put("RSCharacter", cn.superName);
				cn = getClassNode("RSCharacter");
				wrappers.put("RSAnimable", cn.superName);
				cn = getClassNode("RSAnimable");
				wrappers.put("RSInteractable", cn.superName);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public class RSINTERACTABLEPLANE implements Runnable {

		@Override
		public void run(){
			try{
				waitFor(RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA.class);
				final ClassNode cn = getClassNode("RSInteractable");
				for(MethodNode mn : cn.methods){
					if(Modifier.isStatic(mn.access)) continue;
					AbstractInsnNode ain = mn.instructions.getFirst();
					while(ain != null){
						if(ain.getOpcode() == Opcodes.GETFIELD){
							final FieldInsnNode fin = (FieldInsnNode) ain;
							if(fin.desc.charAt(0) == '[' && fin.desc.charAt(1) == '[' && fin.desc.charAt(2) == '['){
								AbstractInsnNode planeInsnField = fin.getNext();
								while((planeInsnField = planeInsnField.getNext()) != null && planeInsnField.getOpcode() != Opcodes.GETFIELD
										&& planeInsnField.getNext().getOpcode() != Opcodes.AALOAD);
								if(planeInsnField != null){
									hooks.put("RSInteractable_Plane", cn.name + '.' + ((FieldInsnNode)planeInsnField).name);
									return;
								}
							}
						}
						ain = ain.getNext();
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}

	}

	public class RSINTERACTABLELOCATIONCONTAINER_LOCATIONCONTAINERLOCATION implements Runnable {

		@Override
		public void run() {
			try{
				waitFor(RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA.class);
				final Object myPlayer = getHookObject("MyRSPlayer", null);
				final Class<?> clazz = Hooks.this.getClass("RSInteractable").getSuperclass();
				outer: for(Field f : clazz.getDeclaredFields()){
					if(Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
					if(!f.isAccessible()) f.setAccessible(true);
					final Object val = f.get(myPlayer);
					if(val != null){
						Class<?> valClass = val.getClass();
						for(Field field : valClass.getDeclaredFields()){
							if(field.getType().isPrimitive())
								continue outer;
						}
						semiouterwowimstupid: for(Field field : valClass.getDeclaredFields()){
							if(!field.isAccessible()) field.setAccessible(true);
							final Object possibleLocData = field.get(val);
							if(possibleLocData != null){
								for(Field thisIsSad : possibleLocData.getClass().getDeclaredFields()){
									if(Modifier.isStatic(thisIsSad.getModifiers()) || thisIsSad.getType() != float.class) continue;
									if(!thisIsSad.isAccessible()) thisIsSad.setAccessible(true);
									float n = thisIsSad.getFloat(possibleLocData);
									if(((int)n) == 0){
										continue semiouterwowimstupid;
									}
								}
								wrappers.put("LocationContainer", valClass.getName());
								wrappers.put("Location", possibleLocData.getClass().getName());
								hooks.put("RSInteractable_LocationContainer", clazz.getName() + '.' + f.getName());
								hooks.put("LocationContainer_Location", valClass.getName() + '.' + field.getName());
								return;
							}
						}
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}

	}

	public class LOCATIONX_LOCATIONY implements Runnable {

		@Override
		public void run(){
			try{
				waitFor(RSINTERACTABLELOCATIONCONTAINER_LOCATIONCONTAINERLOCATION.class);
				getInput("Please stay still...");
				final Object locData = getHookObject("LocationContainer_Location", getHookObject("RSInteractable_LocationContainer", getHookObject("MyRSPlayer", null)));
				final ArrayList<Tuple<Field, Integer>> fields = new ArrayList<>();
				for(Field field : locData.getClass().getDeclaredFields()){
					if(!field.getType().isPrimitive() || field.getType() == boolean.class || Modifier.isStatic(field.getModifiers()))
						continue;
					final int i = ((Number)field.get(locData)).intValue();
					if(i >= 0){
						fields.add(new Tuple<>(field, i));
					}
				}
				String LocationX = null;
				String LocationY = null;
				final int dx = Math.abs(Integer.parseInt(getInput("Please move EAST or WEST and type in how many tiles you moved")));
				for(Tuple<Field, Integer> tuple : fields){
					final int i = ((Number)tuple.val1.get(locData)).intValue();
					if(Math.abs((i >> 9) - (tuple.val2 >> 9)) == dx){
						LocationX = wrappers.get("Location") + '.' + tuple.val1.getName();
						break;
					}
				}
				final int dy = Math.abs(Integer.parseInt(getInput("Please move NORTH or SOUTH and type in how many tiles you moved")));
				for(Tuple<Field, Integer> tuple : fields){
					final int i = ((Number)tuple.val1.get(locData)).intValue();
					if(Math.abs((i >> 9) - (tuple.val2 >> 9)) == dy){
						LocationY = wrappers.get("Location") + '.' + tuple.val1.getName();
						break;
					}
				}
				assert LocationX != null;
				assert LocationY != null;
				if(LocationX.equals(LocationY)){
					for(int i = 0; i < 100; i++){
						System.err.println("LISTEN TO DIRECTIONS!!!");
					}
					System.err.println("...!!!");
				}
				hooks.put("Location_X", LocationX);
				hooks.put("Location_Y", LocationY);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public class RSNPC implements Runnable {
		@Override
		public void run() {
			try{
				waitFor(RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA.class);
				final String rsplayer = wrappers.get("RSPlayer");
				final String rscharacter = wrappers.get("RSCharacter");
				for(ClassNode cn : bytecode.getClassNodes()){
					if(cn.superName.equals(rscharacter) && !cn.name.equals(rsplayer)){
						wrappers.put("RSNPC", cn.name);
						break;
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public class RSPLAYERARRAY_RSPLAYERINDEXARRAY implements Runnable {

		@Override
		public void run() {
			try{
				waitFor(MYRSPLAYER_RSPLAYERNAME.class);
				getInput("Just wait until you can see the other players...(the input here is ignored)");
				final String RSPlayerWrapper = wrappers.get("RSPlayer");
				Tuple<Field, Object[]> playerArrayField = null;
				final Map<Field, int[]> possiblePlayerIndexArrays = new HashMap<>();
				for(Class<?> clazz : runtime.getAllClasses()){
					for(Field field : clazz.getDeclaredFields()){
						if(!Modifier.isStatic(field.getModifiers())) continue;
						if(!field.isAccessible())
							field.setAccessible(true);
						Object val = field.get(null);
						if(val != null && val.getClass().isArray()){
							if(playerArrayField == null && val.getClass().getComponentType().getName().equals(RSPlayerWrapper)){
								playerArrayField = new Tuple<>(field, ((Object[])val).clone());
							}else if(val.getClass().getComponentType() == int.class){
								possiblePlayerIndexArrays.put(field, ((int[])val).clone());
							}
						}
					}
				}
				assert playerArrayField != null;
				hooks.put("RSPlayerArray", playerArrayField.val1.getDeclaringClass().getName() + '.' + playerArrayField.val1.getName());
				outer: for(Field indexArrField : possiblePlayerIndexArrays.keySet()){
					final int[] indexArr = possiblePlayerIndexArrays.get(indexArrField);
					for(int i = 0; i < playerArrayField.val2.length; i++){
						if(playerArrayField.val2[i] != null){
							boolean hasIndex = false;
							for(int j = 0; j < indexArr.length && !hasIndex; j++){
								hasIndex = indexArr[j] == i;
							}
							if(!hasIndex){
								continue outer;
							}
						}
					}
					hooks.put("RSPlayerIndexArray", indexArrField.getDeclaringClass().getName() + '.' + indexArrField.getName());
					break;
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}

	}

	public class RSNPCNODE_RSNPCNODERSNPC_RSNPCNODEARRAY implements Runnable {

		@Override
		public void run() {
			try{
				waitFor(RSNPC.class, NODEID_LINKEDLISTTAIL_LINKEDLISTNEXT.class);
				final Class<?> nodeClass = Hooks.this.getClass("Node");
				final Class<?> rsnpcClass = Hooks.this.getClass("RSNPC");
				outer: for(Class<?> clazz : runtime.getAllClasses()){
					for(Field f : clazz.getDeclaredFields()){
						if(!Modifier.isStatic(f.getModifiers())) continue;
						if(!f.isAccessible()) f.setAccessible(true);
						final Object val = f.get(null);
						if(val != null && val.getClass().isArray()){
							if(nodeClass.isAssignableFrom(val.getClass().getComponentType())){
								Object[] arr = (Object[]) val;
								Object nonNull = null;
								for(Object obj : arr){
									if(obj != null){
										nonNull = obj;
										break;
									}
								}
								if(nonNull == null) continue; //-.-
								for(Field possibleNodeField : nonNull.getClass().getDeclaredFields()){
									if(Modifier.isStatic(possibleNodeField.getModifiers())) continue;
									if(!possibleNodeField.isAccessible()) possibleNodeField.setAccessible(true);
									Object possibleNodeFieldVal = possibleNodeField.get(nonNull);
									if(possibleNodeFieldVal != null){
										if(rsnpcClass.isAssignableFrom(possibleNodeFieldVal.getClass())){
											wrappers.put("RSNPCNode", nonNull.getClass().getName());
											hooks.put("RSNPCNodeArray", f.getDeclaringClass().getName() + '.' + f.getName());
											hooks.put("RSNPCNode_RSNPC", possibleNodeField.getDeclaringClass().getName() + '.' + possibleNodeField.getName());
											break outer;
										}
									}
								}
							}
						}
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}

	}

	public class RSINFO implements Runnable {
		@Override
		public void run() {
			try{
				final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();
				final Map<FieldNode, Integer> mostAccessed = new TreeMap<>();
				synchronized(map){
					for(FieldNode key : map.keySet()){
						if(!key.toString().startsWith("client") || !Modifier.isStatic(key.access)) continue;
						mostAccessed.put(key, map.get(key).size());
					}
				}
				final FieldNode groundData = MapUtils.sortMapHighToLow(mostAccessed, new FieldNode[mostAccessed.size()])[0];
				hooks.put("RSInfo", "client." + groundData.name);
				wrappers.put("RSInfo", groundData.desc.substring(1, groundData.desc.length() - 1));
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	
	//	public class RSINFOGROUNDDATA implements Runnable { //FIXME
	//		@Override
	//		public void run() {
	//			try{
	//				waitFor(RSINFO.class, RSINFORSGROUNDINFO.class);
	//				System.err.println(hooks);
	//				System.err.println(wrappers);
	//				System.exit(0);
	//				
	//				final ClassNode cn = getClassNode("RSInfo");
	//				final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();
	//				final Map<FieldNode, Integer> mostAccessedArrays = new TreeMap<>();
	//				synchronized(map){
	//					for(FieldNode key : cn.fields){
	//						if(Modifier.isStatic(key.access) || key.desc.charAt(0) != '[' || key.desc.charAt(1) != 'L') continue;
	//						mostAccessedArrays.put(key, map.get(key).size());
	//					}
	//				}
	//				System.err.println(mostAccessedArrays);
	//				final FieldNode groundData = MapUtils.sortMapHighToLow(mostAccessedArrays, new FieldNode[1])[0];
	//				hooks.put("RSInfo_RSGroundData", groundData.toString().split(" ")[0]);
	//				wrappers.put("RSGroundData", groundData.desc.substring(2, groundData.desc.length() - 1));
	//			}catch(Exception e){
	//				e.printStackTrace();
	//			}
	//		}
	//	}

	public class RSINFOGROUNDBYTES implements Runnable {
		@Override
		public void run() {
			try{
				waitFor(RSINFO.class);
				final Class<?> clazz = Hooks.this.getClass("RSInfo");
				for(Field field : clazz.getDeclaredFields()){
					if(Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive() || field.getType().isArray())
						continue;
					for(Field f : field.getType().getDeclaredFields()){
						if(Modifier.isStatic(f.getModifiers()) || f.getType() != byte[][][].class) continue;
						hooks.put("RSInfo_RSGroundBytes", wrappers.get("RSInfo") + '.' + field.getName());
						wrappers.put("RSGroundBytes", field.getType().getName());
						hooks.put("RSGroundBytes_Bytes", wrappers.get("RSGroundBytes") + '.' + f.getName());
						break;
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public class RSINFORSOBJECTDEFLOADERS_SOFTREFERENCE_HARDREFERENCE_RSMODEL_CACHE implements Runnable {
		@Override
		public void run() {
			try{
				waitFor(RSINFO.class, HASHTABLE_HASHTABLEBUCKETS.class, NODESUB.class);
				final Class<?> hashtableClass = Hooks.this.getClass("Hashtable");
				getInput("Make sure you are logged in");
				final Object rsInfo = getHookObject("RSInfo", null);
				assert rsInfo != null;
				for(Field possibleRSObjectDefLoaderField : rsInfo.getClass().getDeclaredFields()){
					if(possibleRSObjectDefLoaderField.getType().isPrimitive()
							|| possibleRSObjectDefLoaderField.getType().isArray()
							|| Modifier.isStatic(possibleRSObjectDefLoaderField.getModifiers()) 
							|| Number.class.isAssignableFrom(possibleRSObjectDefLoaderField.getType()))
						continue;
					if(!possibleRSObjectDefLoaderField.isAccessible()) possibleRSObjectDefLoaderField.setAccessible(true);
					final Object possibleRSObjectDefLoader = possibleRSObjectDefLoaderField.get(rsInfo);
					if(possibleRSObjectDefLoader == null) continue;
					for(Field possibleCacheField : possibleRSObjectDefLoader.getClass().getDeclaredFields()){
						if(possibleCacheField.getType().isPrimitive() 
								|| possibleCacheField.getType().isArray()
								|| Modifier.isStatic(possibleCacheField.getModifiers()) 
								|| Number.class.isAssignableFrom(possibleCacheField.getType())) 
							continue;
						if(!possibleCacheField.isAccessible()) possibleCacheField.setAccessible(true);
						final Object possibleCache = possibleCacheField.get(possibleRSObjectDefLoader);
						if(possibleCache == null) continue;
						for(Field possibleHashtableField : possibleCache.getClass().getDeclaredFields()){
							if(hashtableClass == possibleHashtableField.getType()){
								if(!possibleHashtableField.isAccessible()) possibleHashtableField.setAccessible(true);
								final Object[] nodes = (Object[]) getHookObject("Hashtable_Buckets", possibleHashtableField.get(possibleCache));
								for(Object tail : nodes){
									for(Object node = getHookObject("Node_Previous", tail); node != null && node != tail; node = getHookObject("Node_Previous", node)){
										if(wrappers.get("RSObjectDef") != null && wrappers.get("RSAbstractModel") != null){
											System.err.println("SUCCESS");
											return;
										}
										try{
											final Field softOrHardReference = node.getClass().getDeclaredFields()[0];
											if(softOrHardReference.getType() == SoftReference.class && wrappers.get("SoftReferenceNode") == null){
												wrappers.put("SoftReferenceNode", node.getClass().getName());
												hooks.put("SoftReferenceNode_SoftReference", node.getClass().getName() + '.' + softOrHardReference.getName());
											}else if(softOrHardReference.getType() == Object.class && wrappers.get("HardReferenceNode") == null){
												wrappers.put("HardReferenceNode", node.getClass().getName());
												hooks.put("HardReferenceNode_HardReference", node.getClass().getName() + '.' + softOrHardReference.getName());
											}
											softOrHardReference.setAccessible(true);
											Object val = softOrHardReference.get(node);
											if(val instanceof SoftReference)
												val = ((SoftReference<?>)val).get();
											if(val == null) continue;
											if(val.getClass().getSuperclass() == Object.class && wrappers.get("RSObjectDef") == null){
												//val instanceof RSObjectDef
												hooks.put("RSInfo_RSObjectDefLoader", possibleRSObjectDefLoaderField.getDeclaringClass().getName() + '.' + possibleRSObjectDefLoaderField.getName());
												hooks.put("RSObjectDefLoader_Cache", possibleCacheField.getDeclaringClass().getName() + '.' + possibleCacheField.getName());
												hooks.put("Cache_Hashtable", possibleCacheField.getDeclaringClass().getName() + '.' + possibleCacheField.getName());
												wrappers.put("RSObjectDefLoader", possibleRSObjectDefLoader.getClass().getName());
												wrappers.put("Cache", possibleCache.getClass().getName());
												wrappers.put("RSObjectDef", val.getClass().getName());
												System.err.println(hooks + "\n" + wrappers);
											}else if(wrappers.get("RSAbstractModel") == null){
												//val instanceof RSModel
												wrappers.put("RSAbstractModel", val.getClass().getSuperclass().getName());
												wrappers.put("RSModel", val.getClass().getName());
											}
										}catch(Exception e){
											//ignore
										}
									}
								}
							}
						}
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public class RSINFORSGROUNDINFO implements Runnable {
		@Override
		public void run() {
			try{
				waitFor(RSINFO.class, RSINFORSOBJECTDEFLOADERS_SOFTREFERENCE_HARDREFERENCE_RSMODEL_CACHE.class, RSINFOGROUNDBYTES.class,
						RSINFOBASEINFO.class);
				final ClassNode cn = getClassNode("RSInfo");
				final FieldNode[] p;
				final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();
				synchronized(map){
					p = MapUtils.sortMapHighToLow(MapUtils.filterMap(new Filter<FieldNode>(){
						@Override
						public boolean accept(FieldNode t) {
							return t.desc.charAt(0) == 'L' && !Modifier.isStatic(t.access) 
									&& t.toString().startsWith(cn.name)
									&& !wrappers.containsValue(t.desc.substring(1, t.desc.length() - 1));
						}
					}, map), new Comparator<ArrayList<MethodNode>>() {
						@Override
						public int compare(ArrayList<MethodNode> o1, ArrayList<MethodNode> o2) {
							if(o1 == null && o2 != null)
								return -1;
							else if(o1 != null && o2 == null)
								return 1;
							else if(o1 == null && o2 == null)
								return 0;
							return o1.size() - o2.size();
						}
					}, new FieldNode[cn.fields.size()]);
					int highestFieldObjectsCount = -1;
					FieldNode mostLikely = null;
					for(FieldNode node : p){
						int fieldObjectsCount = 0;
						for(FieldNode fn : getClassNode(node.desc.substring(1, node.desc.length() - 1)).fields){
							if(!Modifier.isStatic(fn.access)){
								fieldObjectsCount++;
							}
						}
						if(fieldObjectsCount > highestFieldObjectsCount){
							highestFieldObjectsCount = fieldObjectsCount;
							mostLikely = node;
						}
					}
					assert mostLikely != null;
					hooks.put("RSInfo_RSGroundInfo", cn.name + '.' + mostLikely.name);
					wrappers.put("RSGroundInfo", mostLikely.desc.substring(1, mostLikely.desc.length() - 1));
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public class RSGROUNDINFORSTILEDATAARRAY_RSGROUNDINFORSGROUNDARRAY implements Runnable {

		@Override
		public void run() {
			waitFor(RSINFORSGROUNDINFO.class);
			final ClassNode cn = getClassNode("RSGroundInfo");
			final ArrayList<FieldNode> groundArrayCandidates = new ArrayList<>();
			final ArrayList<FieldNode> tileDataCandidates = new ArrayList<>();
			for(FieldNode fn : cn.fields){
				if(fn.desc.charAt(0) == '['){
					if(fn.desc.charAt(1) == '[' && fn.desc.charAt(2) == '[' && fn.desc.charAt(3) == 'L'){
						//rsgroundarray
						groundArrayCandidates.add(fn);
					}else if(fn.desc.charAt(1) == 'L'){
						//tiledata
						tileDataCandidates.add(fn);
					}
				}
			}
			FieldNode highestGroundArrayCount = null;
			FieldNode highestTileDataCount = null;
			final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();
			synchronized(map){
				for(FieldNode candidate : tileDataCandidates){
					if(highestTileDataCount == null || map.get(candidate).size() > map.get(highestTileDataCount).size()){
						highestTileDataCount = candidate;
					}
				}
				for(FieldNode candidate : groundArrayCandidates){
					if(highestGroundArrayCount == null || map.get(candidate).size() > map.get(highestGroundArrayCount).size()){
						highestGroundArrayCount = candidate;
					}
				}
			}
			assert highestTileDataCount != null;
			assert highestGroundArrayCount != null;
			hooks.put("RSGroundInfo_RSTileDataArray", cn.name + '.' + highestTileDataCount.name);
			hooks.put("RSGroundInfo_RSGroundArray", cn.name + '.' + highestGroundArrayCount.name);
			wrappers.put("RSTileData", highestTileDataCount.desc.substring(2, highestTileDataCount.desc.length() - 1));
			wrappers.put("RSGround", highestGroundArrayCount.desc.substring(4, highestGroundArrayCount.desc.length() - 1));
		}

	}

	public class RSGROUNDRSANIMABLELIST implements Runnable {

		@Override
		public void run() {
			waitFor(RSGROUNDINFORSTILEDATAARRAY_RSGROUNDINFORSGROUNDARRAY.class);
			final ClassNode cn = getClassNode("RSGround");
			for(FieldNode fn : cn.fields){
				if(Modifier.isStatic(fn.access) || fn.desc.charAt(0) != 'L'
						|| wrappers.containsValue(fn.desc.substring(1, fn.desc.length() - 1))
						|| !getClassNode(fn.desc.substring(1, fn.desc.length() - 1)).superName.equals("java/lang/Object")) continue;
				hooks.put("RSGround_RSAnimableNode", cn.name + '.' + fn.name);
				wrappers.put("RSAnimableNode", fn.desc.substring(1, fn.desc.length() - 1));
				return;
			}
		}

	}

	public class RSANIMABLENODENEXT_RSANIMABLENODERSANIMBALE implements Runnable {

		@Override
		public void run() {
			waitFor(RSGROUNDRSANIMABLELIST.class,
					RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA.class);
			final ClassNode cn = getClassNode("RSAnimableNode");
			final String rsanimable = wrappers.get("RSAnimable");
			for(FieldNode fn : cn.fields){
				if(Modifier.isStatic(fn.access) || fn.desc.charAt(0) != 'L') continue;
				final String type = fn.desc.substring(1, fn.desc.length() - 1);
				if(type.equals(cn.name)){
					hooks.put("RSAnimableNode_Next", cn.name + '.' + fn.name);
				}else if(type.equals(rsanimable)){
					hooks.put("RSAnimableNode_RSAnimable", cn.name + '.' + fn.name);
				}
			}
		}

	}

	/*public class GROUNDDATABLOCKS_GROUNDDATAX_GROUNDDATAY implements Runnable {
		@Override
		public void run() {
			try{
				waitFor(RSINFOGROUNDDATA.class);
				final ClassNode cn = getClassNode("RSGroundData");
				for(FieldNode fn : cn.fields){
					if(fn.desc.equals("[[I") && !Modifier.isStatic(fn.access)){
						hooks.put("RSGroundData_Blocks", cn.name + '.' + fn.name);
						break;
					}
				}
				for(MethodNode mn : cn.methods){
					if(!Modifier.isStatic(mn.access) && mn.desc.equals("(II)V")){
						AbstractInsnNode insn = mn.instructions.getFirst();
						int count = 0;
						while(insn != null){
							if(insn instanceof FieldInsnNode){
								count++;
								if(count == 1){
									final String name = ((FieldInsnNode)insn).name;
									hooks.put("RSGroundData_X", cn.name + '.' + name);
									multipliers.put("RSGroundData_X", getPossibleMultipliers(new Filter<FieldInsnNode>(){
										@Override
										public boolean accept(FieldInsnNode insn){
											return insn.name.equals(name);
										}
									}, cn)[0]);
								}else if(count == 2){
									final String name = ((FieldInsnNode)insn).name;
									hooks.put("RSGroundData_Y", cn.name + '.' + name);
									multipliers.put("RSGroundData_Y", getPossibleMultipliers(new Filter<FieldInsnNode>(){
										@Override
										public boolean accept(FieldInsnNode insn){
											return insn.name.equals(name);
										}
									}, cn)[0]);
								}else{
									return;
								}
							}
							insn = insn.getNext();
						}
						break;
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}*/

	public class RSINFOBASEINFO implements Runnable {
		@Override
		public void run() {
			try{
				waitFor(RSINFO.class);
				final ClassNode cn = getClassNode("RSInfo");
				final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();
				final Map<FieldNode, Integer> mostAccessed = new TreeMap<>();
				synchronized(map){
					for(FieldNode fn : cn.fields){
						if(Modifier.isStatic(fn.access) || fn.desc.charAt(0) != 'L') continue;
						mostAccessed.put(fn, map.get(fn).size());
					}
				}
				final FieldNode baseData = MapUtils.sortMapHighToLow(mostAccessed, new FieldNode[1])[0];
				wrappers.put("RSBaseInfo", baseData.desc.substring(1, baseData.desc.length() - 1));
				hooks.put("RSInfo_RSBaseInfo", wrappers.get("RSInfo") + '.' + baseData.name);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public class RSBASEINFOX_RSBASEINFOY implements Runnable {

		@Override
		public void run() {
			try{
				waitFor(RSINFOBASEINFO.class);
				getInput("Make sure you are logged in");
				Object baseInfo = getHookObject("RSInfo_RSBaseInfo", getHookObject("RSInfo", null));
				Tuple<Field, Tuple<Integer, Number>> tupleA = null;
				Tuple<Field, Tuple<Integer, Number>> tupleB = null;
				for(final Field field : baseInfo.getClass().getDeclaredFields()){
					if(field.getType() != int.class || Modifier.isStatic(field.getModifiers())) continue;
					final int val = field.getInt(baseInfo);
					final int multiplier = getPossibleMultipliers(new Filter<FieldInsnNode>(){
						@Override
						public boolean accept(FieldInsnNode t) {
							return t.owner.equals(wrappers.get("RSBaseInfo")) && t.name.equals(field.getName());
						}
					}, getClassNode("RSBaseInfo"))[0].intValue();
					if(val == 0) continue;
					if(tupleA == null){
						tupleA = new Tuple<>(field, new Tuple<Integer, Number>(val, multiplier));
					}else if(tupleB == null){
						tupleB = new Tuple<>(field, new Tuple<Integer, Number>(val, multiplier));
					}else{
						throw new IllegalStateException();
					}
				}
				assert tupleA != null;
				assert tupleB != null;
				getInput("Now move A LOT in the y direction (up or down) until you see the minimap loading again...");
				int a2 = tupleA.val1.getInt(getHookObject("RSInfo_RSBaseInfo", getHookObject("RSInfo", null))) * tupleA.val2.val2.intValue();
				int b2 = tupleB.val1.getInt(getHookObject("RSInfo_RSBaseInfo", getHookObject("RSInfo", null))) * tupleB.val2.val2.intValue();
				int deltaA = Math.abs(tupleA.val2.val1.intValue() * tupleA.val2.val2.intValue() - a2);
				int deltaB = Math.abs(tupleB.val2.val1.intValue() * tupleB.val2.val2.intValue() - b2);
				if(deltaA == deltaB)
					throw new IllegalStateException();
				if(deltaA > deltaB){
					//tupleA.val1 is RSBaseInfo_Y
					hooks.put("RSBaseInfo_Y", wrappers.get("RSBaseInfo") + '.' + tupleA.val1.getName());
					hooks.put("RSBaseInfo_X", wrappers.get("RSBaseInfo") + '.' + tupleB.val1.getName());
				}else{
					//tupleB.val1 is RSBaseInfo_Y
					hooks.put("RSBaseInfo_Y", wrappers.get("RSBaseInfo") + '.' + tupleB.val1.getName());
					hooks.put("RSBaseInfo_X", wrappers.get("RSBaseInfo") + '.' + tupleA.val1.getName());
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}

	}

	public class CANVAS implements Runnable {

		@Override
		public void run(){
			try{
				for(Class<?> clazz : runtime.getAllClasses()){
					for(Field field : clazz.getDeclaredFields()){
						if(!Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
						if(!field.isAccessible()) field.setAccessible(true);
						final Object val = field.get(null);
						if(val != null && val instanceof Canvas){
							hooks.put("Canvas", clazz.getName() + '.' + field.getName());
							return;
						}
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}

	}

	public class SETTINGSDATA implements Runnable {

		@Override
		public void run(){
			try{
				waitFor(RSINFO.class);

				FieldNode settingsInstanceAccessor = null;
				int highest = -1;
				final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();

				for(ClassNode cn : bytecode.getClassNodes()){
					if(wrappers.containsValue(cn.name))
						continue;
					for(FieldNode fn : cn.fields){
						if(fn.desc.equals("[I") && !Modifier.isStatic(fn.access)){

							synchronized(map){
								final String expectedDesc = 'L' + cn.name + ';';
								for(ClassNode classNode : bytecode.getClassNodes()){
									for(FieldNode fieldNode : classNode.fields){
										if(fieldNode.desc.equals(expectedDesc) && Modifier.isStatic(fieldNode.access)){
											if(map.get(fieldNode).size() > highest){
												settingsInstanceAccessor = fieldNode;
												highest = map.get(fieldNode).size();
											}
										}
									}
								}
							}
						}
					}
				}
				assert settingsInstanceAccessor != null;
				hooks.put("Settings", settingsInstanceAccessor.toString().split(" ")[0]);
				wrappers.put("Settings", settingsInstanceAccessor.desc.substring(1, settingsInstanceAccessor.desc.length() - 1));

				final ClassNode cn = getClassNode("Settings");
				FieldNode field = null;
				highest = -1;
				synchronized(map){
					for(FieldNode fn : cn.fields){
						if(Modifier.isStatic(fn.access) || !fn.desc.equals("[I")) continue;
						if(map.get(fn).size() > highest){
							field = fn;
							highest = map.get(fn).size();
						}
					}
				}
				assert field != null;
				hooks.put("Settings_Data", field.toString().split(" ")[0]);
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public class NODESUB implements Runnable {

		@Override
		public void run() {
			waitFor(NODEID_LINKEDLISTTAIL_LINKEDLISTNEXT.class);
			for(ClassNode cn : bytecode.getClassNodes()){
				if(cn.superName.equals(wrappers.get("Node"))){
					final String expectedDesc = 'L' + cn.name + ';';
					int count = 0;
					for(FieldNode fn : cn.fields){
						if(fn.desc.equals(expectedDesc)){
							count++;
						}
					}
					if(count == 2){
						wrappers.put("NodeSub", cn.name);
					}
				}
			}
		}

	}

	public class NODESUBNEXT_NODESUBPREVIOUS implements Runnable {

		@Override
		public void run() {
			waitFor(NODESUB.class);
			final ClassNode cn = getClassNode("NodeSub");
			final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();
			Tuple<FieldNode, Integer> a = null;
			Tuple<FieldNode, Integer> b = null;
			synchronized(map){
				final String expectedDesc = 'L' + cn.name + ';';
				for(FieldNode fn : cn.fields){
					if(fn.desc.equals(expectedDesc)){
						if(a == null){
							a = new Tuple<>(fn, map.get(fn).size());
						}else if(b == null){
							b = new Tuple<>(fn, map.get(fn).size());
						}else{
							throw new IllegalStateException();
						}
					}
				}
			}
			assert a != null;
			assert b != null;
			if(a.val2 > b.val2){
				hooks.put("NodeSub_Next", cn.name + '.' + a.val1.name);
				hooks.put("NodeSub_Prev", cn.name + '.' + b.val1.name);
			}else{
				hooks.put("NodeSub_Next", cn.name + '.' + b.val1.name);
				hooks.put("NodeSub_Prev", cn.name + '.' + a.val1.name);
			}
		}
	}

	public class HASHTABLE_HASHTABLEBUCKETS implements Runnable {

		@Override
		public void run(){
			try{
				waitFor(NODEID_LINKEDLISTTAIL_LINKEDLISTNEXT.class);
				final String expectedNodeDesc = 'L' + wrappers.get("Node") + ';';
				for(ClassNode cn : bytecode.getClassNodes()){
					if(wrappers.containsKey(cn.name)) continue;
					int c = 0;
					FieldNode buckets = null;
					for(FieldNode fn : cn.fields){
						if(fn.desc.equals(expectedNodeDesc)){
							c++;
						}else if(fn.desc.substring(1).equals(expectedNodeDesc)){
							buckets = fn;
						}
					}
					if(buckets != null && c >= 2){
						wrappers.put("Hashtable", cn.name);
						hooks.put("Hashtable_Buckets", buckets.toString().split(" ")[0]);
						return;
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}

	}

	public class RSTILEDATAHEIGHTS implements Runnable {

		@Override
		public void run() {
			waitFor(RSGROUNDINFORSTILEDATAARRAY_RSGROUNDINFORSGROUNDARRAY.class);
			final ClassNode cn = getClassNode("RSTileData");
			for(FieldNode fn : cn.fields){
				if(fn.desc.equals("[[I")){
					hooks.put("RSTileData_Heights", cn.name + '.' + fn.name);
					return;
				}
			}
		}

	}

	public class RSANIMABLEX1_RSANIMABLEY1_RSANIMABLEX2_RSANIMABLEY2 implements Runnable {

		@Override
		public void run() {
			waitFor(RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA.class);
			final ClassNode cn = getClassNode("RSAnimable");
			outer: for(MethodNode mn : cn.methods){
				AbstractInsnNode ain = mn.instructions.getFirst();
				if(Modifier.isStatic(mn.access) || ain == null) continue;

				String x1 = null, x2 = null;
				String y1 = null, y2 = null;

				while(ain != null){
					if(ain.getOpcode() == Opcodes.GETFIELD){
						final FieldInsnNode fin = (FieldInsnNode) ain;
						if(fin.owner.equals(cn.name) && fin.desc.charAt(0) == 'S'){
							if(x1 == null){
								if(fin.getNext().getOpcode() == Opcodes.ISTORE){
									x1 = fin.owner + '.' + fin.name;
								}else{
									continue outer;
								}
							}else if(x2 == null){
								if(fin.getNext().getOpcode() >= Opcodes.IF_ICMPLT && fin.getNext().getOpcode() <= Opcodes.IF_ICMPLE){
									x2 = fin.owner + '.' + fin.name;
								}else{
									continue outer;
								}
							}else if(y1 == null){
								if(fin.getNext().getOpcode() == Opcodes.ISTORE){
									y1 = fin.owner + '.' + fin.name;
								}else{
									continue outer;
								}
							}else if(fin.getNext().getOpcode() >= Opcodes.IF_ICMPLT && fin.getNext().getOpcode() <= Opcodes.IF_ICMPLE){ //y2 null by logic here
								y2 = fin.owner + '.' + fin.name;
								break; //add hooks
							}
						}
					}
					ain = ain.getNext();
				}
				if(x1 == null || x2 == null || y1 == null || y2 == null)
					continue;
				//put hooks
				hooks.put("RSAnimable_X1", x1);
				hooks.put("RSAnimable_X2", x2);
				hooks.put("RSAnimable_Y1", y1);
				hooks.put("RSAnimable_Y2", y2);
				break;
			}
		}

	}

	public class RSCHARACTERANIMATION_RSCHARACTERPASSIVEANIMATION implements Runnable {

		@Override
		public void run(){
			try{
				waitFor(RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA.class);
				final ClassNode cn = getClassNode("RSCharacter");
				String c = null;
				String d = null;
				outer: for(MethodNode mn : cn.methods){
					if(Modifier.isStatic(mn.access) || !mn.name.equals("<init>")) continue;
					AbstractInsnNode ain = mn.instructions.getFirst();
					while(ain != null){
						if(ain.getOpcode() == Opcodes.INVOKESPECIAL){
							if((ain.getPrevious().getOpcode() == Opcodes.ICONST_0 || ain.getPrevious().getOpcode() == Opcodes.ICONST_1)
									&& ain.getPrevious().getPrevious().getOpcode() == Opcodes.ALOAD){
								if(c == null){
									c = cn.name + '.' + ((FieldInsnNode)ain.getNext()).name;
								}else{
									d = cn.name + '.' + ((FieldInsnNode)ain.getNext()).name;
									break outer;
								}
							}
						}
						ain = ain.getNext();
					}
				}
				assert c != null;
				assert d != null;
				final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();
				Tuple<FieldNode, Integer> a = null;
				Tuple<FieldNode, Integer> b = null;
				synchronized(map){
					for(FieldNode fn : cn.fields){
						if(fn.toString().startsWith(c)){
							a = new Tuple<>(fn, map.get(fn).size());
						}else if(fn.toString().startsWith(d)){
							b = new Tuple<>(fn, map.get(fn).size());
						}
					}
				}
				assert a != null;
				assert b != null;
				if(a.val2 > b.val2){
					hooks.put("RSCharacter_Animation", cn.name + '.' + a.val1.name);
					hooks.put("RSCharacter_PassiveAnimation", cn.name + '.' + b.val1.name);
				}else if(b.val2 > a.val2){
					hooks.put("RSCharacter_Animation", cn.name + '.' + b.val1.name);
					hooks.put("RSCharacter_PassiveAnimation", cn.name + '.' + a.val1.name);
				}else{
					throw new IllegalStateException();
				}
				ClassNode type = getClassNode(a.val1.desc.substring(1, a.val1.desc.length() - 1));
				if(!type.superName.equals("java/lang/Object")){
					type = getClassNode(type.superName);
				}
				wrappers.put("RSAnimator", type.name);
			}catch(Exception e){
				e.printStackTrace();
			}
		}

	}

	public class RSANIMATORANIMATIONSEQUENCE_ANIMATIONSEQUENCEID implements Runnable {

		@Override
		public void run() {
			waitFor(RSCHARACTERANIMATION_RSCHARACTERPASSIVEANIMATION.class);
			final ClassNode cn = getClassNode("RSAnimator");
			final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();
			FieldNode animationSequence = null;
			synchronized(map){
				for(FieldNode fn : cn.fields){
					if(Modifier.isStatic(fn.access) || fn.desc.charAt(0) != 'L') continue;
					if(animationSequence == null || map.get(fn).size() > map.get(animationSequence).size()){
						animationSequence = fn;
					}
				}
			}
			assert animationSequence != null;
			hooks.put("RSAnimator_AnimationSequence", cn.name + '.' + animationSequence.name);
			wrappers.put("AnimationSequence", animationSequence.desc.substring(1, animationSequence.desc.length() - 1));

			final Map<String, Integer> possibleIdAccessCount = new HashMap<>();
			methods: for(MethodNode mn : cn.methods){
				if(Modifier.isStatic(mn.access)) continue;
				AbstractInsnNode ain = mn.instructions.getFirst();
				while(ain != null){
					if(ain.getOpcode() == Opcodes.GETFIELD && ain.getPrevious().getOpcode() == Opcodes.GETFIELD){
						final FieldInsnNode fin = (FieldInsnNode) ain;
						if(fin.owner.equals(wrappers.get("AnimationSequence")) && fin.desc.charAt(0) == 'I'){
							Integer i = possibleIdAccessCount.remove(fin.name);
							if(i == null)
								i = 0;
							possibleIdAccessCount.put(fin.name, i + 1);
							continue methods;
						}
					}
					ain = ain.getNext();
				}
			}
			final String animatorId = MapUtils.sortMapHighToLow(possibleIdAccessCount, new String[1])[0];
			final Number multiplier = getPossibleMultipliers(new Filter<FieldInsnNode>(){
				@Override
				public boolean accept(FieldInsnNode t) {
					return t.owner.equals(wrappers.get("AnimationSequence")) && t.name.equals(animatorId);
				}

			}, cn)[0];
			hooks.put("AnimationSequence_ID", wrappers.get("AnimationSequence") + '.' + animatorId);
			multipliers.put("AnimatorSequence_ID", multiplier);
		}
	}

	public class RSCHARACTERCOMBATSTATUSLIST implements Runnable {

		@Override
		public void run() {
			waitFor(RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA.class);
			final ClassNode cn = getClassNode("RSCharacter");
			final String expectedDesc = 'L' + wrappers.get("LinkedList") + ';';
			for(FieldNode fn : cn.fields){
				if(fn.desc.equals(expectedDesc)){
					hooks.put("RSCharacter_CombatStatusList", cn.name + '.' + fn.name);
					return;
				}
			}
		}

	}

	public class RSCHARACTERRSMESSAGEDATA implements Runnable {
		@Override
		public void run() {
			waitFor(RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA.class);
			final ClassNode cn = getClassNode("RSCharacter");
			for(FieldNode fn : cn.fields){
				if(Modifier.isStatic(fn.access) || fn.desc.charAt(0) != 'L') continue;
				final ClassNode fnCn = getClassNode(fn.desc.substring(1, fn.desc.length() - 1));
				for(FieldNode fnCnFn : fnCn.fields){
					if(fnCnFn.desc.equals("Ljava/lang/String;")){
						hooks.put("RSCharacter_RSMessageData", cn.name + '.' + fn.name);
						wrappers.put("RSMessageData", fnCn.name);
						hooks.put("RSMessageData_Message", fnCn.name + '.' + fnCnFn.name);
						return;
					}
				}
			}
		}
	}

	public class RSNPCHASTABLE implements Runnable {

		@Override
		public void run() {
			waitFor(HASHTABLE_HASHTABLEBUCKETS.class);
			final ClassNode cn = getClassNode("client");
			final String expectedDesc = 'L' + wrappers.get("Hashtable") + ';';
			final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();
			FieldNode mostAccessedHashtable = null;
			synchronized(map){
				for(FieldNode fn : cn.fields){
					if(Modifier.isStatic(fn.access) && fn.desc.equals(expectedDesc)){
						if(mostAccessedHashtable == null || map.get(fn).size() > map.get(mostAccessedHashtable).size()){
							mostAccessedHashtable = fn;
						}
					}
				}
			}
			assert mostAccessedHashtable != null;
			hooks.put("RSNPCHashtable", cn.name + '.' + mostAccessedHashtable.name);
		}

	}

	public class RSCHARACTERINTERACTING implements Runnable {
		@Override
		public void run() {
			waitFor(RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA.class,
					RSNPCHASTABLE.class, RSPLAYERARRAY_RSPLAYERINDEXARRAY.class);
			final ClassNode cn = getClassNode("RSCharacter");
			final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();
			synchronized(map){
				for(FieldNode fn : cn.fields){
					if(Modifier.isStatic(fn.access) || fn.desc.charAt(0) != 'I') continue;
					final ArrayList<MethodNode> accesses = map.get(fn);
					if(accesses != null){
						int count = 0;
						for(MethodNode mn : accesses){
							if(!mn.toString().startsWith("client")){
								count++;
							}
						}
						if(count > 0){
							for(MethodNode mn : accesses){
								//check to see if it accesses RSNPCHashtable and RSPlayerArray
								FieldInsnNode hashtableAccess = null;
								FieldInsnNode playerArrayAccess = null;
								AbstractInsnNode ain = mn.instructions.getFirst();
								while(ain != null){
									if(ain.getOpcode() == Opcodes.GETSTATIC){
										final FieldInsnNode fin = (FieldInsnNode) ain;
										if(hooks.get("RSNPCHashtable").equals(fin.owner + '.' + fin.name)){
											hashtableAccess = fin;
										}else if(hooks.get("RSPlayerArray").equals(fin.owner + '.' + fin.name)){
											playerArrayAccess = fin;
										}
									}
									ain = ain.getNext();
								}

								if(hashtableAccess != null && playerArrayAccess != null){
									try{
										AbstractInsnNode insn = hashtableAccess;
										while((insn = insn.getNext()).getOpcode() != Opcodes.GETFIELD);
										final FieldInsnNode a = (FieldInsnNode) insn;
										insn = playerArrayAccess;
										while((insn = insn.getNext()).getOpcode() != Opcodes.GETFIELD);
										final FieldInsnNode b = (FieldInsnNode) insn;
										assert a.name.equals(b.name) && a.owner.equals(b.owner);

										final Number multiplier;
										if(a.getNext().getOpcode() == Opcodes.IMUL){
											multiplier = (Number)((LdcInsnNode)a.getPrevious().getPrevious()).cst;
										}else{
											multiplier = (Number)((LdcInsnNode)a.getNext()).cst;
										}

										hooks.put("RSCharacter_Interacting", a.owner + '.' + a.name);
										multipliers.put("RSCharacter_Interacting", multiplier);
										return;
									}catch(Exception e){

									}
								}
							}
						}
					}
				}
			}
		}
	}

	public class RSCHARACTERSPEED implements Runnable {

		@Override
		public void run() {
			try{
				waitFor(RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA.class,
						MYRSPLAYER_RSPLAYERNAME.class, RSNPC.class);
				final Object myPlayer = getHookObject("MyRSPlayer", null);
				final Class<?> clazz = Hooks.this.getClass("RSCharacter");
				final ArrayList<Field> possibleSpeedFields = new ArrayList<>();
				getInput("Make sure you are logged in and NOT moving");
				for(Field field : clazz.getDeclaredFields()){
					if(!Modifier.isStatic(field.getModifiers()) && field.getType() == int.class){
						if(!field.isAccessible()) field.setAccessible(true);
						int i = field.getInt(myPlayer);
						if(i == 0){
							possibleSpeedFields.add(field);
						}
					}
				}
				while(possibleSpeedFields.size() > 1){
					getInput("Make sure you are moving when you close this dialog...");
					for(int idx = 0; idx < possibleSpeedFields.size(); idx++){
						int i = possibleSpeedFields.get(idx).getInt(myPlayer);
						if(i == 0){
							possibleSpeedFields.remove(idx--);
						}
					}
					getInput("Make sure you are logged in and NOT moving");
					for(int idx = 0; idx < possibleSpeedFields.size(); idx++){
						int i = possibleSpeedFields.get(idx).getInt(myPlayer);
						if(i != 0){
							possibleSpeedFields.remove(idx--);
						}
					}
				}
				final Field speedField = possibleSpeedFields.get(0);
				hooks.put("RSCharacter_Speed", clazz.getName() + '.' + speedField.getName());
				/*multipliers.put("RSCharacter_Speed", getPossibleMultipliers(new Filter<FieldInsnNode>(){
					@Override
					public boolean accept(FieldInsnNode t) {
						return t.owner.equals(wrappers.get("RSCharacter")) && t.desc.charAt(0) == 'I'
								&& t.name.equals(speedField.getName());
					}
				}, getClassNode("RSNPC"), getClassNode("RSPlayer"), getClassNode("RSCharacter"))[0]);*/
			}catch(Exception e){
				e.printStackTrace();
			}
		}

	}

	public class RSCHARACTERORIENTATION implements Runnable {

		@Override
		public void run() {
			try{
				waitFor(RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA.class,
						MYRSPLAYER_RSPLAYERNAME.class);
				Object player = Hooks.this.getHookObject("MyRSPlayer", null);
				viewStuff(Hooks.this.getClass("RSCharacter"), player, null, 200);
				getInput("Please face north");
				Tuple<Field, Number> multiplier = getMultipliedHookIntsOnly(Hooks.this.getHookObject("MyRSPlayer", null), 8192);
				hooks.put("RSCharacter_Orientation", Hooks.this.getClass("RSCharacter").getName() + '.' + multiplier.val1.getName());
				multipliers.put("RSCharacter_Orientation", multiplier.val2);
			}catch(Exception e){
				e.printStackTrace();
			}
		}

	}
	
	private void viewStuff(Class<?> clazz, Object instance, Filter<Field> fieldFilter, long refreshRate){
		final JFrame frame = new JFrame("Kill me now");
		final JPanel content = new JPanel(new GridBagLayout());
		final GridBagConstraints c = new GridBagConstraints();
		final Map<Field, JLabel> map = new HashMap<>();
		final Map<Field, Number> multipliers = new HashMap<>();
		for(final Field field : clazz.getDeclaredFields()){
			if(fieldFilter != null && !fieldFilter.accept(field)) continue;
			field.setAccessible(true);
			boolean sdfsdf = false;
			if(field.getType() == int.class){
				final Number[] possible = getPossibleMultipliers(new Filter<FieldInsnNode>(){
					@Override
					public boolean accept(FieldInsnNode t) {
						return field.getName().equals(t.name) && t.owner.equals(field.getDeclaringClass().getName());
					}
				}, getClassNode(clazz.getSimpleName()));
				if(possible.length > 0){
					sdfsdf = true;
					multipliers.put(field, possible[0]);
				}
			}
			if(!sdfsdf && field.getType() != String.class)continue; 
			JLabel label = new JLabel(field.toString() + (sdfsdf ? " (m) = " : " = "));
			content.add(label, c);
			c.gridx += 5;
			label = new JLabel("Unknown");
			content.add(label, c);
			map.put(field, label);
			c.gridx -= 5;
			c.gridy += 5;
		}
		frame.setContentPane(new JScrollPane(content));
		final AtomicBoolean ab = new AtomicBoolean(true);
		frame.addWindowListener(new WindowAdapter(){
			@Override
			public void windowClosing(WindowEvent e) {
				frame.setVisible(false);
				frame.dispose();
				ab.set(false);
			}
		});
		frame.setSize(300, 800);
		frame.setVisible(true);
		try {
			while(ab.get()){
				for(Field field : map.keySet()){
					if(field.getType() == int.class){
						Number multiplier = multipliers.get(field);
						map.get(field).setText(Integer.toString(field.getInt(instance) * (multiplier == null ? 1 : multiplier.intValue())));
					}else{
						map.get(field).setText(String.valueOf(field.get(instance)));
					}
				}
				Thread.sleep(refreshRate);
			}
		} catch (ReflectiveOperationException | InterruptedException e1) {
			e1.printStackTrace();
		}
	}

	/*public class RSCHARACTERORIENTATIONVIEWER implements Runnable {

		private boolean running = true;

		@Override
		public void run() {
			waitFor(MYRSPLAYER_RSPLAYERNAME.class, RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA.class, RSNPC.class);
			final JFrame frame = new JFrame("Kill me now");
			final JPanel content = new JPanel(new GridBagLayout());
			final GridBagConstraints c = new GridBagConstraints();
			final Map<Field, JLabel> map = new HashMap<>();
			final Map<Field, Number> multipliers = new HashMap<>();
			for(final Field field : Hooks.this.getClass("RSCharacter").getDeclaredFields()){
				if(Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) continue;
				field.setAccessible(true);
				final Number[] possible = getPossibleMultipliers(new Filter<FieldInsnNode>(){
					@Override
					public boolean accept(FieldInsnNode t) {
						return field.getName().equals(t.name) && t.owner.equals(field.getDeclaringClass().getName());
					}
				}, getClassNode("RSCharacter"), getClassNode("RSNPC"), getClassNode("RSPlayer"));
				if(possible.length > 0){
					JLabel label = new JLabel(field.toString() + " = ");
					content.add(label, c);
					c.gridx += 5;
					label = new JLabel("Unknown");
					content.add(label, c);
					map.put(field, label);
					multipliers.put(field, possible[0]);
					c.gridx -= 5;
					c.gridy += 5;
				}
			}

			frame.setContentPane(content);

			frame.addWindowListener(new WindowAdapter(){
				@Override
				public void windowClosing(WindowEvent e) {
					frame.setVisible(false);
					frame.dispose();
					running = false;
				}
			});
			frame.pack();
			frame.setVisible(true);
			try {
				final Object myPlayer = getHookObject("MyRSPlayer", null);
				while(running){
					for(Field field : map.keySet()){
						map.get(field).setText(Integer.toString(field.getInt(myPlayer) * multipliers.get(field).intValue()));
					}
					Thread.sleep(50);
				}
			} catch (ReflectiveOperationException | InterruptedException e1) {
				e1.printStackTrace();
			}
		}
	}*/

	public class RSPLAYERCOMPOSITE implements Runnable {

		@Override
		public void run() {
			waitFor(MYRSPLAYER_RSPLAYERNAME.class);
			final ClassNode cn = getClassNode("RSPlayer");
			for(FieldNode fn : cn.fields){
				if(Modifier.isStatic(fn.access) || fn.desc.charAt(0) != 'L') continue;
				final String type = fn.desc.substring(1, fn.desc.length() - 1);
				if(!wrappers.containsValue(type) && !type.equals("java/lang/String")){
					wrappers.put("RSPlayerComposite", type);
					hooks.put("RSPlayer_RSPlayerComposite", cn.name + '.' + fn.name);
					return;
				}
			}
		}

	}

	public class RSPLAYERCOMPOSITEEQUIPMENT implements Runnable {

		@Override
		public void run() {
			waitFor(RSPLAYERCOMPOSITE.class);
			final ClassNode cn = getClassNode("RSPlayerComposite");
			for(FieldNode fn : cn.fields){
				if(Modifier.isStatic(fn.access) || !fn.desc.equals("[I")) continue;
				hooks.put("RSPlayerComposite_Equipment", cn.name + '.' + fn.name);
				return;
			}
		}
	}

	public class RSNPCDEF implements Runnable {

		@Override
		public void run() {
			waitFor(RSNPC.class);
			final ClassNode cn = getClassNode("RSNPC");
			final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();
			FieldNode defFieldNode = null;
			synchronized(map){
				for(FieldNode fn : cn.fields){
					if(Modifier.isStatic(fn.access) || fn.desc.charAt(0) != 'L') continue;
					if(defFieldNode == null || map.get(fn).size() > map.get(defFieldNode).size()){
						defFieldNode = fn;
					}
				}
			}
			assert defFieldNode != null;
			hooks.put("RSNPC_RSNPCDef", cn.name + '.' + defFieldNode.name);
			wrappers.put("RSNPCDef", defFieldNode.desc.substring(1, defFieldNode.desc.length() - 1));
		}

	}

	public class RSNPCDEFNAME_RSNPCLEVEL_RSNPCDEFACTIONS_RSNPCDEFID implements Runnable {

		@Override
		public void run() {
			try{
				waitFor(RSNPCDEF.class, RSNPCNODE_RSNPCNODERSNPC_RSNPCNODEARRAY.class);
				final Object[] npcNodes = (Object[]) getHookObject("RSNPCNodeArray", null);
				final String input = getInput("Please name one NPC and their level separated by a dash");
				final int index = input.indexOf('-');
				final String name = input.substring(0, index);
				final int level = Integer.parseInt(input.substring(index + 1));
				for(Object node : npcNodes){
					if(node == null)
						break;
					Object rsnpc = getHookObject("RSNPCNode_RSNPC", node);
					if(rsnpc == null) continue;
					Object def = getHookObject("RSNPC_RSNPCDef", rsnpc);
					if(def == null) continue;
					for(final Field defField : def.getClass().getDeclaredFields()){
						if(Modifier.isStatic(defField.getModifiers())) continue;
						if(!defField.isAccessible()) defField.setAccessible(true);
						if(defField.getType() == String.class && (hooks.get("RSNPCDef_Name") == null
								|| hooks.get("RSNPC_Level") == null)){
							String s = (String) defField.get(def);
							if(s.equals(name)){
								hooks.put("RSNPCDef_Name", def.getClass().getName() + '.' + defField.getName());
								final Tuple<Field, Number> levelHook = getMultipliedHookIntsOnly(rsnpc, level);
								if(levelHook != null){
									hooks.put("RSNPC_Level", rsnpc.getClass().getName() + '.' + levelHook.val1.getName());
									multipliers.put("RSNPC_Level", levelHook.val2);
								}
								final ArrayList<Tuple<Integer, Tuple<Field, Number>>> possibleIds = new ArrayList<>();
								for(final Field defField2 : def.getClass().getDeclaredFields()){
									if(Modifier.isStatic(defField2.getModifiers()) || defField2.getType() != int.class) continue;
									if(!defField2.isAccessible()) defField2.setAccessible(true);
									final int possibleIdUnmultiplied = defField2.getInt(def);
									final Number[] multipliers = getPossibleMultipliers(new Filter<FieldInsnNode>(){
										@Override
										public boolean accept(FieldInsnNode t) {
											return t.name.equals(defField2.getName());
										}

									}, getClassNode("RSNPCDef"));
									for(Number multiplier : multipliers){
										possibleIds.add(new Tuple<>(possibleIdUnmultiplied * multiplier.intValue(),
												new Tuple<>(defField2, multiplier)));
									}
								}
								final StringBuilder prompt = new StringBuilder("Please type in the ID you see below most likely to belong to ");
								prompt.append(name).append(":\n\n");
								for(Tuple<Integer, Tuple<Field, Number>> t : possibleIds){
									prompt.append(t.val1.toString()).append(", ");
								}
								final int result = Integer.parseInt(getInput(prompt.toString()));
								for(Tuple<Integer, Tuple<Field, Number>> t : possibleIds){
									if(t.val1.intValue() == result){
										hooks.put("RSNPCDef_ID", wrappers.get("RSNPCDef") + '.' + t.val2.val1.getName());
										multipliers.put("RSNPCDef_ID", t.val2.val2);
										break;
									}
								}
							}
						}else if(defField.getType() == String[].class && hooks.get("RSNPCDef_Actions") == null){
							String[] sArr = (String[]) defField.get(def);
							for(String s : sArr){
								if(s != null && s.toLowerCase().contains("attack")){
									hooks.put("RSNPCDef_Actions", def.getClass().getName() + '.' + defField.getName());
									break;
								}
							}
						}
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	public class RSOBJECTS implements Runnable {

		@Override
		public void run() {
			waitFor(RSANIMABLENODENEXT_RSANIMABLENODERSANIMBALE.class,
					RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA.class);
			try {
				final Class<?> rsInteractableClazz = Hooks.this.getClass("RSInteractable");
				final Set<Class<?>> classes = new HashSet<>();
				System.err.println("Finding RSObject classes");
				final Object[][][] ground = (Object[][][]) getHookObject("RSGroundInfo_RSGroundArray", getHookObject("RSInfo_RSGroundInfo", getHookObject("RSInfo", null)));
				for(int plane = 0; plane < ground.length; plane++){
					for(int x = 0; x < ground[plane].length; x++){
						for(int y = 0; y < ground[plane][x].length; y++){
							final Object rsGround = ground[plane][x][y];
							if(rsGround != null){
								for(Object n = getHookObject("RSGround_RSAnimableNode", rsGround); n != null; n = getHookObject("RSAnimableNode_Next", n)){
									final Object animable = getHookObject("RSAnimableNode_RSAnimable", n);
									if(animable != null && rsInteractableClazz.isInstance(animable) && !wrappers.containsValue(animable.getClass().getName())){
										classes.add(animable.getClass());
									}
								}
								for(Field field : rsGround.getClass().getDeclaredFields()){
									if(Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()
											|| field.getType().isArray())
										continue;
									if(!field.isAccessible()) field.setAccessible(true);
									final Object obj = field.get(rsGround);
									if(obj != null && rsInteractableClazz.isInstance(obj)){
										classes.add(obj.getClass());
									}
								}
							}
						}
					}
				}
				final int[] counts = new int[Character.MAX_VALUE];
				for(Class<?> clazz : classes){
					counts[clazz.getName().charAt(0)]++;
				}
				char max = 0;
				int c;
				for(c = 1; c < Character.MAX_VALUE; c++){
					if(counts[c] > counts[max]){
						max = (char) c;
					}
				}
				for(Class<?> clazz : classes.toArray(new Class<?>[classes.size()])){ //so we can modify
					if(clazz.getName().charAt(0) != max){
						classes.remove(clazz);
					}
				}
				System.err.println(classes);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}


	//TODO rest of RSObject stuff

	public class SELECTEDITEMNAME implements Runnable {

		@Override
		public void run() {
			try{
				final String selectedName = getInput("Please select an item in your inventory and type the name of that item here")
						.toLowerCase();
				outer: for(Class<?> clazz : runtime.getAllClasses()){
					for(Field field : clazz.getDeclaredFields()){
						if(Modifier.isStatic(field.getModifiers()) && field.getType().equals(String.class)){
							if(!field.isAccessible())
								field.setAccessible(true);
							final String s = (String) field.get(null);
							if(s != null && s.toLowerCase().contains(selectedName)){
								hooks.put("SelectedItemName", clazz.getName() + '.' + field.getName());
								break outer;
							}
						}
					}
				}
			}catch(Exception e){
				System.err.println("Error in SELECTEDITEMNAME");
				e.printStackTrace();
			}
		}

	}

	public class RSINTERFACECACHE_RSINTERFACEBASE_RSINTERFACEBASECOMPONENTS_RSINTERFACE implements Runnable {

		@Override
		public void run(){
			final Map<FieldNode, ArrayList<MethodNode>> map = bytecode.getRelationshipSet().getFieldAccessorsMap();
			String maxString = null;
			FieldNode max = null;
			synchronized(map){
				for(ClassNode cn : bytecode.getClassNodes()){
					for(FieldNode fn : cn.fields){
						if(fn.desc.startsWith("[L") && Modifier.isStatic(fn.access)){
							if(max == null || map.get(fn).size() > map.get(max).size()){
								max = fn;
								maxString = cn.name + '.' + fn.name;
							}
						}
					}
				}
			}
			assert max != null && maxString != null;
			hooks.put("RSInterfaceBaseArray", maxString);
			String classType = max.desc.substring(2, max.desc.length() - 1); //desc = "[L<class name here>;", isolate the class name
			wrappers.put("RSInterfaceBase", classType);

			final ClassNode cn = getClassNode(classType);
			max = null;
			synchronized(map){
				for(FieldNode fn : cn.fields){
					if(!Modifier.isStatic(fn.access) && fn.desc.startsWith("[L")){
						if(max == null || map.get(fn).size() > map.get(max).size()){
							max = fn;
						}
					}
				}
			}
			assert max != null;
			hooks.put("RSInterfaceBase_Components", classType + '.' + max.name);
			classType = max.desc.substring(2, max.desc.length() - 1);
			wrappers.put("RSInterface", classType);
		}

	}

	public void run(){

		Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run(){
				System.out.println("Wrappers (" + wrappers.size() + "): " + wrappers);
				System.out.println("Hooks (" + hooks.size() +"): " + hooks);
				System.out.println("Multipliers (" + multipliers.size() + "): " + multipliers);
			}
		});

		run(
				new CURRENTDISPLAYNAME(), 
				new MYRSPLAYER_RSPLAYERNAME(), 
				new RSPLAYERLEVEL(),
				new NODEID_LINKEDLISTTAIL_LINKEDLISTNEXT(), 
				new NODENEXT_NODEPREVIOUS(),
				new LINKEDLISTTAIL_LINKEDLISTNODENEXT_LINKEDLISTNODEPREVIOUS(), 
				new RSCHARACTER_RSANIMABLE_RSINTERACTABLE_RSINTERACTABLEDATA(),
				new RSNPC(), 
				new RSPLAYERARRAY_RSPLAYERINDEXARRAY(), 
				new RSNPCNODE_RSNPCNODERSNPC_RSNPCNODEARRAY(),
				new RSINFO(), 
				//new RSINFOGROUNDDATA(), 
				new RSINFOBASEINFO(), 
				new RSBASEINFOX_RSBASEINFOY(),
				new CANVAS(), 
				new RSINTERACTABLEPLANE(), 
				new RSINTERACTABLELOCATIONCONTAINER_LOCATIONCONTAINERLOCATION(),
				new LOCATIONX_LOCATIONY(),
				new SETTINGSDATA(), 
				//new GROUNDDATABLOCKS_GROUNDDATAX_GROUNDDATAY(), 
				new RSINFOGROUNDBYTES(),
				new RSINFORSOBJECTDEFLOADERS_SOFTREFERENCE_HARDREFERENCE_RSMODEL_CACHE(), 
				new HASHTABLE_HASHTABLEBUCKETS(), 
				new RSNPCHASTABLE(),
				new NODESUB(), 
				new NODESUBNEXT_NODESUBPREVIOUS(), 
				new RSINFORSGROUNDINFO(), 
				new RSGROUNDINFORSTILEDATAARRAY_RSGROUNDINFORSGROUNDARRAY(),
				new RSGROUNDRSANIMABLELIST(), 
				new RSANIMABLENODENEXT_RSANIMABLENODERSANIMBALE(), 
				new RSTILEDATAHEIGHTS(),
				new RSANIMABLEX1_RSANIMABLEY1_RSANIMABLEX2_RSANIMABLEY2(), 
				new RSCHARACTERANIMATION_RSCHARACTERPASSIVEANIMATION(),
				new RSANIMATORANIMATIONSEQUENCE_ANIMATIONSEQUENCEID(), 
				new RSCHARACTERCOMBATSTATUSLIST(),
				new RSCHARACTERRSMESSAGEDATA(),
				new RSCHARACTERINTERACTING(), 
				new RSCHARACTERSPEED(),
				new RSCHARACTERORIENTATION(), 
				new RSPLAYERCOMPOSITE(), 
				new RSPLAYERCOMPOSITEEQUIPMENT(), 
				new RSNPCDEF(),
				new RSNPCDEFNAME_RSNPCLEVEL_RSNPCDEFACTIONS_RSNPCDEFID(),
				new RSOBJECTS(),
				/*Rest of RSObjects*/


				new SELECTEDITEMNAME(), 
				new RSINTERFACECACHE_RSINTERFACEBASE_RSINTERFACEBASECOMPONENTS_RSINTERFACE());

		synchronized(completionLock){
			try {
				completionLock.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
//6-17-2013: 3375 lines of code