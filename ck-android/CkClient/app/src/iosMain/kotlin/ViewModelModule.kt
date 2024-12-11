import com.dobby.logs.CopyLogsInteractor
import com.dobby.logs.LogsRepository
import com.dobby.logs.LogsViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val logsModule = module {
    factory { LogsRepository() }
    factory { CopyLogsInteractor() }
    singleOf(::LogsViewModel)
}
