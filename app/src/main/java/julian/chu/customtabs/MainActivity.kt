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
import java.util.*

class MainActivity : AppCompatActivity() {

    private var mMenuItemNums = 3
    private var mTopBarColor = Color.WHITE
    private var mBottomBarColor = Color.WHITE
    private var mActionButtonIcon: Bitmap? = null
    private var mCloseButtonIcon: Bitmap? = null
    private var mBtn0: Button? = null
    private var mInput: EditText? = null

    private var mSupportPackages: List<String>? = null

    private val mShouldCustomTopColor = true
    private val mShouldCustomBottomColor = true
    private val mShouldDefShareItem = false
    private val mCustomAnimation = false
    private val mShouldShowTitle = true
    private val mShouldActionBtn = true
    private val mShouldActionBtnTint = false
    private val mShouldCloseBtn = false
    private val mShouldHardCode = false
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
        bindButton()
        setSpinners()
        setToggleButton()
        refreshUI()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window = window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = ContextCompat.getColor(this, R.color.colorPrimary)
        }
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

        val msg = if (mServiceClient!!.warmup(0L))
            "War up success"
        else
            "Warm up fail"
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
        mBtn0!!.setOnClickListener { onClickLaunch() }
    }

    private fun setSpinners() {
        (findViewById(R.id.urls_spinner) as Spinner).onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                val urls = selectableUrls
                mInput!!.setText(urls[i])
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {}
        }

        (findViewById(R.id.menu_item_spinner) as Spinner).onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                val nums = resources.getStringArray(R.array.menu_item_nums)
                mMenuItemNums = Integer.parseInt(nums[i])
                mMenuItemNums = Math.min(mMenuItemNums, 5)
                mMenuItemNums = Math.max(mMenuItemNums, 0)
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {}
        }

        (findViewById(R.id.top_color_spinner) as Spinner).onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                mTopBarColor = getColorByIdx(i)
                refreshUI()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {}
        }

        (findViewById(R.id.bottom_color_spinner) as Spinner).onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                mBottomBarColor = getColorByIdx(i)
                refreshUI()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {}
        }

        // change action button icon
        (findViewById(R.id.action_button_drawable_spinner) as Spinner).onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                val texts = resources.getStringArray(R.array.selectable_drawables)
                val drawableText = texts[i]
                if ("With Background" == drawableText) {
                    mActionButtonIcon = getBitmap(R.drawable.ic_ab_background)
                } else if ("System" == drawableText) {
                    mActionButtonIcon = getBitmap(android.R.drawable.ic_menu_share)
                } else if ("Large" == drawableText) {
                    mActionButtonIcon = getBitmap(R.drawable.ic_ab_lg)
                } else if ("Small" == drawableText) {
                    mActionButtonIcon = getBitmap(R.drawable.ic_ab_xs)
                } else {
                    // default
                    mActionButtonIcon = getBitmap(R.drawable.ic_ab)
                }

                refreshUI()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {}
        }

        // change close button icon
        (findViewById(R.id.close_button_drawable_spinner) as Spinner).onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                val texts = resources.getStringArray(R.array.selectable_drawables)
                val drawableText = texts[i]
                if ("With Background" == drawableText) {
                    mCloseButtonIcon = getBitmap(R.drawable.ic_close_background)
                } else if ("System" == drawableText) {
                    mCloseButtonIcon = getBitmap(android.R.drawable.ic_menu_close_clear_cancel)
                } else if ("Large" == drawableText) {
                    mCloseButtonIcon = getBitmap(R.drawable.ic_close_lg)
                } else if ("Small" == drawableText) {
                    mCloseButtonIcon = getBitmap(R.drawable.ic_close_xs)
                } else {
                    // default
                    mCloseButtonIcon = getBitmap(R.drawable.ic_close)
                }

                refreshUI()
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {}
        }

        (findViewById(R.id.launch_mode_spinner) as Spinner).onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                // Yes, I hard-coded, bite me!
                val map = HashMap<Int, Mode>()
                map.put(0, Mode.NONE)
                map.put(1, Mode.NORMAL)
                map.put(2, Mode.UGLY)
                map.put(3, Mode.DEPRECATED)
                mMode = map[i]
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {}
        }

        val hardCodeSpinner = findViewById(R.id.hard_code_spinner) as Spinner
        val spinnerAdapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item,
                mSupportPackages!!)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        hardCodeSpinner.adapter = spinnerAdapter
        spinnerAdapter.notifyDataSetChanged()
        hardCodeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
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

        val urlSpinner = findViewById(R.id.urls_spinner) as Spinner
        val urls = selectableUrls
        val spinnerArrayAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                urls)
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        urlSpinner.adapter = spinnerArrayAdapter
    }

    private fun setToggleButton() {
        (findViewById(R.id.set_top_color) as ToggleButton).setOnCheckedChangeListener(
                buildCheckHandler("mShouldCustomTopColor"))

        (findViewById(R.id.should_set_bottom_bar_color) as ToggleButton).setOnCheckedChangeListener(
                buildCheckHandler("mShouldCustomBottomColor"))

        (findViewById(R.id.should_set_def_share_item) as ToggleButton).setOnCheckedChangeListener(
                buildCheckHandler("mShouldDefShareItem"))

        (findViewById(R.id.widget_custom_animation) as ToggleButton).setOnCheckedChangeListener(
                buildCheckHandler("mCustomAnimation"))

        (findViewById(R.id.widget_show_title) as ToggleButton).setOnCheckedChangeListener(
                buildCheckHandler("mShouldShowTitle"))

        (findViewById(R.id.widget_action_button) as ToggleButton).setOnCheckedChangeListener(
                buildCheckHandler("mShouldActionBtn"))

        (findViewById(R.id.widget_close_button) as ToggleButton).setOnCheckedChangeListener(
                buildCheckHandler("mShouldCloseBtn"))

        (findViewById(R.id.widget_action_button_tint) as ToggleButton).setOnCheckedChangeListener(
                buildCheckHandler("mShouldActionBtnTint"))

        (findViewById(R.id.widget_hard_code) as ToggleButton).setOnCheckedChangeListener(
                buildCheckHandler("mShouldHardCode"))
    }

    private val selectableUrls: Array<String>
        get() {
            val selectable = resources.getStringArray(R.array.selectable_urls)
            val urls = ArrayList<String>()
            urls.addAll(Arrays.asList(*selectable))
            if (!TextUtils.isEmpty(mUserInputUrl)) {
                urls.add(mUserInputUrl)
            }
            return urls.toTypedArray()
        }

    /**
     * Use reflect to build dynamic function to handle Check-button status change

     * @param fieldName which field to change boolean value
     * *
     * @return a handler for CompoundButton.setOnCheckedChangeListener
     */
    private fun buildCheckHandler(fieldName: String): CompoundButton.OnCheckedChangeListener {
        return CompoundButton.OnCheckedChangeListener { compoundButton, b ->
            val clazz = this@MainActivity.javaClass
            try {
                val f = clazz.getDeclaredField(fieldName)
                f.isAccessible = true
                f.setBoolean(this@MainActivity, b)
                refreshUI()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity,
                        "Fail on set " + fieldName,
                        Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    // Yes, Dirty! Bite me!
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
            "#FF8877FF" -> return 0xFF8877FF.toInt()
            "#00FFFFFF" -> return 0x00FFFFFF
            "#FFFFFFF1" -> return 0xFFFFFFF1.toInt()
            "#FFFFFFFF" -> return 0xFFFFFFFF.toInt()
            "#11FF0000" -> return 0x11FF0000
            "#55FF0000" -> return 0x55FF0000
            "#AAFF0000" -> return 0xAAFF0000.toInt()
            "#EEFF0000" -> return 0xEEFF0000.toInt()
            "#1100FF00" -> return 0x1100FF00
            "#5500FF00" -> return 0x5500FF00
            "#AA00FF00" -> return 0xAA00FF00.toInt()
            "#EE00FF00" -> return 0xEE00FF00.toInt()
            else -> return resources.getColor(R.color.mozillaRed)
        }
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
        val uri = Uri.parse(mInput!!.editableText.toString())

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
