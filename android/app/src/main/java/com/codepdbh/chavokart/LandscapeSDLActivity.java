// Derived from the-simpsons-game-android (codepdbh, GPLv3), commit ed0f1ab.
package com.codepdbh.chavokart;

import android.content.Context;
import android.content.pm.ActivityInfo;
import org.libsdl.app.SDLActivity;

/** SDL's resizable window must not replace the manifest's landscape lock. */
public class LandscapeSDLActivity extends SDLActivity {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Prefs.localize(base));
    }

    @Override public void setRequestedOrientation(int requestedOrientation) {
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    @Override protected void onResume() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        super.onResume();
    }
}
