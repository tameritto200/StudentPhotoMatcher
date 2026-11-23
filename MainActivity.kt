package com.tamer.studentphotomatcher.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.tamer.studentphotomatcher.ui.screens.MainAppScaffold
import com.tamer.studentphotomatcher.ui.theme.StudentPhotoMatcherTheme

class MainActivity : ComponentActivity() {

    private val viewModel: StudentPhotoMatcherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            StudentPhotoMatcherTheme {
                // عقود Storage Access Framework لاختيار الملفات والمجلدات دون طلب أذونات معقدة
                val pickExcelLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        contentResolver.takePersistableUriPermission(
                            it,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        viewModel.loadExcelFile(it)
                    }
                }

                val pickPhotosFolderLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    uri?.let {
                        contentResolver.takePersistableUriPermission(
                            it,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        viewModel.loadPhotosFolder(it)
                    }
                }

                val pickOutputFolderLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    uri?.let {
                        contentResolver.takePersistableUriPermission(
                            it,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        viewModel.executeExport(it)
                    }
                }

                MainAppScaffold(
                    viewModel = viewModel,
                    onPickExcelClick = {
                        pickExcelLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    onPickPhotosFolderClick = {
                        pickPhotosFolderLauncher.launch(null)
                    },
                    onPickOutputFolderClick = {
                        pickOutputFolderLauncher.launch(null)
                    }
                )
            }
        }
    }
}
