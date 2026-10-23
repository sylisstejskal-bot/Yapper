package app.yapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.yapper.ui.YapperRoot
import app.yapper.ui.theme.Ink
import app.yapper.ui.theme.YapperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YapperTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    YapperRoot()
                }
            }
        }
    }
}
