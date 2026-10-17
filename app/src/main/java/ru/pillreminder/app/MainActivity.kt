package ru.pillreminder.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import ru.pillreminder.app.ui.PillRoot
import ru.pillreminder.app.ui.PillTheme

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
