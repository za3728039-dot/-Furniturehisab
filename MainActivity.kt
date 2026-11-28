package com.example.furniturehisab

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.text.InputType
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.*
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val APP_VER = 2
    private val FB_URL = "https://zfh-ledger-default-rtdb.firebaseio.com"
    private val BLOCK_URL = "https://raw.githubusercontent.com/za3728039-dot/-Furniturehisab/main/blocked.txt"
    private val VER_URL = "https://raw.githubusercontent.com/za3728039-dot/-Furniturehisab/main/version.txt"

    private val C_DARK = Color.parseColor("#0B4628")
    private val C_GREEN = Color.parseColor("#0D6832")
    private val C_BG = Color.parseColor("#F2F6F4")
    private val C_GOLD = Color.parseColor("#FFD43B")
    private val C_RED = Color.parseColor("#C62828")

    private lateinit var prefs: SharedPreferences
    private lateinit var rootLayout: LinearLayout
    private lateinit var contentFrame: FrameLayout
    private lateinit var navBar: LinearLayout

    private var currentTab = 0
    private var searchQuery = ""
    private var hisabStartMs = 0L
    private var hisabEndMs = Long.MAX_VALUE
    private var isUnlocked = false
    private var updateChecked = false

    private var pendingPhotoCb: ((String) -> Unit)? = null
    private var pendingEditItemIdx = -1
    private var tempPhotoB64 = ""

    private val langs = listOf(
        "en" to "English", "hi" to "हिंदी", "mr" to "मराठी",
        "gu" to "ગુજરાતી", "pa" to "ਪੰਜਾਬੀ", "bn" to "বাংলা",
        "ta" to "தமிழ்", "te" to "తెలుగు", "ur" to "اردو"
    )

    private val tr = mapOf(
        "en" to mapOf("home" to "Home", "search" to "Search", "add" to "Add", "hisab" to "Hisab", "profile" to "Profile", "items" to "Total Items", "bill" to "Total Bill", "mila" to "Received", "baki" to "Balance Due", "add_pay" to "+ Payment Received", "reset" to "Start New Hisab", "all_time" to "All Time", "date_range" to "Date Range", "save" to "Save", "login" to "Continue / Recover Data", "lang" to "Language", "lock" to "Screen Lock", "backup" to "Export Backup", "restore" to "Restore Backup", "logout" to "Logout / Switch Account", "photo" to "Add Photo"),
        "hi" to mapOf("home" to "होम", "search" to "खोजें", "add" to "जोड़ें", "hisab" to "हिसाब", "profile" to "प्रोफ़ाइल", "items" to "कुल काम", "bill" to "कुल बिल", "mila" to "सेठ से मिला", "baki" to "बाकी लेना है", "add_pay" to "+ सेठ से पैसा मिला", "reset" to "नया हिसाब चालू करें", "all_time" to "पूरा हिसाब", "date_range" to "तारीख से तारीख", "save" to "सेव करें", "login" to "लॉगिन / पुराना डेटा वापस लाएं", "lang" to "भाषा", "lock" to "स्क्रीन लॉक", "backup" to "बैकअप सेव करें", "restore" to "बैकअप वापस लाएं", "logout" to "लॉगआउट / नंबर बदलें", "photo" to "फोटो लगाएं"),
        "mr" to mapOf("home" to "होम", "search" to "शोधा", "add" to "जोडा", "hisab" to "हिशोब", "profile" to "प्रोफाइल", "items" to "एकूण काम", "bill" to "एकूण बिल", "mila" to "मिळाले", "baki" to "बाकी येणे", "add_pay" to "+ पैसे मिळाले", "reset" to "नवीन हिशोब सुरू करा", "all_time" to "संपूर्ण", "date_range" to "तारखेनुसार", "save" to "सेव्ह करा", "login" to "लॉगिन / डेटा परत आणा", "lang" to "भाषा", "lock" to "स्क्रीन लॉक", "backup" to "बॅकअप सेव्ह", "restore" to "बॅकअप रिस्टोअर", "logout" to "लॉगआउट", "photo" to "फोटो जोडा"),
        "gu" to mapOf("home" to "હોમ", "search" to "શોધો", "add" to "ઉમેરો", "hisab" to "હિસાબ", "profile" to "પ્રોફાઇલ", "items" to "કુલ કામ", "bill" to "કુલ બિલ", "mila" to "મળ્યા", "baki" to "બાકી લેવાના", "add_pay" to "+ પૈસા મળ્યા", "reset" to "નવો હિસાબ શરૂ કરો", "all_time" to "બધો હિસાબ", "date_range" to "તારીખ મુજબ", "save" to "સેવ કરો", "login" to "લોગિન / ડેટા પાછો લાવો", "lang" to "ભાષા", "lock" to "સ્ક્રીન લોક", "backup" to "બેકઅપ સેવ", "restore" to "બેકઅપ રિસ્ટોર", "logout" to "લોગઆઉટ", "photo" to "ફોટો ઉમેરો"),
        "pa" to mapOf("home" to "ਹੋਮ", "search" to "ਖੋਜੋ", "add" to "ਜੋੜੋ", "hisab" to "ਹਿਸਾਬ", "profile" to "ਪ੍ਰੋਫਾਈਲ", "items" to "ਕੁੱਲ ਕੰਮ", "bill" to "ਕੁੱਲ ਬਿੱਲ", "mila" to "ਮਿਲੇ", "baki" to "ਬਾਕੀ ਲੈਣੇ", "add_pay" to "+ ਪੈਸੇ ਮਿਲੇ", "reset" to "ਨਵਾਂ ਹਿਸਾਬ ਸ਼ੁਰੂ ਕਰੋ", "all_time" to "ਸਾਰਾ ਹਿਸਾਬ", "date_range" to "ਤਾਰੀਖ ਮੁਤਾਬਕ", "save" to "ਸੇਵ ਕਰੋ", "login" to "ਲੌਗਇਨ / ਡਾਟਾ ਵਾਪਸ", "lang" to "ਭਾਸ਼ਾ", "lock" to "ਸਕ੍ਰੀਨ ਲੌਕ", "backup" to "ਬੈਕਅੱਪ", "restore" to "ਰੀਸਟੋਰ", "logout" to "ਲੌਗਆਉਟ", "photo" to "ਫੋਟੋ ਲਗਾਓ"),
        "bn" to mapOf("home" to "হোম", "search" to "খুঁজুন", "add" to "যোগ করুন", "hisab" to "হিসাব", "profile" to "প্রোফাইল", "items" to "মোট কাজ", "bill" to "মোট বিল", "mila" to "জমা", "baki" to "বাকি পাওনা", "add_pay" to "+ টাকা জমা", "reset" to "নতুন হিসাব শুরু", "all_time" to "সব হিসাব", "date_range" to "তারিখ অনুযায়ী", "save" to "সেভ করুন", "login" to "লগইন / ডেটা পুনরুদ্ধার", "lang" to "ভাষা", "lock" to "স্ক্রিন লক", "backup" to "ব্যাকআপ", "restore" to "রিস্টোর", "logout" to "লগআউট", "photo" to "ছবি দিন"),
        "ta" to mapOf("home" to "முகப்பு", "search" to "தேடு", "add" to "சேர்", "hisab" to "கணக்கு", "profile" to "சுயவிவரம்", "items" to "மொத்த வேலை", "bill" to "மொத்த பில்", "mila" to "பெற்றது", "baki" to "பாக்கி", "add_pay" to "+ பணம் பெற்றது", "reset" to "புதிய கணக்கு", "all_time" to "முழு கணக்கு", "date_range" to "தேதி வாரியாக", "save" to "சேமி", "login" to "உள்நுழை / மீட்டெடு", "lang" to "மொழி", "lock" to "திரை பூட்டு", "backup" to "காப்பு", "restore" to "மீட்டமை", "logout" to "வெளியேறு", "photo" to "புகைப்படம்"),
        "te" to mapOf("home" to "హోమ్", "search" to "వెతకండి", "add" to "జోడించు", "hisab" to "లెక్క", "profile" to "ప్రొఫైల్", "items" to "మొత్తం పని", "bill" to "మొత్తం బిల్లు", "mila" to "వచ్చింది", "baki" to "బాకీ", "add_pay" to "+ డబ్బు వచ్చింది", "reset" to "కొత్త లెక్క", "all_time" to "మొత్తం", "date_range" to "తేదీ ప్రకారం", "save" to "సేవ్", "login" to "లాగిన్ / డేటా రికవర్", "lang" to "భాష", "lock" to "స్క్రీన్ లాక్", "backup" to "బ్యాకప్", "restore" to "రీస్టోర్", "logout" to "లాగౌట్", "photo" to "ఫోటో"),
        "ur" to mapOf("home" to "ہوم", "search" to "تلاش", "add" to "شامل کریں", "hisab" to "حساب", "profile" to "پروفائل", "items" to "کل کام", "bill" to "کل بل", "mila" to "وصول", "baki" to "باقی لینا ہے", "add_pay" to "+ پیسے ملے", "reset" to "نیا حساب شروع کریں", "all_time" to "مکمل حساب", "date_range" to "تاریخ سے تاریخ", "save" to "محفوظ کریں", "login" to "لاگ ان / ڈیٹا ریکور", "lang" to "زبان", "lock" to "اسکرین لاک", "backup" to "بیک اپ", "restore" to "ریسٹور", "logout" to "لاگ آؤٹ", "photo" to "تصویر لگائیں")
    )

    private fun t(k: String): String {
        val code = prefs.getString("lang", "hi") ?: "hi"
        return tr[code]?.get(k) ?: tr["en"]?.get(k) ?: k
    }

    private val camLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) pendingPhotoCb?.invoke(bmpToB64(bmp))
    }

    private val galLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use {
                    val bmp = BitmapFactory.decodeStream(it)
                    if (bmp != null) pendingPhotoCb?.invoke(bmpToB64(bmp))
                }
            } catch (_: Exception) {}
        }
    }

    private val lockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            isUnlocked = true
            startAppFlow()
        } else finish()
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val root = JSONObject().apply {
                    put("items", getArr("items"))
                    put("payments", getArr("payments"))
                }
                contentResolver.openOutputStream(uri)?.use { it.write(root.toString().toByteArray()) }
                toast("Backup Saved!")
            } catch (_: Exception) { toast("Backup Failed") }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@registerForActivityResult
                val obj = JSONObject(txt)
                if (obj.has("items")) prefs.edit().putString("items", obj.getJSONArray("items").toString()).apply()
                if (obj.has("payments")) prefs.edit().putString("payments", obj.getJSONArray("payments").toString()).apply()
                syncToCloud()
                renderTab()
                toast("Backup Restored!")
            } catch (_: Exception) { toast("Invalid File") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("zfh_prefs", MODE_PRIVATE)
        showSplash()
    }

    private fun showSplash() {
        val sp = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(C_DARK)
            addView(TextView(context).apply {
                text = "ZFH"
                textSize = 48f
                setTypeface(null, Typeface.BOLD)
                setTextColor(C_GOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "Ledger"
                textSize = 24f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "Hisab Rakho, Tension Chhodo..."
                textSize = 14f
                setTextColor(Color.parseColor("#B9E4C9"))
                setPadding(0, 24, 0, 0)
                gravity = Gravity.CENTER
            })
        }
        setContentView(sp)
        Handler(Looper.getMainLooper()).postDelayed({ checkSecurity() }, 1200)
    }

    private fun checkSecurity() {
        val lockOn = prefs.getBoolean("lock_on", false)
        if (lockOn && !isUnlocked) {
            try {
                val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
                @Suppress("DEPRECATION")
                val intent = km.createConfirmDeviceCredentialIntent("ZFH Ledger", "Unlock App")
                if (intent != null) { lockLauncher.launch(intent); return }
            } catch (_: Exception) {}
        }
        isUnlocked = true
        startAppFlow()
    }

    private fun startAppFlow() {
        val phone = prefs.getString("user_phone", "") ?: ""
        if (phone.length != 10) {
            showRegistration()
            return
        }
        checkBlocked(phone) { blocked ->
            if (blocked) showBlockedScreen()
            else {
                buildMainUI()
                registerUserInCloud()
                if (!updateChecked) { updateChecked = true; checkUpdate(false) }
            }
        }
    }

    private fun checkUpdate(manual: Boolean) {
        Thread {
            try {
                val lines = URL(VER_URL).readText().lines().map { it.trim() }.filter { it.isNotEmpty() }
                val latest = lines.getOrNull(0)?.toIntOrNull() ?: 0
                val dlUrl = lines.getOrNull(1) ?: ""
                runOnUiThread {
                    if (latest > APP_VER && dlUrl.startsWith("http")) {
                        AlertDialog.Builder(this)
                            .setTitle("🆕 Update Available")
                            .setMessage("ZFH Ledger का नया वर्ज़न आ गया है! अभी डाउनलोड करें?")
                            .setPositiveButton("Update Now") { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dlUrl)))
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    } else if (manual) toast("App is up to date!")
                }
            } catch (_: Exception) {
                if (manual) runOnUiThread { toast("No updates found") }
            }
        }.start()
    }

    private fun checkBlocked(phone: String, cb: (Boolean) -> Unit) {
        Thread {
            var isBlk = false
            try {
                val txt = URL(BLOCK_URL).readText()
                isBlk = txt.lines().any { it.trim() == phone }
            } catch (_: Exception) {}
            runOnUiThread { cb(isBlk) }
        }.start()
    }

    private fun showBlockedScreen() {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(48, 48, 48, 48)
            addView(TextView(context).apply {
                text = "⛔ Account Blocked"
                textSize = 24f
                setTypeface(null, Typeface.BOLD)
                setTextColor(C_RED)
            })
            addView(TextView(context).apply {
                text = "Your access has been restricted by the admin."
                textSize = 15f
                setPadding(0, 16, 0, 0)
                gravity = Gravity.CENTER
            })
        }
        setContentView(v)
    }

    private fun showRegistration() {
        val sc = ScrollView(this).apply { setBackgroundColor(C_BG) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        sc.addView(box)
        box.setOnApplyWindowInsetsListener { v, ins -> applyInsets(v, ins) }

        val langBtn = pillBtn("🌐 ${t("lang")}: ${langs.find { it.first == (prefs.getString("lang", "hi") ?: "hi") }?.second}", C_GREEN) {
            pickLanguage { showRegistration() }
        }
        box.addView(langBtn)

        box.addView(TextView(this).apply {
            text = "ZFH Ledger"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(C_DARK)
            setPadding(0, 32, 0, 8)
        })
        box.addView(TextView(this).apply {
            text = "Enter your mobile number to start or recover your old data"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 24)
        })

        var dpB64 = prefs.getString("user_dp", "") ?: ""
        val dpImg = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(160, 160).apply { gravity = Gravity.CENTER_HORIZONTAL }
            setBackgroundColor(Color.LTGRAY)
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (dpB64.isNotEmpty()) b64ToBmp(dpB64)?.let { setImageBitmap(it) }
        }
        box.addView(dpImg)
        box.addView(pillBtn("📷 ${t("photo")}", Color.DKGRAY) {
            pickPhoto { b64 -> dpB64 = b64; b64ToBmp(b64)?.let { dpImg.setImageBitmap(it) } }
        })

        val etName = inputBox("Full Name *", prefs.getString("user_name", "") ?: "")
        val etPhone = inputBox("10-Digit Mobile Number *", prefs.getString("user_phone", "") ?: "", InputType.TYPE_CLASS_PHONE)
        val etShop = inputBox("Shop / Karkhana Name (Optional)", prefs.getString("user_shop", "") ?: "")
        box.addView(etName); box.addView(etPhone); box.addView(etShop)

        box.addView(pillBtn(t("login"), C_DARK) {
            val nm = etName.text.toString().trim()
            val ph = etPhone.text.toString().trim()
            val sh = etShop.text.toString().trim()
            if (ph.length != 10 || !ph.all { it.isDigit() }) {
                toast("Enter valid 10-digit mobile number"); return@pillBtn
            }
            if (nm.isEmpty()) {
                toast("Enter your name"); return@pillBtn
            }
            prefs.edit().putString("user_name", nm).putString("user_phone", ph).putString("user_shop", sh).putString("user_dp", dpB64).apply()
            toast("Checking cloud data...")
            recoverFromCloud(ph) {
                startAppFlow()
            }
        })
        setContentView(sc)
    }

    private fun buildMainUI() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C_BG)
        }
        rootLayout.setOnApplyWindowInsetsListener { v, ins -> applyInsets(v, ins) }

        val header = LinearLayout(this).apply {
            setBackgroundColor(C_DARK)
            setPadding(36, 28, 36, 28)
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "ZFH Ledger"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(TextView(context).apply {
                text = "🌐"
                textSize = 20f
                setPadding(16, 8, 16, 8)
                setOnClickListener { pickLanguage { buildMainUI() } }
            })
        }
        rootLayout.addView(header)

        contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        rootLayout.addView(contentFrame)

        navBar = LinearLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            elevation = 12f
            setPadding(8, 14, 8, 14)
        }
        val tabs = listOf("🏠\n${t("home")}", "🔍\n${t("search")}", "➕\n${t("add")}", "💰\n${t("hisab")}", "⚙️\n${t("profile")}")
        tabs.forEachIndexed { i, lbl ->
            navBar.addView(TextView(this).apply {
                text = lbl
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(if (i == currentTab) C_DARK else Color.GRAY)
                if (i == currentTab) setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setOnClickListener {
                    if (i != 2) pendingEditItemIdx = -1
                    currentTab = i
                    buildMainUI()
                }
            })
        }
        rootLayout.addView(navBar)
        setContentView(rootLayout)
        renderTab()
    }

    private fun renderTab() {
        contentFrame.removeAllViews()
        when (currentTab) {
            0 -> renderHome(false)
            1 -> renderHome(true)
            2 -> renderAddEdit()
            3 -> renderHisab()
            4 -> renderProfile()
        }
    }

    private fun renderHome(isSearch: Boolean) {
        val sc = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 24, 28, 24) }
        sc.addView(list)

        if (isSearch) {
            val et = inputBox("Search model / serial / size / date...", searchQuery)
            et.setOnEditorActionListener { _, _, _ -> searchQuery = et.text.toString().trim(); renderTab(); true }
            list.addView(et)
            list.addView(pillBtn("🔍 ${t("search")}", C_GREEN) { searchQuery = et.text.toString().trim(); renderTab() })
        }

        val arr = getArr("items")
        var sum = 0
        val matched = mutableListOf<Pair<Int, JSONObject>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val q = searchQuery.lowercase()
            if (!isSearch || q.isEmpty() || o.optString("model").lowercase().contains(q) || o.optString("serial").lowercase().contains(q) || o.optString("size").lowercase().contains(q) || o.optString("date").lowercase().contains(q)) {
                matched.add(i to o)
                sum += o.optInt("amount", 0)
            }
        }

        list.addView(cardBox(C_DARK).apply {
            addView(TextView(context).apply {
                text = "${t("items")}: ${matched.size}   |   ${t("bill")}: ₹$sum"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(C_GOLD)
            })
        })

        for (pair in matched.reversed()) {
            val idx = pair.first
            val o = pair.second
            val c = cardBox(Color.WHITE)
            c.setOnClickListener { pendingEditItemIdx = idx; currentTab = 2; buildMainUI() }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val b64 = o.optString("photo")
            if (b64.isNotEmpty()) {
                row.addView(ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(150, 150).apply { rightMargin = 24 }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    b64ToBmp(b64)?.let { setImageBitmap(it) }
                })
            }
            val m = o.optString("model").ifEmpty { "No Model" }
            val sr = o.optString("serial")
            val sz = o.optString("size").ifEmpty { "-" }
            val amt = o.optInt("amount", 0)
            val amtTxt = if (amt > 0) "₹$amt" else "Pending ✏️"
            val srTxt = if (sr.isNotEmpty()) " | Sr: $sr" else ""
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            info.addView(TextView(this).apply {
                text = "#${idx + 1} • $m"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(C_DARK)
            })
            info.addView(TextView(this).apply {
                text = "Size: $sz$srTxt | $amtTxt\n${o.optString("date")}"
                textSize = 13f
                setTextColor(if (amt > 0) Color.DKGRAY else C_RED)
            })
            row.addView(info)
            row.addView(TextView(this).apply {
                text = "✏️"
                textSize = 18f
                setPadding(16, 8, 16, 8)
                setOnClickListener { pendingEditItemIdx = idx; currentTab = 2; buildMainUI() }
            })
            row.addView(TextView(this).apply {
                text = "🗑️"
                textSize = 18f
                setPadding(12, 8, 8, 8)
                setOnClickListener {
                    confirmDel {
                        val na = JSONArray()
                        for (j in 0 until arr.length()) if (j != idx) na.put(arr.get(j))
                        prefs.edit().putString("items", na.toString()).apply()
                        syncToCloud()
                        renderTab()
                    }
                }
            })
            c.addView(row)
            list.addView(c)
        }
        contentFrame.addView(sc)
    }

    private fun renderAddEdit() {
        val sc = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 28, 36, 28) }
        sc.addView(box)

        val arr = getArr("items")
        val editObj = if (pendingEditItemIdx in 0 until arr.length()) arr.getJSONObject(pendingEditItemIdx) else null
        tempPhotoB64 = editObj?.optString("photo") ?: ""

        val img = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 360).apply { bottomMargin = 20 }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (tempPhotoB64.isNotEmpty()) b64ToBmp(tempPhotoB64)?.let { setImageBitmap(it) }
        }
        box.addView(img)
        box.addView(pillBtn("📷 ${t("photo")}", Color.DKGRAY) {
            pickPhoto { b64 -> tempPhotoB64 = b64; b64ToBmp(b64)?.let { img.setImageBitmap(it) } }
        })

        val etModel = inputBox("Model No. (Optional)", editObj?.optString("model") ?: "")
        val etSerial = inputBox("Serial No. (Optional)", editObj?.optString("serial") ?: "")
        val etSize = inputBox("Size (Optional, e.g. 6x4)", editObj?.optString("size") ?: "")
        val oldAmt = editObj?.optInt("amount", 0) ?: 0
        val etAmt = inputBox("Amount ₹ (Optional - fill later)", if (oldAmt > 0) oldAmt.toString() else "", InputType.TYPE_CLASS_NUMBER)
        box.addView(etModel); box.addView(etSerial); box.addView(etSize); box.addView(etAmt)

        fun saveItemNow(m: String, sr: String, s: String, a: Int) {
            val nowMs = editObj?.optLong("ts") ?: System.currentTimeMillis()
            val dt = editObj?.optString("date") ?: SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(nowMs))
            val obj = JSONObject().apply {
                put("model", m); put("serial", sr); put("size", s); put("amount", a)
                put("photo", tempPhotoB64); put("date", dt); put("ts", nowMs)
            }
            if (editObj != null) arr.put(pendingEditItemIdx, obj) else arr.put(obj)
            prefs.edit().putString("items", arr.toString()).apply()
            pendingEditItemIdx = -1
            syncToCloud()
            currentTab = 0
            buildMainUI()
        }

        box.addView(pillBtn("💾 ${t("save")}", C_GREEN) {
            val m = etModel.text.toString().trim()
            val sr = etSerial.text.toString().trim()
            val s = etSize.text.toString().trim()
            val a = etAmt.text.toString().trim().toIntOrNull() ?: 0
            if (m.isEmpty() && sr.isEmpty() && s.isEmpty() && a == 0 && tempPhotoB64.isEmpty()) {
                toast("Add photo or at least one detail"); return@pillBtn
            }
            if (a == 0) {
                AlertDialog.Builder(this)
                    .setTitle("Amount नहीं डाला है!")
                    .setMessage("आपने Amount नहीं लिखा है। बिना Amount के Save करें?")
                    .setPositiveButton("Yes") { _, _ -> saveItemNow(m, sr, s, 0) }
                    .setNegativeButton("No", null)
                    .show()
            } else {
                saveItemNow(m, sr, s, a)
            }
        })
        contentFrame.addView(sc)
    }

    private fun renderHisab() {
        val sc = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 24, 28, 24) }
        sc.addView(box)

        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        filterRow.addView(pillBtn(t("all_time"), if (hisabStartMs == 0L) C_DARK else Color.GRAY) {
            hisabStartMs = 0L; hisabEndMs = Long.MAX_VALUE; renderTab()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 12 } })
        filterRow.addView(pillBtn("📅 ${t("date_range")}", if (hisabStartMs > 0L) C_DARK else Color.GRAY) {
            pickDate { s -> pickDate { e -> hisabStartMs = s; hisabEndMs = e + 86399999L; renderTab() } }
        }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        box.addView(filterRow)

        val items = getArr("items")
        val pays = getArr("payments")
        var itemCount = 0; var totalBill = 0; var totalMila = 0

        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val ts = o.optLong("ts", 0L)
            if (ts in hisabStartMs..hisabEndMs) { itemCount++; totalBill += o.optInt("amount", 0) }
        }
        val payMatched = mutableListOf<Pair<Int, JSONObject>>()
        for (i in 0 until pays.length()) {
            val p = pays.getJSONObject(i)
            val ts = p.optLong("ts", 0L)
            if (ts in hisabStartMs..hisabEndMs) { totalMila += p.optInt("amount", 0); payMatched.add(i to p) }
        }
        val baki = totalBill - totalMila

        box.addView(cardBox(C_DARK).apply {
            addView(TextView(context).apply {
                text = "${t("items")}: $itemCount\n${t("bill")}: ₹$totalBill\n${t("mila")}: ₹$totalMila"
                textSize = 16f; setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = "${t("baki")}: ₹$baki"
                textSize = 22f; setTypeface(null, Typeface.BOLD); setTextColor(C_GOLD); setPadding(0, 12, 0, 0)
            })
        })

        box.addView(pillBtn(t("add_pay"), C_GREEN) { showPayDialog(-1, null) })
        box.addView(pillBtn("🔄 ${t("reset")}", C_RED) {
            AlertDialog.Builder(this).setTitle("Reset Hisab?").setMessage("Clear all items & payments?")
                .setPositiveButton("Yes") { _, _ ->
                    prefs.edit().putString("items", "[]").putString("payments", "[]").apply()
                    syncToCloud(); renderTab()
                }.setNegativeButton("No", null).show()
        })

        for (pair in payMatched.reversed()) {
            val idx = pair.first; val p = pair.second
            box.addView(cardBox(Color.WHITE).apply {
                val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                r.addView(TextView(context).apply {
                    text = "💵 ₹${p.optInt("amount")}  •  ${p.optString("note")}\n${p.optString("date")}"
                    textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                })
                r.addView(TextView(context).apply {
                    text = "✏️"; textSize = 18f; setPadding(14, 8, 14, 8)
                    setOnClickListener { showPayDialog(idx, p) }
                })
                r.addView(TextView(context).apply {
                    text = "🗑️"; textSize = 18f; setPadding(10, 8, 8, 8)
                    setOnClickListener {
                        confirmDel {
                            val np = JSONArray()
                            for (j in 0 until pays.length()) if (j != idx) np.put(pays.get(j))
                            prefs.edit().putString("payments", np.toString()).apply()
                            syncToCloud(); renderTab()
                        }
                    }
                })
                addView(r)
            })
        }
        contentFrame.addView(sc)
    }

    private fun showPayDialog(idx: Int, old: JSONObject?) {
        val lay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 20, 36, 10) }
        val etA = inputBox("Amount (₹)", if (old != null) old.optInt("amount").toString() else "", InputType.TYPE_CLASS_NUMBER)
        val etN = inputBox("Note (e.g. Cash / UPI)", old?.optString("note") ?: "Cash")
        lay.addView(etA); lay.addView(etN)
        AlertDialog.Builder(this).setTitle(t("add_pay")).setView(lay)
            .setPositiveButton(t("save")) { _, _ ->
                val a = etA.text.toString().trim().toIntOrNull() ?: return@setPositiveButton
                val n = etN.text.toString().trim()
                val arr = getArr("payments")
                val ts = old?.optLong("ts") ?: System.currentTimeMillis()
                val dt = old?.optString("date") ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ts))
                val o = JSONObject().apply { put("amount", a); put("note", n); put("date", dt); put("ts", ts) }
                if (idx >= 0) arr.put(idx, o) else arr.put(o)
                prefs.edit().putString("payments", arr.toString()).apply()
                syncToCloud(); renderTab()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun renderProfile() {
        val sc = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 28, 32, 28) }
        sc.addView(box)

        val nm = prefs.getString("user_name", "") ?: ""
        val ph = prefs.getString("user_phone", "") ?: ""
        val sh = prefs.getString("user_shop", "") ?: ""
        val dp = prefs.getString("user_dp", "") ?: ""

        box.addView(cardBox(Color.WHITE).apply {
            val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(130, 130).apply { rightMargin = 24 }
                setBackgroundColor(Color.LTGRAY); scaleType = ImageView.ScaleType.CENTER_CROP
                if (dp.isNotEmpty()) b64ToBmp(dp)?.let { setImageBitmap(it) }
            })
            r.addView(TextView(context).apply {
                text = "$nm\n📞 $ph\n🏪 $sh"
                textSize = 15f; setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            r.addView(TextView(context).apply {
                text = "✏️"; textSize = 20f; setPadding(16, 8, 8, 8)
                setOnClickListener { showRegistration() }
            })
            addView(r)
        })

        val curL = langs.find { it.first == (prefs.getString("lang", "hi") ?: "hi") }?.second ?: "हिंदी"
        box.addView(pillBtn("🌐 ${t("lang")}: $curL", C_GREEN) { pickLanguage { buildMainUI() } })
        val lk = prefs.getBoolean("lock_on", false)
        box.addView(pillBtn("🔒 ${t("lock")}: ${if (lk) "ON" else "OFF"}", Color.DKGRAY) {
            prefs.edit().putBoolean("lock_on", !lk).apply(); renderTab()
        })
        box.addView(pillBtn("📤 ${t("backup")}", C_DARK) { exportLauncher.launch("ZFH_Ledger_Backup.json") })
        box.addView(pillBtn("📥 ${t("restore")}", C_DARK) { importLauncher.launch(arrayOf("application/json", "*/*")) })
        box.addView(pillBtn("☁️ Sync Now", C_GREEN) { syncToCloud(); toast("Synced to Cloud!") })
        box.addView(pillBtn("🔄 Check Update (v$APP_VER)", C_GREEN) { checkUpdate(true) })
        box.addView(pillBtn("🚪 ${t("logout")}", C_RED) {
            prefs.edit().remove("user_phone").apply()
            showRegistration()
        })
        contentFrame.addView(sc)
    }

    private fun registerUserInCloud() {
        val ph = prefs.getString("user_phone", "") ?: return
        if (ph.length != 10) return
        Thread {
            try {
                val u = JSONObject().apply {
                    put("name", prefs.getString("user_name", ""))
                    put("phone", ph)
                    put("shop", prefs.getString("user_shop", ""))
                    put("lang", prefs.getString("lang", "hi"))
                    put("lastSeen", SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date()))
                }
                httpPut("$FB_URL/users/$ph.json", u.toString())
            } catch (_: Exception) {}
        }.start()
    }

    private fun syncToCloud() {
        val ph = prefs.getString("user_phone", "") ?: return
        if (ph.length != 10) return
        Thread {
            try {
                val payload = JSONObject().apply {
                    put("name", prefs.getString("user_name", ""))
                    put("shop", prefs.getString("user_shop", ""))
                    put("items", getArr("items"))
                    put("payments", getArr("payments"))
                }
                httpPut("$FB_URL/data/$ph.json", payload.toString())
            } catch (_: Exception) {}
        }.start()
    }

    private fun recoverFromCloud(ph: String, done: () -> Unit) {
        Thread {
            try {
                val res = URL("$FB_URL/data/$ph.json").readText().trim()
                if (res != "null" && res.startsWith("{")) {
                    val obj = JSONObject(res)
                    val ed = prefs.edit()
                    if (obj.has("items")) ed.putString("items", obj.getJSONArray("items").toString())
                    if (obj.has("payments")) ed.putString("payments", obj.getJSONArray("payments").toString())
                    if (obj.optString("name").isNotEmpty()) ed.putString("user_name", obj.getString("name"))
                    if (obj.optString("shop").isNotEmpty()) ed.putString("user_shop", obj.getString("shop"))
                    ed.apply()
                    runOnUiThread { toast("Old Data Recovered!"); done() }
                    return@Thread
                }
            } catch (_: Exception) {}
            runOnUiThread { done() }
        }.start()
    }

    private fun httpPut(urlStr: String, body: String) {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            doOutput = true; connectTimeout = 8000; readTimeout = 8000
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        conn.responseCode
        conn.disconnect()
    }

    private fun pickLanguage(onDone: () -> Unit) {
        val names = langs.map { it.second }.toTypedArray()
        AlertDialog.Builder(this).setTitle("🌐 Select Language")
            .setItems(names) { _, which ->
                prefs.edit().putString("lang", langs[which].first).apply()
                onDone()
            }.show()
    }

    private fun pickPhoto(cb: (String) -> Unit) {
        pendingPhotoCb = cb
        AlertDialog.Builder(this).setTitle(t("photo"))
            .setItems(arrayOf("📷 Camera", "🖼️ Gallery")) { _, w ->
                if (w == 0) camLauncher.launch(null) else galLauncher.launch("image/*")
            }.show()
    }

    private fun pickDate(cb: (Long) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val cal = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
            cb(cal.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun confirmDel(yes: () -> Unit) {
        AlertDialog.Builder(this).setTitle("Delete?").setPositiveButton("Yes") { _, _ -> yes() }.setNegativeButton("No", null).show()
    }

    private fun getArr(k: String): JSONArray = try { JSONArray(prefs.getString(k, "[]")) } catch (_: Exception) { JSONArray() }

    private fun bmpToB64(bmp: Bitmap): String {
        val sc = Bitmap.createScaledBitmap(bmp, 360, (bmp.height * 360f / bmp.width).toInt().coerceAtLeast(1), true)
        val out = ByteArrayOutputStream()
        sc.compress(Bitmap.CompressFormat.JPEG, 55, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun b64ToBmp(b64: String): Bitmap? = try {
        val b = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(b, 0, b.size)
    } catch (_: Exception) { null }

    private fun pillBtn(txt: String, col: Int, click: () -> Unit) = Button(this).apply {
        text = txt; setTextColor(Color.WHITE); isAllCaps = false; textSize = 14f
        background = GradientDrawable().apply { setColor(col); cornerRadius = 40f }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 }
        setOnClickListener { click() }
    }

    private fun cardBox(col: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(28, 24, 28, 24)
        background = GradientDrawable().apply { setColor(col); cornerRadius = 28f }
        elevation = 4f
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 }
    }

    private fun inputBox(h: String, v: String, type: Int = InputType.TYPE_CLASS_TEXT) = EditText(this).apply {
        hint = h; setText(v); inputType = type; setPadding(28, 24, 28, 24)
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 24f; setStroke(2, Color.LTGRAY) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 }
    }

    private fun applyInsets(v: View, ins: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= 30) {
            val b = ins.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
        } else {
            @Suppress("DEPRECATION")
            v.setPadding(ins.systemWindowInsetLeft, ins.systemWindowInsetTop, ins.systemWindowInsetRight, ins.systemWindowInsetBottom)
        }
        return ins
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
