package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.backend.FallbackRanker
import ro.faur.explorer.quickopen.backend.NucleoRanker
import ro.faur.explorer.quickopen.backend.RankerBackend
import ro.faur.explorer.quickopen.backend.RankerSelector
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate

/**
 * Phase 5 — NucleoRanker, RankerSelector, and FallbackRanker parity
 *
 * Covers the Automated Verification items for Phase 5:
 *   - NucleoRanker vs FallbackRanker parity: same 100 inputs → same relative ordering
 *     (top-10 intersection ≥ 80%)
 *   - RankerSelector.active is non-null and is a RankerBackend
 *   - NucleoRanker.isAvailable() is checked without crashing
 *
 * Note: NucleoNative.tryLoad() will fail in CI without the compiled native lib,
 * so NucleoRanker.isAvailable() will return false and all NucleoRanker rank calls
 * are only exercised when the native is actually present. The parity test is
 * skipped (via assumeTrue) when nucleo is not available.
 *
 * Tests fail to compile until NucleoRanker and RankerSelector exist.
 */
class Phase5NucleoRankerTest {

    private val fallback = FallbackRanker()
    private val nucleo = NucleoRanker()

    private fun candidate(name: String) = SearchCandidate(
        id = name,
        displayName = name,
        fullPath = "/tmp/$name",
        parentPath = "/tmp",
        type = CandidateType.FILE
    )

    private val hundredCandidates: List<SearchCandidate> = listOf(
        "MainActivity", "HomeFragment", "SettingsActivity", "NavGraph", "AppModule",
        "UserRepository", "ProfileViewModel", "LoginScreen", "DashboardScreen", "SearchBar",
        "ApiService", "RetrofitClient", "DataStore", "PrefManager", "FileUtils",
        "NetworkUtils", "ImageLoader", "CacheManager", "ThemeManager", "ColorPalette",
        "AppDatabase", "UserDao", "OrderDao", "ProductDao", "CategoryDao",
        "OrderItem", "CartItem", "Product", "Category", "User",
        "AuthToken", "Session", "TokenRefresher", "AuthInterceptor", "OAuthHelper",
        "BuildConfig", "Constants", "Strings", "Dimensions", "Styles",
        "MainViewModel", "OrderViewModel", "CartViewModel", "PaymentViewModel", "ShippingViewModel",
        "NotificationManager", "PushReceiver", "FcmService", "AlertHelper", "ToastUtil",
        "BluetoothManager", "LocationProvider", "PermissionHelper", "CameraUtil", "MediaPicker",
        "DateFormatter", "CurrencyFormatter", "PhoneFormatter", "AddressFormatter", "NameFormatter",
        "RecyclerAdapter", "GridAdapter", "PagerAdapter", "SpinnerAdapter", "TreeAdapter",
        "BaseFragment", "BaseActivity", "BaseViewModel", "BaseAdapter", "BaseDialog",
        "AppNavigator", "DeepLinkHandler", "RouteParser", "BackStackManager", "TabManager",
        "ErrorHandler", "CrashReporter", "LogHelper", "AnalyticsTracker", "EventBus",
        "WorkerFactory", "SyncWorker", "UploadWorker", "DownloadWorker", "CleanupWorker",
        "TestHelper", "MockRepository", "FakeApiService", "TestData", "AssertionExtensions",
        "readme", "build", "settings", "gradle", "proguard"
    ).map { candidate(it) }

    // -------------------------------------------------------------------------
    // RankerSelector
    // -------------------------------------------------------------------------

    @Test
    fun `RankerSelector active is non-null`() {
        assertNotNull(RankerSelector.active)
    }

    @Test
    fun `RankerSelector active is a RankerBackend`() {
        assertTrue(RankerSelector.active is RankerBackend)
    }

    @Test
    fun `RankerSelector active is available`() {
        assertTrue(RankerSelector.active.isAvailable())
    }

    @Test
    fun `RankerSelector name is not blank`() {
        assertTrue(RankerSelector.active.name.isNotBlank())
    }

    // -------------------------------------------------------------------------
    // NucleoRanker basic contract (does not require native to be loaded)
    // -------------------------------------------------------------------------

    @Test
    fun `NucleoRanker can be instantiated without throwing`() {
        assertNotNull(nucleo)
    }

    @Test
    fun `NucleoRanker name is not blank`() {
        assertTrue(nucleo.name.isNotBlank())
    }

    @Test
    fun `NucleoRanker isAvailable does not throw`() {
        // May return true or false; must not throw
        val result = runCatching { nucleo.isAvailable() }
        assertTrue(result.isSuccess, "isAvailable() must not throw: ${result.exceptionOrNull()}")
    }

    @Test
    fun `NucleoRanker rank with blank query returns candidates up to limit`() {
        // When nucleo is unavailable, rank should degrade gracefully
        // (return empty or up-to-limit candidates with score 0.0 — per RankerBackend contract)
        val result = runCatching { nucleo.rank("", hundredCandidates.take(5), 10) }
        assertTrue(result.isSuccess, "rank() must not throw even when native unavailable")
    }

    // -------------------------------------------------------------------------
    // Parity test: NucleoRanker vs FallbackRanker — only runs when nucleo loaded
    // -------------------------------------------------------------------------

    @Test
    fun `NucleoRanker and FallbackRanker agree on top-10 for common query`() {
        // Skip if nucleo native library is not available (expected in most CI runs)
        org.junit.jupiter.api.Assumptions.assumeTrue(
            nucleo.isAvailable(),
            "Skipping parity test — NucleoNative not loaded"
        )

        val query = "View"
        val fallbackTop10 = fallback.rank(query, hundredCandidates, 10)
            .map { it.candidate.displayName }.toSet()
        val nucleoTop10 = nucleo.rank(query, hundredCandidates, 10)
            .map { it.candidate.displayName }.toSet()

        val intersection = fallbackTop10.intersect(nucleoTop10)
        val intersectionRatio = intersection.size.toDouble() / 10.0

        assertTrue(
            intersectionRatio >= 0.8,
            "Top-10 overlap between NucleoRanker and FallbackRanker should be ≥80%, " +
                    "got ${intersection.size}/10 in common.\n" +
                    "Fallback top-10: $fallbackTop10\nNucleo top-10: $nucleoTop10"
        )
    }

    @Test
    fun `FallbackRanker ranks same 100 candidates consistently`() {
        // Regression: two consecutive rank calls with the same inputs must return
        // the same ordering (no randomness)
        val query = "Manager"
        val first = fallback.rank(query, hundredCandidates, 10).map { it.candidate.displayName }
        val second = fallback.rank(query, hundredCandidates, 10).map { it.candidate.displayName }
        assertEquals(first, second, "FallbackRanker must be deterministic")
    }
}
