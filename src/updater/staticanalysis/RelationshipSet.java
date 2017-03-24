package updater.staticanalysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.armanious.Tuple;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class RelationshipSet {

	private final ArrayList<Relationship> list;

	private Map<FieldNode, ArrayList<MethodNode>> fieldsMap;

	public RelationshipSet(){
		list = new ArrayList<>();
	}

	public void add(ClassNode cn){
		final Relationship r = new Relationship(cn);
		if(!list.contains(r)){
			list.add(r);
			fieldsMap = null;
		}
	}

	public Relationship[] getRelationships(){
		return list.toArray(new Relationship[list.size()]);
	}

	public final ClassNode[] getClassNodes(){
		final ClassNode[] nodes = new ClassNode[list.size()];
		for(int i = 0; i < nodes.length; i++){
			nodes[i] = list.get(i).cn;
		}
		return nodes;
	}

	public synchronized Map<FieldNode, ArrayList<MethodNode>> getFieldAccessorsMap(){
		if(fieldsMap == null){
			fieldsMap = Collections.synchronizedMap(new TreeMap<FieldNode, ArrayList<MethodNode>>());
			synchronized(fieldsMap){
				for(Relationship r : list){

					for(MethodNode mn : r.accesses.keySet()){
						final Tuple<ArrayList<FieldNode>, ArrayList<String>> s = r.accesses.get(mn);
						for(FieldNode node : s.val1){
							ArrayList<MethodNode> m = fieldsMap.get(node);
							if(m == null){
								m = new ArrayList<>();
								fieldsMap.put(node, m);
							}
							if(!m.contains(mn)){
								m.add(mn);
							}
						}
					}
				}
				fieldsMap = Collections.unmodifiableMap(fieldsMap);
			}
		}
		return fieldsMap;
	}

}
