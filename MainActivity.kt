package com.example.furniturehisab

import android.app.*
import android.content.*
import android.database.sqlite.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.*
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class Furniture(val id: Long, val model: String, val size: String, val amount: Double, val dateTime: String, val photo: String, val createdMs: Long)
data class Payment(val id: Long, val amount: Double, val note: String, val dateTime: String, val createdMs: Long)

class Db(context: Context) : SQLiteOpenHelper(context, "furniture.db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT, model TEXT, size TEXT, amount REAL, dateTime TEXT, photo TEXT, createdMs INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE payments(id INTEGER PRIMARY KEY AUTOINCREMENT, amount REAL, note TEXT, dateTime TEXT, createdMs INTEGER DEFAULT 0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("CREATE TABLE IF NOT EXISTS payments(id INTEGER PRIMARY KEY AUTOINCREMENT, amount REAL, note TEXT, dateTime TEXT, createdMs INTEGER DEFAULT 0)")
        try { db.execSQL("ALTER TABLE items ADD COLUMN createdMs INTEGER DEFAULT 0") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE payments ADD COLUMN createdMs INTEGER DEFAULT 0") } catch (_: Exception) {}
    }
    fun all() = mutableListOf<Furniture>().apply {
        readableDatabase.rawQuery("SELECT id, model, size, amount, dateTime, photo, createdMs FROM items ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) add(Furniture(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getDouble(3), c.getString(4) ?: "", c.getString(5) ?: "", c.getLong(6)))
        }
    }
    fun save(id: Long?, model: String, size: String, amount: Double, date: String, photo: String, ms: Long) {
        val v = ContentValues().apply { put("model", model); put("size", size); put("amount", amount); put("dateTime", date); put("photo", photo); put("createdMs", ms) }
        if (id == null) writableDatabase.insert("items", null, v) else writableDatabase.update("items", v, "id=?", arrayOf(id.toString()))
    }
    fun delete(id: Long) { writableDatabase.delete("items", "id=?", arrayOf(id.toString())) }
    fun allPayments() = mutableListOf<Payment>().apply {
        readableDatabase.rawQuery("SELECT id, amount, note, dateTime, createdMs FROM payments ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) add(Payment(c.getLong(0), c.getDouble(1), c.getString(2) ?: "", c.getString(3) ?: "", c.getLong(4)))
        }
    }
    fun savePayment(id: Long?, amount: Double, note: String, date: String, ms: Long) {
        val v = ContentValues().apply { put("amount", amount); put("note", note); put("dateTime", date); put("createdMs", ms) }
        if (id == null) writableDatabase.insert("payments", null, v) else writableDatabase.update("payments", v, "id=?", arrayOf(id.toString()))
    }
    fun deletePayment(id: Long) { writableDatabase.delete("payments", "id=?", arrayOf(id.toString())) }
    fun clearAll() { writableDatabase.delete("items", null, null); writableDatabase.delete("payments", null, null) }
}

class MainActivity : ComponentActivity() {
    private lateinit var db: Db
    private lateinit var root: FrameLayout
    private var chosenPhoto = ""
    private var chosenDp = ""
    private var previewImg: ImageView? = null
    private var dpImg: ImageView? = null
    private var photoStatus: TextView? = null
    private var searchText = ""
    private var currentTab = 0
    private var filterByDate = false
    private var fromMs = 0L
    private var toMs = 0L

    private val greenDark = 0xFF0B4628.toInt()
    private val greenBtn = 0xFF0D6832.toInt()
    private val greenLight = 0xFFE6F4EC.toInt()
    private val goldColor = 0xFFFFD43B.toInt()
    private val bgColor = 0xFFF2F6F4.toInt()
    private val textDark = 0xFF14201A.toInt()
    private val textMuted = 0xFF5F6F67.toInt()
    private val blockedUrl = "https://raw.githubusercontent.com/za3728039-dot/-Furniturehisab/main/blocked.txt"

    private fun prefs() = getSharedPreferences("fh_prefs", Context.MODE_PRIVATE)
    private fun isLockEnabled() = prefs().getBoolean("screen_lock", true)

    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) afterUnlock() else showLockedScreen()
    }
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val dest = File(filesDir, "photo_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            chosenPhoto = dest.absolutePath; BitmapFactory.decodeFile(chosenPhoto)?.let { previewImg?.setImageBitmap(it) }; photoStatus?.text = "📷 Change Photo ✓"
        }
    }
    private val takeCameraPhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            val dest = File(filesDir, "photo_${System.currentTimeMillis()}.jpg")
            dest.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            chosenPhoto = dest.absolutePath; previewImg?.setImageBitmap(bmp); photoStatus?.text = "📷 Change Photo ✓"
        }
    }
    private val pickDp = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val dest = File(filesDir, "dp_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            chosenDp = dest.absolutePath; BitmapFactory.decodeFile(chosenDp)?.let { dpImg?.setImageBitmap(it) }
        }
    }
    private val takeDpCamera = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            val dest = File(filesDir, "dp_${System.currentTimeMillis()}.jpg")
            dest.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            chosenDp = dest.absolutePath; dpImg?.setImageBitmap(bmp)
        }
    }
    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(makeBackup().toString(2)) }; toast("Backup Saved ✓") } catch (_: Exception) { toast("Backup failed") }
    }
    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            restoreBackup(JSONObject(text)); openTab(0); toast("Backup Restored ✓")
        } catch (_: Exception) { toast("Invalid backup file") }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = greenDark; window.navigationBarColor = 0xFFFFFFFF.toInt()
        db = Db(this)
        val c = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        fromMs = c.timeInMillis
        toMs = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
        root = FrameLayout(this).apply {
            setBackgroundColor(greenDark)
            setOnApplyWindowInsetsListener { v, insets -> v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom); insets }
        }
        setContentView(root); showSplash()
    }

    private fun showSplash() {
        root.removeAllViews()
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(24), dp(24), dp(24))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF146C43.toInt(), 0xFF062C1A.toInt()))
        }
        splash.addView(TextView(this).apply { text = "🏠"; textSize = 72f; gravity = Gravity.CENTER })
        splash.addView(TextView(this).apply { text = "ZFH"; textSize = 36f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER })
        splash.addView(TextView(this).apply { text = "Ledger"; textSize = 36f; setTypeface(null, Typeface.BOLD); setTextColor(goldColor); gravity = Gravity.CENTER }, lp(-1, -2, dp(12)))
        splash.addView(TextView(this).apply { text = "Hisab Rakho,\nTension Chhodo..."; textSize = 18f; setTypeface(null, Typeface.ITALIC); setTextColor(0xFFE8F5E9.toInt()); gravity = Gravity.CENTER }, lp(-1, -2, dp(28)))
        splash.addView(ProgressBar(this).apply { isIndeterminate = true })
        root.addView(splash)
        Handler(Looper.getMainLooper()).postDelayed({ checkAuthAndOpen() }, 900)
    }

    @Suppress("DEPRECATION")
    private fun checkAuthAndOpen() {
        if (!isLockEnabled()) { afterUnlock(); return }
        try {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (km.isKeyguardSecure) {
                km.createConfirmDeviceCredentialIntent("ZFH Ledger Locked", "Fingerprint, Face Lock ya Screen Lock se unlock karein")?.let {
                    showLockedScreen(); authLauncher.launch(it); return
                }
            }
        } catch (_: Throwable) {}
        afterUnlock()
    }

    private fun afterUnlock() {
        if (!prefs().getBoolean("reg_done", false)) showRegister(false) else checkNotBlocked()
    }

    private fun checkNotBlocked() {
        val myPhone = (prefs().getString("reg_phone", "") ?: "").filter { it.isDigit() }.takeLast(10)
        if (myPhone.length < 10) { showRegister(false); return }
        Thread {
            var isBlocked: Boolean? = null
            try {
                val conn = (URL("$blockedUrl?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000; readTimeout = 4000
                }
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    isBlocked = text.lines().any { line -> line.filter { it.isDigit() }.takeLast(10) == myPhone }
                } else if (conn.responseCode == 404) {
                    isBlocked = false
                }
            } catch (_: Exception) {}
            runOnUiThread {
                when (isBlocked) {
                    true -> { prefs().edit().putBoolean("is_blocked", true).apply(); showBlockedScreen(myPhone) }
                    false -> { prefs().edit().putBoolean("is_blocked", false).apply(); openTab(0) }
                    null -> if (prefs().getBoolean("is_blocked", false)) showBlockedScreen(myPhone) else openTab(0)
                }
            }
        }.start()
    }

    private fun showBlockedScreen(phone: String) {
        root.removeAllViews()
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(28))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF7B1F1F.toInt(), 0xFF3A0C0C.toInt()))
        }
        p.addView(TextView(this).apply { text = "⛔"; textSize = 64f; gravity = Gravity.CENTER }, lp(-1, -2, dp(12)))
        p.addView(TextView(this).apply { text = "Account Blocked"; textSize = 24f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER }, lp(-1, -2, dp(8)))
        p.addView(TextView(this).apply {
            text = "Number ($phone) ko Admin (Zubair) ne block kar diya hai."; textSize = 15f; setTextColor(0xFFFFCDD2.toInt()); gravity = Gravity.CENTER
        }, lp(-1, -2, dp(24)))
        p.addView(TextView(this).apply {
            text = "🔄  Recheck Status"; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF7B1F1F.toInt()); gravity = Gravity.CENTER
            background = roundBg(0xFFFFFFFF.toInt(), 26); setPadding(dp(24), dp(12), dp(24), dp(12)); setOnClickListener { checkNotBlocked() }
        })
        root.addView(p)
    }

    private fun showLockedScreen() {
        root.removeAllViews()
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(28))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF146C43.toInt(), 0xFF062C1A.toInt()))
        }
        p.addView(TextView(this).apply { text = "🔒"; textSize = 64f; gravity = Gravity.CENTER }, lp(-1, -2, dp(14)))
        p.addView(TextView(this).apply { text = "ZFH Ledger Locked"; textSize = 24f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER }, lp(-1, -2, dp(24)))
        p.addView(TextView(this).apply {
            text = "🔓  Unlock App"; textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(greenDark); gravity = Gravity.CENTER
            background = roundBg(0xFFFFFFFF.toInt(), 26); setPadding(dp(28), dp(14), dp(28), dp(14)); setOnClickListener { checkAuthAndOpen() }
        })
        root.addView(p)
    }

    private fun showRegister(isEdit: Boolean) {
        chosenDp = prefs().getString("reg_dp", "") ?: ""
        root.removeAllViews()
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgColor) }
        page.addView(topBar(if (isEdit) "Edit Profile" else "Registration (रजिस्ट्रेशन)"))
        val scroll = ScrollView(this); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(20), dp(24), dp(20), dp(24)) }
        val avatar = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; background = roundBg(greenLight, 55, greenBtn); clipToOutline = true
            val bmp = if (chosenDp.isNotBlank() && File(chosenDp).exists()) BitmapFactory.decodeFile(chosenDp) else null
            if (bmp != null) setImageBitmap(bmp) else setImageResource(android.R.drawable.ic_menu_myplaces)
        }
        dpImg = avatar; box.addView(avatar, LinearLayout.LayoutParams(dp(110), dp(110)).apply { bottomMargin = dp(10) })
        box.addView(TextView(this).apply {
            text = "📷 DP लगाएं / बदलें"; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt())
            background = roundBg(greenBtn, 20); setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Select Profile DP")
                    .setItems(arrayOf("📷 Camera", "🖼️ Gallery")) { _, w -> if (w == 0) takeDpCamera.launch(null) else pickDp.launch("image/*") }.show()
            }
        }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(20) })

        fun rInp(label: String, valStr: String, phone: Boolean = false): EditText {
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = roundBg(0xFFFFFFFF.toInt(), 12, 0xFFD5E2DB.toInt()); setPadding(dp(14), dp(10), dp(14), dp(10)) }
            c.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(greenDark); setTypeface(null, Typeface.BOLD) })
            val e = EditText(this).apply { setText(valStr); setBackgroundColor(0); setPadding(0, dp(2), 0, 0); textSize = 16f; if (phone) inputType = InputType.TYPE_CLASS_PHONE }
            c.addView(e); box.addView(c, lp(-1, -2, dp(12))); return e
        }
        val nameE = rInp("Full Name (अपना नाम)", prefs().getString("reg_name", "") ?: "")
        val phoneE = rInp("Mobile Number (मोबाइल नंबर)", prefs().getString("reg_phone", "") ?: "", true)
        val shopE = rInp("Shop / Business Name (Optional)", prefs().getString("reg_shop", "") ?: "")

        box.addView(TextView(this).apply {
            text = if (isEdit) "✓  Save Profile" else "✓  Register & Start"; textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            background = roundBg(greenBtn, 26); setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener {
                val nm = nameE.text.toString().trim(); val ph = phoneE.text.toString().trim().filter { it.isDigit() }.takeLast(10)
                if (nm.isBlank() || ph.length < 10) toast("Sahi Naam aur 10-digit Mobile Number bhariye")
                else {
                    prefs().edit().putBoolean("reg_done", true).putString("reg_name", nm).putString("reg_phone", ph)
                        .putString("reg_shop", shopE.text.toString().trim()).putString("reg_dp", chosenDp).apply()
                    checkNotBlocked()
                }
            }
        }, lp(-1, -2, dp(10)))
        if (isEdit) box.addView(TextView(this).apply { text = "Cancel"; textSize = 15f; setTextColor(textMuted); setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { openTab(3) } })
        scroll.addView(box); page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(page)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun roundBg(color: Int, r: Int, stroke: Int = 0) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(1), stroke) }

    private fun topBar(title: String, rightText: String = "", onRight: (() -> Unit)? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(greenDark); setPadding(dp(16), dp(14), dp(16), dp(14))
        addView(TextView(context).apply { text = title; textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
        if (rightText.isNotEmpty() && onRight != null) addView(TextView(context).apply { text = rightText; textSize = 20f; setTextColor(0xFFFFFFFF.toInt()); setPadding(dp(12), dp(4), dp(4), dp(4)); setOnClickListener { onRight() } })
    }

    private fun bottomBar(): LinearLayout {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xFFFFFFFF.toInt()); elevation = dp(8).toFloat(); setPadding(0, dp(10), 0, dp(10)) }
        fun tab(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
            val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setOnClickListener { onClick() } }
            item.addView(TextView(this).apply { text = icon; textSize = 20f; gravity = Gravity.CENTER })
            item.addView(TextView(this).apply { text = label; textSize = 11f; gravity = Gravity.CENTER; setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL); setTextColor(if (active) greenBtn else textMuted) })
            bar.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        tab("🏠", "Home", currentTab == 0) { openTab(0) }
        tab("🔍", "Search", currentTab == 1) { openTab(1) }
        val plus = TextView(this).apply { text = "+"; textSize = 22f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER; background = roundBg(greenBtn, 16); setPadding(dp(14), dp(4), dp(14), dp(4)); setOnClickListener { showForm(null) } }
        bar.addView(FrameLayout(this).apply { addView(plus, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)) }, LinearLayout.LayoutParams(0, -2, 1f))
        tab("💰", "Hisab", currentTab == 2) { openTab(2) }
        tab("⚙️", "Profile", currentTab == 3) { openTab(3) }
        return bar
    }

    private fun openTab(tab: Int) { currentTab = tab; when (tab) { 0 -> showHome(); 1 -> showSearchTab(); 2 -> showHisabDashboard(); 3 -> showSettings() } }
    private fun wrapWithBottomBar(page: LinearLayout) {
        page.addView(View(this).apply { setBackgroundColor(0xFFDCE7E1.toInt()) }, LinearLayout.LayoutParams(-1, dp(1)))
        page.addView(bottomBar()); root.removeAllViews(); root.addView(page)
    }

    private fun startNewHisab() {
        AlertDialog.Builder(this).setTitle("नया हिसाब चालू करें?").setMessage("क्या आप सारे फर्नीचर और पेमेंट रिकॉर्ड साफ करके ₹0 से नया हिसाब शुरू करना चाहते हैं?")
            .setNeutralButton("पहले Backup सेव करें") { _, _ -> exportBackup.launch("ZFH_Ledger_Backup.json") }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("हाँ, नया हिसाब चालू करें") { _, _ -> db.clearAll(); openTab(currentTab); toast("नया हिसाब शुरू हो गया ✓") }.show()
    }

    private fun showHome() {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgColor) }
        page.addView(topBar("ZFH Ledger", "☁", { showBackup() }))
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        val records = db.all(); val total = records.sumOf { it.amount }
        val sc = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = roundBg(0xFFFFFFFF.toInt(), 18, 0xFFDCE7E1.toInt()); elevation = dp(4).toFloat(); setPadding(dp(18), dp(18), dp(18), dp(18)) }
        val sl = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sl.addView(TextView(this).apply { text = "Total Kaam (All Entries)"; textSize = 14f; setTextColor(textMuted) })
        sl.addView(TextView(this).apply { text = "₹ ${money(total)}"; textSize = 27f; setTypeface(null, Typeface.BOLD); setTextColor(textDark); setPadding(0, dp(4), 0, dp(6)) })
        sl.addView(TextView(this).apply { text = "📊 Total Furniture: ${records.size}"; textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(greenDark) })
        sc.addView(sl, LinearLayout.LayoutParams(0, -2, 1f))
        sc.addView(TextView(this).apply { text = "📈"; textSize = 38f; gravity = Gravity.CENTER; background = roundBg(greenLight, 14); setPadding(dp(14), dp(10), dp(14), dp(10)) })
        body.addView(sc, lp(-1, -2, dp(14)))
        body.addView(TextView(this).apply {
            text = "+  Add New Furniture"; textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            background = roundBg(greenBtn, 26); elevation = dp(3).toFloat(); setPadding(dp(16), dp(14), dp(16), dp(14)); setOnClickListener { showForm(null) }
        }, lp(-1, -2, dp(18)))
        body.addView(TextView(this).apply { text = "Furniture Photo Series (${records.size})"; textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(textDark) }, lp(-1, -2, dp(10)))
        if (records.isEmpty()) body.addView(TextView(this).apply { text = "Abhi koi entry nahi hai.\n'+ Add New Furniture' par tap karein."; gravity = Gravity.CENTER; textSize = 15f; setTextColor(textMuted); setPadding(dp(16), dp(30), dp(16), dp(30)); background = roundBg(0xFFFFFFFF.toInt(), 14) })
        else records.forEachIndexed { idx, item -> body.addView(recordCard(item, records.size - idx), lp(-1, -2, dp(10))) }
        scroll.addView(body); page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); wrapWithBottomBar(page)
    }

    private fun showSearchTab() {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgColor) }
        page.addView(topBar("Search Furniture"))
        val sb = EditText(this).apply { hint = "🔍 Search Model, Size, Date or Amount..."; setText(searchText); background = roundBg(0xFFFFFFFF.toInt(), 12, 0xFFD0DDD6.toInt()); setPadding(dp(14), dp(12), dp(14), dp(12)) }
        page.addView(sb, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(14), dp(12), dp(14), dp(6)) })
        val scroll = ScrollView(this); val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(20)) }
        fun render(q: String) {
            list.removeAllViews()
            val m = db.all().filter { q.isBlank() || it.model.contains(q, true) || it.size.contains(q, true) || it.amount.toString().contains(q) || it.dateTime.contains(q, true) }
            m.forEachIndexed { i, it -> list.addView(recordCard(it, m.size - i), lp(-1, -2, dp(10))) }
        }
        render(searchText)
        sb.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { searchText = s.toString(); render(searchText) }
            override fun afterTextChanged(s: Editable?) {}
        })
        scroll.addView(list); page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); wrapWithBottomBar(page)
    }

    private fun showHisabDashboard() {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgColor) }
        page.addView(topBar("हिसाब (Hisab Dashboard)", "🔄", { startNewHisab() }))
        val scroll = ScrollView(this); val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(24)) }
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val mr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        mr.addView(TextView(this).apply {
            text = "All Time (कुल)"; gravity = Gravity.CENTER; textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(if (!filterByDate) 0xFFFFFFFF.toInt() else greenDark)
            background = roundBg(if (!filterByDate) greenBtn else 0xFFFFFFFF.toInt(), 20, greenBtn); setPadding(dp(10), dp(9), dp(10), dp(9)); setOnClickListener { filterByDate = false; showHisabDashboard() }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        mr.addView(TextView(this).apply {
            text = "📅 तारीख से तारीख (1–30)"; gravity = Gravity.CENTER; textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(if (filterByDate) 0xFFFFFFFF.toInt() else greenDark)
            background = roundBg(if (filterByDate) greenBtn else 0xFFFFFFFF.toInt(), 20, greenBtn); setPadding(dp(10), dp(9), dp(10), dp(9)); setOnClickListener { filterByDate = true; showHisabDashboard() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(mr, lp(-1, -2, dp(10)))
        if (filterByDate) {
            val dr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun pick(isFrom: Boolean) {
                val cal = Calendar.getInstance().apply { timeInMillis = if (isFrom) fromMs else toMs }
                DatePickerDialog(this, { _, y, m, d ->
                    val c = Calendar.getInstance().apply { set(y, m, d, if (isFrom) 0 else 23, if (isFrom) 0 else 59, if (isFrom) 0 else 59) }
                    if (isFrom) fromMs = c.timeInMillis else toMs = c.timeInMillis; showHisabDashboard()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
            dr.addView(TextView(this).apply { text = "From: ${fmt.format(Date(fromMs))}"; gravity = Gravity.CENTER; textSize = 13f; setTextColor(textDark); background = roundBg(0xFFFFFFFF.toInt(), 12, 0xFFD0DDD6.toInt()); setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { pick(true) } }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
            dr.addView(TextView(this).apply { text = "To: ${fmt.format(Date(toMs))}"; gravity = Gravity.CENTER; textSize = 13f; setTextColor(textDark); background = roundBg(0xFFFFFFFF.toInt(), 12, 0xFFD0DDD6.toInt()); setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { pick(false) } }, LinearLayout.LayoutParams(0, -2, 1f))
            body.addView(dr, lp(-1, -2, dp(12)))
        }
        val items = db.all().let { if (!filterByDate) it else it.filter { x -> x.createdMs == 0L || x.createdMs in fromMs..toMs } }
        val pays = db.allPayments().let { if (!filterByDate) it else it.filter { x -> x.createdMs == 0L || x.createdMs in fromMs..toMs } }
        val bill = items.sumOf { it.amount }; val rec = pays.sumOf { it.amount }; val bal = bill - rec
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = roundBg(0xFFFFFFFF.toInt(), 18, 0xFFDCE7E1.toInt()); elevation = dp(4).toFloat(); setPadding(dp(18), dp(16), dp(18), dp(16)) }
        fun sRow(l: String, v: String, vc: Int, big: Boolean = false) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(TextView(this).apply { text = l; textSize = if (big) 16f else 14f; setTextColor(textMuted) }, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(TextView(this).apply { text = v; textSize = if (big) 22f else 17f; setTypeface(null, Typeface.BOLD); setTextColor(vc) })
            card.addView(r, lp(-1, -2, dp(8)))
        }
        sRow("Kaam (Items Done)", "${items.size} Items", textDark)
        sRow("Total Kaam (Bill)", "₹ ${money(bill)}", textDark)
        sRow("Seth ne Diya (Mila)", "₹ ${money(rec)}", greenBtn)
        card.addView(View(this).apply { setBackgroundColor(0xFFE0EAE4.toInt()) }, lp(-1, dp(1), dp(10)))
        sRow("Baki Lena Hai (Balance)", "₹ ${money(bal)}", if (bal > 0) 0xFFB71C1C.toInt() else greenDark, true)
        card.addView(TextView(this).apply { text = "🧾 Total Transactions: ${pays.size}"; textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(greenDark); background = roundBg(greenLight, 10); setPadding(dp(12), dp(8), dp(12), dp(8)) })
        body.addView(card, lp(-1, -2, dp(14)))
        body.addView(TextView(this).apply { text = "+  सेठ से पैसा मिला (Add Payment)"; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER; background = roundBg(greenBtn, 26); elevation = dp(3).toFloat(); setPadding(dp(16), dp(14), dp(16), dp(14)); setOnClickListener { showPaymentDialog(null) } }, lp(-1, -2, dp(16)))
        body.addView(TextView(this).apply { text = "Transaction History (${pays.size})"; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(textDark) }, lp(-1, -2, dp(10)))
        if (pays.isEmpty()) body.addView(TextView(this).apply { text = "Is period me koi payment transaction nahi hai."; gravity = Gravity.CENTER; textSize = 15f; setTextColor(textMuted); setPadding(dp(16), dp(26), dp(16), dp(26)); background = roundBg(0xFFFFFFFF.toInt(), 14) })
        else pays.forEachIndexed { idx, p ->
            val tc = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = roundBg(0xFFFFFFFF.toInt(), 14, 0xFFE2EBE6.toInt()); elevation = dp(2).toFloat(); setPadding(dp(14), dp(12), dp(10), dp(12)) }
            tc.addView(TextView(this).apply { text = "💵"; textSize = 24f; gravity = Gravity.CENTER; background = roundBg(greenLight, 12); setPadding(dp(12), dp(10), dp(12), dp(10)) }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(12) })
            val ti = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            ti.addView(TextView(this).apply { text = "#${pays.size - idx}   ₹ ${money(p.amount)}"; textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(greenBtn) })
            if (p.note.isNotBlank()) ti.addView(TextView(this).apply { text = p.note; textSize = 14f; setTextColor(textDark) })
            ti.addView(TextView(this).apply { text = "📅 ${p.dateTime}"; textSize = 11f; setTextColor(textMuted) })
            tc.addView(ti, LinearLayout.LayoutParams(0, -2, 1f))
            tc.addView(TextView(this).apply {
                text = "⋮"; textSize = 22f; setTypeface(null, Typeface.BOLD); setTextColor(textMuted); setPadding(dp(10), dp(6), dp(6), dp(6))
                setOnClickListener { v -> PopupMenu(context, v).apply { menu.add("Edit"); menu.add("Delete"); setOnMenuItemClickListener { m -> if (m.title == "Edit") showPaymentDialog(p) else AlertDialog.Builder(context).setMessage("Delete ₹ ${money(p.amount)}?").setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> db.deletePayment(p.id); showHisabDashboard() }.show(); true } }.show() }
            })
            body.addView(tc, lp(-1, -2, dp(10)))
        }
        scroll.addView(body); page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); wrapWithBottomBar(page)
    }

    private fun showPaymentDialog(p: Payment?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(8)) }
        val amt = EditText(this).apply { hint = "Amount (₹)"; setText(if (p != null) p.amount.toString() else ""); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val note = EditText(this).apply { hint = "Note (Seth ne diya / Cash / Online)"; setText(p?.note ?: "Seth Payment") }
        val date = EditText(this).apply { hint = "Date & Time"; setText(p?.dateTime ?: SimpleDateFormat("dd MMM yyyy, EEE • hh:mm a", Locale.getDefault()).format(Date())) }
        box.addView(amt, lp(-1, -2, dp(8))); box.addView(note, lp(-1, -2, dp(8))); box.addView(date)
        AlertDialog.Builder(this).setTitle(if (p == null) "सेठ से पैसा मिला" else "Edit Payment").setView(box).setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val a = amt.text.toString().trim().toDoubleOrNull()
                if (a == null || a <= 0) toast("Sahi amount bhariye")
                else { db.savePayment(p?.id, a, note.text.toString().trim(), date.text.toString().trim(), p?.createdMs ?: System.currentTimeMillis()); showHisabDashboard(); toast("Payment Saved ✓") }
            }.show()
    }

    private fun recordCard(item: Furniture, serialNo: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = roundBg(0xFFFFFFFF.toInt(), 14, 0xFFE2EBE6.toInt()); elevation = dp(2).toFloat(); setPadding(dp(12), dp(12), dp(10), dp(12))
        addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; background = roundBg(greenLight, 10); clipToOutline = true
            val bmp = if (item.photo.isNotBlank() && File(item.photo).exists()) BitmapFactory.decodeFile(item.photo) else null
            if (bmp != null) setImageBitmap(bmp) else setImageResource(android.R.drawable.ic_menu_gallery)
        }, LinearLayout.LayoutParams(dp(74), dp(68)).apply { rightMargin = dp(12) })
        val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        info.addView(TextView(context).apply { text = "#$serialNo  ${item.model}"; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(textDark) })
        if (item.size.isNotBlank()) info.addView(TextView(context).apply { text = "Size: ${item.size}"; textSize = 13f; setTextColor(textMuted) })
        info.addView(TextView(context).apply { text = "₹ ${money(item.amount)}"; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(greenBtn); setPadding(0, dp(2), 0, dp(2)) })
        info.addView(TextView(context).apply { text = "📅 ${item.dateTime}"; textSize = 11f; setTextColor(textMuted) })
        addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(context).apply {
            text = "⋮"; textSize = 22f; setTypeface(null, Typeface.BOLD); setTextColor(textMuted); setPadding(dp(10), dp(6), dp(6), dp(6))
            setOnClickListener { v -> PopupMenu(context, v).apply { menu.add("Edit"); menu.add("Delete"); setOnMenuItemClickListener { m -> if (m.title == "Edit") showForm(item) else AlertDialog.Builder(context).setMessage("Delete '${item.model}'?").setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> db.delete(item.id); openTab(currentTab) }.show(); true } }.show() }
        })
    }

    private fun showForm(item: Furniture?) {
        chosenPhoto = item?.photo ?: ""; root.removeAllViews()
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgColor) }
        val scroll = ScrollView(this); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        val photoBox = FrameLayout(this).apply { background = roundBg(greenLight, 16, 0xFFCDE0D5.toInt()); clipToOutline = true }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            val bmp = if (chosenPhoto.isNotBlank() && File(chosenPhoto).exists()) BitmapFactory.decodeFile(chosenPhoto) else null
            if (bmp != null) setImageBitmap(bmp) else setImageResource(android.R.drawable.ic_menu_camera)
        }
        previewImg = img; photoBox.addView(img, FrameLayout.LayoutParams(-1, dp(180)))
        val camPill = TextView(this).apply {
            text = if (chosenPhoto.isNotBlank()) "📷 Change Photo" else "📷 Choose Photo"; setTextColor(0xFFFFFFFF.toInt()); textSize = 14f; setTypeface(null, Typeface.BOLD)
            background = roundBg(0xCC111111.toInt(), 20); setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Select Furniture Photo")
                    .setItems(arrayOf("📷 Camera (फोटो खींचें)", "🖼️ Gallery (गैलरी से चुनें)")) { _, w -> if (w == 0) takeCameraPhoto.launch(null) else pickPhoto.launch("image/*") }.show()
            }
        }
        photoStatus = camPill; photoBox.addView(camPill, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(12) })
        box.addView(photoBox, lp(-1, dp(180), dp(14)))
        fun inp(icon: String, label: String, value: String, num: Boolean = false): EditText {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = roundBg(0xFFFFFFFF.toInt(), 12, 0xFFD5E2DB.toInt()); setPadding(dp(14), dp(10), dp(14), dp(10)) }
            row.addView(TextView(this).apply { text = icon; textSize = 20f; setPadding(0, 0, dp(12), 0) })
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(greenDark); setTypeface(null, Typeface.BOLD) })
            val e = EditText(this).apply { setText(value); setBackgroundColor(0); setPadding(0, dp(2), 0, 0); textSize = 16f; if (num) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
            col.addView(e); row.addView(col, LinearLayout.LayoutParams(0, -2, 1f)); box.addView(row, lp(-1, -2, dp(12))); return e
        }
        val size = inp("📐", "Size (e.g. 6x3 ft)", item?.size ?: "")
        val model = inp("#️⃣", "Model No. (e.g. SF-101)", item?.model ?: "")
        val amount = inp("₹", "Amount (₹)", if (item != null) item.amount.toString() else "", true)
        val date = inp("📅", "Date & Time", item?.dateTime ?: SimpleDateFormat("dd MMM yyyy, EEE • hh:mm a", Locale.getDefault()).format(Date()))
        val doSave = {
            val a = amount.text.toString().trim().toDoubleOrNull()
            if (model.text.isBlank() || a == null) toast("Model No. aur valid Amount bhariye")
            else { db.save(item?.id, model.text.toString().trim(), size.text.toString().trim(), a, date.text.toString().trim(), chosenPhoto, item?.createdMs ?: System.currentTimeMillis()); openTab(0); toast("Record Saved ✓") }
        }
        box.addView(TextView(this).apply { text = "✓  Save Record"; textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER; background = roundBg(greenBtn, 26); setPadding(dp(16), dp(14), dp(16), dp(14)); setOnClickListener { doSave() } }, lp(-1, -2, dp(10)))
        scroll.addView(box)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(greenDark); setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(TextView(context).apply { text = "←"; textSize = 22f; setTextColor(0xFFFFFFFF.toInt()); setPadding(dp(8), dp(4), dp(12), dp(4)); setOnClickListener { openTab(currentTab) } })
            addView(TextView(context).apply { text = if (item == null) "Add Furniture" else "Edit Furniture"; textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(context).apply { text = "💾"; textSize = 20f; setTextColor(0xFFFFFFFF.toInt()); setPadding(dp(12), dp(4), dp(8), dp(4)); setOnClickListener { doSave() } })
        }
        page.addView(bar); page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(page)
    }

    private fun showBackup() {
        root.removeAllViews()
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgColor) }
        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(greenDark); setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(TextView(context).apply { text = "←"; textSize = 22f; setTextColor(0xFFFFFFFF.toInt()); setPadding(dp(8), dp(4), dp(12), dp(4)); setOnClickListener { openTab(currentTab) } })
            addView(TextView(context).apply { text = "Backup & Restore"; textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt()) })
        })
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(20), dp(18), dp(20)) }
        body.addView(TextView(this).apply { text = "☁️"; textSize = 54f; gravity = Gravity.CENTER })
        body.addView(TextView(this).apply { text = "Keep your data safe"; textSize = 16f; setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(textDark) }, lp(-1, -2, dp(18)))
        fun bCard(t: String, s: String, bg: Int, st: Int, fn: () -> Unit) {
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = roundBg(bg, 14, st); setPadding(dp(16), dp(16), dp(16), dp(16)); setOnClickListener { fn() } }
            c.addView(TextView(this).apply { text = t; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(textDark) })
            c.addView(TextView(this).apply { text = s; textSize = 13f; setTextColor(textMuted) }); body.addView(c, lp(-1, -2, dp(12)))
        }
        bCard("✓ Automatic Backup (Android)", "Data is backed up automatically on device", greenLight, 0xFF81C784.toInt()) { toast("Auto Backup On ✓") }
        bCard("💾 Manual Backup (Save File)", "Save all furniture, photos & hisab transactions", 0xFFE3F2FD.toInt(), 0xFF90CAF9.toInt()) { exportBackup.launch("ZFH_Ledger_Backup.json") }
        bCard("📂 Restore Data", "Restore from your backup JSON file", 0xFFFFFFFF.toInt(), 0xFFD0DDD6.toInt()) { importBackup.launch(arrayOf("application/json", "text/*")) }
        page.addView(body); root.addView(page)
    }

    private fun showSettings() {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgColor) }
        page.addView(topBar("Profile & Settings"))
        val scroll = ScrollView(this); val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(20)) }
        val pCard = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = roundBg(0xFFFFFFFF.toInt(), 16, 0xFFDCE7E1.toInt()); elevation = dp(3).toFloat(); setPadding(dp(14), dp(14), dp(14), dp(14)) }
        val dpPath = prefs().getString("reg_dp", "") ?: ""
        pCard.addView(ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; background = roundBg(greenLight, 32, greenBtn); clipToOutline = true
            val bmp = if (dpPath.isNotBlank() && File(dpPath).exists()) BitmapFactory.decodeFile(dpPath) else null
            if (bmp != null) setImageBitmap(bmp) else setImageResource(android.R.drawable.ic_menu_myplaces)
        }, LinearLayout.LayoutParams(dp(64), dp(64)).apply { rightMargin = dp(12) })
        val pInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pInfo.addView(TextView(this).apply { text = prefs().getString("reg_name", "ZFH User") ?: "ZFH User"; textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(textDark) })
        pInfo.addView(TextView(this).apply { text = "📞 ${prefs().getString("reg_phone", "") ?: ""}"; textSize = 14f; setTextColor(greenBtn) })
        val shop = prefs().getString("reg_shop", "") ?: ""
        if (shop.isNotBlank()) pInfo.addView(TextView(this).apply { text = "🏪 $shop"; textSize = 13f; setTextColor(textMuted) })
        pCard.addView(pInfo, LinearLayout.LayoutParams(0, -2, 1f))
        pCard.addView(TextView(this).apply { text = "✏️"; textSize = 20f; setPadding(dp(10), dp(8), dp(6), dp(8)); setOnClickListener { showRegister(true) } })
        body.addView(pCard, lp(-1, -2, dp(14)))

        fun row(t: String, rTxt: String, fn: () -> Unit) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = roundBg(0xFFFFFFFF.toInt(), 12, 0xFFE0EAE4.toInt()); setPadding(dp(16), dp(16), dp(16), dp(16)); setOnClickListener { fn() } }
            r.addView(TextView(this).apply { text = t; textSize = 16f; setTextColor(textDark) }, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(TextView(this).apply { text = rTxt; textSize = 14f; setTextColor(greenBtn) }); body.addView(r, lp(-1, -2, dp(10)))
        }
        row("🔒 Fingerprint / Screen Lock", if (isLockEnabled()) "ON >" else "OFF >") { prefs().edit().putBoolean("screen_lock", !isLockEnabled()).apply(); toast(if (isLockEnabled()) "App Lock ON ✓" else "App Lock OFF"); showSettings() }
        row("🔄 नया हिसाब चालू करें", "Reset >") { startNewHisab() }
        row("☁️ Backup Settings", "Auto Backup On >") { showBackup() }
        row("📂 Restore Data", ">") { importBackup.launch(arrayOf("application/json", "text/*")) }
        row("ℹ️ About App", "ZFH Ledger v2.6 >") { toast("ZFH Ledger v2.6 - Zubair Edition") }
        row("🔗 Share App", ">") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "ZFH Ledger App!") }, "Share ZFH Ledger")) }
        scroll.addView(body); page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); wrapWithBottomBar(page)
    }

    private fun makeBackup(): JSONObject {
        val arr = JSONArray()
        db.all().forEach { f ->
            val o = JSONObject().put("id", f.id).put("model", f.model).put("size", f.size).put("amount", f.amount).put("dateTime", f.dateTime).put("createdMs", f.createdMs)
            if (f.photo.isNotBlank() && File(f.photo).exists()) o.put("photoName", File(f.photo).name).put("photoBase64", Base64.encodeToString(File(f.photo).readBytes(), Base64.NO_WRAP))
            arr.put(o)
        }
        val pArr = JSONArray()
        db.allPayments().forEach { p -> pArr.put(JSONObject().put("id", p.id).put("amount", p.amount).put("note", p.note).put("dateTime", p.dateTime).put("createdMs", p.createdMs)) }
        return JSONObject().put("app", "ZFH Ledger").put("version", 6).put("items", arr).put("payments", pArr)
    }
    private fun restoreBackup(data: JSONObject) {
        val arr = data.getJSONArray("items"); db.clearAll()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); var photo = ""
            if (o.has("photoBase64")) {
                val f = File(filesDir, o.optString("photoName", "restored_$i.jpg").replace(Regex("[^A-Za-z0-9._-]"), "_"))
                f.writeBytes(Base64.decode(o.getString("photoBase64"), Base64.DEFAULT)); photo = f.absolutePath
            }
            db.save(null, o.optString("model"), o.optString("size"), o.optDouble("amount"), o.optString("dateTime"), photo, o.optLong("createdMs", System.currentTimeMillis()))
        }
        if (data.has("payments")) {
            val pArr = data.getJSONArray("payments")
            for (i in 0 until pArr.length()) { val o = pArr.getJSONObject(i); db.savePayment(null, o.optDouble("amount"), o.optString("note"), o.optString("dateTime"), o.optLong("createdMs", System.currentTimeMillis())) }
        }
    }
    private fun money(v: Double) = String.format(Locale("en", "IN"), "%,.0f", v)
    private fun lp(w: Int, h: Int, bottom: Int = 0) = LinearLayout.LayoutParams(w, h).apply { if (bottom > 0) bottomMargin = bottom }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
