package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.*
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsFor
import com.example.ui.screens.*
import com.example.ui.theme.BillCircleTheme
import com.example.ui.viewmodel.BillCircleViewModel
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory Firebase App Check initialization before Compose UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            BillCircleTheme {
                BillCircleRootApp()
            }
        }
    }
}

private enum class MainTab { HOME, BILLS, GROUPS, MESSAGES, PROFILE }

private sealed interface SubScreen {
    data object None : SubScreen
    data object CreateBill : SubScreen
    data class BillDetail(val billId: String) : SubScreen
    data class Reports(val initialTab: Int) : SubScreen
}

@Composable
private fun BillCircleRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsStore = remember(context) { PreferencesStore(context.applicationContext) }
    val selectedLang by prefsStore.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.ENGLISH)

    val firebaseAuth = remember(context) { safeFirebaseAuthOrNull(context) }
    val authFlow = remember(firebaseAuth) { firebaseAuth?.authStateFlow() ?: flowOf(null) }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = firebaseAuth?.currentUser)

    CompositionLocalProvider(LocalAppStrings provides stringsFor(selectedLang)) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                selectedLanguage = selectedLang,
                onSelectLanguage = { lang -> scope.launch { prefsStore.setLanguage(lang) } },
                onAuthSuccess = { /* authStateFlow updates automatically */ }
            )
        } else {
            key(user.uid) {
                AuthenticatedBillCircleShell(user = user)
            }
        }
    }
}

@Composable
private fun AuthenticatedBillCircleShell(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val strings = LocalAppStrings.current

    val viewModel: BillCircleViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                BillCircleViewModel(
                    currentUserId = user.uid,
                    initialDisplayName = user.displayName ?: "BillCircle User",
                    initialEmail = user.email ?: "",
                    initialPhotoUrl = user.photoUrl?.toString(),
                    groupRepo = GroupAndUserRepository(db),
                    billRepo = BillOperationsRepository(db),
                    aiRepo = AiBillScannerRepository(),
                    prefsStore = PreferencesStore(app)
                )
            }
        }
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeTab by remember { mutableStateOf(MainTab.HOME) }
    var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    var messagesInitialTab by remember { mutableIntStateOf(0) }
    var groupAutoCreate by remember { mutableStateOf(false) }
    var groupAutoAddMember by remember { mutableStateOf(false) }

    LaunchedEffect(state.feedbackMessage, state.errorMessage) {
        val msg = state.feedbackMessage ?: state.errorMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearFeedback()
        }
    }

    val navItems = listOf(
        Triple(MainTab.HOME, strings.navHome, Icons.Filled.Home to Icons.Outlined.Home),
        Triple(MainTab.BILLS, strings.navBills, Icons.AutoMirrored.Filled.ReceiptLong to Icons.AutoMirrored.Outlined.ReceiptLong),
        Triple(MainTab.GROUPS, strings.navGroups, Icons.Filled.Groups to Icons.Outlined.Groups),
        Triple(MainTab.MESSAGES, strings.navMessages, Icons.Filled.Forum to Icons.Outlined.Forum),
        Triple(MainTab.PROFILE, strings.navProfile, Icons.Filled.Person to Icons.Outlined.Person)
    )

    // Render full-screen sub-screens when active
    when (val sub = subScreen) {
        is SubScreen.CreateBill -> {
            CreateBillScreen(
                state = state,
                onBack = { subScreen = SubScreen.None },
                onGoToGroups = {
                    subScreen = SubScreen.None
                    activeTab = MainTab.GROUPS
                    groupAutoCreate = true
                },
                onScanBillBitmap = { bmp -> viewModel.scanBillWithAi(bmp) },
                onResetAiScan = { viewModel.resetAiScanState() },
                onPublishBill = { draft ->
                    viewModel.createBill(draft) { newBillId ->
                        subScreen = SubScreen.BillDetail(newBillId)
                    }
                },
                onError = { viewModel.showFeedback(it) }
            )
            return
        }
        is SubScreen.BillDetail -> {
            BillDetailScreen(
                billId = sub.billId,
                state = state,
                viewModel = viewModel,
                onBack = { subScreen = SubScreen.None }
            )
            return
        }
        is SubScreen.Reports -> {
            ReportsAndHistoryScreen(
                state = state,
                initialTab = sub.initialTab,
                onBack = { subScreen = SubScreen.None },
                onOpenBill = { id -> subScreen = SubScreen.BillDetail(id) },
                onFeedback = { viewModel.showFeedback(it) }
            )
            return
        }
        SubScreen.None -> Unit
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 680.dp

        @Composable
        fun TabContent(modifier: Modifier = Modifier) {
            Box(modifier = modifier.fillMaxSize()) {
                when (activeTab) {
                    MainTab.HOME -> DashboardScreen(
                        state = state,
                        onCreateNewBill = { subScreen = SubScreen.CreateBill },
                        onOpenAddMember = {
                            activeTab = MainTab.GROUPS
                            groupAutoAddMember = true
                        },
                        onOpenCreateGroup = {
                            activeTab = MainTab.GROUPS
                            groupAutoCreate = true
                        },
                        onViewAllBills = { activeTab = MainTab.BILLS },
                        onOpenReportsAndHistory = { tabIdx -> subScreen = SubScreen.Reports(tabIdx) },
                        onOpenNotifications = {
                            messagesInitialTab = 0
                            activeTab = MainTab.MESSAGES
                        },
                        onOpenBillDetail = { id -> subScreen = SubScreen.BillDetail(id) },
                        onOpenMessages = {
                            messagesInitialTab = 1
                            activeTab = MainTab.MESSAGES
                        }
                    )
                    MainTab.BILLS -> BillsListScreen(
                        state = state,
                        onCreateBill = { subScreen = SubScreen.CreateBill },
                        onOpenBill = { id -> subScreen = SubScreen.BillDetail(id) }
                    )
                    MainTab.GROUPS -> GroupsScreen(
                        state = state,
                        viewModel = viewModel,
                        openCreateDialogImmediately = groupAutoCreate,
                        openAddMemberImmediately = groupAutoAddMember,
                        onConsumeInitialTrigger = {
                            groupAutoCreate = false
                            groupAutoAddMember = false
                        }
                    )
                    MainTab.MESSAGES -> MessagesAndNotificationsScreen(
                        state = state,
                        viewModel = viewModel,
                        initialTab = messagesInitialTab,
                        onOpenBill = { id -> subScreen = SubScreen.BillDetail(id) }
                    )
                    MainTab.PROFILE -> ProfileScreen(
                        state = state,
                        viewModel = viewModel,
                        onSignOut = { signOutUser(credentialManager, scope) }
                    )
                }
            }
        }

        val totalUnreadAlerts = state.unreadNotificationsCount + state.unreadMessagesCount

        if (isWideScreen) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    NavigationRail {
                        navItems.forEach { (tab, label, icons) ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    NavBadgeIcon(
                                        icon = if (selected) icons.first else icons.second,
                                        badgeCount = if (tab == MainTab.MESSAGES) totalUnreadAlerts else 0,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                    TabContent(modifier = Modifier.weight(1f))
                }
            }
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar {
                        navItems.forEach { (tab, label, icons) ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    NavBadgeIcon(
                                        icon = if (selected) icons.first else icons.second,
                                        badgeCount = if (tab == MainTab.MESSAGES) totalUnreadAlerts else 0,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                TabContent(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
private fun NavBadgeIcon(icon: ImageVector, badgeCount: Int, contentDescription: String) {
    if (badgeCount > 0) {
        BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
            Icon(icon, contentDescription = contentDescription)
        }
    } else {
        Icon(icon, contentDescription = contentDescription)
    }
}
