package com.example.musiclockart

import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.ComponentActivity
import com.example.musiclockart.databinding.ActivitySettingsBinding

/**
 * User-adjustable settings, persisted via Prefs and applied live by the
 * listener (blur) and overlay (brightness, animation).
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Blur 0..80
        binding.blurSeek.max = 80
        binding.blurSeek.progress = prefs.blurRadius
        binding.blurValue.text = prefs.blurRadius.toString()
        binding.blurSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                binding.blurValue.text = p.toString()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                prefs.blurRadius = binding.blurSeek.progress
            }
        })

        // Brightness 5..100 (%)
        binding.brightnessSeek.max = 100
        binding.brightnessSeek.progress = (prefs.brightness * 100).toInt()
        binding.brightnessValue.text = "${(prefs.brightness * 100).toInt()}%"
        binding.brightnessSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                binding.brightnessValue.text = "$p%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                prefs.brightness = binding.brightnessSeek.progress / 100f
            }
        })

        // Auto brightness toggle
        binding.autoBrightnessSwitch.isChecked = prefs.autoBrightness
        binding.autoBrightnessSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.autoBrightness = checked
            binding.brightnessSeek.isEnabled = !checked
        }
        binding.brightnessSeek.isEnabled = !prefs.autoBrightness

        // Animated-art optimization toggle
        binding.animatedSwitch.isChecked = prefs.animatedArt
        binding.animatedSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.animatedArt = checked
        }
    }
}
