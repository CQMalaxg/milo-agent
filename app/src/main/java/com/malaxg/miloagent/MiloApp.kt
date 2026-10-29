package com.malaxg.miloagent

import android.app.Application
import com.malaxg.milo.core.data.MiloRepositoryFactory
import com.malaxg.milo.core.domain.repository.LedgerRepository

/**
 * 手动 DI 容器。
 *
 * P0 阶段模块很少，不值得引入 DI 框架；等 P2 接入 AI、模块变多后再考虑 Koin。
 * 这里刻意保持简单：一处组装，全局只读。
 */
class MiloApp : Application() {

    lateinit var ledgerRepository: LedgerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        ledgerRepository = MiloRepositoryFactory.createLedgerRepository(this)
    }
}
