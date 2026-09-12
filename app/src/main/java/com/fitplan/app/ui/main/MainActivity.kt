package com.fitplan.app.ui.main

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.fitplan.app.di.AppGraph
import com.fitplan.core.metro.metroGraph

class MainActivity : ComponentActivity() {

    private val graph: AppGraph by lazy { metroGraph() }

    override fun onCreate(savedInstanceState: Bundle?) {
        graph.inject(this)
        super.onCreate(savedInstanceState)

        Log.d("FitPlan", "MainActivity injected with $graph")

        setContent {
            MaterialTheme {
                Surface {
                    Text("FitPlan")
                }
            }
        }
    }
}
