package com.wireguard.android.fragment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.os.BundleCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.databinding.DeployConfigFragmentBinding
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.android.model.SinfoniaTier3
import com.wireguard.android.util.ErrorMessages
import com.wireguard.android.viewmodel.ConfigProxy
import com.wireguard.android.viewmodel.SinfoniaProxy
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeployConfigFragment : BaseFragment() {
    private var binding: DeployConfigFragmentBinding? = null
    private var tunnel: ObservableTunnel? = null

    private fun onConfigLoaded(config: Config) {
        Log.i(TAG, "onConfigLoaded")
        binding?.sinfonia?.deployment?.tunnelConfig = ConfigProxy(config)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.i(TAG, "onCreateView")
        super.onCreateView(inflater, container, savedInstanceState)
        binding = DeployConfigFragmentBinding.inflate(inflater, container, false)
        binding?.executePendingBindings()
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.i(TAG, "onViewCreated")
        if (savedInstanceState == null) {
            val activity = requireActivity()
            val sinfonia = SinfoniaTier3(
                    url = "https://cmu.findcloudlet.org",
                    applicationName = "helloworld",
                    zeroconf = false,
                    application = listOf("com.android.chrome")
            )
            activity.lifecycleScope.launch(Dispatchers.IO) {
                binding?.sinfonia = SinfoniaProxy(sinfonia.deploy())
                addOnClickListener(view)
            }
        } else {
            addOnClickListener(view)
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        Log.i(TAG, "onViewStateRestored")
        binding ?: return
        binding!!.fragment = this
        if (savedInstanceState == null) {
            onSelectedTunnelChanged(null, selectedTunnel)
        } else {
            tunnel = selectedTunnel
            val sinfonia = BundleCompat.getParcelable(savedInstanceState, KEY_LOCAL_CONFIG, SinfoniaProxy::class.java)!!
            val originalName = savedInstanceState.getString(KEY_ORIGINAL_NAME)
            if (tunnel != null && tunnel!!.name != originalName) onSelectedTunnelChanged(null, tunnel) else binding!!.sinfonia = sinfonia
        }
        super.onViewStateRestored(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.i(TAG, "onSaveInstanceState")
        if (binding != null) outState.putParcelable(KEY_LOCAL_CONFIG, binding!!.sinfonia)
        outState.putString(KEY_ORIGINAL_NAME, if (tunnel == null) null else tunnel!!.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        Log.i(TAG, "onDestroyView")
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = null
        super.onDestroyView()
    }

    private fun onTunnelCreated(newTunnel: ObservableTunnel?, throwable: Throwable?) {
        Log.i(TAG, "onTunnelCreated")
        val ctx = activity ?: Application.get()
        if (throwable == null) {
            tunnel = newTunnel
            val message = ctx.getString(R.string.tunnel_create_success, tunnel!!.name)
            Log.d(TAG, message)
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            onFinished()
        } else {
            val error = ErrorMessages[throwable]
            val message = ctx.getString(R.string.tunnel_create_error, error)
            Log.e(TAG, message, throwable)
            val binding = binding
            if (binding != null)
                Snackbar.make(binding.applicationDetailCard, message, Snackbar.LENGTH_LONG).show()
            else
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }
    private fun onFinished() {
        Log.i(TAG, "onFinished")
        // Hide the keyboard; it rarely goes away on its own.
        val activity = activity ?: return
        val focusedView = activity.currentFocus
        if (focusedView != null) {
            val inputManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            inputManager?.hideSoftInputFromWindow(
                    focusedView.windowToken,
                    InputMethodManager.HIDE_NOT_ALWAYS
            )
        }
        parentFragmentManager.popBackStackImmediate()

        // If we just made a new one, save it to select the details page.
        if (selectedTunnel != tunnel)
            selectedTunnel = tunnel
    }

    override fun onSelectedTunnelChanged(
            oldTunnel: ObservableTunnel?,
            newTunnel: ObservableTunnel?
    ) {
        Log.i(TAG, "onSelectedTunnelChanged")
        tunnel = newTunnel
        if (binding == null) return
        binding!!.sinfonia?.deployment?.tunnelConfig = ConfigProxy()
        if (tunnel != null) {
            binding!!.name = tunnel!!.name
            lifecycleScope.launch {
                try {
                    onConfigLoaded(tunnel!!.getConfigAsync())
                } catch (_: Throwable) {
                }
            }
        } else {
            binding!!.name = ""
        }
        binding!!.tunnel = tunnel
    }

    fun onLaunchClicked(view: View, checked: Boolean) {
        val binding = binding ?: return
        val sinfonia = binding.sinfonia ?: return
        Log.i(TAG, "onLaunchClicked: $checked")
        val newConfig = try {
            sinfonia.deployment?.resolve()?.tunnelConfig
        } catch (e: Throwable) {
            val error = ErrorMessages[e]
            val tunnelName = if (tunnel == null) binding.name else tunnel!!.name
            val message = getString(R.string.config_save_error, tunnelName, error)
            Log.e(TAG, message, e)
            Snackbar.make(binding.applicationDetailCard, error, Snackbar.LENGTH_LONG).show()
            return
        }

        // Set the tunnel name
        binding.name = sinfonia.applicationName

        val activity = requireActivity()
        activity.lifecycleScope.launch {
            Log.d(TAG, "Attempting to create new tunnel " + binding.name)
            val manager = Application.getTunnelManager()
            try {
                onTunnelCreated(manager.create(binding.name!!, newConfig), null)
            } catch (e: Throwable) {
                onTunnelCreated(null, e)
                return@launch
            }
            setTunnelState(view, true)
            try {
                sinfonia.application.forEach { application: String -> launchApplication(application) }
            } catch (e: Throwable) {
                val error = ErrorMessages[e]
                Log.e(TAG, "Cannot launch application: ${binding.name}", e)
                Snackbar.make(binding.applicationDetailCard, error, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun addOnClickListener(view: View) {
        view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val launchButton = view.findViewById(R.id.deployment_button) as MaterialButton?
                        ?: return
                launchButton.setOnClickListener { onLaunchClicked(view, true) }
            }
        })
    }

    private fun launchApplication(application: String) {
        val packageManager = Application.get().packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(application)
        if (launchIntent != null) startActivity(launchIntent) else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(application)))
    }

    companion object {
        private const val KEY_LOCAL_CONFIG = "local_config"
        private const val KEY_ORIGINAL_NAME = "original_name"
        private const val TAG = "WireGuard/DeployConfigFragment"
    }
}