package app.yapper.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import app.yapper.YapperApp
import app.yapper.data.YapperRepository

fun Context.yapperRepository(): YapperRepository {
    return (applicationContext as YapperApp).repository
}

@Composable
inline fun <reified VM : ViewModel> yapperViewModel(
    key: String? = null,
    crossinline factory: (YapperRepository) -> VM,
): VM {
    val repository = LocalContext.current.yapperRepository()
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = build()

            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = build()

            private fun <T : ViewModel> build(): T {
                @Suppress("UNCHECKED_CAST")
                return factory(repository) as T
            }
        },
    )
}
