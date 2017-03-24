package obsolete;


public abstract class HookFinder implements Runnable {
	
	protected HooksFinderUtil hfu;
	
	public final void run(){
		try {
			findHook();
			System.out.println(getClass().getSimpleName() + " ran successfully.");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public abstract void findHook() throws Exception;
	
	public final void setHooksFinderUtil(HooksFinderUtil hfu){
		this.hfu = hfu;
	}

}
