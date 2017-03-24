package obsolete.hooksbeta.hookfinders;

import java.lang.reflect.Modifier;

import org.armanious.Filter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;

import obsolete.HookFinder;
import obsolete.HookFinderAnnotations;
import obsolete.HooksFinderUtil;

@HookFinderAnnotations(hookKeys = { "Node_ID" }, multiplierKeys = { "Node_ID" }, wrapperKeys = { "Node", "LinkedListNode" }, requiredHooks = {}, requiredMultipliers = {}, requiredWrappers = {})
public class NodeID_LinkedListTail_LinkedListNext extends HookFinder {

	@Override
	public void findHook() throws Exception { outer:
		for(final ClassNode cn : hfu.getBytecode().getClassNodes()){
			final boolean hasNodeWrapper = hfu.getWrapper("Node") != null;
			final boolean hasLinkedListNodeWrapper = hfu.getWrapper("LinkedListNode") != null;
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
								final Number nodeIdMultiplier = HooksFinderUtil.getPossibleMultipliers(new Filter<FieldInsnNode>(){
									@Override
									public boolean accept(FieldInsnNode fin){
										return fin.owner.equals(cn.name) && fin.name.equals(fn.name);
									}
								}, hfu.getBytecode().getClassNodes())[0];
								if(nodeIdMultiplier instanceof Long){
									hfu.addWrapper("Node", cn.name);
									hfu.addHook("Node_ID", cn.name + '.' + fn.name);
									hfu.addMultiplier("Node_ID", nodeIdMultiplier);
									continue outer;
								}
							}
						}
					}
					//not Node; should be LinkedListNode
					if(!hasLinkedListNodeWrapper){
						hfu.addWrapper("LinkedListNode", cn.name);
						continue outer;
					}
				}
			}
		}
	}
	
}
