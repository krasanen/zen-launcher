package fi.zmengames.zen;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.R;

public class LauncherAppWidgetHostView extends AppWidgetHostView {
    // Share a bounded number of workers across widgets and activity recreations.
    private static final Executor INFLATION_EXECUTOR = Executors.newFixedThreadPool(2);
    private final LayoutInflater mInflater;
    private final Context context;
    GestureDetector gd;
    private static final String TAG = LauncherAppWidgetHostView.class.getSimpleName();
    public LauncherAppWidgetHostView(Context context) {
        super(context);
        this.context = context;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Configure this before AppWidgetHost.createView delivers its first
            // RemoteViews update. The framework applies the result on the UI thread.
            setExecutor(INFLATION_EXECUTOR);
        }
        gd = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (BuildConfig.DEBUG) Log.d(TAG,"onSingleTapConfirmed: "+ e);
                return super.onSingleTapConfirmed(e);
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (BuildConfig.DEBUG) Log.d(TAG,"onSingleTapUp: "+ e);
                if (getAppWidgetInfo().provider.getPackageName().startsWith("com.huawei")){
                    if (BuildConfig.DEBUG) Log.d(TAG,"intercepted touch to incompatible Huawei widget that would require special system permissions");
                    // dealWithHuaweiPermissions();
                    return true;
                }
                return super.onSingleTapUp(e);
            }
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (BuildConfig.DEBUG) Log.d(TAG,"onScroll: "+ e1);

                return super.onScroll(e1, e2, velocityX, velocityY);
            }
            @Override
            public void onLongPress(MotionEvent e) {
                if (BuildConfig.DEBUG) Log.d(TAG,"onLongPress: "+ e);
                performLongClick();
            }
        });
    }

    @Override
    protected View getErrorView() {
        return mInflater.inflate(R.layout.appwidget_error, this, false);
    }

    private void dealWithHuaweiPermissions(){
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(this.getAppWidgetInfo().provider.getPackageName());
        if (BuildConfig.DEBUG) Log.d(TAG,"dealWithHuaweiPermissions: "+this.getAppWidgetInfo().provider.getPackageName());

        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(this.getAppWidgetInfo().provider.getPackageName(), PackageManager.GET_PERMISSIONS);
            if (BuildConfig.DEBUG) Log.d(TAG,"requestedPermissions: "+ Arrays.toString(packageInfo.requestedPermissions));
        } catch (PackageManager.NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        gd.onTouchEvent(ev);
        return false;
    }

    @Override
    public int getDescendantFocusability() {
        return ViewGroup.FOCUS_BLOCK_DESCENDANTS;
    }
}
