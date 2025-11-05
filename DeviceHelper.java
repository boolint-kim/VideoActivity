package com.boolint.satpic.helper;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.view.WindowMetrics;

public class DeviceHelper {

    private static final int TABLET_SMALLEST_WIDTH_DP = 600;

    public static boolean isTabletDevice(Configuration config) {
        return config.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP;
    }

    public static boolean isTabletDevice(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return isTabletDevice(config);
    }

    public static boolean isLandscapeOrientation(Configuration config) {
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isLandscapeOrientation(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return isLandscapeOrientation(config);
    }

    public static boolean isPortraitOrientation(Configuration config) {
        return config.orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    // 전체 크기
    public static Point getDisplaySize(Context context) {
        Point size = new Point();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager wm = context.getSystemService(WindowManager.class);
            WindowMetrics metrics = wm.getCurrentWindowMetrics();
            Rect bounds = metrics.getBounds();

            size.x = bounds.width();
            size.y = bounds.height();
        } else {
            DisplayMetrics outMetrics = new DisplayMetrics();
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            display.getMetrics(outMetrics);  // 인셋 제외, 현재 창 크기
            size.x = outMetrics.widthPixels;
            size.y = outMetrics.heightPixels;
        }

        return size;
    }


}