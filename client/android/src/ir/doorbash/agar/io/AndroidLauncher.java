package ir.doorbash.agar.io;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.surfaceview.ResolutionStrategy;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.resolutionStrategy = (widthMeasureSpec, heightMeasureSpec) -> new ResolutionStrategy.MeasuredDimension(heightMeasureSpec, heightMeasureSpec);
        initialize(new Game(), config);
    }
}
