package obsolete;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface HookFinderAnnotations {
	
	String[] wrapperKeys();
	String[] hookKeys();
	String[] multiplierKeys();
	
	String[] requiredWrappers();
	String[] requiredHooks();
	String[] requiredMultipliers();
	
}
