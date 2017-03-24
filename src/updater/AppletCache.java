package updater;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class AppletCache {

	private final Map<Field, InstanceCache> instanceCache = new HashMap<>();

	public AppletCache(Class<?>[] classes) throws ReflectiveOperationException {
		for(Class<?> clazz : classes){
			for(Field field : clazz.getDeclaredFields()){
				if(Modifier.isStatic(field.getModifiers())){
					if(!field.isAccessible())
						field.setAccessible(true);
					instanceCache.put(field, getCached(field.get(null)));
				}
			}
		}
	}

	private InstanceCache getCached(Object val) throws ReflectiveOperationException{
		if(val == null)
			return null;
		final int objectHash = val.hashCode();
		for(InstanceCache cache : instanceCache.values()){
			if(cache.objectHash == objectHash){
				return cache;
			}
		}
		return new InstanceCache(val);
	}

	private class InstanceCache {

		private final Map<Field, Object> values = new HashMap<>();
		private final int objectHash;

		public InstanceCache(Object instance) throws ReflectiveOperationException {
			assert instance != null;
			objectHash = instance.hashCode();
			Class<?> clazz = instance.getClass();
			while(clazz != Object.class){
				for(Field field : clazz.getDeclaredFields()){
					if(Modifier.isStatic(field.getModifiers()))
						continue;
					if(!field.isAccessible())
						field.setAccessible(true);
					values.put(field, getCached(field.get(instance)));
				}
				clazz = clazz.getSuperclass();
			}
		}

	}

}
