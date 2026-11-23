package com.tamer.studentphotomatcher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamer.studentphotomatcher.ui.StudentPhotoMatcherViewModel
import com.tamer.studentphotomatcher.ui.UiState

enum class AppNavDestination(val labelAr: String) {
    DASHBOARD("لوحة التحكم"),
    REVIEW("المراجعة والاعتماد"),
    EXPORT("التسمية والتصدير")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    viewModel: StudentPhotoMatcherViewModel,
    onPickExcelClick: () -> Unit,
    onPickPhotosFolderClick: () -> Unit,
    onPickOutputFolderClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentDestination by remember { mutableStateOf(AppNavDestination.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackBarMessage) {
        uiState.snackBarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissSnackBar()
        }
    }

    // تفعيل الاتجاه العربي الأصيل RTL لكامل الواجهة
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Student Photo Matcher & Organizer",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "راصد ومنظم صور الطلاب الذكي بالذكاء الاصطناعي",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.clearSession() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "تفريغ الجلسة", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = currentDestination == AppNavDestination.DASHBOARD,
                        onClick = { currentDestination = AppNavDestination.DASHBOARD },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                        label = { Text(AppNavDestination.DASHBOARD.labelAr, fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppNavDestination.REVIEW,
                        onClick = { currentDestination = AppNavDestination.REVIEW },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (uiState.dashboardStats.pendingReviewCount > 0) {
                                        Badge { Text(uiState.dashboardStats.pendingReviewCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null)
                            }
                        },
                        label = { Text(AppNavDestination.REVIEW.labelAr, fontSize = 11.sp) }
                    )
                    NavigationBarItem(
                        selected = currentDestination == AppNavDestination.EXPORT,
                        onClick = { currentDestination = AppNavDestination.EXPORT },
                        icon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                        label = { Text(AppNavDestination.EXPORT.labelAr, fontSize = 11.sp) }
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentDestination) {
                    AppNavDestination.DASHBOARD -> DashboardScreen(
                        uiState = uiState,
                        onPickExcelClick = onPickExcelClick,
                        onPickPhotosFolderClick = onPickPhotosFolderClick,
                        onRunOcrAndMatchClick = { viewModel.runOcrAndMatching() },
                        onNavigateToReview = { currentDestination = AppNavDestination.REVIEW },
                        onNavigateToExport = { currentDestination = AppNavDestination.EXPORT }
                    )
                    AppNavDestination.REVIEW -> VisualReviewScreen(
                        uiState = uiState,
                        onApproveMatch = { viewModel.approveMatch(it) },
                        onRejectMatch = { viewModel.rejectMatch(it) },
                        onEditDetectedText = { code, name, nid -> viewModel.editDetectedData(code, name, nid) },
                        onAssignDifferentStudent = { code, photo -> viewModel.assignPhotoToStudent(code, photo) },
                        onUnlinkPhoto = { viewModel.unlinkPhoto(it) },
                        onSetFilter = { viewModel.setReviewFilter(it) },
                        onSetSearchQuery = { viewModel.setSearchQuery(it) },
                        onToggleBatchMode = { viewModel.setBatchReviewMode(it) },
                        onNextItem = { viewModel.nextReviewItem() },
                        onPrevItem = { viewModel.prevReviewItem() }
                    )
                    AppNavDestination.EXPORT -> ExportSettingsScreen(
                        uiState = uiState,
                        onSelectNamingOption = { viewModel.setNamingOption(it) },
                        onPickOutputFolderClick = onPickOutputFolderClick,
                        onStartExportClick = onPickOutputFolderClick
                    )
                }
            }
        }
    }
}
