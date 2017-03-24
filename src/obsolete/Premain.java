package obsolete;

import java.lang.instrument.Instrumentation;

public class Premain  {
	
	public static Instrumentation inst;
	
	public static void premain(String agentArgs, Instrumentation inst){
		Premain.inst = inst;
	}

}
