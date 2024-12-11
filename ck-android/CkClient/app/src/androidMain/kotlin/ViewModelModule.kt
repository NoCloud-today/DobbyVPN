import com.dobby.logs.CopyLogsInteractor
import com.dobby.logs.LogsRepository
import com.dobby.logs.LogsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

actual val logsModule = module {
    factory {
        LogsRepository(fileDirProvider = { androidContext().filesDir })
    }
    factory {
        CopyLogsInteractor(context = androidContext())
    }
    viewModelOf(::LogsViewModel)
}
