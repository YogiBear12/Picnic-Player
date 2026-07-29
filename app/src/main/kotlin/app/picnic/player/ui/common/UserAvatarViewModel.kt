package app.picnic.player.ui.common

import androidx.lifecycle.ViewModel
import app.picnic.player.data.auth.ActiveUserAvatar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Exposes the shared [ActiveUserAvatar] to screens that have no view model of their own. */
@HiltViewModel
class UserAvatarViewModel @Inject constructor(
    activeUserAvatar: ActiveUserAvatar
) : ViewModel() {
    val url: StateFlow<String?> = activeUserAvatar.url
}
