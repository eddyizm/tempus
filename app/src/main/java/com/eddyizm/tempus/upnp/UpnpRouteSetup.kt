package com.eddyizm.tempus.upnp

import android.util.Log
import android.view.Menu
import androidx.core.view.MenuItemCompat
import androidx.mediarouter.app.MediaRouteActionProvider
import androidx.mediarouter.media.MediaRouteSelector
import com.eddyizm.tempus.R

// The button never hides itself. setAlwaysVisible is a bare return, isVisible defaults to true.
object UpnpRouteSetup {

    private const val TAG = "UpnpRouteSetup"

    @JvmStatic
    fun pointAtUpnpRenderers(menu: Menu) {
        val item = menu.findItem(R.id.media_route_menu_item) ?: return

        // AppCompat keeps an actionProviderClass provider where MenuItem.getActionProvider cannot see it.
        val provider = MenuItemCompat.getActionProvider(item) as? MediaRouteActionProvider
        if (provider == null) {
            Log.w(TAG, "route item has no MediaRouteActionProvider, leaving it alone")
            return
        }

        provider.routeSelector = MediaRouteSelector.Builder()
            .addSelector(provider.routeSelector)
            .addControlCategory(UpnpRouteProvider.CATEGORY_UPNP)
            .build()
    }
}
