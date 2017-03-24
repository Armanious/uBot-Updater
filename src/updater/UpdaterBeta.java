package updater;

import java.io.File;
import java.io.IOException;

import javax.swing.UIManager;

public class UpdaterBeta {

	public static void main(String...args) throws Exception {
		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		new UpdaterBeta().run();
	}

	private final RuntimeUpdater runtime;
	private final BytecodeAnalyzerUpdater bytecode;

	private UpdaterBeta() throws IOException, ReflectiveOperationException {
		runtime = new RuntimeUpdater(AppletGetter.getApplet());
		bytecode = BytecodeAnalyzerUpdater.loadFromJarFile(RunescapeInnerFiles.get());
	}


	public void run() throws IOException {
		bytecode.update();
		bytecode.save(new File("C:\\Users\\David\\Desktop\\RSFiles"));
		try {
			new Hooks(runtime, bytecode).run();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
