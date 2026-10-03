package io.homeassistant.companion.android.settings.customheaders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Fragment wrapper of [CustomHeadersScreen] to be used from the Fragment based server settings.
 * Pass the `serverId` in the arguments through [newInstance].
 */
@AndroidEntryPoint
internal class CustomHeadersFragment : Fragment() {

    companion object {
        private const val EXTRA_SERVER = "server_id"

        fun newInstance(serverId: Int) = CustomHeadersFragment().apply {
            arguments = bundleOf(EXTRA_SERVER to serverId)
        }
    }

    private val viewModel: CustomHeadersViewModel by viewModels(
        extrasProducer = {
            val serverId =
                arguments?.getInt(EXTRA_SERVER, ServerManager.SERVER_ID_ACTIVE) ?: ServerManager.SERVER_ID_ACTIVE
            defaultViewModelCreationExtras.withCreationCallback<CustomHeadersViewModelFactory> { it.create(serverId) }
        },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HATheme {
                    CustomHeadersScreen(
                        viewModel = viewModel,
                        onBackClick = ::close,
                        modifier = Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                        ),
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.events
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { if (it == CustomHeadersEvent.Saved) close() }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun close() {
        parentFragmentManager.popBackStack()
    }
}
