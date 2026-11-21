package com.newtimer.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import androidx.core.view.WindowCompat

class MainActivity : Activity() {
    private lateinit var hours: EditText
    private lateinit var minutes: EditText
    private lateinit var seconds: EditText
    private lateinit var status: TextView
    private lateinit var root: LinearLayout
    private val prefs by lazy { getSharedPreferences("timer", MODE_PRIVATE) }
    private val bg = Color.rgb(0, 0, 0)
    private val panel = Color.rgb(29, 29, 31)
    private val accent = Color.rgb(128, 210, 203)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        buildUi()
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 5)
        }
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setFillViewport(true)
            isVerticalScrollBarEnabled = false
            setBackgroundColor(bg)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(bg)
        }
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "NewTimer"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })
        status = TextView(this).apply {
            text = "Set a countdown"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(8), 0, dp(16))
        }
        root.addView(status)

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        hours = field("HH")
        minutes = field("MM")
        seconds = field("SS")
        timeRow.addView(hours, timeWeight())
        timeRow.addView(minutes, timeWeight())
        timeRow.addView(seconds, timeWeight())
        root.addView(timeRow)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(action("START TIMER", accent, Color.BLACK) { startTimer() }, actionWeight())
        actions.addView(action("STOP", panel, Color.WHITE) { stopTimer() }, actionWeight())
        root.addView(actions)

        section("QUICK PRESETS")
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("1 min" to 60L, "3 min" to 180L, "5 min" to 300L, "10 min" to 600L, "25 min" to 1500L).forEach { (name, value) ->
            quick.addView(action(name, panel, Color.WHITE) { setTime(value) }, chipWeight())
        }
        root.addView(quick)

        root.addView(action("＋  SAVE CURRENT TIME AS PRESET", panel, Color.WHITE) { savePreset() }, fullWidth())
        section("YOUR SAVED PRESETS")
        val saved = prefs.getStringSet("presets", emptySet())?.toList()?.sorted().orEmpty()
        if (saved.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "Your saved presets will appear here"
                textSize = 13f
                setTextColor(Color.GRAY)
                setPadding(0, dp(2), 0, dp(8))
            })
        } else {
            saved.forEach { item ->
                val parts = item.split("|", limit = 2)
                if (parts.size == 2) {
                    val value = parts[1].toLongOrNull() ?: 0L
                    if (value > 0) {
                        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                        row.addView(action(parts[0], panel, Color.WHITE) { setTime(value) }, LinearLayout.LayoutParams(0, dp(48), 1f))
                        row.addView(action("×", panel, Color.LTGRAY) {
                            val updated = prefs.getStringSet("presets", emptySet())?.toMutableSet() ?: mutableSetOf()
                            updated.remove(item)
                            prefs.edit().putStringSet("presets", updated).apply()
                            buildUi()
                        }, LinearLayout.LayoutParams(dp(48), dp(48)))
                        root.addView(row)
                    }
                }
            }
        }

        section("RING VOLUME")
        root.addView(TextView(this).apply {
            text = "App playback level"
            textSize = 12f
            setTextColor(Color.GRAY)
        })
        val volume = SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("ring_gain", 100)
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    prefs.edit().putInt("ring_gain", value).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(volume, fullWidth())

        section("AUTO-STOP RING AFTER")
        val durations = listOf("5 seconds", "15 seconds", "30 seconds", "1 minute", "2 minutes", "5 minutes", "10 minutes")
        val durationValues = listOf(5, 15, 30, 60, 120, 300, 600)
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, durations).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(durationValues.indexOf(prefs.getInt("ring_duration", 60)).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("ring_duration", durationValues[position]).apply()
                }
            }
            background = rounded(panel, 12)
            setPadding(dp(10), 0, dp(10), 0)
        }
        root.addView(spinner, fullWidth().apply { height = dp(52) })

        section("RING SOUND")
        root.addView(action("USE SYSTEM ALARM SOUND", panel, Color.WHITE) {
            prefs.edit().remove("ring_uri").apply()
            status.text = "System alarm sound selected"
        }, fullWidth())
        root.addView(action("CHOOSE CUSTOM SOUND FILE", panel, Color.WHITE) {
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(picker, 23)
        }, fullWidth())

        section("VOLUME-KEY STOP")
        root.addView(action("OPEN ACCESSIBILITY SETTINGS", panel, Color.WHITE) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, fullWidth())
        root.addView(TextView(this).apply {
            text = "Enable NewTimer in Accessibility settings. Volume keys can stop the timer or alarm when Android delivers the key event. Screen-off support depends on your phone and Android version."
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dp(8), 0, dp(18))
        })
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        setHintTextColor(Color.GRAY)
        setTextColor(Color.WHITE)
        textSize = 27f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        singleLine = true
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        background = rounded(panel, 14)
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }

    private fun section(text: String) {
        root.addView(TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(20), 0, dp(8))
        })
    }

    private fun action(text: String, color: Int, textColor: Int, onClick: () -> Unit) =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.MEDIUM)
            setTextColor(textColor)
            gravity = Gravity.CENTER
            minHeight = dp(48)
            isClickable = true
            isFocusable = true
            background = rounded(color, 14)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { onClick() }
        }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun timeWeight() = LinearLayout.LayoutParams(0, dp(72), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(10)) }
    private fun actionWeight() = LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(3), dp(8), dp(3), dp(4)) }
    private fun chipWeight() = LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(2), 0, dp(2), dp(4)) }
    private fun fullWidth() = LinearLayout.LayoutParams(-1, dp(50)).apply { setMargins(0, dp(4), 0, dp(4)) }

    private fun readTime(): Long =
        (hours.text.toString().toLongOrNull() ?: 0L) * 3600L +
        (minutes.text.toString().toLongOrNull() ?: 0L) * 60L +
        (seconds.text.toString().toLongOrNull() ?: 0L)

    private fun setTime(total: Long) {
        hours.setText((total / 3600).toString())
        minutes.setText(((total % 3600) / 60).toString())
        seconds.setText((total % 60).toString())
    }

    private fun savePreset() {
        val total = readTime()
        if (total <= 0) {
            status.text = "Set a time before saving a preset"
            return
        }
        val nameInput = EditText(this).apply { hint = "Preset name" }
        android.app.AlertDialog.Builder(this)
            .setTitle("Save quick preset")
            .setView(nameInput)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim().ifEmpty { formatDuration(total) }
                val set = prefs.getStringSet("presets", emptySet())?.toMutableSet() ?: mutableSetOf()
                set.add("$name|$total")
                prefs.edit().putStringSet("presets", set).apply()
                buildUi()
                setTime(total)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatDuration(total: Long): String {
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 && s > 0 -> "${m}m ${s}s"
            m > 0 -> "${m} min"
            else -> "${s} sec"
        }
    }

    private fun startTimer() {
        val total = readTime()
        if (total <= 0) {
            status.text = "Enter a duration greater than zero"
            return
        }
        val intent = Intent(this, TimerService::class.java)
            .setAction(TimerService.ACTION_START)
            .putExtra(TimerService.EXTRA_SECONDS, total)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        status.text = "Timer started"
    }

    private fun stopTimer() {
        val intent = Intent(this, TimerService::class.java).setAction(TimerService.ACTION_STOP)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        status.text = "Timer stopped"
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 23 && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                prefs.edit().putString("ring_uri", uri.toString()).apply()
                status.text = "Custom sound selected"
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) stopTimer()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
