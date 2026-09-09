package io.github.jevhee.literalshield.sample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import io.github.jevhee.literalshield.sample.library.LibraryConfig
import io.github.jevhee.literalshield.sample.network.NetworkConfig

class MainActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(TextView(this).apply {
            text = listOf(ClientConfig.client(), NetworkConfig.endpoint(), LibraryConfig.endpoint()).joinToString("\n")
        })
    }
}
