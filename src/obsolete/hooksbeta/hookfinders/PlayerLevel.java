package obsolete.hooksbeta.hookfinders;

import java.lang.reflect.Field;

import org.armanious.Tuple;

import obsolete.HookFinder;
import obsolete.HookFinderAnnotations;

@HookFinderAnnotations(hookKeys = { "Player_Level" }, multiplierKeys = {"Player_Level"}, requiredHooks = { "MyPlayer" }, requiredMultipliers = {}, requiredWrappers = {}, wrapperKeys = {})
public class PlayerLevel extends HookFinder {

	@Override
	public void findHook() throws Exception {
		final Object myPlayer = hfu.getHookObject("MyPlayer", null);

		int curLevel = Integer.parseInt(hfu.getInput("Current combat level: "));
		Tuple<Field, Number> hook = hfu.getMultipliedHookIntsOnly(myPlayer, curLevel);
		hfu.addHook("Player_Level", hook.val1.getDeclaringClass().getName() + '.' + hook.val1.getName());
		hfu.addMultiplier("Player_Level", hook.val2);
	}

}
