package updater.dynamicanalysis;

import java.applet.Applet;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import javax.swing.JFrame;

public class RuntimeUpdater {

	private final ClassLoader innerPackClassLoader;
	private final Field classesField;

	public RuntimeUpdater(Applet applet) throws ReflectiveOperationException {
		JFrame f = new JFrame("Rs2Applet");
		f.setContentPane(applet);
		f.setSize(800, 600);
		f.setLocationRelativeTo(null);
		f.setVisible(true);
		applet.init();
		applet.start();
		classesField = ClassLoader.class.getDeclaredField("classes");
		classesField.setAccessible(true);
		Object client = null;
		for(Field field : applet.getClass().getDeclaredFields()){
			boolean acc = field.isAccessible();
			if(!acc)
				field.setAccessible(true);
			final Object obj = field.get(applet);
			if(obj != null && obj.getClass().getName().equals("client")){
				client = obj;
				break;
			}
			if(!acc)
				field.setAccessible(false);
		}
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		assert client != null;
		innerPackClassLoader = client.getClass().getClassLoader();
	}

	public Class<?> loadClass(String name) {
		try{
			return innerPackClassLoader.loadClass(name);
		}catch(ClassNotFoundException e){
			e.printStackTrace();
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	public Class<?>[] getAllClasses(){
		try{
			final Vector<Class<?>> classes = (Vector<Class<?>>) classesField.get(innerPackClassLoader);
			return classes.toArray(new Class<?>[classes.size()]);
		}catch(ReflectiveOperationException e){
			e.printStackTrace();
			return new Class<?>[0];
		}
	}

	public Field[] getChangedFields(Object instance, long ms) throws ReflectiveOperationException, InterruptedException {
		return getChangedFields(instance.getClass().getDeclaredFields(), instance, ms);
	}

	public Field[] getUnchangedFields(Object instance, long ms) throws ReflectiveOperationException, InterruptedException {
		return getUnchangedFields(instance.getClass().getDeclaredFields(), instance, ms);
	}

	public Field[] getChangedFields(Field[] fields, Object instance, long ms) throws ReflectiveOperationException, InterruptedException {
		ensureAccessible(fields);
		final Object[] values = new Object[fields.length];
		for(int i = 0; i < values.length; i++){
			values[i] = fields[i].get(instance);
		}
		Thread.sleep(ms);
		final Set<Field> set = new HashSet<>();
		for(int i = 0; i < values.length; i++){
			final Object newObj = fields[i].get(instance);
			if(!objectIsSame(values[i], newObj)){
				set.add(fields[i]);
			}
		}
		return set.toArray(new Field[set.size()]);
	}


	public Field[] getUnchangedFields(Field[] fields, Object instance, long ms) throws ReflectiveOperationException, InterruptedException {
		ensureAccessible(fields);
		final Object[] values = new Object[fields.length];
		for(int i = 0; i < values.length; i++){
			values[i] = fields[i].get(instance);
		}
		Thread.sleep(ms);
		final Set<Field> set = new HashSet<>();
		for(int i = 0; i < values.length; i++){
			final Object newObj = fields[i].get(instance);
			if(objectIsSame(values[i], newObj)){
				set.add(fields[i]);
			}
		}
		return set.toArray(new Field[set.size()]);
	}

	private static void ensureAccessible(Field...fields){
		for(Field f : fields){
			f.setAccessible(true);
		}
	}

	private static boolean objectIsSame(Object obj1, Object obj2){
		if(obj1 == obj2)
			return true;
		if(obj1 == null || obj2 == null)
			return false;
		return obj1.equals(obj2);
	}

}
