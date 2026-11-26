package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.AppDatabase
import com.example.data.local.LocalBillingRepository
import com.example.data.local.LocalRegisterRepository
import com.example.data.remote.CloudBackupRepository
import com.example.data.remote.RegisterOcrRepository
import com.example.ui.DailyRegisterViewModel
import com.example.ui.MasoorBillingApp
import com.example.ui.MasoorBillingViewModel
import com.example.ui.theme.MasoorBillingTheme
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent BEFORE setContent, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MasoorBillingTheme {
                val billingVm: MasoorBillingViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) {
                                "APPLICATION_KEY missing from CreationExtras"
                            }
                            val db = AppDatabase.getInstance(app)
                            val databaseId = app.getString(R.string.firestore_database_id)
                            val firestore = FirebaseFirestore.getInstance(databaseId)
                            MasoorBillingViewModel(
                                localRepo = LocalBillingRepository(db),
                                cloudRepo = CloudBackupRepository(firestore),
                                localRegisterRepo = LocalRegisterRepository(db)
                            )
                        }
                    }
                )
                val registerVm: DailyRegisterViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) {
                                "APPLICATION_KEY missing from CreationExtras"
                            }
                            val db = AppDatabase.getInstance(app)
                            DailyRegisterViewModel(
                                localRepo = LocalRegisterRepository(db),
                                ocrRepo = RegisterOcrRepository()
                            )
                        }
                    }
                )
                MasoorBillingApp(
                    viewModel = billingVm,
                    dailyRegisterViewModel = registerVm
                )
            }
        }
    }
}
