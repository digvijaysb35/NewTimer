package com.newtimer.app

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {
    private lateinit var hours: EditText
    private lateinit var minutes: EditText
    private lateinit var seconds: EditText
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("timer", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = 0
        buildUi()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 5)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(0,0,0))
        }
        val title = TextView(this).apply { text = "NewTimer"; textSize = 30f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }
        root.addView(title)
        status = TextView(this).apply { text = "Set a countdown"; textSize = 16f; setTextColor(Color.LTGRAY); setPadding(0, 12, 0, 18) }
        root.addView(status)
        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        hours = field("HH"); minutes = field("MM"); seconds = field("SS")
        timeRow.addView(hours, weight()); timeRow.addView(minutes, weight()); timeRow.addView(seconds, weight())
        root.addView(timeRow)
        val start = button("START TIMER") { startTimer() }
        root.addView(start, match())
        val stop = button("STOP") { stopTimer() }
        root.addView(stop, match())
        val presetTitle = label("QUICK PRESETS")
        root.addView(presetTitle)
        val presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("1 min" to 60L, "3 min" to 180L, "5 min" to 300L, "10 min" to 600L, "25 min" to 1500L).forEach { (name, value) ->
            presetRow.addView(button(name) { setTime(value) }, LinearLayout.LayoutParams(0, 52, 1f))
        }
        root.addView(presetRow)
        root.addView(label("RING VOLUME (APP LEVEL)"))
        val volume = SeekBar(this).apply {
            max = 100; progress = prefs.getInt("ring_gain", 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { prefs.edit().putInt("ring_gain", p).apply() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(volume)
        root.addView(label("AUTO-STOP RING AFTER"))
        val durations = listOf("5 sec", "15 sec", "30 sec", "1 min", "2 min", "5 min", "10 min")
        val durationValues = listOf(5, 15, 30, 60, 120, 300, 600)
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, durations)
        spinner.setSelection(durationValues.indexOf(prefs.getInt("ring_duration", 60)).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { prefs.edit().putInt("ring_duration", durationValues[position]).apply() }
        }
        root.addView(spinner)
        root.addView(button("CHOOSE CUSTOM RING") {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose timer sound")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            }
            @Suppress("DEPRECATION") startActivityForResult(i, 22)
        }, match())
        root.addView(button("ENABLE VOLUME-KEY STOP (SCREEN ON / DEVICE SUPPORT DEPENDENT)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, match())
        root.addView(label("Tip: volume keys stop the timer/alarm when Android delivers the key event to the accessibility service. Screen-off behavior varies by phone and Android version. The app-level ring slider adjusts software gain; your phone's alarm-volume limit still applies."))
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); textSize = 28f
        gravity = Gravity.CENTER; inputType = android.text.InputType.TYPE_CLASS_NUMBER
        background = rounded(Color.rgb(30,30,30)); setPadding(4, 10, 4, 10)
    }
    private fun label(s: String) = TextView(this).apply { text = s; textSize = 13f; setTextColor(Color.LTGRAY); setPadding(0, 20, 0, 8) }
    private fun button(s: String, action: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
        background = rounded(Color.rgb(35,35,35)); setOnClickListener { action() }
    }
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = 18f }
    private fun weight() = LinearLayout.LayoutParams(0, 76, 1f).apply { setMargins(4, 4, 4, 12) }
    private fun match() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 54).apply { setMargins(0, 5, 0, 5) }

    private fun setTime(total: Long) {
        hours.setText((total / 3600).toString()); minutes.setText(((total % 3600) / 60).toString()); seconds.setText((total % 60).toString())
    }
    private fun startTimer() {
        val total = (hours.text.toString().toLongOrNull() ?: 0) * 3600 +
            (minutes.text.toString().toLongOrNull() ?: 0) * 60 + (seconds.text.toString().toLongOrNull() ?: 0)
        if (total <= 0) { status.text = "Enter a duration greater than zero"; return }
        val i = Intent(this, TimerService::class.java).setAction(TimerService.ACTION_START).putExtra(TimerService.EXTRA_SECONDS, total)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        status.text = "Timer started"
    }
    private fun stopTimer() {
        startService(Intent(this, TimerService::class.java).setAction(TimerService.ACTION_STOP))
        status.text = "Stopped"
    }
    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 22 && resultCode == RESULT_OK) {
            val uri = data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            prefs.edit().putString("ring_uri", uri?.toString()).apply()
            status.text = if (uri == null) "System alarm sound selected" else "Custom ring selected"
        }
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            stopTimer(); return true
        }
        return super.dispatchKeyEvent(event)
    }
}
