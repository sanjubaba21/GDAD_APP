package com.gdad.bags

import android.app.Application
import com.gdad.bags.data.remote.SupabaseConfig
import com.gdad.bags.di.AppContainer
import com.gdad.bags.di.ProductionAppContainer

class GdadApplication : Application() {
    val appContainer: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ProductionAppContainer(
            context = applicationContext,
            supabaseConfig = SupabaseConfig(
                url = BuildConfig.SUPABASE_URL,
                publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            ),
        )
    }
}
