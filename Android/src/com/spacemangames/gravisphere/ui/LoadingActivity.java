package com.spacemangames.gravisphere.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.googlecode.androidannotations.annotations.Bean;
import com.googlecode.androidannotations.annotations.EActivity;
import com.spacemangames.framework.ILoadingDoneListener;
import com.spacemangames.framework.SpaceUtil;
import com.spacemangames.gravisphere.DebugSettings;
import com.spacemangames.gravisphere.GameThreadHolder;
import com.spacemangames.gravisphere.R;
import com.spacemangames.gravisphere.SpaceGameThread;
import com.spacemangames.gravisphere.contentprovider.LevelDbAdapter;
import com.spacemangames.gravisphere.pal.AndroidBitmapFactory;
import com.spacemangames.gravisphere.pal.AndroidLog;
import com.spacemangames.gravisphere.pal.AndroidResourceHandler;
import com.spacemangames.library.SpaceData;
import com.spacemangames.math.Rect;
import com.spacemangames.pal.EmptyLog;
import com.spacemangames.pal.PALManager;

@EActivity(R.layout.loading_layout)
public class LoadingActivity extends Activity implements ILoadingDoneListener {
    static {
        System.loadLibrary("gdx");
    }

    @Bean
    protected LevelDbAdapter levelDbAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bootstrap();

        // start the game thread
        final SpaceGameThread lThread = GameThreadHolder.createThread(getApplicationContext());
        lThread.setRunning(true);
        lThread.freeze();
        if (lThread.getState() == Thread.State.NEW)
            lThread.start();

        lThread.postRunnable(new Runnable() {
            @Override
            public void run() {
                SpaceData.getInstance().preloadAllLevels();
                levelDbAdapter.insertAllLevelsIfEmpty();
                // load the fist level
                lThread.changeLevel(0, true);
                SpaceData.getInstance().setLoadingDone();
            }
        });
    }

    private void bootstrap() {
        initializePAL();
        initDisplay();

        SpaceApp.mAppContext = this;
        GoogleAnalyticsTracker.getInstance().startNewSession("UA-34397887-2", 30, this);
        SpaceData.getInstance().addLoadingDoneListener(this);
    }

    private void initDisplay() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        SpaceUtil.init(dm.xdpi, dm.ydpi);
        Rect resolution = new Rect(0, 0, dm.widthPixels, dm.heightPixels);
        SpaceUtil.setResolution(resolution);
    }

    private void initializePAL() {
        PALManager.setResourceHandler(new AndroidResourceHandler());
        PALManager.setBitmapFactory(new AndroidBitmapFactory());
        if (DebugSettings.DEBUG_LOGGING) {
            PALManager.setLog(new AndroidLog());
        } else {
            PALManager.setLog(new EmptyLog());
        }
    }

    @Override
    public void loadingDone() {
        SpaceData.getInstance().remLoadingDoneListener(this);
        // loading done, continue to the MainMenuActivity
        startActivity(new Intent(SpaceApp.mAppContext, com.spacemangames.gravisphere.ui.MainMenu_.class));
    }
}
