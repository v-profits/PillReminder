package com.semenenko.pillreminder

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.semenenko.pillreminder.data.Prefs
import com.semenenko.pillreminder.ui.PillRoot
import com.semenenko.pillreminder.ui.PillTheme

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Считаем каждое новое открытие (не поворот экрана) — для окна «Оцените приложение»
        if (savedInstanceState == null) Prefs(this).let { it.launchCount += 1 }
        enableEdgeToEdge()
        setContent {
            PillTheme {
                PillRoot(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.refreshReminders()
    }
}
