package com.example.backgroundodometer

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val screen = TextView(this)

        screen.text = """
            BACKGROUND ODOMETER

            V1 FOUNDATION

            App started successfully.
        """.trimIndent()

        screen.textSize = 22f

        screen.setTextColor(
            Color.WHITE
        )

        screen.setBackgroundColor(
            Color.BLACK
        )

        screen.gravity =
            Gravity.CENTER

        screen.setPadding(
            40,
            40,
            40,
            40
        )

        setContentView(screen)
    }
}
