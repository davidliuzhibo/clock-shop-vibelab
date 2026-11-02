package com.vrgames.clockshop.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.vrgames.clockshop.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
