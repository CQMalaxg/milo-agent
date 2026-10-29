package com.malaxg.miloagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.malaxg.milo.core.designsystem.theme.MiloagentTheme
import com.malaxg.milo.feature.ledger.LedgerRoute

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as MiloApp).ledgerRepository
        setContent {
            MiloagentTheme {
                LedgerRoute(repository = repository)
            }
        }
    }
}
