package updater.staticanalysis.stages;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

public class ExceptionRemoverStage {

	private static int round = 0;
	public static void removeAllExceptions(ClassNode[] classes) {
		final String bannedMethod = getBannedMethod(classes);
		if(bannedMethod == null){
			round = 0;
			return;
		}
		int classesModified = 0;
		int totalRemoved = 0;
		for(ClassNode cn : classes){
			int removedCount = 0;
			for(MethodNode mn : cn.methods){
				AbstractInsnNode ain = mn.instructions.getFirst();
				while(ain != null){
					if(ain.getOpcode() == Opcodes.INVOKESTATIC){
						final MethodInsnNode min = (MethodInsnNode) ain;
						if(bannedMethod.equals(min.owner + '.' + min.name + min.desc)){
							final Map<LabelNode, TryCatchBlockNode> handlers = new HashMap<>();
							for(TryCatchBlockNode tcbn : mn.tryCatchBlocks){
								if(tcbn.type != null){
									handlers.put(tcbn.handler, tcbn);
								}
							}

							int upperIndex = mn.instructions.indexOf(ain);
							int lowerIndex = upperIndex;

							//get upper bound
							AbstractInsnNode end = ain;
							while(end.getNext() != null){
								end = end.getNext();
								upperIndex++;
								if(end.getOpcode() == Opcodes.ATHROW || (end.getOpcode() >= Opcodes.IRETURN && end.getOpcode() <= Opcodes.RETURN)){
									break;
								}
							}
							//lol
							AbstractInsnNode beginning = ain;
							while(beginning.getPrevious() != null){
								beginning = beginning.getPrevious();
								lowerIndex--;
								if(beginning.getType() == AbstractInsnNode.LABEL){
									if(handlers.containsKey(beginning)){
										mn.tryCatchBlocks.remove(handlers.get(beginning));
										break;
									}
								}
							}
							for(int idx = upperIndex; idx >= lowerIndex; idx--){
								mn.instructions.remove(mn.instructions.get(idx));
							}
							removedCount++;
						}
					}
					if(ain instanceof LineNumberNode){
						final AbstractInsnNode n = ain.getNext();
						mn.instructions.remove(ain);
						ain = n;
					}else{
						ain = ain.getNext();
					}
				}
			}
			if(removedCount > 0){
				classesModified++;
				totalRemoved += removedCount;
			}
		}

		System.out.println("Removed a total of " + totalRemoved + " exceptions in " + classesModified + " classes in round " + ++round + ".");
		removeAllExceptions(classes);
	}

	private static String getBannedMethod(ClassNode[] classes){
		for(ClassNode cn : classes){
			for(MethodNode mn : cn.methods){
				if(mn.instructions.size() > 0){//non abstract
					if(mn.instructions.getLast().getOpcode() == Opcodes.ATHROW){
						final AbstractInsnNode candidate = mn.instructions.getLast().getPrevious();
						if(candidate.getOpcode() == Opcodes.INVOKESTATIC){
							final MethodInsnNode min = (MethodInsnNode) candidate;
							final String methodInvocation = min.owner + '.' + min.name + min.desc;
							return methodInvocation;
						}
					}
				}
			}
		}
		return null;
	}

}
