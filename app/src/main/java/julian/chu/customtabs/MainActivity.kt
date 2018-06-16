package julian.chu.customtabs

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.customtabs.CustomTabsClient
import android.support.customtabs.CustomTabsIntent
import android.support.customtabs.CustomTabsServiceConnection
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.*
import java.lang.Long.parseLong
import java.util.*

typealias CompBtnListener = CompoundButton.OnCheckedChangeListener

class MainActivity : AppCompatActivity() {


    private var mMenuItemNums = 3
    private var mTopBarColor = Color.WHITE
    private var mBottomBarColor = Color.WHITE
    private var mActionButtonIcon: Bitmap? = null
    private var mCloseButtonIcon: Bitmap? = null
    private lateinit var mBtn0: Button
    private lateinit var mInput: EditText

    private var mSupportPackages: List<String>? = null

    private var mShouldCustomTopColor = true
    private var mShouldCustomBottomColor = true
    private var mShouldDefShareItem = false
    private var mCustomAnimation = false
    private var mShouldShowTitle = true
    private var mShouldActionBtn = true
    private var mShouldActionBtnTint = false
    private var mShouldCloseBtn = false
    private var mShouldHardCode = false
    private var mTargetPackage: String? = null
    private var mMode = Mode.NONE

    private var mServiceClient: CustomTabsClient? = null

    private var mUserInputUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initActionBar()
        restorePreferences()

        mSupportPackages = supportPackagesName

        mActionButtonIcon = getBitmap(R.drawable.ic_ab)
        mCloseButtonIcon = getBitmap(R.drawable.ic_close)
        mBtn0 = findViewById(R.id.btn_0) as Button
        mInput = findViewById(R.id.edit_text) as EditText
        initUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        storePreferences()
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putString(KEY_USER_INPUT_URL, mUserInputUrl)
    }

    override fun onRestoreInstanceState(bundle: Bundle) {
        super.onRestoreInstanceState(bundle)
        mUserInputUrl = bundle.getString(KEY_USER_INPUT_URL, "")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_options, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_connect_service).isEnabled = mServiceClient == null
        menu.findItem(R.id.menu_warm_up).isEnabled = mServiceClient != null
        menu.findItem(R.id.menu_may_launch).isEnabled = mServiceClient != null

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_button -> {
                // sadly, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS wont be supported until API24
                val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
            R.id.menu_connect_service -> onConnectService()
            R.id.menu_warm_up -> onWarmUp()
            R.id.menu_may_launch -> onMayLaunch()
            R.id.menu_edit_url -> onEditUrlClicked()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun initUI() {
        bindButton()
        setSpinners()
        setToggleButton()

        val urlSpinner = findViewById(R.id.urls_spinner) as Spinner
        val urls = selectableUrls
        val spinnerArrayAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                urls)
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        urlSpinner.adapter = spinnerArrayAdapter

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window = window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = ContextCompat.getColor(this, R.color.colorPrimary)
        }


        refreshUI()
    }

    @Synchronized private fun onConnectService() {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        val info = packageManager
                .resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val pkgName = if (mShouldHardCode && mTargetPackage != null)
            mTargetPackage
        else
            info.activityInfo.packageName

        if (TextUtils.isEmpty(pkgName)) {
            prompt("No service to connect")
            return
        }

        val connection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
                mServiceClient = client
                prompt(pkgName + " connected")
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                mServiceClient = null
                prompt(pkgName + " disconnected")
            }
        }

        val success = CustomTabsClient.bindCustomTabsService(this, pkgName, connection)
        if (!success) {
            prompt("Connect fail")
        }
    }

    @Synchronized private fun onWarmUp() {
        if (mServiceClient == null) {
            return
        }

        val msg = if (mServiceClient!!.warmup(0L)) "War up success" else "Warm up fail"
        prompt(msg)
    }

    @Synchronized private fun onMayLaunch() {
        if (mServiceClient == null) {
            return
        }
        prompt("Not implement yet")
    }

    private fun onEditUrlClicked() {
        val inflater = layoutInflater
        val content = inflater.inflate(R.layout.layout_input_dialog, null)
        val editText = content.findViewById(R.id.edit_text) as EditText
        val builder = AlertDialog.Builder(this)

        editText.setText(mUserInputUrl)
        builder.setView(content)
        builder.setPositiveButton(android.R.string.ok) { dialogInterface, i ->
            mUserInputUrl = editText.text.toString()
            refreshUI()
        }
        builder.setNeutralButton(android.R.string.cancel) { dialogInterface, i -> }
        builder.show()
    }

    private fun initActionBar() {
        val toolbar = findViewById(R.id.action_bar) as Toolbar
        setSupportActionBar(toolbar)
    }

    private fun getBitmap(res: Int): Bitmap {
        return BitmapFactory.decodeResource(resources, res)
    }

    private fun restorePreferences() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        mUserInputUrl = preferences.getString(KEY_USER_INPUT_URL, "")
    }

    private fun storePreferences() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = preferences.edit()
        editor.putString(KEY_USER_INPUT_URL, mUserInputUrl)
        editor.commit()
    }

    private fun bindButton() {
        mBtn0.setOnClickListener { onClickLaunch() }
    }

    private fun setSpinners() {

        // a helper function to build Listener
        fun build(cb: (Int) -> Unit): AdapterView.OnItemSelectedListener {
            return object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, idx: Int, l: Long) {
                    // FIXME: how to avoid this null checking if the Activity is re-created by system
                    if (adapterView != null && view != null) {
                        cb(idx)
                        refreshUI()
                    }
                }

                override fun onNothingSelected(adapterView: AdapterView<*>) {}
            }
        }

        (findViewById(R.id.urls_spinner) as Spinner).onItemSelectedListener = build({ idx ->
            val urls = selectableUrls
            mInput.setText(urls[idx])
        })

        (findViewById(R.id.menu_item_spinner) as Spinner).onItemSelectedListener = build({ idx ->
            val nums = resources.getStringArray(R.array.menu_item_nums)
            mMenuItemNums = Integer.parseInt(nums[idx])
            mMenuItemNums = Math.min(mMenuItemNums, 5)
            mMenuItemNums = Math.max(mMenuItemNums, 0)

        })

        (findViewById(R.id.top_color_spinner) as Spinner).onItemSelectedListener = build({ idx ->
            mTopBarColor = getColorByIdx(idx)
        })

        (findViewById(R.id.bottom_color_spinner) as Spinner).onItemSelectedListener = build({ idx ->
            mBottomBarColor = getColorByIdx(idx)
        })

        // change action button icon
        (findViewById(R.id.action_button_drawable_spinner) as Spinner).onItemSelectedListener = build({ idx ->
            val texts = resources.getStringArray(R.array.selectable_drawables)
            val res = when (texts[idx]) {
                "With Background" -> R.drawable.ic_ab_background
                "System" -> android.R.drawable.ic_menu_share
                "Large" -> R.drawable.ic_ab_lg
                "Small" -> R.drawable.ic_ab_xs
                else -> R.drawable.ic_ab
            }
            mActionButtonIcon = getBitmap(res)
        })

        // change close button icon
        (findViewById(R.id.close_button_drawable_spinner) as Spinner).onItemSelectedListener = build({ idx ->
            val texts = resources.getStringArray(R.array.selectable_drawables)
            val drawableText = texts[idx]
            mCloseButtonIcon = when (drawableText) {
                "With Background" -> getBitmap(R.drawable.ic_close_background)
                "System" -> getBitmap(android.R.drawable.ic_menu_close_clear_cancel)
                "Large" -> getBitmap(R.drawable.ic_close_lg)
                "Small" -> getBitmap(R.drawable.ic_close_xs)
                else -> getBitmap(R.drawable.ic_close)
            }
        })

        (findViewById(R.id.launch_mode_spinner) as Spinner).onItemSelectedListener = build({ idx ->
            // Yes, I hard-coded, bite me!
            val map = mapOf(
                    0 to Mode.NONE,
                    1 to Mode.NORMAL,
                    2 to Mode.UGLY,
                    3 to Mode.DEPRECATED)
            mMode = map[idx] as Mode
        })

        val hardCodeSpinner = findViewById(R.id.hard_code_spinner) as Spinner
        val spinnerAdapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item,
                mSupportPackages!!)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        hardCodeSpinner.adapter = spinnerAdapter
        spinnerAdapter.notifyDataSetChanged()
        // view might be null, so we don't use build function
        hardCodeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View?, i: Int, l: Long) {
                if (mSupportPackages!!.size < 1) {
                    return
                }
                mTargetPackage = mSupportPackages!![i]
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {}
        }

    }

    private fun refreshUI() {
        findViewById(R.id.top_color_spinner).isEnabled = mShouldCustomTopColor
        findViewById(R.id.bottom_color_spinner).isEnabled = mShouldCustomBottomColor
        findViewById(R.id.def_share_item_desc).isEnabled = mShouldDefShareItem
        findViewById(R.id.exit_animation_desc).isEnabled = mCustomAnimation
        findViewById(R.id.show_title_desc).isEnabled = mShouldShowTitle
        findViewById(R.id.top_color_preview).setBackgroundColor(mTopBarColor)
        findViewById(R.id.bottom_color_preview).setBackgroundColor(mBottomBarColor)

        (findViewById(R.id.action_button_preview) as ImageView).setImageBitmap(mActionButtonIcon)
        (findViewById(R.id.close_button_preview) as ImageView).setImageBitmap(mCloseButtonIcon)

        findViewById(R.id.action_button_drawable_spinner).isEnabled = mShouldActionBtn
        findViewById(R.id.widget_action_button_tint).isEnabled = mShouldActionBtn
        findViewById(R.id.action_button_tint_desc).isEnabled = mShouldActionBtn && mShouldActionBtnTint

        findViewById(R.id.close_button_drawable_spinner).isEnabled = mShouldCloseBtn

        findViewById(R.id.hard_code_spinner).isEnabled = mShouldHardCode
    }

    private fun setToggleButton() {
        // map view-id to listener
        val map = mapOf<Int, (Boolean) -> Unit>(
                R.id.set_top_color to { b -> mShouldCustomTopColor = b },
                R.id.should_set_bottom_bar_color to { b -> mShouldCustomBottomColor = b },
                R.id.should_set_def_share_item to { b -> mShouldDefShareItem = b },
                R.id.widget_custom_animation to { b -> mCustomAnimation = b },
                R.id.widget_show_title to { b -> mShouldShowTitle = b },
                R.id.widget_action_button to { b -> mShouldActionBtn = b },
                R.id.widget_close_button to { b -> mShouldCloseBtn = b },
                R.id.widget_action_button_tint to { b -> mShouldActionBtnTint = b },
                R.id.widget_hard_code to { b -> mShouldHardCode = b }
        )

        // bind listeners to View
        for ((viewId, cb) in map) {
            (findViewById(viewId) as ToggleButton).setOnCheckedChangeListener({ _, b ->
                cb(b)
                refreshUI()
            })
        }
    }

    private val selectableUrls: Array<String>
        get() {
            val selectable = resources.getStringArray(R.array.selectable_urls)
            val urls = ArrayList<String>()
            urls.addAll(Arrays.asList(*selectable))
            if (!TextUtils.isEmpty(mUserInputUrl)) {
                urls.add(mUserInputUrl as String)
            }
            return urls.toTypedArray()
        }

    private fun getColorByIdx(idx: Int): Int {
        val arrays = resources.getStringArray(R.array.selectable_colors)
        val colorText = arrays[idx]
        when (colorText) {
            "MAGENTA" -> return Color.MAGENTA
            "CYAN" -> return Color.CYAN
            "GREEN" -> return Color.GREEN
            "RED" -> return Color.RED
            "BLACK" -> return Color.BLACK
            "WHITE" -> return Color.WHITE
        }

        // to match pattern "#FF123456"
        if (Regex("^#[A-Fa-f0-9]{8}$").matchEntire(colorText) != null) {
            val hex = colorText.substring(1) // remove '#'
            return parseLong(hex, 16).toInt()
        }
        return resources.getColor(R.color.mozillaRed)
    }

    private fun createBuilder(mode: Mode): CustomTabsIntent.Builder {
        val builder = CustomTabsIntent.Builder()

        // set default share item
        if (mShouldDefShareItem) {
            builder.addDefaultShareMenuItem()
        }

        // set action button
        if (mShouldActionBtn) {
            builder.setActionButton(mActionButtonIcon!!,
                    "The initium",
                    createIntent(REQ_INITIUM, "https://theinitium.com/"),
                    mShouldActionBtnTint)
        }

        // set close button
        if (mShouldCloseBtn) {
            builder.setCloseButtonIcon(mCloseButtonIcon!!)
        }

        if (mShouldCustomTopColor) {
            builder.setToolbarColor(mTopBarColor)
        }

        builder.setShowTitle(mShouldShowTitle)

        // set menu items
        setMenuItems(builder)

        // set animation
        if (mCustomAnimation) {
            builder.setStartAnimations(this, R.anim.push_down_in, R.anim.push_down_out)
            builder.setExitAnimations(this, R.anim.push_up_in, R.anim.push_up_out)
        }

        if (mode != Mode.NONE) {
            setBottomToolbar(builder, mode)
        }

        return builder
    }

    private fun setMenuItems(builder: CustomTabsIntent.Builder) {
        for (i in 0..mMenuItemNums - 1) {
            builder.addMenuItem("Search " + (i + 1),
                    createIntent(0x42 + i, "https://duckduckgo.com/?q=" + i))
        }
    }

    private fun createIntent(reqCode: Int, uri: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(uri)
        val pending = PendingIntent.getActivities(this,
                reqCode,
                arrayOf(intent),
                PendingIntent.FLAG_CANCEL_CURRENT)

        return pending
    }

    private fun setBottomToolbar(builder: CustomTabsIntent.Builder, mode: Mode) {
        // add toolbar items
        if (mShouldCustomBottomColor) {
            builder.setSecondaryToolbarColor(mBottomBarColor)
        }
        if (mode == Mode.DEPRECATED) {
            deprecatedAddingToolbarItem(builder)
        } else {
            setSecondaryToolbar(builder, mode)
        }
    }

    private fun setSecondaryToolbar(builder: CustomTabsIntent.Builder, mode: Mode) {
        val layoutRes = if (mode == Mode.NORMAL)
            R.layout.layout_remote_views
        else
            R.layout.layout_ugly_remote_views

        val rvs = RemoteViews(packageName, layoutRes)
        builder.setSecondaryToolbarViews(rvs,
                intArrayOf(R.id.remote_btn_1, R.id.remote_btn_2),
                createIntent(REQ_MOZILLA, "https://www.mozilla.org"))
    }

    private fun deprecatedAddingToolbarItem(builder: CustomTabsIntent.Builder) {
        // up to 5 items, otherwise got Exception
        //   java.lang.IllegalStateException: Exceeded maximum toolbar item count of 5
        builder.addToolbarItem(1, getBitmap(R.drawable.ic_a), "item a", createIntent(1, "https://duckduckgo.com/?q=a"))
        builder.addToolbarItem(2, getBitmap(R.drawable.ic_b), "item b", createIntent(2, "https://duckduckgo.com/?q=b"))
        builder.addToolbarItem(3, getBitmap(R.drawable.ic_c), "item c", createIntent(3, "https://duckduckgo.com/?q=c"))
        builder.addToolbarItem(4, getBitmap(R.drawable.ic_d), "item d", createIntent(4, "https://duckduckgo.com/?q=d"))
        builder.addToolbarItem(5, getBitmap(R.drawable.ic_e), "item e", createIntent(5, "https://duckduckgo.com/?q=e"))
    }

    /**
     * snippet from https://github.com/GoogleChrome/custom-tabs-client/blob/master/shared/
     * src/main/java/org/chromium/customtabsclient/shared/CustomTabsHelper.java#L63
     */
    private val supportPackagesName: List<String>
        get() {
            val ACTION_CUSTOM_TABS_CONNECTION = "android.support.customtabs.action.CustomTabsService"
            val activityIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://t.co"))
            val pm = packageManager
            val activities = pm.queryIntentActivities(activityIntent, PackageManager.MATCH_ALL)
            val supportPackageNames = ArrayList<String>()
            for (info in activities) {
                val serviceIntent = Intent()
                serviceIntent.action = ACTION_CUSTOM_TABS_CONNECTION
                serviceIntent.`package` = info.activityInfo.packageName
                if (pm.resolveService(serviceIntent, 0) != null) {
                    supportPackageNames.add(info.activityInfo.packageName)
                }
            }
            return supportPackageNames
        }

    private fun onClickLaunch() {
        val builder = createBuilder(mMode)
        val customTabsIntent = builder.build()
        val intent = customTabsIntent.intent
        val uri = Uri.parse(mInput.editableText.toString())

        // similar implementation as CustomTabsIntent.launchUrl
        intent.data = uri

        if (mShouldHardCode && mTargetPackage != null) {
            intent.`package` = mTargetPackage
        }

        try {
            ActivityCompat.startActivity(this, intent, customTabsIntent.startAnimationBundle)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Specified CustomTabsActivity not found", e)
            // use normal way to launch custom-tab-activity
            val rebuiltIntent = createBuilder(mMode).build()
            rebuiltIntent.launchUrl(this, uri)
        }

    }

    private fun prompt(text: CharSequence) {
        Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
    }

    private enum class Mode {
        NONE,
        NORMAL,
        UGLY,
        DEPRECATED
    }

    companion object {

        private val TAG = "TabLauncher"
        private val REQ_INITIUM = 0x1122
        private val REQ_MOZILLA = 0x011A

        private val KEY_USER_INPUT_URL = "_the_url_input_by_user_"
    }
}
