package ir.doorbash.agar_io_clone.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import ir.doorbash.agar_io_clone.Game;

public class DesktopLauncher {
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.title = "Agar.io Clone";
		config.useGL30 = true;
		config.height = 600;
		config.width = 600;
		new LwjglApplication(new Game(), config);
	}
}
