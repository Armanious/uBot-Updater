package updater.staticanalysis;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;

import org.armanious.Filter;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;

public class RelationshipViewer extends JFrame implements MouseListener, ListCellRenderer<String> {
	private static final long serialVersionUID = -3413077823971793297L;

	private final DefaultListModel<String> listModel = new DefaultListModel<>();
	private final DefaultListCellRenderer defaultCellRenderer = new DefaultListCellRenderer();
	private String textToHighlight = null;

	public RelationshipViewer(RelationshipSet set){
		this(set, true);
	}

	public RelationshipViewer(RelationshipSet set, boolean useClassNodeRoots){
		this(set, useClassNodeRoots, null);
	}

	public RelationshipViewer(RelationshipSet set, boolean useClassNodeRoots, Filter<FieldNode> fieldNodeFilter){

		final JPanel contentPane = new JPanel(new BorderLayout(10, 0));

		final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Relationship Viewer");
		final JTree tree = new JTree(root);

		final Map<FieldNode, ArrayList<MethodNode>> map = set.getFieldAccessorsMap();
		synchronized(map){
			if(useClassNodeRoots){
				DefaultMutableTreeNode classRoot = null;
				for(FieldNode node : map.keySet()){
					if(fieldNodeFilter != null && !fieldNodeFilter.accept(node))
						continue;
					classRoot = getClassRoot(classRoot, node);
					if(classRoot.getParent() == null){
						root.add(classRoot);
					}
					final DefaultMutableTreeNode fieldNodeRoot = new DefaultMutableTreeNode(node);
					classRoot.add(fieldNodeRoot);
					for(MethodNode mn : map.get(node)){
						fieldNodeRoot.add(new DefaultMutableTreeNode(mn));
					}
				}
			}else{
				for(FieldNode node : map.keySet()){
					if(fieldNodeFilter != null && !fieldNodeFilter.accept(node))
						continue;
					final DefaultMutableTreeNode fieldNodeRoot = new DefaultMutableTreeNode(node);
					root.add(fieldNodeRoot);
					for(MethodNode mn : map.get(node)){
						fieldNodeRoot.add(new DefaultMutableTreeNode(mn));
					}
				}
			}
		}
		tree.addMouseListener(this);
		final Dimension dim = new Dimension(300, 1);
		final JScrollPane sp = new JScrollPane(tree);
		sp.setMinimumSize(dim);
		sp.setSize(dim);
		sp.setMaximumSize(dim);
		sp.setPreferredSize(dim);
		contentPane.add(sp, BorderLayout.WEST);

		final JList<String> list = new JList<>(listModel);
		list.setCellRenderer(this);

		contentPane.add(new JScrollPane(list), BorderLayout.CENTER);

		setContentPane(contentPane);
	}

	private static DefaultMutableTreeNode getClassRoot(DefaultMutableTreeNode classRoot, FieldNode node){
		final String s = node.toString().substring(0, node.toString().indexOf('.'));
		if(classRoot == null || !classRoot.getUserObject().toString().equals(s))
			return new DefaultMutableTreeNode(s);
		return classRoot;
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		if(e.getClickCount() == 2){
			try{
				final DefaultMutableTreeNode node = (DefaultMutableTreeNode)((JTree) e.getSource()).getPathForLocation(e.getX(), e.getY()).getLastPathComponent();
				final Object obj = node.getUserObject();
				if(obj instanceof MethodNode){
					listModel.clear();
					listModel.addElement("Bytecode for method: " + obj);
					listModel.addElement("\n");
					textToHighlight = ((DefaultMutableTreeNode)node.getParent()).getUserObject().toString().replace('.', ' ');
					final String[] bytecode = getReadableBytecode(((MethodNode)obj).instructions.getFirst());
					for(int i = 0; i < bytecode.length; i++){
						final String padding = i < 10 ? "  " : (i < 100 ? " " : "");
						listModel.addElement(padding + i + ":      " + bytecode[i]);
					}
				}
			}catch(Exception ignored){}
		}
	}

	@Override
	public void mousePressed(MouseEvent e) {}

	@Override
	public void mouseReleased(MouseEvent e) {}

	@Override
	public void mouseEntered(MouseEvent e) {}

	@Override
	public void mouseExited(MouseEvent e) {}

	public static String[] getReadableBytecode(final AbstractInsnNode start){
		final ArrayList<String> bytecode = new ArrayList<>();
		AbstractInsnNode insn = start;
		String prefix = "      ";
		final ArrayList<Label> labels = new ArrayList<>();

		while(insn != null){
			if(insn instanceof LabelNode){
				labels.add(((LabelNode)insn).getLabel());
			}
			insn = insn.getNext();
		}

		insn = start;
		while(insn != null){
			if(insn instanceof LabelNode){
				prefix = 'L' + String.valueOf(labels.indexOf(((LabelNode)insn).getLabel())) + ": ";
			}else if(insn.getOpcode() >= 0){
				final StringBuilder sb = new StringBuilder(prefix);
				sb.append(Printer.OPCODES[insn.getOpcode()]).append(' ');
				if(insn instanceof JumpInsnNode){
					sb.append(" L").append(labels.indexOf(((JumpInsnNode) insn).label.getLabel()));
				}else if(insn instanceof FieldInsnNode){
					final FieldInsnNode fin = (FieldInsnNode) insn;
					sb.append(fin.owner).append(' ').append(fin.name).append(' ').append(fin.desc);
				}else if(insn instanceof IincInsnNode){
					final IincInsnNode iin = (IincInsnNode) insn;
					sb.append(iin.var).append(" by ").append(iin.incr);
				}else if(insn instanceof IntInsnNode){
					sb.append(((IntInsnNode) insn).operand);
				}else if(insn instanceof InvokeDynamicInsnNode){
					final InvokeDynamicInsnNode idin = (InvokeDynamicInsnNode) insn;
					sb.append(idin.name).append(idin.desc);
				}else if(insn instanceof LdcInsnNode){
					final LdcInsnNode lin = (LdcInsnNode) insn;
					if(lin.cst instanceof Integer)
						sb.append(lin.cst);
					else if(lin.cst instanceof Float)
						sb.append(lin.cst).append('F');
					else if(lin.cst instanceof Long)
						sb.append(lin.cst).append('L');
					else if(lin.cst instanceof Double)
						sb.append(lin.cst).append('D');
					else if(lin.cst instanceof String)
						sb.append('"').append(lin.cst).append('"');
					else
						sb.append(lin.cst);
				}else if(insn instanceof LookupSwitchInsnNode){
					final LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) insn;
					int prelength = sb.substring(sb.lastIndexOf("\n")).length();
					String pre = "";
					while(prelength-- > 0) pre += ' ';
					for(int i = 0; i < lsin.labels.size(); i++){
						sb.append(lsin.keys.get(i)).append(": L").append(labels.indexOf(lsin.labels.get(i).getLabel()));
					}
					sb.append(pre).append("default: L").append(labels.indexOf(lsin.dflt.getLabel()));
				}else if(insn instanceof TableSwitchInsnNode){
					final TableSwitchInsnNode tsin = (TableSwitchInsnNode) insn;
					int prelength = sb.substring(sb.lastIndexOf("\n")).length();
					String pre = "";
					while(prelength-- > 0) pre += ' ';
					for(int i = 0; i < tsin.labels.size(); i++){
						if(i > 0)
							sb.append(pre);
						sb.append(i + tsin.min).append(": L").append(labels.indexOf(tsin.labels.get(i).getLabel()));
					}
					sb.append(pre).append("default: L").append(labels.indexOf(tsin.dflt.getLabel()));
				}else if(insn instanceof MethodInsnNode){
					final MethodInsnNode min = (MethodInsnNode) insn;
					sb.append(min.owner).append(' ').append(min.name).append(min.desc);
				}else if(insn instanceof MultiANewArrayInsnNode){
					final MultiANewArrayInsnNode manain = (MultiANewArrayInsnNode) insn;
					sb.append(manain.desc).append(' ').append(manain.dims);
				}else if(insn instanceof TypeInsnNode){
					sb.append(((TypeInsnNode) insn).desc);
				}else if(insn instanceof VarInsnNode){
					final VarInsnNode vin = (VarInsnNode) insn;
					if(vin.var <= 3){
						sb.setCharAt(sb.length() - 1, '_');
					}
					sb.append(vin.var);
				}
				bytecode.add(sb.toString());
				prefix = "      ";
			}
			insn = insn.getNext();
		}
		return bytecode.toArray(new String[bytecode.size()]);
	}

	@Override
	public Component getListCellRendererComponent(JList<? extends String> list, String value, int index, boolean isSelected, boolean cellHasFocus) {
		final Component comp = defaultCellRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		if(value.contains(textToHighlight))
			comp.setForeground(Color.RED);
		return comp;
	}

}
