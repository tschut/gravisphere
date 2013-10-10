package com.spacemangames.gravisphere.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.spacemangames.framework.ILevelChangedListener;
import com.spacemangames.framework.SpaceGameState;
import com.spacemangames.gravisphere.DebugSettings;
import com.spacemangames.gravisphere.FreezeGameThreadRunnable;
import com.spacemangames.gravisphere.GameThreadHolder;
import com.spacemangames.gravisphere.LevelDbAdapter;
import com.spacemangames.gravisphere.R;
import com.spacemangames.gravisphere.SpaceGameThread;
import com.spacemangames.gravisphere.UnfreezeGameThreadRunnable;
import com.spacemangames.gravisphere.ui.EndLevelDialogFragment.EndLevelDialogFragmentData;
import com.spacemangames.library.SpaceData;
import com.spacemangames.pal.PALManager;

public class SpaceApp extends FragmentActivity implements ILevelChangedListener {
    class PointsUpdateThread extends Thread {
        private final float minFrameTime;
        private long        lastTime;
        public boolean      running = true;

        public PointsUpdateThread(float aMinFrameTime) {
            minFrameTime = aMinFrameTime;
            lastTime = System.nanoTime();
        }

        @Override
        public void run() {
            while (running) {
                if (SpaceGameState.getInstance().getState() < SpaceGameState.STATE_NOT_STARTED) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e1) {
                    }
                    continue;
                }

                long now = System.nanoTime();
                float elapsed = (now - lastTime) / 1000000000f;

                if (elapsed < minFrameTime) {
                    try {
                        sleep((long) ((minFrameTime - elapsed) * 1000));
                        continue;
                    } catch (InterruptedException e) {
                    }
                }
                lastTime = System.nanoTime();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView pointsView = (TextView) findViewById(R.id.pointsView);
                        pointsView.setText(Integer.toString(SpaceData.getInstance().mPoints.getCurrentPoints()));
                    }
                });
            }
        }
    }

    public static final int        ACTIVITY_LEVELSELECT = 0;
    public static final int        ACTIVITY_HELP        = 1;

    public static final int        DIALOG_END_LEVEL     = 0;

    public static final int        LAST_UNLOCKED_LEVEL  = -2;

    public static final String     LEVEL_ID_STRING      = "com.games.spaceman.level_id";

    public static final int        MENU_LEVELSELECT_ID  = Menu.FIRST;
    public static final int        MENU_RESTARTLEVEL_ID = Menu.FIRST + 1;

    private static final String    TAG                  = "SpaceApp";

    public static Context          mAppContext;

    private PointsUpdateThread     mHUDThread;

    private static int             mRestoreLevel        = -1;

    private GoogleAnalyticsTracker tracker;

    // parse events that occurred. This is called after the physics have been
    // updated
    public Handler                 mMsgHandler          = new Handler() {
                                                            @Override
                                                            public void handleMessage(Message msg) {
                                                                showEndLevelDialog();
                                                            }
                                                        };

    private void showEndLevelDialog() {
        tracker.trackPageView("/endLevelDialog");

        int points = SpaceData.getInstance().mPoints.getCurrentPoints();
        int best = LevelDbAdapter.getInstance().highScore(SpaceData.getInstance().getCurrentLevelId());
        int imageResource = R.drawable.star_enabled;
        int titleResource = R.string.end_level_title_won;
        int textResource = R.string.end_level_subtitle_won;
        boolean nextLevelUnlocked = false;

        // set title and subtitle
        switch (SpaceGameState.getInstance().endState()) {
        case SpaceGameState.WON_BRONZE:
            imageResource = R.drawable.star_bronze;
            break;
        case SpaceGameState.WON_SILVER:
            imageResource = R.drawable.star_silver;
            break;
        case SpaceGameState.WON_GOLD:
            imageResource = R.drawable.star_gold;
            break;
        case SpaceGameState.LOST_DIE:
            titleResource = R.string.end_level_title_lost_die;
            textResource = R.string.end_level_subtitle_lost_die;
            break;
        case SpaceGameState.LOST_LOST:
            titleResource = R.string.end_level_title_lost_lost;
            textResource = R.string.end_level_subtitle_lost_lost;
            break;
        }

        if (LevelDbAdapter.getInstance().levelIsUnlocked(SpaceData.getInstance().getCurrentLevelId() + 1)) {
            nextLevelUnlocked = true;
        }

        FragmentManager fm = getSupportFragmentManager();
        EndLevelDialogFragment endLevelDialog = new EndLevelDialogFragment_();
        EndLevelDialogFragmentData data = new EndLevelDialogFragmentData();
        data.activity(this).points(points).best(best).star(imageResource).subtitle(titleResource).message(textResource)
                .nextLevelUnlocked(nextLevelUnlocked);
        endLevelDialog.setProperties(data);
        endLevelDialog.show(fm, "end_level_dialog");
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DebugSettings.DEBUG_LOGGING) {
            Log.i(TAG, "onCreate");
        }

        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            // we were just launched: set up a new game
            // nothing to do?
            PALManager.getLog().i(TAG, "Normal startup");

            setContentView(R.layout.space_layout);

            mHUDThread = new PointsUpdateThread(SpaceGameThread.MIN_FRAME_TIME);
            mHUDThread.start();
            SpaceView lSpaceView = (SpaceView) findViewById(R.id.space);
            GameThreadHolder.getThread().setSurfaceHolder(lSpaceView.getHolder());
            GameThreadHolder.getThread().setMsgHandler(mMsgHandler);

            tracker = GoogleAnalyticsTracker.getInstance();

            // register for level changed events
            SpaceData.getInstance().addLevelChangedListener(this);
        } else {
            // we are being restored: restart the app
            Intent i = new Intent(this, com.spacemangames.gravisphere.ui.LoadingActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i);
            finish();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (SpaceGameState.getInstance().getState() == SpaceGameState.STATE_FLYING)
            SpaceGameState.getInstance().setPaused(true);

        showPauseMenu();
        return true;
    }

    private void showPauseMenu() {
        tracker.trackPageView("/pauseDialog");

        FragmentManager fm = getSupportFragmentManager();
        PauseMenuFragment pauseMenu = new PauseMenuFragment_();
        pauseMenu.setStartingActivity(this);
        pauseMenu.show(fm, "pause_menu");
    }

    @Override
    public void onActivityResult(int aRequestCode, int aResultCode, Intent aData) {
        PALManager.getLog().v(TAG, "onActivityResult");
        switch (aRequestCode) {
        case ACTIVITY_LEVELSELECT:
            if (aResultCode == Activity.RESULT_OK) {
                mRestoreLevel = aData.getIntExtra(LEVEL_ID_STRING, 0);
            }
        }
    }

    @Override
    protected void onPause() {
        PALManager.getLog().i(TAG, "onPause");
        if (SpaceGameState.getInstance().getState() == SpaceGameState.STATE_FLYING) {
            SpaceGameState.getInstance().setPaused(true);
        }
        GameThreadHolder.getThread().postSyncRunnable(new FreezeGameThreadRunnable());
        mRestoreLevel = SpaceData.getInstance().getCurrentLevelId();
        PALManager.getLog().v(TAG, "storing level id " + mRestoreLevel);
        super.onPause();
    }

    @Override
    protected void onResume() {
        PALManager.getLog().i(TAG, "onResume");
        super.onResume();

        Intent i = getIntent();
        if (i != null) {
            mRestoreLevel = i.getIntExtra("level", mRestoreLevel);
            i.removeExtra("level");
        }

        if (mRestoreLevel == LAST_UNLOCKED_LEVEL) {
            int level = LevelDbAdapter.getInstance().getLastUnlockedLevelID();
            PALManager.getLog().v(TAG, "restoring last unlocked level: " + level);
            GameThreadHolder.getThread().changeLevel(level, false);
        } else if (mRestoreLevel != -1) {
            PALManager.getLog().v(TAG, "restoring level id " + mRestoreLevel);
            GameThreadHolder.getThread().changeLevel(mRestoreLevel, false);
        } else {
            PALManager.getLog().v(TAG, "restoring level id " + 0);
            GameThreadHolder.getThread().changeLevel(0, false);
        }
        GameThreadHolder.getThread().postSyncRunnable(new UnfreezeGameThreadRunnable());
        GameThreadHolder.getThread().redrawOnce();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        PALManager.getLog().i(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
    }

    @Override
    public void LevelChanged(int aNewLevelID, boolean aSpecial) {
        if (!aSpecial) {
            tracker.trackPageView("/levels/" + aNewLevelID);
        }
    }
}