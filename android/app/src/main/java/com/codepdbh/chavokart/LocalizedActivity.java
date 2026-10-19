package com.codepdbh.chavokart;

import android.app.Activity;
import android.content.Context;

/** Base activity: applies the launcher language and keeps the landscape, fullscreen layout. */
abstract class LocalizedActivity extends Activity {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Prefs.localize(base));
    }
}
