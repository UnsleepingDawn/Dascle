package com.fitplan.app.di

import android.content.Context
import com.fitplan.core.metro.metroGraph

val Context.appGraph get() = metroGraph<AppGraph>()
