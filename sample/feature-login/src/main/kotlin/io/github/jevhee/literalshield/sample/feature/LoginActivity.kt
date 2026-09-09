package io.github.jevhee.literalshield.sample.feature

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class LoginActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(TextView(this).apply { text = "/v1/feature-sentinel-5821" })
    }
}
