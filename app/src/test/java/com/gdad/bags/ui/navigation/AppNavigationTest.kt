package com.gdad.bags.ui.navigation

import android.content.Context
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import com.gdad.bags.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppNavigationTest {
    @Test
    fun eachRoleSeesOnlyAuthorizedDestinations() {
        UserRole.entries.forEach { role ->
            val visible = NavigationPolicy.visibleItems(role).map { it.destination }.toSet()
            assertTrue(visible.isNotEmpty())
            FeatureDestination.entries.forEach { destination ->
                assertEquals(
                    "$role / $destination",
                    destination in visible,
                    NavigationPolicy.canOpen(role, destination),
                )
            }
        }
    }

    @Test
    fun lowerRolesCannotOpenHigherRoleDestinationsDirectly() {
        assertFalse(NavigationPolicy.canOpen(UserRole.SALESMAN, FeatureDestination.ACCOUNTS))
        assertFalse(NavigationPolicy.canOpen(UserRole.SALESMAN, FeatureDestination.FINANCE))
        assertTrue(NavigationPolicy.canOpen(UserRole.SALESMAN, FeatureDestination.REPORTS))
        assertFalse(NavigationPolicy.canOpen(UserRole.SUPER_ADMIN, FeatureDestination.SALES))
    }

    @Test
    fun pinOnlyReleaseRejectsEveryExternalCallbackOrDeepLink() {
        assertEquals(ExternalNavigationDecision.REJECT, ExternalNavigationPolicy.decide(null))
        assertEquals(ExternalNavigationDecision.REJECT, ExternalNavigationPolicy.decide("gdad://auth/callback"))
        assertEquals(ExternalNavigationDecision.REJECT, ExternalNavigationPolicy.decide("https://example.invalid/products"))
    }

    @Test
    fun typedBackStackRestoresFeatureAfterProcessRecreation() {
        val original = controller()
        original.navigate(FeatureRoute(FeatureDestination.PRODUCTS))
        val saved = original.saveState()

        val restored = controller(setGraph = false)
        restored.restoreState(saved)
        restored.graph = graph(restored)

        assertEquals(
            FeatureDestination.PRODUCTS,
            restored.currentBackStackEntry?.toRoute<FeatureRoute>()?.destination,
        )
        assertTrue(restored.popBackStack())
        assertEquals(DashboardRoute, restored.currentBackStackEntry?.toRoute<DashboardRoute>())
    }

    private fun controller(setGraph: Boolean = true): TestNavHostController {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestNavHostController(context).also { controller ->
            controller.navigatorProvider.addNavigator(ComposeNavigator())
            if (setGraph) controller.graph = graph(controller)
        }
    }

    private fun graph(controller: TestNavHostController) = controller.createGraph(
        startDestination = DashboardRoute,
    ) {
        composable<DashboardRoute> { }
        composable<FeatureRoute> { }
    }
}
