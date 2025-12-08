package fr.neamar.kiss;

import android.Manifest;
import static android.view.HapticFeedbackConstants.LONG_PRESS;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.app.WallpaperManager;
import android.app.admin.DevicePolicyManager;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import fi.zmengames.zen.AppGridActivity;
import fi.zmengames.zen.DriveServiceHelper;
import fi.zmengames.zen.LauncherService;
import fi.zmengames.zen.ScreenReceiver;
import fi.zmengames.zen.Utility;
import fi.zmengames.zen.ZEvent;
import fi.zmengames.zen.ZenAdmin;
import fr.neamar.kiss.adapter.RecordAdapter;
import fr.neamar.kiss.broadcast.BadgeCountHandler;
import fr.neamar.kiss.broadcast.IncomingCallHandler;
import fr.neamar.kiss.cache.MemoryCacheHelper;
import fr.neamar.kiss.dataprovider.AppProvider;
import fr.neamar.kiss.db.DBHelper;
import fr.neamar.kiss.db.ShortcutRecord;
import fr.neamar.kiss.forwarder.ForwarderManager;
import fr.neamar.kiss.preference.DefaultLauncherPreference;
import fr.neamar.kiss.searcher.ApplicationsSearcher;
import fr.neamar.kiss.searcher.AppsWithNotifSearcher;
import fr.neamar.kiss.searcher.ContactSearcher;
import fr.neamar.kiss.searcher.HistorySearcher;
import fr.neamar.kiss.searcher.QueryInterface;
import fr.neamar.kiss.searcher.QuerySearcher;
import fr.neamar.kiss.searcher.Searcher;
import fr.neamar.kiss.searcher.ShortcutsSearcher;
import fr.neamar.kiss.searcher.TagsSearcher;
import fr.neamar.kiss.searcher.UntaggedSearcher;
import fr.neamar.kiss.ui.AnimatedListView;
import fr.neamar.kiss.ui.KeyboardScrollHider;
import fr.neamar.kiss.ui.ListPopup;
import fr.neamar.kiss.ui.SearchEditText;
import fr.neamar.kiss.utils.ShortcutUtil;
import fr.neamar.kiss.utils.SystemUiVisibilityHelper;
import me.dm7.barcodescanner.zxing.ZXingScannerView;

import static fi.zmengames.zen.ZEvent.State.ACTION_SET_DEFAULT_LAUNCHER;
import static fi.zmengames.zen.ZEvent.State.ALARM_DATE_PICKER_MILLIS;
import static fi.zmengames.zen.ZEvent.State.ALARM_ENTERED_TEXT;
import static fi.zmengames.zen.ZEvent.State.ALARM_PICKER;
import static fi.zmengames.zen.ZEvent.State.DATE_TIME_PICKER;
import static fi.zmengames.zen.ZEvent.State.DISABLE_PROXIMITY;
import static fi.zmengames.zen.ZEvent.State.NIGHTMODE_OFF;
import static fi.zmengames.zen.ZEvent.State.NIGHTMODE_ON;
import static fr.neamar.kiss.forwarder.Widget.WIDGET_PREFERENCE_ID;

public class MainActivity extends Activity implements QueryInterface, KeyboardScrollHider.KeyboardHandler, View.OnTouchListener, Searcher.DataObserver, View.OnLongClickListener, /*ZBarScannerView.ResultHandler,*/ ZXingScannerView.ResultHandler {

    private static final String TAG = MainActivity.class.getSimpleName();

    // for device admin
    public static final int REQUEST_DEVICE_ADMIN_FOR_LOCK_NOW = 3;
    public static final int REQUEST_DEVICE_ADMIN_FOR_LOCK_AFTER = 4;
    public static final int REQUEST_DEVICE_ADMIN_FROM_PREFERENCES = 5;

    // for widgets
    public static final int REQUEST_CREATE_APPWIDGET = 6;
    public static final int REQUEST_CONFIGURE_APPWIDGET = 7;
    public static final int REQUEST_PICK_APPWIDGET = 8;
    public static final int REQUEST_REFRESH_APPWIDGET = 9;
    public static final int REQUEST_BIND_APPWIDGET = 10;

    // storing and loading layout
    private static final int REQUEST_LOAD_REPLACE_TAGS = 11;
    private static final int REQUEST_LOAD_REPLACE_SETTINGS = 12;
    public static final int MY_PERMISSIONS_REQUEST_READ_STORAGE = 13;
    private static final int RC_SIGN_IN = 14;

    // other purposes
    private static final int MY_PERMISSIONS_OVERLAY = 15;
    private static final int MY_PERMISSIONS_HUAWEI = 16;
    private static final int MY_PERMISSIONS_DND = 17;
    public static final int MY_PERMISSIONS_CAMERA = 18;
    private static final int ZEN_NOTIFICATION_ID = 19;

    // Google Drive helper
    private DriveServiceHelper mDriveServiceHelper;

    // BAR code reader stuff
    private ZXingScannerView mScannerViewXing;

    /**
     * Adapter to display records
     */
    public RecordAdapter adapter;

    /**
     * Store user preferences
     */
    public SharedPreferences prefs;

    /**
     * View for the Search text
     */
    public SearchEditText searchEditText;

    /**
     * Main list view
     */
    public AnimatedListView list;
    public View listContainer;
    /**
     * View to display when list is empty
     */
    public View emptyListView;
    /**
     * Utility for automatically hiding the keyboard when scrolling down
     */
    private KeyboardScrollHider hider;

    /**
     * The ViewGroup that wraps the buttons at the right hand side of the searchEditText
     */
    public ViewGroup rightHandSideButtonsWrapper;
    /**
     * Menu button
     */
    public View menuButton;
    /**
     * Zen Launcher bar
     */
    public View kissBar;
    /**
     * Favorites bar. Can be either the favorites within the Zen Launcher bar,
     * or the external favorites bar (default)
     */
    public ViewGroup favoritesBar;
    /**
     * Progress bar displayed when loading
     */
    private View loaderSpinner;

    /**
     * The ViewGroup that wraps the buttons at the left hand side of the searchEditText
     */
    public ViewGroup leftHandSideButtonsWrapper;
    /**
     * Launcher button, can be clicked to display all apps
     */
    public View launcherButton;

    /**
     * Launcher button's white counterpart, which appears when launcher button is clicked
     */
    public View whiteLauncherButton;
    /**
     * "X" button to empty the search field
     */
    public View clearButton;

    public View numericButton;
    public View keyboardButton;
    public View historyButton;

    private View resultsLayout;

    /**
     * Task launched on text change
     */
    private Searcher searchTask;

    /**
     * SystemUiVisibility helper
     */
    private SystemUiVisibilityHelper systemUiVisibilityHelper;

    /**
     * Is the Zen Launcher bar currently displayed?
     * (flag updated before animation is over)
     */
    private boolean isDisplayingKissBar = false;

    public static PopupWindow mPopup;

    private ForwarderManager forwarderManager;
    public static boolean mDebugJson = false;
    GoogleSignInClient mGoogleSignInClient;
    private int widgetAddY,widgetAddX;
    public int action;

    /**
     * Access instance from broadcasters
     */
    public static MainActivity instance;
    private SamsungBadgeObserver samsungBadgeObserver;

    public static MainActivity getInstance() {
        return instance;
    }

    public void signIn(int action) {
        if (BuildConfig.DEBUG) Log.i(TAG, "signIn");
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        this.action = action;
        // The Task returned from this call is always completed, no need to attach
        // a listener.
        startActivityForResult(signInIntent, RC_SIGN_IN);

    }

    public void signOut() {
        if (BuildConfig.DEBUG) Log.i(TAG, "signOut");
        mGoogleSignInClient.signOut();
        mSignedIn = false;
    }

    public static byte[] objToByte(ZenLayout tcpPacket) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        ObjectOutputStream objStream = new ObjectOutputStream(byteStream);
        objStream.writeObject(tcpPacket);
        return byteStream.toByteArray();
    }

    public static Object byteToObj(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
        ObjectInputStream objStream = new ObjectInputStream(byteStream);
        return objStream.readObject();
    }

    // current Zen Launcher layout - serializable
    private ZenLayout mZenLayout;
    private Bitmap overlay(Bitmap bmp1, Bitmap bmp2) {
        Bitmap bmOverlay = Bitmap.createBitmap(bmp1.getWidth(), bmp1.getHeight(), bmp1.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.drawBitmap(bmp1, new Matrix(), null);
        canvas.drawBitmap(bmp2, new Matrix(), null);
        return bmOverlay;
    }

    /**
     * Gets a screenshot to use with snapshots. Note that in practice you probably do not want to
     * use this approach because tablet screen sizes can become pretty large and because the image
     * will contain any UI and layout surrounding the area of interest.
     */
    Bitmap getScreenShot() {
        final WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        final Drawable wallpaperDrawable = wallpaperManager.peekDrawable();
        View root = getWindow().getDecorView().getRootView();
        Bitmap screenshot = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        Bitmap coverImage = ((BitmapDrawable) wallpaperDrawable).getBitmap();
        Bitmap combo = overlay(screenshot, coverImage);
        Canvas canvas = new Canvas(combo);
        root.draw(canvas);
        return RotateBitmap(combo, 270f);


    }

    ByteArrayOutputStream getScreenShotWallPaper() {
        final WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
        final Drawable wallpaperDrawable = wallpaperManager.peekDrawable();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ((BitmapDrawable) wallpaperDrawable).getBitmap().compress(Bitmap.CompressFormat.JPEG, 80, bytes);

        return bytes;
    }

    public static Bitmap RotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    public boolean checkPermissionReadStorage(Activity activity) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_STORAGE);
            } else {

                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_STORAGE);
            }
        } else {
            return true;
        }
        return false;
    }

    public boolean checkPermissionOverlay() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, MY_PERMISSIONS_OVERLAY);
                return false;
            }
            if (BuildConfig.DEBUG) Log.i(TAG, "overlay permission ok");
            return true;
        } else {
            if (BuildConfig.DEBUG)
                Log.i(TAG, "overlay permission ok, sdk:" + Build.VERSION.SDK_INT);
            return true;
        }
    }

    public boolean askPermissionCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_CAMERA);
            Log.d(TAG,"askPermissionCamera FALSE");
            return false;
        } else {
            Log.d(TAG,"askPermissionCamera TRUE");
            return true;
        }
    }

    public boolean askPermissionStorage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_CAMERA);
            Log.d(TAG,"askPermissionCamera FALSE");
            return false;
        } else {
            Log.d(TAG,"askPermissionCamera TRUE");
            return true;
        }
    }

    public boolean askPermissionDeviceAdmin(int usage) {
        if (!isDeviceAdminActive()) {
           if (BuildConfig.DEBUG) Log.v("DeviceAdminSwitch", "askPermissionDeviceAdmin");
            ComponentName compName = new ComponentName(this, ZenAdmin.class);
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName);
            switch (usage) {
                case REQUEST_DEVICE_ADMIN_FROM_PREFERENCES:
                case REQUEST_DEVICE_ADMIN_FOR_LOCK_AFTER:
                case REQUEST_DEVICE_ADMIN_FOR_LOCK_NOW:
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Permission is needed to be able to lock device directly from Zen Launcher");
                    break;
                default:
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Permission is needed to be able to lock device directly from Zen Launcher");
                    break;
            }

            startActivityForResult(intent, usage);
            Log.d(TAG,"askPermissionDeviceAdmin FALSE");
            return false;
        } else {
            Log.d(TAG,"askPermissionDeviceAdmin TRUE");
            return true;
        }

    }
    private boolean isDeviceAdminActive() {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName compName = new ComponentName(this, ZenAdmin.class);
        if (devicePolicyManager != null) {
            return devicePolicyManager.isAdminActive(compName);
        } else {
            return false;
        }
    }

    public boolean checkPermissionDoNotDisturb(Activity activity) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.EXTRA_DO_NOT_DISTURB_MODE_ENABLED,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, MY_PERMISSIONS_DND);
                return false;
            }
            if (BuildConfig.DEBUG) Log.i(TAG, "dnd permission ok");
            return true;
        } else {
            if (BuildConfig.DEBUG)
                Log.i(TAG, "dnd permission ok, sdk:" + Build.VERSION.SDK_INT);
            return true;
        }
    }


    public void checkPermissionHuawei(Activity activity) {
        if (BuildConfig.DEBUG) Log.i(TAG, "checkPermissionHuawei");

            // Should we show an explanation?
            ActivityCompat.requestPermissions(activity,
                    new String[]{"com.huawei.android.totemweather.permission.ACCESS_WEATHERCLOCK_PROVIDER"},
                    MY_PERMISSIONS_HUAWEI);
    }

    public RecordAdapter getAdapter(){
        return adapter;
    }

    private boolean isExternalFavoriteBarEnabled() {
        return prefs.getBoolean("enable-favorites-bar", true);
    }

    Uri samsungBadgeUri = Uri.parse("content://com.sec.badge/apps");
    IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
    BroadcastReceiver mReceiver = new ScreenReceiver(this);
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        instance = this;
        super.onCreate(savedInstanceState);
        if (BuildConfig.DEBUG) Log.i(TAG, "onCreate()");
        mReceiver = new ScreenReceiver(this);
        KissApplication.setMainActivity(this);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mReceiver, filter);

        /*
         * Initialize preferences
         */
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        MemoryCacheHelper.updatePreferences(prefs);

        // Configure sign-in to request the user's ID, email address, and basic
        // profile.

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope(DriveScopes.DRIVE_APPDATA))
                .requestEmail()
                .build();
        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        /*
         * Initialize all forwarders
         */
        forwarderManager = new ForwarderManager(this);

        /*
         * Set the view and store all useful components
         */
        setContentView(R.layout.main);

        this.list = this.findViewById(android.R.id.list);
        this.listContainer = (View) this.list.getParent();
        this.emptyListView = this.findViewById(android.R.id.empty);
        this.kissBar = findViewById(R.id.mainKissbar);
        this.rightHandSideButtonsWrapper = findViewById(R.id.rightHandSideButtonsWrapper);
        this.menuButton = findViewById(R.id.menuButton);
        this.searchEditText = findViewById(R.id.searchEditText);
        this.loaderSpinner = findViewById(R.id.loaderBar);
        this.leftHandSideButtonsWrapper = findViewById(R.id.leftHandSideButtonsWrapper);
        this.launcherButton = findViewById(R.id.launcherButton);
        this.whiteLauncherButton = findViewById(R.id.whiteLauncherButton);
        this.clearButton = findViewById(R.id.clearButton);
        this.numericButton = findViewById(R.id.numericButton);
        this.keyboardButton = findViewById(R.id.keyboardButton);
        this.historyButton = findViewById(R.id.historyButton);
        if (prefs.getBoolean("enable-historybutton", true)) {
            this.historyButton.setVisibility(View.VISIBLE);
        }
        this.resultsLayout = findViewById(R.id.resultLayout);
        this.resultsLayout.setTag(this.resultsLayout.getVisibility());
        this.resultsLayout.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int newVis = resultsLayout.getVisibility();
            if((int)resultsLayout.getTag() != newVis)
            {
                resultsLayout.setTag(resultsLayout.getVisibility());
                resultsVisible = newVis != View.GONE;
            }
        });

        /*
         * Initialize components behavior
         * Note that a lot of behaviors are also initialized through the forwarderManager.onCreate() call.
         */
        displayLoader(true);
        loaderSpinner.setOnClickListener(v -> launcherButton.callOnClick());

        // Add touch listener for history popup to root view
        findViewById(android.R.id.content).setOnTouchListener(this);
        findViewById(android.R.id.content).setOnLongClickListener(this);
        findViewById(R.id.launcherButton).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                kissBar.setVisibility(View.VISIBLE);
                findViewById(R.id.embeddedZenButtons).setVisibility(View.VISIBLE);
                if (!isExternalFavoriteBarEnabled()) {
                    findViewById(R.id.embeddedFavoritesBar).setVisibility(View.GONE);
                }

                return true;
            }
        });

        // add history popup touch listener to empty view (prevents on not working there)
        this.emptyListView.setOnTouchListener(this);

        // Create adapter for records
        this.adapter = new RecordAdapter(this, new ArrayList<>());
        this.list.setAdapter(this.adapter);

        this.list.setOnItemClickListener((parent, v, position, id) -> adapter.onClick(position, v));

        this.list.setLongClickable(true);
        this.list.setOnItemLongClickListener((parent, v, pos, id) -> {
            ((RecordAdapter) parent.getAdapter()).onLongClick(pos, v);
            return true;
        });

        // Display empty list view when having no results
        this.adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                if (BuildConfig.DEBUG) Log.d(TAG,"onChanged");
                super.onChanged();
                if (adapter.isEmpty()) {
                    // Display help text when no results available
                    listContainer.setVisibility(View.GONE);
                    emptyListView.setVisibility(View.VISIBLE);
                } else {
                    // Otherwise, display results
                    listContainer.setVisibility(View.VISIBLE);
                    emptyListView.setVisibility(View.GONE);
                }

                forwarderManager.onDataSetChanged();

            }
        });

        // Listen to changes
        searchEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                // Auto left-trim text.
                if (s.length() > 0 && s.charAt(0) == ' ')
                    s.delete(0, 1);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isViewingAllApps()) {
                    displayKissBar(false, false, new ApplicationsSearcher(MainActivity.this));
                }
                String text = s.toString();
                updateSearchRecords(text);
                displayClearOnInput();
                if (searchEditText.getText().length() != 0) {
                    list.setFastScrollAlwaysVisible(false);
                    list.setFastScrollEnabled(false);
                } else {
                    list.setFastScrollAlwaysVisible(true);
                    list.setFastScrollEnabled(true);
                }
            }
        });


        // Fixes bug when dropping onto a textEdit widget which can cause a NPE
        // This fix should be on ALL TextEdit Widgets !!!
        // See : https://stackoverflow.com/a/23483957
        searchEditText.setOnDragListener((v, event) -> {
            if (BuildConfig.DEBUG) Log.d(TAG,"searchEditText, onDrag()");
            return true;
        });


        // On validate, launch first record
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.R.id.closeButton) {
                if (mPopup != null) {
                    mPopup.dismiss();
                    return true;
                }
                hider.fixScroll();
                return false;
            }
            RecordAdapter adapter = ((RecordAdapter) list.getAdapter());

            adapter.onClick(adapter.getCount() - 1, v);

            return true;
        });

        registerForContextMenu(menuButton);

        // When scrolling down on the list,
        // Hide the keyboard.
        this.hider = new KeyboardScrollHider(this,
                this.list,
                this.findViewById(R.id.listEdgeEffect)
        );
        this.hider.start();

        // Enable/disable phone broadcast receiver
        IncomingCallHandler.setEnabled(this, prefs.getBoolean("enable-phone-history", false));

        // Enable/disable badge count broadcast receiver
        BadgeCountHandler.setEnabled(this, prefs.getBoolean("enable-app-badges", true));

        // Hide the "X" after the text field, instead displaying the menu button
        displayClearOnInput();

        systemUiVisibilityHelper = new SystemUiVisibilityHelper(this);

        /*
         * Defer everything else to the forwarders
         */
        forwarderManager.onCreate();
        initializeKeyboardListener();
        //if(BuildConfig.DEBUG) Log.i(TAG,">setOnDragListener");

        //findViewById(R.id.main).setOnLongClickListener(new MyOnClickListener());
        //if(BuildConfig.DEBUG) Log.i(TAG,"<setOnDragListener");
        if (prefs.getBoolean("bluelightfilter", false)) {
            // setBlueLightFilter(true);
        }

        DefaultLauncherPreference.AsyncTaskCompleteListener callback = new DefaultLauncherPreference.AsyncTaskCompleteListener() {
            @Override
            public void onTaskComplete(boolean result, Activity activity) {
                // Handle the result here
                if (result) {
                    // rebuild shortcuts
                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        ShortcutUtil.addAllShortcuts(activity);
                } else {
                    if (BuildConfig.DEBUG) Log.d(TAG,"not a default launcher");
                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        ShortcutUtil.removeAllShortcuts(activity);
                    defaultLauncherNotification(activity.getCurrentFocus());
                }
            }
        };

        DefaultLauncherPreference.ResolveDefaultLauncherTask myAsyncTask = new DefaultLauncherPreference.ResolveDefaultLauncherTask(callback,this);
        myAsyncTask.execute();

        if (!isExternalFavoriteBarEnabled()) {
            findViewById(R.id.embeddedZenButtons).setVisibility(View.GONE);
        }

    }

    // Remove notification
    private void removeNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(ZEN_NOTIFICATION_ID);
        }
    }

    public void defaultLauncherNotification(View view) {

        //Get an instance of NotificationManager//
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // The id of the channel.
        String id = "zen_channel_01";

        // The user-visible name of the channel.
        CharSequence name = "Default Launcher";

        // The user-visible description of the channel.
        String description = "Zen Launcher notifications";

        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationCompat.Builder mBuilder;
        // Configure the notification channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel(id, name,importance);
            mChannel.setDescription(description);
            if (mNotificationManager != null) {
                mNotificationManager.createNotificationChannel(mChannel);
            }
            mBuilder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_z)
                    .setContentTitle(getResources().getString(R.string.set_as_default))
                    .setChannelId(mChannel.getId())
                    .setContentText(getResources().getString(R.string.set_as_default_info));
        } else {
            mBuilder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_z)
                    .setContentTitle(getResources().getString(R.string.set_as_default))
                    .setContentText(getResources().getString(R.string.set_as_default_info));
        }
        Intent notifyIntent = new Intent(this, MainActivity.class);
        notifyIntent.setAction(ACTION_SET_DEFAULT_LAUNCHER.toString());
       /*notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);*/
        PendingIntent pendingIntent = PendingIntent.getActivity(this, ZEN_NOTIFICATION_ID, notifyIntent,  PendingIntent.FLAG_IMMUTABLE);

        mBuilder.setContentIntent(pendingIntent);


        // When you issue multiple notifications about the same type of event,
        // it’s best practice for your app to try to update an existing notification
        // with this new information, rather than immediately creating a new notification.
        // If you want to update this notification at a later date, you need to assign it an ID.
        // You can then use this ID whenever you issue a subsequent notification.
        // If the previous notification is still visible, the system will update this existing notification,
        // rather than create a new one. In this example, the notification’s ID is 001//

        if (mNotificationManager != null) {
            mNotificationManager.notify(ZEN_NOTIFICATION_ID, mBuilder.build());
        }
    }
    private void buildWidgetPopupMenu() {
        try {
            checkPermissionHuawei(this);
        } catch (Exception e){
            e.printStackTrace();
        }
        widgetAddY = lastTouchY;
        widgetAddX = lastTouchX;
        show(this, lastTouchX, lastTouchY);
    }

    public void show(Activity activity, final float x, final float y) {
        if (BuildConfig.DEBUG) Log.i(TAG, "show");
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        final int ADD_WIDGET = 0;
        final int WIDGET_SETTINGS = 1;
        final int UPDATE_WALLPAPER = 2;
        final int TOGGLE_WIFI = 3;
        final int AIRPLANE_MODE = 4;

        final ViewGroup root = activity.getWindow().getDecorView().findViewById(android.R.id.content);

        final View view = new View(activity.getApplicationContext());
        view.setLayoutParams(new ViewGroup.LayoutParams(1, 1));


        root.addView(view);

        view.setX(x);
        view.setY(y);

        PopupMenu popupExcludeMenu = new PopupMenu(MainActivity.this, view);
        //Adding menu items
        popupExcludeMenu.getMenu().add(ADD_WIDGET, Menu.NONE, Menu.NONE, R.string.menu_widget_add);
        popupExcludeMenu.getMenu().add(WIDGET_SETTINGS, Menu.NONE, Menu.NONE, R.string.menu_widget_settings);
        popupExcludeMenu.getMenu().add(UPDATE_WALLPAPER, Menu.NONE, Menu.NONE, R.string.menu_wallpaper);
        if (wifiManager != null) {
            if (wifiManager.isWifiEnabled()) {
                popupExcludeMenu.getMenu().add(TOGGLE_WIFI, Menu.NONE, Menu.NONE, R.string.wifi_off);
            } else {
                popupExcludeMenu.getMenu().add(TOGGLE_WIFI, Menu.NONE, Menu.NONE, R.string.wifi_on);
            }
        }
        popupExcludeMenu.getMenu().add(AIRPLANE_MODE, Menu.NONE, Menu.NONE, R.string.settings_airplane);


        //registering popup with OnMenuItemClickListener
        popupExcludeMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getGroupId()) {
                    case ADD_WIDGET:
                        hideKeyboard();
                        forwarderManager.addWidget();
                        break;
                    case WIDGET_SETTINGS:
                        hideKeyboard();
                        forwarderManager.showWidgetSettings();
                        break;
                    case UPDATE_WALLPAPER:
                        hideKeyboard();
                        Intent intent2 = new Intent(Intent.ACTION_SET_WALLPAPER);
                        startActivity(Intent.createChooser(intent2, getString(R.string.menu_wallpaper)));
                        break;
                    case TOGGLE_WIFI:
                        toggleWifiState(!item.getTitle().equals(getString(R.string.wifi_off)));
                        break;
                    case AIRPLANE_MODE:
                        Intent intent3 = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
                        startActivity(intent3);
                        break;
                }
                return true;
            }
        });


        popupExcludeMenu.setOnDismissListener(menu -> root.removeView(view));
        Utility.showPopup(popupExcludeMenu, this);
    }

    private void toggleWifiState(boolean state) {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && state && wifiManager.isWifiEnabled()) {
            Toast.makeText(this, getString(R.string.wifi_on), Toast.LENGTH_LONG).show();
        }
        if (wifiManager != null) {
            wifiManager.setWifiEnabled(state);
        }

    }

    public void onMainbarButtonclicked(View view) {
        if (isKeyboardVisible()) {
            switchInputType();
            systemUiVisibilityHelper.onKeyboardVisibilityChanged(true);
        } else {
            if (isShowingPopup()){
                dismissPopup();
            }
            numericButton.setVisibility(View.GONE);
            keyboardButton.setVisibility(View.GONE);
            if (prefs.getBoolean("enable-historybutton", true)) {
                historyButton.setVisibility(View.VISIBLE);
            } else {
                historyButton.setVisibility(View.GONE);
            }
            if (!resultsVisible) {
                showHistory();
            } else {
                searchEditText.setText("");
            }
        }
    }

    // place to put widget when long clicking on widgetlayout
    public int getWidgetAddY() {
        return widgetAddY;
    }
    // place to put widget when long clicking on widgetlayout
    public int getWidgetAddX() {
        return widgetAddX;
    }
    public void resetWidgetAddY() {
        widgetAddY = 0;
    }
    public void resetWidgetAddX() {
        widgetAddX = 0;
    }

    Rect r = new Rect();
    private int lastKeypadHeight = 0;
    private int baseRootHeight = 0;
    private void initializeKeyboardListener() {
        final View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int rootHeight = rootView.getHeight();
            
            // Store base height when keyboard is not visible
            if (baseRootHeight == 0 || rootHeight > baseRootHeight) {
                baseRootHeight = rootHeight;
            }

            // r.bottom is the position above soft keypad or device button.
            // if keypad is shown, the r.bottom is smaller than that before.
            int keypadHeight = screenHeight - r.bottom;

            // 0.15 ratio is perhaps enough to determine keypad height.
            boolean isKeyboardVisible = keypadHeight > screenHeight * 0.15;
            systemUiVisibilityHelper.onKeyboardVisibilityChanged(isKeyboardVisible);
            
            // Check how much the system already resized the view
            int systemResizeAmount = baseRootHeight - rootHeight;
            
            // If system handled most of the resize (>50% of keyboard height), don't add extra padding
            // Otherwise add the full keyboard height as padding
            int neededPadding = 0;
            if (isKeyboardVisible) {
                if (systemResizeAmount > keypadHeight * 0.5) {
                    // System handled resize, no extra padding needed
                    neededPadding = 0;
                } else {
                    // System didn't handle resize, add full padding
                    neededPadding = keypadHeight;
                }
            }
            
            if (keypadHeight != lastKeypadHeight) {
                lastKeypadHeight = keypadHeight;
                
                // Apply padding to root view to push everything up (including search bar)
                rootView.setPadding(0, 0, 0, neededPadding);
                
                // Ensure list scrolls to show latest results
                if (isKeyboardVisible && list != null && list.getCount() > 0) {
                    list.setSelection(list.getCount() - 1);
                }
            }
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (BuildConfig.DEBUG) Log.i(TAG, "onCreateContextMenu");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        forwarderManager.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (BuildConfig.DEBUG) Log.d(TAG,"onStart");
        EventBus.getDefault().register(this);
        forwarderManager.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (BuildConfig.DEBUG) Log.d(TAG,"onStop");
        EventBus.getDefault().unregister(this);
        if (camera != null) {
            camera.release();
        }
        forwarderManager.onStop();
    }


    private void updateUI(boolean signedIn) {
        if (signedIn) {
            // Sign-in OK!
            mSignedIn = true;
            prefs.edit().putBoolean("wasSigned", true).apply();
            if (BuildConfig.DEBUG) Log.i(TAG, "Sign-in successful!");
        } else {
            mSignedIn = false;
            if (BuildConfig.DEBUG) Log.i(TAG, "Not signed to Google!");
        }
    }

    public static boolean flashToggle;

    Camera camera = null;

    public void toggleFlashLight() {
        if (BuildConfig.DEBUG) Log.i(TAG, "toggleFlashLight");
        flashToggle = !flashToggle;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                CameraManager cameraManager = (CameraManager) getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
                if (cameraManager != null) {
                    for (String id : cameraManager.getCameraIdList()) {
                        // Turn on the flash if camera has one
                        if (cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)) {
                            cameraManager.setTorchMode(id, flashToggle);
                        }
                    }
                }
            } catch (Exception e2) {
                Toast.makeText(getApplicationContext(), "Torch Failed: " + e2.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {  //Lollipop and older
            toggleFlashLightPreM(flashToggle);
        }

        if (flashToggle) {
            Toast.makeText(this, R.string.flashlight_on, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.flashlight_off, Toast.LENGTH_SHORT).show();
        }

    }

    public void toggleFlashLightPreM(boolean on) {
        flashToggle = on;
        try {
            if (on) {
                camera = Camera.open();
                Camera.Parameters p = camera.getParameters();
                p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                camera.setParameters(p);
                camera.startPreview();
            } else {
                if (camera != null) {
                    camera.release();
                }
            }
        } catch (RuntimeException e) {

        }
    }

    public void startAppGridActivity() {
        startActivity(new Intent(this, AppGridActivity.class));
    }

    /**
     * Restart if required,
     * Hide the kissbar by default
     */
    @SuppressLint("CommitPrefEdits")
    protected void onResume() {
        if (BuildConfig.DEBUG) Log.i(TAG, "onResume()");
        // Apps may notify badge updates for Samsung devices
        // through a ContentResolver on the url: content://com.sec.badge/apps
        if (SamsungBadgeObserver.providerExists(this)) {

            //Content Resolver has content, so, register for updates and load its actual content
            samsungBadgeObserver = new SamsungBadgeObserver(new Handler(), this);
            getContentResolver()
                    .registerContentObserver(samsungBadgeUri, false,
                            new SamsungBadgeObserver(new Handler(), this));
            SamsungBadgeObserver.loadBadges(this);
        }

        if (flashToggle) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                toggleFlashLightPreM(flashToggle);
            }
        }

        if (mDebugJson) {
            try {
                String settings = this.getSerializedSettings2();
                if (BuildConfig.DEBUG) Log.i(TAG, "settings:" + settings);
            } catch (JSONException e) {
                if (BuildConfig.DEBUG) Log.i(TAG, "JSONException");
            }
        }
        if (prefs.getBoolean("require-layout-update", false)) {
            super.onResume();
            Log.i(TAG, "Restarting app after setting changes");
            // Restart current activity to refresh view, since some preferences
            // may require using a new UI
            prefs.edit().putBoolean("require-layout-update", false).apply();
            this.recreate();
            return;
        }

        dismissPopup();

        if (KissApplication.getApplication(this) != null
                && KissApplication.getApplication(this).getDataHandler().allProvidersHaveLoaded) {
            displayLoader(false);
            if (BuildConfig.DEBUG) Log.i(TAG, ">onFavoriteChange");
            onFavoriteChange();
        }

        // We need to update the history in case an external event created new items
        // (for instance, installed a new app, got a phone call or simply clicked on a favorite)
        updateSearchRecords();
        displayClearOnInput();

        if (isViewingAllApps()) {
            displayKissBar(false);
        }

        forwarderManager.onResume();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (BuildConfig.DEBUG) Log.d(TAG,"onDestroy");
        if (mReceiver!=null) {
            try {
                unregisterReceiver(mReceiver);
            } catch (final IllegalArgumentException unregisteredException) {
                Log.w(TAG, "Broadcast receiver already unregistered (" + unregisteredException.getMessage() + ")");
            }
            mReceiver = null;
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(ZEvent event) {
        if (BuildConfig.DEBUG) Log.i(TAG, "Got message from service: " + event.getState());

        switch (event.getState()) {
            case GOOGLE_SIGNIN:
                signIn(0);
                break;
            case GOOGLE_SIGNOUT:
                signOut();
                break;
            case LOAD_OVER:
                if (BuildConfig.DEBUG) Log.v(TAG, "provider done loading.");
                KissApplication.getApplication(this).getDataHandler().handleProviderLoaded();
                updateSearchRecords();
                // onFavoriteChange();
                EventBus.getDefault().removeAllStickyEvents();
                break;
            case FULL_LOAD_OVER:
                if (BuildConfig.DEBUG) Log.v(TAG, "All providers are done loading.");
                displayLoader(false);
                onFavoriteChange();
                // Run GC once to free all the garbage accumulated during provider initialization
                System.gc();
                EventBus.getDefault().removeAllStickyEvents();
                break;
            case SHOW_TOAST:
                if (BuildConfig.DEBUG) Log.v(TAG, "Show toast");
                Toast.makeText(getBaseContext(), event.getText(),
                        Toast.LENGTH_LONG).show();

                break;
            case BADGE_COUNT:
                if (BuildConfig.DEBUG) Log.v(TAG, "BADGE_COUNT update, event.getText():"+event.getText());
                DataHandler dh = KissApplication.getApplication(this).getDataHandler();
                if (dh.isFavorite(event.getText())) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "isFavorite:" + event.getText());
                    onFavoriteChange();
                }
                break;
            case NOTIFICATION_COUNT:
                if (BuildConfig.DEBUG) Log.v(TAG, "NOTIFICATION_COUNT update, event.getText():"+event.getText());
                DataHandler dh2 = KissApplication.getApplication(this).getDataHandler();
                if (dh2.isFavorite(event.getText())) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "isFavorite:" + event.getText());
                    onFavoriteChange();
                }
                if (!isDisplayingKissBar) {
                    adapter.notifyDataSetChanged();
                }
                break;
            case WIFI_ON:
                toggleWifiState(true);
                break;
            case WIFI_OFF:
                toggleWifiState(false);
                break;
            case NIGHTMODE_ON:
                setBlueLightFilter(true);
                break;
            case NIGHTMODE_OFF:
                setBlueLightFilter(false);
                break;
            case FLASHLIGHT_ON:
                flashToggle = false;
                toggleFlashLight();
                break;
            case FLASHLIGHT_OFF:
                flashToggle = true;
                toggleFlashLight();
                break;
            case UPDATE_WALLPAPER:
                updateWallPaper();
                break;
            case ALARM_AT:
                askAlarmAt();
                break;
            case DATE_TIME_PICKER:
                dateTimePicker(event.getText());
                break;
            case REFRESH_UI:
                if (BuildConfig.DEBUG) Log.d(TAG,"REFRESH_UI");
                //Update favorite bar
                this.onFavoriteChange();
                //Update Search to reflect favorite add, if the "exclude favorites" option is active
                if (this.prefs.getBoolean("exclude-favorites-apps", false) && this.isViewingSearchResults()) {
                    this.updateSearchRecords();
                }
                EventBus.getDefault().removeStickyEvent(event);
                break;
            case BARCODE_READER:
                if (askPermissionCamera()) {
                    startBarCodeScan();
                }
                break;
            case DEV_ADMIN_LOCK_AFTER:
                if (askPermissionDeviceAdmin(REQUEST_DEVICE_ADMIN_FOR_LOCK_AFTER)) {

                }
                break;
            case DEV_ADMIN_LOCK_PROXIMITY:
                if (askPermissionDeviceAdmin(REQUEST_DEVICE_ADMIN_FOR_LOCK_AFTER)) {

                }
                break;
            case SHOW_HISTORYBUTTON:
                historyButton.setVisibility(View.VISIBLE);
                EventBus.getDefault().removeStickyEvent(event);
                break;

            case HIDE_HISTORYBUTTON:
                historyButton.setVisibility(View.GONE);
                EventBus.getDefault().removeStickyEvent(event);
                break;
            case REQUEST_REMOVE_DEVICE_ADMIN_AND_UNINSTALL:
                disableDeviceAdminAndUninstall();
                break;
            case RELOAD_APPS:
                if (BuildConfig.DEBUG) Log.v(TAG, "RELOAD_APPS:");
                KissApplication kissApplication = KissApplication.getApplication(this);
                if (kissApplication!=null){
                    if (BuildConfig.DEBUG) Log.v(TAG, "RELOAD_APPS2:");
                    DataHandler dataHandler = kissApplication.getDataHandler();
                    if (dataHandler!=null){
                        AppProvider appProvider = dataHandler.getAppProvider();

                        // if AppProvider is not initialized yet (boot phase), this event can be ignored.
                        if (appProvider!=null){
                            if (BuildConfig.DEBUG) Log.v(TAG, "RELOAD_APPS: appProvider.reload");
                            MemoryCacheHelper.trimMemory();
                            appProvider.reload();
                            onFavoriteChange();
                        }
                    }
                }
                EventBus.getDefault().removeStickyEvent(event);
                break;
        }
    }

    private void disableDeviceAdminAndUninstall() {
        disableDeviceAdmin(this);
        Intent intent = new Intent(Intent.ACTION_DELETE,
                Uri.fromParts("package", this.getPackageName(), null));
        try {
            this.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.application_not_found, Toast.LENGTH_LONG).show();
        }
    }

    private void dateTimePicker(String text) {
        alarmText = text.replace(DATE_TIME_PICKER.toString(), "");
        showDialog(999);
    }

    String alarmText;
    Calendar calAlarm = Calendar.getInstance();
    int year,month,date;
    private final DatePickerDialog.OnDateSetListener myDateListener = new DatePickerDialog.OnDateSetListener() {
        @Override
        public void onDateSet(DatePicker arg0, int y, int m, int d) {
            year = y;
            month = m;
            date = d;
            Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int date = c.get(Calendar.DATE);
            arg0.updateDate(year,month,date);
            showDialog(1000);
        }
    };
    private final TimePickerDialog.OnTimeSetListener myTimeListener = new TimePickerDialog.OnTimeSetListener() {
        @Override
        public void onTimeSet(TimePicker timePicker, int hours, int minutes) {
            calAlarm.set(year,month, date, hours,minutes,0);
            Intent alarmIntent = new Intent(getApplicationContext(), LauncherService.class);
            alarmIntent.putExtra(ALARM_DATE_PICKER_MILLIS.toString(), calAlarm.getTimeInMillis());
            alarmIntent.putExtra(ALARM_ENTERED_TEXT.toString(),alarmText);
            alarmIntent.setAction(ALARM_PICKER.toString());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Calendar c = Calendar.getInstance();
                int hour = c.get(Calendar.HOUR_OF_DAY);
                int minute = c.get(Calendar.MINUTE);
                timePicker.setHour(hour);
                timePicker.setMinute(minute);
            }
            startService(alarmIntent);
        }
    };

    protected Dialog onCreateDialog(int id) {
        // TODO Auto-generated method stub
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int date = c.get(Calendar.DATE);
        int hours = c.get(Calendar.HOUR_OF_DAY);
        int minutes = c.get(Calendar.MINUTE);

        switch (id) {
            case 999:
                return new DatePickerDialog(this, myDateListener, year, month, date);
            case 1000:
                return new TimePickerDialog(this, myTimeListener, hours, minutes, true);
        }
        return null;
    }
    Locale getCurrentLocale(Context context){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            return context.getResources().getConfiguration().getLocales().get(0);
        } else{
            //noinspection deprecation
            return context.getResources().getConfiguration().locale;
        }
    }

    private void askAlarmAt() {

        Date currentTime = Calendar.getInstance().getTime();

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.alarmAt);


        final EditText input = new EditText(this);

        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        builder.setView(input);
        input.setText(currentTime.toString());
        input.selectAll();

// Set up the buttons
        builder.setPositiveButton(R.string.alarmAt, (dialog, which) -> {

        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();

    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (BuildConfig.DEBUG) Log.d(TAG,"onNewIntent");
        // This is called when the user press Home again while already browsing MainActivity
        // onResume() will be called right after, hiding the kissbar if any.
        // http://developer.android.com/reference/android/app/Activity.html#onNewIntent(android.content.Intent)
        // Animation can't happen in this method, since the activity is not resumed yet, so they'll happen in the onResume()
        // https://github.com/Neamar/KISS/issues/569
        if (!searchEditText.getText().toString().isEmpty()) {
            if (BuildConfig.DEBUG) Log.i(TAG, "Clearing search field");
            searchEditText.setText("");
        }

        // Hide kissbar when coming back to kiss
        if (isViewingAllApps()) {
            displayKissBar(false);
        }

        // Close the backButton context menu
        closeContextMenu();
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SET_DEFAULT_LAUNCHER.toString().equals(action)) {
                DefaultLauncherPreference.selectLauncher(this);
                removeNotification();
            }
        }
    }

    private void hideZenButtonsIfNeeded(){
        if (findViewById(R.id.embeddedZenButtons).isShown()){
            if (!isExternalFavoriteBarEnabled()) {
                findViewById(R.id.mainKissbar).setVisibility(View.GONE);
                findViewById(R.id.embeddedZenButtons).setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (BuildConfig.DEBUG) Log.i(TAG, "onBackPressed");

        // Check if widget resize mode should be exited first
        if (forwarderManager.onBackPressed()) {
            return;
        }

        if (mPopup != null) {
            mPopup.dismiss();
        } else if (isViewingAllApps()||findViewById(R.id.embeddedZenButtons).isShown()) {
            displayKissBar(false);
        } else {
            // If no kissmenu, empty the search bar
            // (this will trigger a new event if the search bar was already empty)
            // (which means pressing back in minimalistic mode with history displayed
            // will hide history again)
            searchEditText.setText("");
        }
        if (mScannerViewXing!=null && mScannerViewXing.isShown()){
            if (BuildConfig.DEBUG) Log.d(TAG,"onBackPressed, mScannerViewXing");
            mScannerViewXing.stopCamera();           // Stop camera on pause
            ViewGroup contentFrame = findViewById(R.id.content_frame);
            contentFrame.removeView(mScannerViewXing);
        }
        // No call to super.onBackPressed(), since this would quit the launcher.
    }

    @Override
    public boolean onKeyDown(int keycode, @NonNull KeyEvent e) {
        if (BuildConfig.DEBUG) Log.i(TAG, "onKeyDown: "+keycode);
        if (keycode == KeyEvent.KEYCODE_MENU) {
            // For devices with a physical menu button, we still want to display *our* contextual menu
            menuButton.showContextMenu();
            menuButton.performHapticFeedback(LONG_PRESS);
            return true;
        }

        return super.onKeyDown(keycode, e);
    }

    private void queryDriveFiles() {
        if (mDriveServiceHelper != null) {
            if (BuildConfig.DEBUG) Log.i(TAG, "Querying for files.");
            showSpinner(R.string.loading_from_cloud);
            mDriveServiceHelper.queryFiles()
                    .addOnSuccessListener(fileList -> {
                        dismissSpinner();
                        final ViewGroup root = this.getWindow().getDecorView().findViewById(android.R.id.content);

                        final View view = new View(MainActivity.this);
                        view.setLayoutParams(new ViewGroup.LayoutParams(1, 1));
                        root.addView(view);
                        PopupMenu popupExcludeMenu = new PopupMenu(MainActivity.this, view);
                        int i = 0;
                        for (File file : fileList.getFiles()) {
                            //mDriveServiceHelper.deleteFile(file);
                            //builder.append(file.getName()).append("\n");
                            //Adding menu items
                            // popupExcludeMenu.getMenu().add(i, Menu.NONE, Menu.NONE, file.getId());
                            final SubMenu listSubMenu = popupExcludeMenu.getMenu().addSubMenu(i, Menu.NONE, Menu.NONE, file.getName());
                            MenuItem readMenu = listSubMenu.add(i, Menu.NONE, Menu.NONE, "Load");
                            readMenu.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                final File fileLocal = file;

                                @Override
                                public boolean onMenuItemClick(MenuItem menuItem) {
                                    readFile(fileLocal.getId());
                                    return true;
                                }
                            });
                            MenuItem deleteMenu = listSubMenu.add(i + 1, Menu.NONE, Menu.NONE, "Delete");
                            //registering popup with OnMenuItemClickListener

                            deleteMenu.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                final File fileLocal = file;

                                @Override
                                public boolean onMenuItemClick(MenuItem menuItem) {
                                    mDriveServiceHelper.deleteFile(fileLocal);
                                    return true;
                                }
                            });

                            // TODO: now working yet
                            /* MenuItem renameMenu = listSubMenu.add(i + 1, Menu.NONE, Menu.NONE, "Rename");
                            //registering popup with OnMenuItemClickListener

                            renameMenu.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                final File fileLocal = file;

                                @Override
                                public boolean onMenuItemClick(MenuItem menuItem) {
                                    queryNewName(fileLocal.getName(), fileLocal);
                                    return true;
                                }
                            }); */


                            i++;
                        }
                        popupExcludeMenu.setOnDismissListener(menu -> root.removeView(view));

                        Utility.showPopup(popupExcludeMenu, this);
                    /*    popupExcludeMenu.getMenu().getItem(0).getActionView().setOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View view) {
                                deleteFile((String) popupExcludeMenu.getMenu().getItem(0).getTitle());
                                return true;
                            }
                        }); */

                    })
                    .addOnFailureListener(exception -> {
                        Toast.makeText(getBaseContext(), "Unable to query files.",
                                Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Unable to query files.", exception);
                        dismissSpinner();
                    });
        }
    }


    private void queryNewName(String name, File fileLocal) {


        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Rename File");

// Set up the input
        final EditText input = new EditText(this);
// Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        builder.setView(input);
        input.setText(name);
        input.selectAll();

// Set up the buttons
        builder.setPositiveButton("Rename", (dialog, which) -> mDriveServiceHelper.renameFile(fileLocal, String.valueOf(input.getText())).addOnFailureListener(e -> {
            if (BuildConfig.DEBUG) Log.i(TAG, "Rename Failed exception:" + e);
        }));
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();


    }

    private void updateWallPaper(){
        hideKeyboard();
        Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
        startActivity(Intent.createChooser(intent, getString(R.string.menu_wallpaper)));
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (forwarderManager.onOptionsItemSelected(item)) {
            return true;
        }
        Intent intent;
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                return true;
            case R.id.wallpaper:
                updateWallPaper();
                return true;
            case R.id.preferences:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.shareTags:
                intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                String serializedTags;
                try {
                    serializedTags = getSerializedTags();
                } catch (JSONException e) {
                    serializedTags = e.toString();
                }
                intent.putExtra(Intent.EXTRA_TEXT, serializedTags);
                intent.putExtra(Intent.EXTRA_SUBJECT, "Zen Launcher__tags_" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()) + ".json");
                intent.setType("application/json");
                startActivity(Intent.createChooser(intent, getString(R.string.share_tags_chooser)));
                return true;
            case R.id.loadReplaceTags:
                intent = new Intent();
                intent.setType("application/json");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, getString(R.string.share_tags_chooser)), REQUEST_LOAD_REPLACE_TAGS);
                return true;
/*          case R.id.loadReplaceSettings:
                intent = new Intent();
                intent.setType("application/json");
                intent.setAction(Intent.ACTION_GET_CONTENT);

                startActivityForResult(Intent.createChooser(intent, getString(R.string.load_replace_tags)), REQUEST_LOAD_REPLACE_SETTINGS);
                return true;
            case R.id.shareSettings:
                intent = new Intent();
                intent.setAction(Intent.ACTION_SEND);
                String serializedSettings;
                try {
                    serializedSettings = getSerializedSettings2();
                } catch (JSONException e) {
                    serializedSettings = e.toString();
                }
                String serializedWidgetSettings;
                try {
                    serializedWidgetSettings = getSerializedWidgetSettings();
                } catch (JSONException e) {
                    serializedWidgetSettings = e.toString();
                }
                intent.putExtra(Intent.EXTRA_TEXT, serializedSettings + serializedWidgetSettings);
                intent.putExtra(Intent.EXTRA_SUBJECT, "Zen Launcher_settings" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()) + ".json");
                intent.setType("application/json");
                startActivity(Intent.createChooser(intent, getString(R.string.share_settings)));
                return true;
*/
            case R.id.saveToGoogle:
                signIn(R.id.saveToGoogle);
                return true;

            case R.id.loadFromGoogle:
                signIn(R.id.loadFromGoogle);
                return true;
            case R.id.nightModeOn:
                if (BuildConfig.DEBUG) Log.i(TAG, "nightModeOn");
                setBlueLightFilter(true);
                 return true;
            case R.id.nightModeOff:
                if (BuildConfig.DEBUG) Log.i(TAG, "nightModeOff");
                setBlueLightFilter(false);
                return true;
            case R.id.appGrid:
                startAppGridActivity();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void startBarCodeScan() {
        ViewGroup contentFrame = findViewById(R.id.content_frame);

        if (mScannerViewXing !=null) {
            if (BuildConfig.DEBUG) Log.d(TAG,"startBarCodeScan, remove existing mScannerViewXing");
            mScannerViewXing.stopCamera();           // Stop camera on pause
            contentFrame.removeView(mScannerViewXing);
        }
        mScannerViewXing = new ZXingScannerView(this);   // Programmatically initialize the scanner view
        mScannerViewXing.setResultHandler(this);
        mScannerViewXing.setBorderColor(R.color.zenlauncher);
        mScannerViewXing.setBorderCornerRadius(5);
        contentFrame.addView(mScannerViewXing);
        mScannerViewXing.startCamera();
    }

    private void loadFromGoogle() {
        if (mSignedIn) {
            queryDriveFiles();
        } else {
            Toast.makeText(getBaseContext(), "Not signed in to Google",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToGoogle() {

        if (mSignedIn) {
            createFile("" + Calendar.getInstance().getTime());
        } else {
            Toast.makeText(getBaseContext(), "Not signed in to Google",
                    Toast.LENGTH_SHORT).show();
        }

    }

    // progress dialog we display while we're loading state from the cloud
    ProgressDialog mSpinner = null;

    void showSpinner(int resId) {
        if (mSpinner == null) {
            mSpinner = new ProgressDialog(this);
            mSpinner.setMessage(getString(resId));
        }

        mSpinner.show();

    }

    void dismissSpinner() {
        if (mSpinner != null && mSpinner.isShowing()) {
            mSpinner.dismiss();
            mSpinner = null;
        }
    }


    String getSerializedSettings2() throws JSONException {
        Map<String, ?> tags;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        tags = prefs.getAll();
        JSONObject json = new JSONObject(tags);
        for (Map.Entry<String, ?> entry : tags.entrySet()) {
            Object v = entry.getValue();
            String key = entry.getKey();
          /*  if (key.equals("favorite-apps-list")){
                json.putOpt(key, v + "_!_" + "java.util.HashSet");
            } else { */
            json.putOpt(key, v + "_!_" + v.getClass().getSimpleName());
            //}
        }


        return json.toString(1);
    }

    private String getSerializedWidgetSettings() throws JSONException {
        Map<String, ?> tags;
        SharedPreferences prefsWidget = this.getSharedPreferences(WIDGET_PREFERENCE_ID, Context.MODE_PRIVATE);
        tags = prefsWidget.getAll();

        JSONObject jsonWidget = new JSONObject(tags);
        for (Map.Entry<String, ?> entry : tags.entrySet()) {
            Object v = entry.getValue();
            String key = entry.getKey();
          /*  if (key.equals("favorite-apps-list")){
                json.putOpt(key, v + "_!_" + "java.util.HashSet");
            } else { */
            jsonWidget.putOpt(key, v + "_!_" + v.getClass().getSimpleName());
            //}
        }

        if (BuildConfig.DEBUG)
            Log.i(TAG, "getSerializedWidgetSettings:" + jsonWidget.toString(1));
        return jsonWidget.toString(1);
    }


    private int loadJson(String jsonText) throws JSONException {
        int count = 0;
        TagsHandler tagsHandler = KissApplication.getApplication(this).getDataHandler().getTagsHandler();
        if (BuildConfig.DEBUG) Log.i(TAG, "jsonText:" + jsonText);
        JSONObject json = new JSONObject(jsonText);
        Iterator<String> iter = json.keys();
        SharedPreferences.Editor editor = prefs.edit();
        String booleanClassname = "Boolean";
        String stringClassname = "String";
        String hashsetClassname = "HashSet";

        while (iter.hasNext()) {
            String key = iter.next();
            String[] values = json.get(key).toString().split("_!_");
            String value = values[0];
            String classValue = values[1];

            if (BuildConfig.DEBUG)
                Log.i(TAG, "key:" + key + " value:" + value + " classValue:" + classValue);
            if (classValue.equals(booleanClassname)) {
                if (BuildConfig.DEBUG) Log.i(TAG, "putBoolean");
                editor.putBoolean(key, Boolean.parseBoolean(value));
            } else if (classValue.equals(stringClassname)) {
                if (BuildConfig.DEBUG) Log.i(TAG, "putString");
                editor.putString(key, value);
            } else if (classValue.equals(hashsetClassname)) {
                if (BuildConfig.DEBUG) Log.i(TAG, "putStringSet:" + value);
                String[] hsets = value.substring(1, value.length() - 1).split(", ");
                Set<String> hs = new HashSet<>(Arrays.asList(hsets));
                editor.putStringSet(key, hs);
            } else if (key.equals("tags")) {
                if (BuildConfig.DEBUG) Log.i(TAG, "value:" + value);
                String toparse = value.substring(1, value.length() - 1);
                if (BuildConfig.DEBUG) Log.i(TAG, "toparse:" + toparse);
                if (!toparse.isEmpty()) {
                    String[] values2 = toparse.split(", ");
                    for (String s : values2) {
                        if (BuildConfig.DEBUG) Log.i(TAG, "values2:" + s);
                        String[] app = s.split("=");
                        if (BuildConfig.DEBUG) Log.i(TAG, "appId:" + app[0]);
                        if (BuildConfig.DEBUG) Log.i(TAG, "tagsForApp:" + app[1]);
                        tagsHandler.setTags(app[0], app[1]);
                    }
                }
            }
            editor.apply();
            count += 1;
        }
        editor.commit();
        //forwarderManager.onDataSetChanged();
        //KissApplication.getApplication(this).getDataHandler().getAppProvider().reload();

        return count;
    }

    private int loadWidgetJson(String jsonText) throws JSONException {
        int count = 0;
        TagsHandler tagsHandler = KissApplication.getApplication(this).getDataHandler().getTagsHandler();
        if (BuildConfig.DEBUG) Log.i(TAG, "jsonText:" + jsonText);
        JSONObject json = new JSONObject(jsonText);
        Iterator<String> iter = json.keys();
        SharedPreferences prefsWidget = this.getSharedPreferences(WIDGET_PREFERENCE_ID, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefsWidget.edit();
        editor.clear().apply();
        String booleanClassname = "Boolean";
        String stringClassname = "String";
        String hashsetClassname = "HashSet";

        while (iter.hasNext()) {
            String key = iter.next();
            String[] values = json.get(key).toString().split("_!_");
            String value = values[0];
            String classValue = values[1];

            if (BuildConfig.DEBUG)
                Log.i(TAG, "key:" + key + " value:" + value + " classValue:" + classValue);
            if (classValue.equals(booleanClassname)) {
                if (BuildConfig.DEBUG) Log.i(TAG, "putBoolean");
                editor.putBoolean(key, Boolean.parseBoolean(value));
            } else if (classValue.equals(stringClassname)) {
                if (BuildConfig.DEBUG) Log.i(TAG, "putString");
                editor.putString(key, value);
            } else if (classValue.equals(hashsetClassname)) {
                if (BuildConfig.DEBUG) Log.i(TAG, "putStringSet:" + value);
                String[] hsets = value.substring(1, value.length() - 1).split(", ");
                Set<String> hs = new HashSet<>(Arrays.asList(hsets));
                editor.putStringSet(key, hs);
            } else if (key.equals("tags")) {
                if (BuildConfig.DEBUG) Log.i(TAG, "value:" + value);
                String toparse = value.substring(1, value.length() - 1);
                if (BuildConfig.DEBUG) Log.i(TAG, "toparse:" + toparse);
                if (!toparse.isEmpty()) {
                    String[] values2 = toparse.split(", ");
                    for (String s : values2) {
                        if (BuildConfig.DEBUG) Log.i(TAG, "values2:" + s);
                        String[] app = s.split("=");
                        if (BuildConfig.DEBUG) Log.i(TAG, "appId:" + app[0]);
                        if (BuildConfig.DEBUG) Log.i(TAG, "tagsForApp:" + app[1]);
                        tagsHandler.setTags(app[0], app[1]);
                    }
                }
            }
            editor.apply();
            count += 1;
        }
        editor.commit();
        //forwarderManager.onDataSetChanged();
        //KissApplication.getApplication(this).getDataHandler().getAppProvider().reload();

        return count;
    }


    private boolean mSignedIn = false;

    /* Creates a new file via the Drive REST API.
     */
    private void createFile(String name) {
        if (mDriveServiceHelper != null) {
            if (BuildConfig.DEBUG) Log.i(TAG, "Creating a file.");
            showSpinner(R.string.saving_to_cloud);
            mDriveServiceHelper.createFile(name)
                    .addOnSuccessListener(fileId -> saveFile(fileId, name))
                    .addOnFailureListener(exception -> {
                        Log.e(TAG, "Couldn't create file.", exception);
                        Toast.makeText(this, "Couldn't create file", Toast.LENGTH_LONG).show();
                        dismissSpinner();
                    });
        }
    }

    /**
     * Retrieves the title and content of a file identified by {@code fileId} and populates the UI.
     */
    private void readFile(String fileId) {
        if (mDriveServiceHelper != null) {
            if (BuildConfig.DEBUG) Log.i(TAG, "Reading file " + fileId);
            showSpinner(R.string.loading_from_cloud);
            mDriveServiceHelper.readFile(fileId)
                    .addOnSuccessListener(nameAndContent -> {
                        String name = nameAndContent.first;
                        byte[] content = nameAndContent.second;
                        if (BuildConfig.DEBUG) Log.i(TAG, "name " + name);
                        getDataFromOpenedFile(content);
                    })
                    .addOnSuccessListener(stringPair -> dismissSpinner())
                    .addOnFailureListener(exception -> {
                        Toast.makeText(this, "Couldn't read file.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Couldn't read file.", exception);
                        dismissSpinner();
                    });

        }
    }

    private void getDataFromOpenedFile(byte[] content) {
        try {
            forwarderManager.removeWidgets();
            mZenLayout = (ZenLayout) byteToObj(content);
            DBHelper.writeDatabase(mZenLayout.getDataBase(), this);
            int count = 0;
            try {
                count = loadJson(mZenLayout.getSavedSettings());
            } catch (Exception e) {
                Log.e(TAG, "can't load tags", e);
                Toast.makeText(this, "can't load tags", Toast.LENGTH_LONG).show();
            }
            Toast.makeText(this, "loaded tags for " + count + " app(s)", Toast.LENGTH_SHORT).show();
            try {
                count = loadWidgetJson(mZenLayout.getSavedWidgets());
            } catch (Exception e) {
                Log.e(TAG, "can't load widgets", e);
                Toast.makeText(this, "can't load tags", Toast.LENGTH_LONG).show();
            }
            Toast.makeText(this, "loaded widgets for " + count + " app(s)", Toast.LENGTH_LONG).show();

        } catch (ClassNotFoundException e) {
            Log.e(TAG, "ClassNotFoundException", e);
            e.printStackTrace();
        } catch (IOException e) {
            Toast.makeText(this, "can't load settings", Toast.LENGTH_LONG).show();
            Log.e(TAG, "IOException", e);
            e.printStackTrace();
        }
        if (mZenLayout != null && mZenLayout.getData()!=null) {
            new Thread(() -> {
                Bitmap bMap = BitmapFactory.decodeByteArray(mZenLayout.getData(), 0, mZenLayout.getData().length);
                try {
                    getApplicationContext().setWallpaper(bMap);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
                System.exit(0);
            }).start();
        }
    }

    /**
     * Saves the currently opened file
     *
     */
    private void saveFile(String fileId, String fileName) {

        if (mDriveServiceHelper != null) {
            if (BuildConfig.DEBUG) Log.i(TAG, "Saving " + fileId);
            byte[] fileContent = null;
            try {
                if (checkPermissionReadStorage(this)) {
                    mZenLayout = new ZenLayout(getSerializedSettings2(), getSerializedWidgetSettings(), getScreenShotWallPaper(), DBHelper.getDatabaseBytes());
                } else {
                    mZenLayout = new ZenLayout(getSerializedSettings2(), getSerializedWidgetSettings(), null, DBHelper.getDatabaseBytes());
                }

                fileContent = objToByte(mZenLayout);
            } catch (IOException | SecurityException | JSONException e) {
                e.printStackTrace();
            }

            mDriveServiceHelper.saveFile(fileId, fileName, fileContent)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            dismissSpinner();
                            Toast.makeText(getBaseContext(), "Saved",
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(exception -> {
                        dismissSpinner();
                        Toast.makeText(getBaseContext(), "Unable to save",
                                Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Unable to save file via REST.", exception);
                    });
        }
    }

    String getSerializedTags() throws JSONException {
        Map<String, String> tags = DBHelper.loadTags(this);
        JSONObject json = new JSONObject(tags);
        return json.toString(1);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);

        return true;
    }

    /**
     * Display menu, on short or long press.
     *
     * @param menuButton "kebab" menu (3 dots)
     */
    public void onMenuButtonClicked(View menuButton) {
        // When the Zen Launcher bar is displayed, the button can still be clicked in a few areas (due to favorite margin)
        // To fix this, we discard any click event occurring when the kissbar is displayed
        if (!isViewingSearchResults()) {
            return;
        }
        if (!forwarderManager.onMenuButtonClicked(this.menuButton)) {
            try {
                this.menuButton.showContextMenu();
                this.menuButton.performHapticFeedback(LONG_PRESS);
            } catch (Exception e){
                Log.e(TAG, "onMenuButtonClicked exception:"+ e);
            }
        }
    }

    private void handleSignInResult(Intent result) {
        GoogleSignIn.getSignedInAccountFromIntent(result)
                .addOnSuccessListener(googleAccount -> {
                    if (BuildConfig.DEBUG)
                        Log.i(TAG, "Signed in as " + googleAccount.getEmail());

                    // Use the authenticated account to sign in to the Drive service.
                    GoogleAccountCredential credential =
                            GoogleAccountCredential.usingOAuth2(
                                    this, Collections.singleton(DriveScopes.DRIVE_APPDATA));
                    credential.setSelectedAccount(googleAccount.getAccount());
                    Drive googleDriveService =
                            new Drive.Builder(
                                    AndroidHttp.newCompatibleTransport(),
                                    new GsonFactory(),
                                    credential)
                                    .setApplicationName("Zen Launcher")
                                    .build();
                    updateUI(true);

                    // The DriveServiceHelper encapsulates all REST API and SAF functionality.
                    mDriveServiceHelper = new DriveServiceHelper(googleDriveService);
                    if (action > 0) {
                        switch (action) {
                            case R.id.saveToGoogle:
                                saveToGoogle();
                                break;
                            case R.id.loadFromGoogle:
                                loadFromGoogle();
                                break;
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Unable to sign in.", e);
                    String error = String.format("Unable to sign in, error: %1$s", e.getMessage());

                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    updateUI(false);
                });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (BuildConfig.DEBUG) Log.d(TAG, "onActivityResult,"+requestCode+ " "+resultCode);
        switch (requestCode) {
            case REQUEST_DEVICE_ADMIN_FOR_LOCK_AFTER:
            case REQUEST_DEVICE_ADMIN_FOR_LOCK_NOW:
            case REQUEST_DEVICE_ADMIN_FROM_PREFERENCES:
                if (resultCode == Activity.RESULT_OK) {
                    prefs.edit().putBoolean("device-admin-switch", true).apply();
                } else {
                    prefs.edit().putBoolean("device-admin-switch", false).apply();
                    EventBus.getDefault().removeAllStickyEvents();
                }
                break;
            case REQUEST_BIND_APPWIDGET:
                if (BuildConfig.DEBUG) Log.i(TAG, "REQUEST_BIND_APPWIDGET");
                if (resultCode == Activity.RESULT_OK) {
                    int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    addWidget(appWidgetId);
                }
                break;
            case MY_PERMISSIONS_OVERLAY:
                if (BuildConfig.DEBUG) Log.i(TAG, "MY_PERMISSIONS_OVERLAY:" + resultCode);
                if (Build.VERSION.SDK_INT >= 23) {
                    if (Settings.canDrawOverlays(this)) {
                        setBlueLightFilter(true);
                    } else {
                        Toast.makeText(this, "Need overlay permission for this feature", Toast.LENGTH_LONG).show();
                    }
                } else {
                    setBlueLightFilter(true);
                }
                break;
            case MY_PERMISSIONS_HUAWEI:
                if (BuildConfig.DEBUG) Log.i(TAG, "MY_PERMISSIONS_HUAWEI:" + resultCode);
                break;
            case RC_SIGN_IN:
                handleSignInResult(data);
                break;

            case REQUEST_LOAD_REPLACE_TAGS:
                if (BuildConfig.DEBUG) Log.i(TAG, "REQUEST_LOAD_REPLACE_TAGS");
                if (resultCode == RESULT_OK) {
                    TagsHandler tagsHandler = KissApplication.getApplication(this).getDataHandler().getTagsHandler();
                    Uri selectedFile = data.getData();
                    if (selectedFile != null) {
                        InputStream stream;
                        try {
                            stream = getContentResolver().openInputStream(selectedFile);
                        } catch (FileNotFoundException e) {
                            Log.e("TBog", "con't open file", e);
                            stream = null;
                        }
                        if (stream != null) {
                            int count = 0;
                            try {
                                String jsonText = convertStreamToString(stream);
                                JSONObject json = new JSONObject(jsonText);
                                Iterator<String> iter = json.keys();
                                while (iter.hasNext()) {
                                    String appId = iter.next();
                                    String tagsForApp = json.get(appId).toString();
                                    tagsHandler.setTags(appId, tagsForApp);
                                    count += 1;
                                }
                            } catch (Exception e) {
                                Log.e("TBog", "can't load tags", e);
                                Toast.makeText(this, "can't load tags", Toast.LENGTH_LONG).show();
                            }
                            Toast.makeText(this, "loaded tags for " + count + " app(s)", Toast.LENGTH_LONG).show();
                        }
                    }
                }
                break;
            case REQUEST_LOAD_REPLACE_SETTINGS:
                if (BuildConfig.DEBUG) Log.i(TAG, "REQUEST_LOAD_REPLACE_SETTINGS");
                if (resultCode == RESULT_OK) {
                    Uri selectedFile = data.getData();
                    if (selectedFile != null) {
                        InputStream stream;
                        try {
                            stream = getContentResolver().openInputStream(selectedFile);
                        } catch (FileNotFoundException e) {
                            Log.e(TAG, "con't open file", e);
                            stream = null;
                        }
                        if (stream != null) {
                            int count = 0;
                            try {
                                count = loadJson(convertStreamToString(stream));
                            } catch (Exception e) {
                                Log.e(TAG, "can't load tags", e);
                                Toast.makeText(this, "can't load tags", Toast.LENGTH_LONG).show();
                            }
                            Toast.makeText(this, "loaded tags for " + count + " app(s)", Toast.LENGTH_LONG).show();
                        }
                    }
                }

                break;
        }
        forwarderManager.onActivityResult(requestCode, resultCode, data);
    }

    private void setBlueLightFilter(boolean b) {
        if (checkPermissionOverlay()) {
            if (b) {
                prefs.edit().putBoolean("bluelightfilter", true).apply();
                Intent nighton = new Intent(this, LauncherService.class);
                nighton.setAction(NIGHTMODE_ON.toString());
                KissApplication.startLaucherService(nighton, this);
            } else {
                prefs.edit().putBoolean("bluelightfilter", false).apply();
                Intent nighton = new Intent(this, LauncherService.class);
                nighton.setAction(NIGHTMODE_OFF.toString());
                KissApplication.startLaucherService(nighton, this);
            }
        }
    }


    public static String convertStreamToString(InputStream is) throws IOException {

        BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.defaultCharset()));
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } finally {
            is.close();
        }
        return sb.toString();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        forwarderManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lastTouchX = (int) event.getX();
            lastTouchY = (int) event.getY();
        }
        if (forwarderManager.onTouch(view, event)) {
            return true;
        }

        if (view.getId() == searchEditText.getId()) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                searchEditText.performClick();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onLongClick(View view) {
        if (BuildConfig.DEBUG) Log.i(TAG, "onLongClick");
        buildWidgetPopupMenu();
        return true;
    }

    /**
     * Clear text content when touching the cross button
     */
    @SuppressWarnings("UnusedParameters")
    public void onClearButtonClicked(View clearButton) {
        searchEditText.setText("");
    }

    /**
     * Display Zen Launcher menu
     */
    public void onLauncherButtonClicked(View launcherButton) {
        // Display or hide the Zen Launcher bar, according to current view tag (showMenu / hideMenu).
        displayKissBar(launcherButton.getTag().equals("showMenu"));
    }

    public void onContactsButtonClicked(View contactsButton) {
        // Display or hide the Zen Launcher contacts bar, according to current view tag (showMenu / hideMenu).
        displayContacts(contactsButton.getTag().equals("showMenu"));
    }

    public int lastTouchX, lastTouchY;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        if (BuildConfig.DEBUG) Log.i(TAG, "dispatchKeyEvent: " + event.getAction() + " - " + event.getKeyCode());
        if (event.getAction() == KeyEvent.ACTION_DOWN)
        {
            if(event.getKeyCode() == KeyEvent.KEYCODE_ENTER)
            {
                if (BuildConfig.DEBUG) Log.i(TAG, "dispatchKeyEvent KEYCODE_ENTER:");
                // TODO: check if focused to favorites and start focused favorite


                return super.dispatchKeyEvent(event);
            }


        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (BuildConfig.DEBUG) Log.i(TAG, "dispatchTouchEvent: " + ev.getAction());

        if (mPopup != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            dismissPopup();
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void displayClearOnInput() {
        if (searchEditText.getText().length() > 0) {
            clearButton.setVisibility(View.VISIBLE);
            menuButton.setVisibility(View.INVISIBLE);
        } else {
            clearButton.setVisibility(View.INVISIBLE);
            menuButton.setVisibility(View.VISIBLE);
        }
    }

    private void printCallStack(){
        Log.d(TAG,"Printing stack trace:");
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        for (int i = 1; i < elements.length; i++) {
            StackTraceElement s = elements[i];
            Log.d(TAG,"\tat " + s.getClassName() + "." + s.getMethodName() + "(" + s.getFileName() + ":" + s.getLineNumber() + ")");
        }
    }

    public void displayLoader(Boolean display) {
        if (BuildConfig.DEBUG) Log.d(TAG,"displayLoader:" + display);
        //printCallStack();
        int animationDuration = getResources().getInteger(
                android.R.integer.config_longAnimTime);

        // Do not display animation if launcher button is already visible
        if (!display && launcherButton.getVisibility() == View.INVISIBLE) {
            launcherButton.setVisibility(View.VISIBLE);

            // Animate transition from loader to launch button
            launcherButton.setAlpha(0);
            launcherButton.animate()
                    .alpha(1f)
                    .setDuration(animationDuration)
                    .setListener(null);
            loaderSpinner.animate()
                    .alpha(0f)
                    .setDuration(animationDuration)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            loaderSpinner.setVisibility(View.GONE);
                            loaderSpinner.setAlpha(1);
                        }
                    });
        } else if (display) {
            launcherButton.setVisibility(View.INVISIBLE);
            loaderSpinner.setVisibility(View.VISIBLE);
        }
    }

    public void onFavoriteChange() {
        if (BuildConfig.DEBUG) Log.i(TAG, "onFavoriteChange");
        forwarderManager.onFavoriteChange();
    }

    public void displayKissBar(Boolean display) {
        this.displayKissBar(display, true, new ApplicationsSearcher(MainActivity.this));
    }

    public void displayContacts(Boolean display) {
        this.displayKissBar(display, true, new ContactSearcher(MainActivity.this));
    }

    public void displayAppsWithNotif(Boolean display) {
        this.displayKissBar(display, true, new AppsWithNotifSearcher(MainActivity.this));
    }

    public void displayShortcuts(Boolean display) {
        this.displayKissBar(display, true, new ShortcutsSearcher(MainActivity.this, false));
    }
    public void displayWebShortcuts(Boolean display) {
        this.displayKissBar(display, true, new ShortcutsSearcher(MainActivity.this, true));
    }
    private void displayKissBar(boolean display, boolean clearSearchText, Searcher searchTask) {
        dismissPopup();
        // get the center for the clipping circle
        ViewGroup launcherButtonWrapper = (ViewGroup) launcherButton.getParent();
        int cx = (launcherButtonWrapper.getLeft() + launcherButtonWrapper.getRight()) / 2;
        int cy = (launcherButtonWrapper.getTop() + launcherButtonWrapper.getBottom()) / 2;

        // get the final radius for the clipping circle
        int finalRadius = Math.max(kissBar.getWidth(), kissBar.getHeight());

        if (display) {
            // Display the app list
            if (searchEditText.getText().length() != 0) {
                searchEditText.setText("");
            }
            resetTask();

            // Needs to be done after setting the text content to empty
            isDisplayingKissBar = true;

            searchTask.executeOnExecutor(Searcher.SEARCH_THREAD);

            // Reveal the bar
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int animationDuration = getResources().getInteger(
                        android.R.integer.config_shortAnimTime);
                try {
                    Animator anim = ViewAnimationUtils.createCircularReveal(kissBar, cx, cy, 0, finalRadius);
                    anim.setDuration(animationDuration);
                    anim.start();
                } catch (IllegalStateException e) {
                    // If the view hasn't been laid out yet, we can't animate it
                    e.printStackTrace();
                }
            }
            kissBar.setVisibility(View.VISIBLE);

            // Display the alphabet on the scrollbar (#926)
            list.setFastScrollEnabled(true);
            //list.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_LEFT);
            //list.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
            list.setFastScrollAlwaysVisible(!searchTask.getClass().equals(ContactSearcher.class));

        } else {
            hideZenButtonsIfNeeded();
            isDisplayingKissBar = false;
            // Hide the bar
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int animationDuration = getResources().getInteger(
                        android.R.integer.config_shortAnimTime);

                try {
                    Animator anim = ViewAnimationUtils.createCircularReveal(kissBar, cx, cy, finalRadius, 0);
                    anim.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            kissBar.setVisibility(View.GONE);
                            super.onAnimationEnd(animation);
                        }
                    });
                    anim.setDuration(animationDuration);
                    anim.start();
                } catch (IllegalStateException e) {
                    // If the view hasn't been laid out yet, we can't animate it
                    kissBar.setVisibility(View.GONE);
                }
            } else {
                // No animation before Lollipop
                kissBar.setVisibility(View.GONE);
            }

            if (clearSearchText) {
                searchEditText.setText("");
            }
        }

        forwarderManager.onDisplayKissBar(display);
    }

    public void updateSearchRecords() {
        if (isViewingSearchResults())
            updateSearchRecords(searchEditText.getText().toString());
    }

    /**
     * This function gets called on query changes.
     * It will ask all the providers for data
     * This function is not called for non search-related changes! Have a look at onDataSetChanged() if that's what you're looking for :)
     *
     * @param query the query on which to search
     */
    private void updateSearchRecords(String query) {
        resetTask();
        dismissPopup();

        forwarderManager.updateSearchRecords(query);

        if (query.isEmpty()) {
            systemUiVisibilityHelper.resetScroll();
        } else {
            runTask(new QuerySearcher(this, query));
        }
    }

    public void runTask(Searcher task) {
        resetTask();
        searchTask = task;
        searchTask.executeOnExecutor(Searcher.SEARCH_THREAD);
    }

    public void resetTask() {
        if (searchTask != null) {
            searchTask.cancel(true);
            searchTask = null;
        }
    }

    protected void onPause() {
        super.onPause();
        forwarderManager.onPause();
        if (SamsungBadgeObserver.providerExists(this)) {
            getContentResolver()
                    .unregisterContentObserver(samsungBadgeObserver);
        }
        /*if (mScannerViewBar !=null) {
            if (BuildConfig.DEBUG) Log.d(TAG,"onPause, mScannerViewBar");
            mScannerViewBar.stopCamera();           // Stop camera on pause
            ViewGroup contentFrame = (ViewGroup) findViewById(R.id.content_frame);
            contentFrame.removeView(mScannerViewBar);

        } */
        if (mScannerViewXing !=null) {
            if (BuildConfig.DEBUG) Log.d(TAG,"onPause, mScannerViewXing");
            mScannerViewXing.stopCamera();           // Stop camera on pause
            ViewGroup contentFrame = findViewById(R.id.content_frame);
            contentFrame.removeView(mScannerViewXing);

        }
        instance = null;
    }


    /**
     * transcriptMode on the listView decides when to scroll back to the first item.
     * The value we have by default, TRANSCRIPT_MODE_ALWAYS_SCROLL, means that on every new search,
     * (actually, on any change to the listview's adapter items)
     * scroll is reset to the bottom, which makes sense as we want the most relevant search results
     * to be visible first (searching for "ab" after "a" should reset the scroll).
     * However, when updating an existing result set (for instance to remove a record, add a tag,
     * etc.), we don't want the scroll to be reset. When this happens, we temporarily disable
     * the scroll mode.
     * However, we need to be careful here: the PullView system we use actually relies on
     * TRANSCRIPT_MODE_ALWAYS_SCROLL being active. So we add a new message in the queue to change
     * back the transcript mode once we've rendered the change.
     * <p>
     * (why is PullView dependent on this? When you show the keyboard, no event is being dispatched
     * to our application, but if we don't reset the scroll when the keyboard appears then you
     * could be looking at an element that isn't the latest one as you start scrolling down
     * [which will hide the keyboard] and start a very ugly animation revealing items currently
     * hidden. Fairly easy to test, remove the transcript mode from the XML and the .post() here,
     * then scroll in your history, display the keyboard and scroll again on your history)
     */
    @Override
    public void temporarilyDisableTranscriptMode() {
        list.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_DISABLED);
        // Add a message to be processed after all current messages, to reset transcript mode to default
        list.post(() -> list.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL));
    }

    /**
     * Force  set transcript mode.
     * Be careful when using this, it's almost always better to use temporarilyDisableTranscriptMode()
     * unless you need to deal with the keyboard appearing for something else than a search.
     * Always make sure you call this function twice, once to disable, and once to re-enable
     *
     * @param transcriptMode new transcript mode to set on the list
     */
    @Override
    public void updateTranscriptMode(int transcriptMode) {
        list.setTranscriptMode(transcriptMode);
    }

    /**
     * Call this function when we're leaving the activity after clicking a search result
     * to clear the search list.
     * We can't use onPause(), since it may be called for a configuration change
     */
    @Override
    public void launchOccurred() {
        // We selected an item on the list,
        // now we can cleanup the filter:
        if (!searchEditText.getText().toString().isEmpty()) {
            searchEditText.setText("");
            displayClearOnInput();
            hideKeyboard();
        } else if (isViewingAllApps()) {
            displayKissBar(false);
        }
    }

    public void registerPopup(ListPopup popup) {
        if (mPopup == popup)
            return;
        dismissPopup();
        mPopup = popup;
        popup.setVisibilityHelper(systemUiVisibilityHelper);
        popup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                mPopup = null;
            }
        });
        hider.fixScroll();
    }

    @Override
    public void showDialog(DialogFragment dialog) {
        final View resultLayout = findViewById(R.id.resultLayout);
        if (dialog instanceof CustomIconDialog) {
            // We assume the mResultLayout was visible
            resultLayout.setVisibility(View.GONE);
            ((CustomIconDialog) dialog).setOnDismissListener(dlg -> {
                resultLayout.setVisibility(View.VISIBLE);
                // force icon reload by searching again; is there any better way?
                updateSearchRecords();
            });
        }
        try {
            dialog.show(getFragmentManager(), "dialog");
        } catch (IllegalStateException e){ // activity onSaveInstanceState called?
            Log.d(TAG,"showDialog, IllegalStateException:" +e);
        }
    }

    public void refreshWidget(int appWidgetId) {
        Intent intent = new Intent();
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        forwarderManager.onActivityResult(REQUEST_REFRESH_APPWIDGET, RESULT_OK, intent);
    }

    public void addWidget(int appWidgetId) {
        Intent intent = new Intent();
        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        forwarderManager.onActivityResult(REQUEST_CREATE_APPWIDGET, RESULT_OK, intent);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        systemUiVisibilityHelper.onWindowFocusChanged(hasFocus);
        forwarderManager.onWindowFocusChanged(hasFocus);
    }


    public void showKeyboard() {
        if (BuildConfig.DEBUG) Log.d(TAG,"showKeyboard");
        searchEditText.requestFocus();
        InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        assert mgr != null;
        mgr.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
    }

    @Override
    public void hideKeyboard() {
        if (BuildConfig.DEBUG) Log.d(TAG,"hideKeyboard");
        if (isKeyboardVisible()) {
            // Check if no view has focus:
            View view = this.getCurrentFocus();
            if (view != null) {
                InputMethodManager inputManager = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
                //noinspection ConstantConditions
                inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
            dismissPopup();
        }
    }

    @Override
    public void applyScrollSystemUi() {
        systemUiVisibilityHelper.applyScrollSystemUi();
    }

    /**
     * Check if history / search or app list is visible
     *
     * @return true of history, false on app list
     */
    public boolean isViewingSearchResults() {
        return !isDisplayingKissBar;
    }

    public boolean isViewingAllApps() {
        return isDisplayingKissBar;
    }

    @Override
    public void beforeListChange() {
        list.prepareChangeAnim();
    }

    @Override
    public void afterListChange() {
        list.animateChange();
    }

    public static void dismissPopup() {
        if (mPopup != null)
            mPopup.dismiss();
    }
    public static boolean isShowingPopup(){
        return mPopup != null && mPopup.isShowing();
    }

    public void showMatchingTags(String tag) {
        runTask(new TagsSearcher(this, tag));

        clearButton.setVisibility(View.VISIBLE);
        menuButton.setVisibility(View.INVISIBLE);
    }

    public void showUntagged() {
        runTask(new UntaggedSearcher(this));

        clearButton.setVisibility(View.VISIBLE);
        menuButton.setVisibility(View.INVISIBLE);
    }

    boolean resultsVisible = false;
    public void showHistory() {
        runTask(new HistorySearcher(this));
        clearButton.setVisibility(View.VISIBLE);
        menuButton.setVisibility(View.INVISIBLE);
    }

    public boolean isKeyboardVisible() {
        return systemUiVisibilityHelper.isKeyboardVisible();
    }


    public void onWallpaperScroll(float fCurrent) {
        forwarderManager.onWallpaperScroll(fCurrent);
    }

    public void onClick(View view) {
        if (BuildConfig.DEBUG) Log.i(TAG, "onClick:" + view.getTag());
        searchEditText.setText((CharSequence) view.getTag());
        //displayKissBar(false,false,false);
    }

    public void onAllAppsButtonClicked(View view) {
        startAppGridActivity();
    }

    public void onApsWithNotifButtonClicked(View view) {
        boolean showMenu = view.getTag().equals("showMenu");
        displayAppsWithNotif(showMenu);
    }

    public void lockScreen() {
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName compName = new ComponentName(this, ZenAdmin.class);
        boolean active = devicePolicyManager.isAdminActive(compName);

        if (active) {
            devicePolicyManager.lockNow();
        } else {
            Toast.makeText(MainActivity.this, "Enable device admin to be able to lock", Toast.LENGTH_SHORT).show();
            askPermissionDeviceAdmin(REQUEST_DEVICE_ADMIN_FOR_LOCK_AFTER);
        }

    }

    public static void disableDeviceAdmin(Context context) {
        try {
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(DEVICE_POLICY_SERVICE);
            ComponentName compName = new ComponentName(context, ZenAdmin.class);
            devicePolicyManager.removeActiveAdmin(compName);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putBoolean("proximity-switch-lock", false).commit();
            Intent proximity = new Intent(context, LauncherService.class);
            proximity.setAction(DISABLE_PROXIMITY.toString());
            KissApplication.startLaucherService(proximity, context);
        } catch (SecurityException e){
            Toast.makeText(context, R.string.permission_denied, Toast.LENGTH_SHORT).show();
        }

    }

    public void onShortcutsButtonClicked(View view) {
        boolean showMenu = view.getTag().equals("showMenu");
        displayShortcuts(showMenu);
    }

    public void onWebShortcutsButtonClicked(View view) {
        boolean showMenu = view.getTag().equals("showMenu");
        displayWebShortcuts(showMenu);
    }

    public void onSearchEditClick(View view) {
        if (BuildConfig.DEBUG) Log.d(TAG,"onSearchEditClick");
    }

    public void switchInputType() {
        forwarderManager.switchInputType();
    }

    @Override
    public void handleResult(com.google.zxing.Result rawResult) {
        // Do something with the result here
        if (BuildConfig.DEBUG) Log.v(TAG, "handleResult zxing:"+rawResult.getText()); // Prints scan results
        if (BuildConfig.DEBUG) Log.v(TAG, "handleResult zxing:"+rawResult.getBarcodeFormat().toString()); // Prints the scan format (qrcode, pdf417 etc.)
        searchEditText.setText(rawResult.getText());
        mScannerViewXing.stopCamera();
        ViewGroup contentFrame = findViewById(R.id.content_frame);
        contentFrame.removeView(mScannerViewXing);
    }
}
