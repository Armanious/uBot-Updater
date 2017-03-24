package obsolete.hooksbeta.hookfinders;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import obsolete.HookFinder;
import obsolete.HookFinderAnnotations;

@HookFinderAnnotations(hookKeys = {"CurrentDisplayName"}, multiplierKeys = {}, wrapperKeys = {},
requiredHooks = {}, requiredMultipliers = {}, requiredWrappers = {})
public class CurrentDisplayName extends HookFinder {

	
	public void findHook() throws Exception {
		String playerName = hfu.getInput("Player's display name (make sure you're logged in): ").trim();
		outer: for(Class<?> clazz : hfu.getRuntime().getAllClasses()){
			for(Field field : clazz.getDeclaredFields()){
				if(Modifier.isStatic(field.getModifiers()) && field.getType().equals(String.class)){
					if(!field.isAccessible())
						field.setAccessible(true);
					final String s = (String) field.get(null);
					if(playerName.equals(s)){
						hfu.addHook("CurrentDisplayName", clazz.getName() + '.' + field.getName());
						break outer;
					}
				}
			}
		}
	}

}
