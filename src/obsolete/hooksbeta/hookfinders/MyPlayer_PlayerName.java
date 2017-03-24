package obsolete.hooksbeta.hookfinders;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import obsolete.HookFinder;
import obsolete.HookFinderAnnotations;

@HookFinderAnnotations(hookKeys = {"MyPlayer", "Player_Name"}, multiplierKeys = {},  wrapperKeys = {"Player"},
requiredHooks = {"CurrentDisplayName"}, requiredMultipliers = {}, requiredWrappers = {})
public class MyPlayer_PlayerName extends HookFinder {
	
	public void findHook() throws Exception {
		final String playerName = (String) hfu.getHookObject("CurrentDisplayName", null);
		outer: for(Class<?> clazz : hfu.getRuntime().getAllClasses()){
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
									hfu.addHook("MyPlayer", clazz.getName() + '.' + field.getName());
									hfu.addWrapper("Player", possibleMyPlayer.getClass().getName());
									hfu.addHook("Player_Name", possibleMyPlayer.getClass().getName() + '.' + p_field.getName());
									break outer;
								}
							}
						}

					}
				}
			}
		}
	}

}
