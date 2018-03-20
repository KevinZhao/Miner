package com.miner;

import android.app.Application;

import com.miner.utils.CrashHandler;
import com.miner.utils.LogcatHelper;

/**
 * Created by fanshengyue on 2017/11/28.
 */

public class MinerApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.getInstance().init(this);
        LogcatHelper.getInstance(this).start();
    }
}
