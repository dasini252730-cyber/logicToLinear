package com.logiclinear.reading

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.logiclinear.reading.ui.navigation.AppNavigation
import com.logiclinear.reading.ui.theme.ReadingLogTheme

/** 앱 진입점. 화면 구성은 [AppNavigation]이 맡는다. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReadingLogTheme {
                AppNavigation()
            }
        }
    }
}
