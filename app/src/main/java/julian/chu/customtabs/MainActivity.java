package julian.chu.customtabs;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsServiceConnection;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TabLauncher";
    private static final int REQ_INITIUM = 0x1122;
    private static final int REQ_MOZILLA = 0x011A;

    private static final String KEY_USER_INPUT_URL = "_the_url_input_by_user_";

    private int mMenuItemNums = 3;
    private int mTopBarColor = Color.WHITE;
    private int mBottomBarColor = Color.WHITE;
    private Bitmap mActionButtonIcon;
    private Bitmap mCloseButtonIcon;
    private Button mBtn0;
    private EditText mInput;

    private List<String> mSupportPackages;

    private boolean mShouldCustomTopColor = true;
    private boolean mShouldCustomBottomColor = true;
    private boolean mShouldDefShareItem = false;
    private boolean mCustomAnimation = false;
    private boolean mShouldShowTitle = true;
    private boolean mShouldActionBtn = true;
    private boolean mShouldActionBtnTint = false;
    private boolean mShouldCloseBtn = false;
    private boolean mShouldHardCode = false;
    private String mTargetPackage = null;
    private Mode mMode = Mode.NONE;

    private CustomTabsClient mServiceClient = null;

    private String mUserInputUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initActionBar();
        restorePreferences();

        mSupportPackages = getSupportPackagesName();

        mActionButtonIcon = getBitmap(R.drawable.ic_ab);
        mCloseButtonIcon = getBitmap(R.drawable.ic_close);
        mBtn0 = (Button) findViewById(R.id.btn_0);
        mInput = (EditText) findViewById(R.id.edit_text);
        bindButton();
        setSpinners();
        setToggleButton();
        refreshUI();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        storePreferences();
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putString(KEY_USER_INPUT_URL, mUserInputUrl);
    }

    @Override
    protected void onRestoreInstanceState(Bundle bundle) {
        super.onRestoreInstanceState(bundle);
        mUserInputUrl = bundle.getString(KEY_USER_INPUT_URL, "");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_connect_service).setEnabled(mServiceClient == null);
        menu.findItem(R.id.menu_warm_up).setEnabled(mServiceClient != null);
        menu.findItem(R.id.menu_may_launch).setEnabled(mServiceClient != null);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_button:
                // sadly, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS wont be supported until API24
                Intent intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                break;
            case R.id.menu_connect_service:
                onConnectService();
                break;
            case R.id.menu_warm_up:
                onWarmUp();
                break;
            case R.id.menu_may_launch:
                onMayLaunch();
                break;
            case R.id.menu_edit_url:
                onEditUrlClicked();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private synchronized void onConnectService() {
        final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://"));
        final ResolveInfo info = getPackageManager()
                .resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY);
        final String pkgName = (mShouldHardCode && mTargetPackage != null)
                ? mTargetPackage
                : info.activityInfo.packageName;

        if (TextUtils.isEmpty(pkgName)) {
            prompt("No service to connect");
            return;
        }

        CustomTabsServiceConnection connection = new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(ComponentName name, CustomTabsClient client) {
                mServiceClient = client;
                prompt(pkgName + " connected");
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                mServiceClient = null;
                prompt(pkgName + " disconnected");
            }
        };

        boolean success = CustomTabsClient.bindCustomTabsService(this, pkgName, connection);
        if (!success) {
            prompt("Connect fail");
        }
    }

    private synchronized void onWarmUp() {
        if (mServiceClient == null) {
            return;
        }

        String msg = mServiceClient.warmup(0l)
                ? "War up success"
                : "Warm up fail";
        prompt(msg);
    }

    private synchronized void onMayLaunch() {
        if (mServiceClient == null) {
            return;
        }
        prompt("Not implement yet");
    }

    private void onEditUrlClicked() {
        final LayoutInflater inflater = getLayoutInflater();
        final View content = inflater.inflate(R.layout.layout_input_dialog, null);
        final EditText editText = (EditText) content.findViewById(R.id.edit_text);
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        editText.setText(mUserInputUrl);
        builder.setView(content);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mUserInputUrl = editText.getText().toString();
                refreshUI();
            }
        });
        builder.setNeutralButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });
        builder.show();
    }

    private void initActionBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.action_bar);
        setSupportActionBar(toolbar);
    }

    private Bitmap getBitmap(int res) {
        return BitmapFactory.decodeResource(getResources(), res);
    }

    private void restorePreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        mUserInputUrl = preferences.getString(KEY_USER_INPUT_URL, "");
    }

    private void storePreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_USER_INPUT_URL, mUserInputUrl);
        editor.commit();
    }

    private void bindButton() {
        mBtn0.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickLaunch();
            }
        });
    }

    private void setSpinners() {
        ((Spinner) findViewById(R.id.urls_spinner)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String[] urls = getSelectableUrls();
                mInput.setText(urls[i]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        ((Spinner) findViewById(R.id.menu_item_spinner)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String[] nums = getResources().getStringArray(R.array.menu_item_nums);
                mMenuItemNums = Integer.parseInt(nums[i]);
                mMenuItemNums = Math.min(mMenuItemNums, 5);
                mMenuItemNums = Math.max(mMenuItemNums, 0);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        ((Spinner) findViewById(R.id.top_color_spinner)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mTopBarColor = getColorByIdx(i);
                refreshUI();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        ((Spinner) findViewById(R.id.bottom_color_spinner)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mBottomBarColor = getColorByIdx(i);
                refreshUI();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        // change action button icon
        ((Spinner) findViewById(R.id.action_button_drawable_spinner)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String[] texts = getResources().getStringArray(R.array.selectable_drawables);
                String drawableText = texts[i];
                if ("With Background".equals(drawableText)) {
                    mActionButtonIcon = getBitmap(R.drawable.ic_ab_background);
                } else if ("System".equals(drawableText)) {
                    mActionButtonIcon = getBitmap(android.R.drawable.ic_menu_share);
                } else if ("Large".equals(drawableText)) {
                    mActionButtonIcon = getBitmap(R.drawable.ic_ab_lg);
                } else if ("Small".equals(drawableText)) {
                    mActionButtonIcon = getBitmap(R.drawable.ic_ab_xs);
                } else {
                    // default
                    mActionButtonIcon = getBitmap(R.drawable.ic_ab);
                }

                refreshUI();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        // change close button icon
        ((Spinner) findViewById(R.id.close_button_drawable_spinner)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String[] texts = getResources().getStringArray(R.array.selectable_drawables);
                String drawableText = texts[i];
                if ("With Background".equals(drawableText)) {
                    mCloseButtonIcon = getBitmap(R.drawable.ic_close_background);
                } else if ("System".equals(drawableText)) {
                    mCloseButtonIcon = getBitmap(android.R.drawable.ic_menu_close_clear_cancel);
                } else if ("Large".equals(drawableText)) {
                    mCloseButtonIcon = getBitmap(R.drawable.ic_close_lg);
                } else if ("Small".equals(drawableText)) {
                    mCloseButtonIcon = getBitmap(R.drawable.ic_close_xs);
                } else {
                    // default
                    mCloseButtonIcon = getBitmap(R.drawable.ic_close);
                }

                refreshUI();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        ((Spinner) findViewById(R.id.launch_mode_spinner)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                // Yes, I hard-coded, bite me!
                HashMap<Integer, Mode> map = new HashMap<>();
                map.put(0, Mode.NONE);
                map.put(1, Mode.NORMAL);
                map.put(2, Mode.UGLY);
                map.put(3, Mode.DEPRECATED);
                mMode = map.get(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        Spinner hardCodeSpinner = (Spinner) findViewById(R.id.hard_code_spinner);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                mSupportPackages);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        hardCodeSpinner.setAdapter(spinnerAdapter);
        spinnerAdapter.notifyDataSetChanged();
        hardCodeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (mSupportPackages.size() < 1) {
                    return;
                }
                mTargetPackage = mSupportPackages.get(i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

    }

    private void refreshUI() {
        findViewById(R.id.top_color_spinner).setEnabled(mShouldCustomTopColor);
        findViewById(R.id.bottom_color_spinner).setEnabled(mShouldCustomBottomColor);
        findViewById(R.id.def_share_item_desc).setEnabled(mShouldDefShareItem);
        findViewById(R.id.exit_animation_desc).setEnabled(mCustomAnimation);
        findViewById(R.id.show_title_desc).setEnabled(mShouldShowTitle);
        findViewById(R.id.top_color_preview).setBackgroundColor(mTopBarColor);
        findViewById(R.id.bottom_color_preview).setBackgroundColor(mBottomBarColor);

        ((ImageView) findViewById(R.id.action_button_preview)).setImageBitmap(mActionButtonIcon);
        ((ImageView) findViewById(R.id.close_button_preview)).setImageBitmap(mCloseButtonIcon);

        findViewById(R.id.action_button_drawable_spinner).setEnabled(mShouldActionBtn);
        findViewById(R.id.widget_action_button_tint).setEnabled(mShouldActionBtn);
        findViewById(R.id.action_button_tint_desc).setEnabled(mShouldActionBtn && mShouldActionBtnTint);

        findViewById(R.id.close_button_drawable_spinner).setEnabled(mShouldCloseBtn);

        findViewById(R.id.hard_code_spinner).setEnabled(mShouldHardCode);

        final Spinner urlSpinner = (Spinner) findViewById(R.id.urls_spinner);
        String[] urls = getSelectableUrls();
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                urls);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        urlSpinner.setAdapter(spinnerArrayAdapter);
    }

    private void setToggleButton() {
        ((ToggleButton) findViewById(R.id.set_top_color)).setOnCheckedChangeListener(
                buildCheckHandler("mShouldCustomTopColor"));

        ((ToggleButton) findViewById(R.id.should_set_bottom_bar_color)).setOnCheckedChangeListener(
                buildCheckHandler("mShouldCustomBottomColor"));

        ((ToggleButton) findViewById(R.id.should_set_def_share_item)).setOnCheckedChangeListener(
                buildCheckHandler("mShouldDefShareItem"));

        ((ToggleButton) findViewById(R.id.widget_custom_animation)).setOnCheckedChangeListener(
                buildCheckHandler("mCustomAnimation"));

        ((ToggleButton) findViewById(R.id.widget_show_title)).setOnCheckedChangeListener(
                buildCheckHandler("mShouldShowTitle"));

        ((ToggleButton) findViewById(R.id.widget_action_button)).setOnCheckedChangeListener(
                buildCheckHandler("mShouldActionBtn"));

        ((ToggleButton) findViewById(R.id.widget_close_button)).setOnCheckedChangeListener(
                buildCheckHandler("mShouldCloseBtn"));

        ((ToggleButton) findViewById(R.id.widget_action_button_tint)).setOnCheckedChangeListener(
                buildCheckHandler("mShouldActionBtnTint"));

        ((ToggleButton) findViewById(R.id.widget_hard_code)).setOnCheckedChangeListener(
                buildCheckHandler("mShouldHardCode"));
    }

    @NonNull
    private String[] getSelectableUrls() {
        final String[] selectable = getResources().getStringArray(R.array.selectable_urls);
        ArrayList<String> urls = new ArrayList<>();
        urls.addAll(Arrays.asList(selectable));
        if (!TextUtils.isEmpty(mUserInputUrl)) {
            urls.add(mUserInputUrl);
        }
        return urls.toArray(new String[urls.size()]);
    }

    /**
     * Use reflect to build dynamic function to handle Check-button status change
     *
     * @param fieldName which field to change boolean value
     * @return a handler for CompoundButton.setOnCheckedChangeListener
     */
    private CompoundButton.OnCheckedChangeListener buildCheckHandler(final String fieldName) {
        return new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                Class<?> clazz = MainActivity.this.getClass();
                try {
                    Field f = clazz.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    f.setBoolean(MainActivity.this, b);
                    refreshUI();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            "Fail on set " + fieldName,
                            Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }
        };
    }

    // Yes, Dirty! Bite me!
    private int getColorByIdx(int idx) {
        String[] arrays = getResources().getStringArray(R.array.selectable_colors);
        String colorText = arrays[idx];
        switch (colorText) {
            case "MAGENTA":
                return Color.MAGENTA;
            case "CYAN":
                return Color.CYAN;
            case "GREEN":
                return Color.GREEN;
            case "RED":
                return Color.RED;
            case "BLACK":
                return Color.BLACK;
            case "WHITE":
                return Color.WHITE;
            case "#FF8877FF":
                return 0xFF8877FF;
            case "#00FFFFFF":
                return 0x00FFFFFF;
            case "#FFFFFFF1":
                return 0xFFFFFFF1;
            case "#FFFFFFFF":
                return 0xFFFFFFFF;
            case "#11FF0000":
                return 0x11FF0000;
            case "#55FF0000":
                return 0x55FF0000;
            case "#AAFF0000":
                return 0xAAFF0000;
            case "#EEFF0000":
                return 0xEEFF0000;
            case "#1100FF00":
                return 0x1100FF00;
            case "#5500FF00":
                return 0x5500FF00;
            case "#AA00FF00":
                return 0xAA00FF00;
            case "#EE00FF00":
                return 0xEE00FF00;
            default:
                return getResources().getColor(R.color.mozillaRed);
        }
    }

    private CustomTabsIntent.Builder createBuilder(Mode mode) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

        // set default share item
        if (mShouldDefShareItem) {
            builder.addDefaultShareMenuItem();
        }

        // set action button
        if (mShouldActionBtn) {
            builder.setActionButton(mActionButtonIcon,
                    "The initium",
                    createIntent(REQ_INITIUM, "https://theinitium.com/"),
                    mShouldActionBtnTint);
        }

        // set close button
        if (mShouldCloseBtn) {
            builder.setCloseButtonIcon(mCloseButtonIcon);
        }

        if (mShouldCustomTopColor) {
            builder.setToolbarColor(mTopBarColor);
        }

        builder.setShowTitle(mShouldShowTitle);

        // set menu items
        setMenuItems(builder);

        // set animation
        if (mCustomAnimation) {
            builder.setStartAnimations(this, R.anim.push_down_in, R.anim.push_down_out);
            builder.setExitAnimations(this, R.anim.push_up_in, R.anim.push_up_out);
        }

        if (mode != Mode.NONE) {
            setBottomToolbar(builder, mode);
        }

        return builder;
    }

    private void setMenuItems(CustomTabsIntent.Builder builder) {
        for (int i = 0; i < mMenuItemNums; i++) {
            builder.addMenuItem("Search " + (i + 1),
                    createIntent(0x42 + i, "https://duckduckgo.com/?q=" + i));
        }
    }

    private PendingIntent createIntent(int reqCode, String uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(uri));
        PendingIntent pending = PendingIntent.getActivities(this,
                reqCode,
                new Intent[]{intent},
                PendingIntent.FLAG_CANCEL_CURRENT);

        return pending;
    }

    private void setBottomToolbar(CustomTabsIntent.Builder builder, Mode mode) {
        // add toolbar items
        if (mShouldCustomBottomColor) {
            builder.setSecondaryToolbarColor(mBottomBarColor);
        }
        if (mode == Mode.DEPRECATED) {
            deprecatedAddingToolbarItem(builder);
        } else {
            setSecondaryToolbar(builder, mode);
        }
    }

    private void setSecondaryToolbar(CustomTabsIntent.Builder builder, Mode mode) {
        int layoutRes = (mode == Mode.NORMAL) ?
                R.layout.layout_remote_views :
                R.layout.layout_ugly_remote_views;

        RemoteViews rvs = new RemoteViews(getPackageName(), layoutRes);
        builder.setSecondaryToolbarViews(rvs,
                new int[]{R.id.remote_btn_1, R.id.remote_btn_2},
                createIntent(REQ_MOZILLA, "https://www.mozilla.org"));
    }

    private void deprecatedAddingToolbarItem(CustomTabsIntent.Builder builder) {
        // up to 5 items, otherwise got Exception
        //   java.lang.IllegalStateException: Exceeded maximum toolbar item count of 5
        builder.addToolbarItem(1, getBitmap(R.drawable.ic_a), "item a", createIntent(1, "https://duckduckgo.com/?q=a"));
        builder.addToolbarItem(2, getBitmap(R.drawable.ic_b), "item b", createIntent(2, "https://duckduckgo.com/?q=b"));
        builder.addToolbarItem(3, getBitmap(R.drawable.ic_c), "item c", createIntent(3, "https://duckduckgo.com/?q=c"));
        builder.addToolbarItem(4, getBitmap(R.drawable.ic_d), "item d", createIntent(4, "https://duckduckgo.com/?q=d"));
        builder.addToolbarItem(5, getBitmap(R.drawable.ic_e), "item e", createIntent(5, "https://duckduckgo.com/?q=e"));
    }

    /**
     * snippet from https://github.com/GoogleChrome/custom-tabs-client/blob/master/shared/
     * src/main/java/org/chromium/customtabsclient/shared/CustomTabsHelper.java#L63
     */
    private List<String> getSupportPackagesName() {
        String ACTION_CUSTOM_TABS_CONNECTION = "android.support.customtabs.action.CustomTabsService";
        Intent activityIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://t.co"));
        PackageManager pm = getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(activityIntent, PackageManager.MATCH_ALL);
        List<String> supportPackageNames = new ArrayList<>();
        for (ResolveInfo info : activities) {
            Intent serviceIntent = new Intent();
            serviceIntent.setAction(ACTION_CUSTOM_TABS_CONNECTION);
            serviceIntent.setPackage(info.activityInfo.packageName);
            if (pm.resolveService(serviceIntent, 0) != null) {
                supportPackageNames.add(info.activityInfo.packageName);
            }
        }
        return supportPackageNames;
    }

    private void onClickLaunch() {
        CustomTabsIntent.Builder builder = createBuilder(mMode);
        CustomTabsIntent customTabsIntent = builder.build();
        Intent intent = customTabsIntent.intent;
        Uri uri = Uri.parse(mInput.getEditableText().toString());

        // similar implementation as CustomTabsIntent.launchUrl
        intent.setData(uri);

        if (mShouldHardCode && mTargetPackage != null) {
            intent.setPackage(mTargetPackage);
        }

        try {
            ActivityCompat.startActivity(this, intent, customTabsIntent.startAnimationBundle);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Specified CustomTabsActivity not found", e);
            // use normal way to launch custom-tab-activity
            CustomTabsIntent rebuiltIntent = createBuilder(mMode).build();
            rebuiltIntent.launchUrl(this, uri);
        }
    }

    private void prompt(CharSequence text) {
        Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
    }

    private enum Mode {
        NONE,
        NORMAL,
        UGLY,
        DEPRECATED
    }
}
