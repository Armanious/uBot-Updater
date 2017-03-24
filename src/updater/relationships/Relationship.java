package updater.relationships;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.armanious.Tuple;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class Relationship {

	final ClassNode cn;

	/**
	 * keys are the methods; values are the fields
	 */
	final Map<MethodNode, Tuple<ArrayList<FieldNode>, ArrayList<String>>> accesses;

	Relationship(ClassNode cn){
		this.cn = new OverrideClassNode(cn);

		final List<MethodNode> methodsList = cn.methods;
		cn.methods = new ArrayList<>();
		for(int i = 0; i < methodsList.size(); i++){
			cn.methods.add(new OverrideMethodNode(cn.name, methodsList.get(i)));
		}
		final List<FieldNode> fieldsList = cn.fields;
		cn.fields = new ArrayList<>();
		for(int i = 0; i < fieldsList.size(); i++){
			cn.fields.add(new OverrideFieldNode(cn.name, fieldsList.get(i)));
		}

		accesses = new HashMap<>();

		for(MethodNode mn : cn.methods){
			AbstractInsnNode insn = mn.instructions.getFirst();
			Tuple<ArrayList<FieldNode>, ArrayList<String>> tuple = accesses.get(mn);
			if(tuple == null){
				tuple = new Tuple<>(new ArrayList<FieldNode>(), new ArrayList<String>());
				accesses.put(mn, tuple);
			}
			while(insn != null){
				if(insn.getOpcode() == Opcodes.GETSTATIC || insn.getOpcode() == Opcodes.GETFIELD){
					final FieldNode s = new OverrideFieldNode((FieldInsnNode)insn);
					if(!tuple.val1.contains(s)){
						tuple.val1.add(s);
					}
				}else if(insn instanceof MethodInsnNode){
					final String s = ((MethodInsnNode)insn).owner + '.' + ((MethodInsnNode)insn).name + ((MethodInsnNode)insn).desc;
					if(!tuple.val2.contains(s)){
						tuple.val2.add(s);
					}
				}
				insn = insn.getNext();
			}
		}
	}

	private static class OverrideClassNode extends ClassNode implements Comparable<OverrideClassNode> {

		public OverrideClassNode(ClassNode cn){
			cn.accept(this);
		}

		@Override
		public String toString() {
			return this.name;
		}
		@Override
		public boolean equals(Object obj) {
			return obj != null && hashCode() == obj.hashCode();
		}
		@Override
		public int hashCode() {
			return this.name.hashCode();
		}

		@Override
		public int compareTo(OverrideClassNode o) {
			return toString().compareTo(o.toString());
		}
	}

	private static class OverrideMethodNode extends MethodNode implements Comparable<OverrideMethodNode> {

		private final String owner;

		public OverrideMethodNode(String owner, MethodNode mn){
			super(mn.access, mn.name, mn.desc, mn.signature, mn.exceptions.toArray(new String[mn.exceptions.size()]));
			mn.accept(this);
			this.owner = owner;
		}

		@Override
		public String toString() {
			return owner + '.' + name + desc;
		}
		@Override
		public boolean equals(Object obj) {
			return obj != null && hashCode() == obj.hashCode();
		}
		@Override
		public int hashCode() {
			return this.name.hashCode() * 11 + this.desc.hashCode();
		}

		@Override
		public int compareTo(OverrideMethodNode o) {
			return toString().compareTo(o.toString());
		}
	}

	private static class OverrideFieldNode extends FieldNode implements Comparable<OverrideFieldNode> {

		private final String owner;

		public OverrideFieldNode(FieldInsnNode node) {
			super(Opcodes.ACC_PUBLIC + (node.getOpcode() == Opcodes.GETSTATIC ? Opcodes.ACC_STATIC : 0), 
					node.name, node.desc, null, null);
			owner = node.owner;
		}

		public OverrideFieldNode(String owner, FieldNode fn){
			super(fn.access, fn.name, fn.desc, fn.signature, fn.value);
			this.owner = owner;
		}

		@Override
		public String toString() {
			return owner + '.' + name + ' ' + desc;
		}
		@Override
		public boolean equals(Object obj) {
			return obj != null && hashCode() == obj.hashCode();
		}
		@Override
		public int hashCode() {
			return this.name.hashCode() * 11 + this.desc.hashCode();
		}

		@Override
		public int compareTo(OverrideFieldNode o) {
			return toString().compareTo(o.toString());
		}

	}

}
