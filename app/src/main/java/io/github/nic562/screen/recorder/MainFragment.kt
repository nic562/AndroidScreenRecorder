package io.github.nic562.screen.recorder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.*
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import io.github.nic562.screen.recorder.base.BaseFragment
import io.github.nic562.screen.recorder.databinding.FragmentMainBinding

/**
 * Created by Nic on 2021/11/27.
 */
class MainFragment : BaseFragment(), View.OnClickListener {
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val reqCodeAccessibility = 101
    private val reqCodeNotificationSetting = 201
    private val reqCodePermission = 301

    private val uploadBroadcastAction by lazy {
        getString(R.string.broadcast_receiver_action_upload_manager)
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                uploadBroadcastAction -> {
                    when (val act = intent.getStringExtra("action")) {
                        "start" -> {
                            toast(getString(R.string.upload_wait))
                        }
                        "progress" -> {
                        }
                        "finish" -> {
                        }
                        "error" -> {
                            intent.getStringExtra("error")?.let {
                                findNavController().navigate(
                                    R.id.action_mainManagerFragment_to_logFragment,
                                    Bundle().apply {
                                        putString("log", it)
                                    })
                            }
                        }
                        else -> {
                            logger.warning("unKnow broadcastReceiver action for [${uploadBroadcastAction}]: $act")
                        }
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.let {
            it.btnRecordStart.setOnClickListener(this)
            it.btnRecordStop.setOnClickListener(this)
            it.btnVideo.setOnClickListener(this)
            it.btnUploadApi.setOnClickListener(this)
            it.btnAccessibility.setOnClickListener(this)
            getMainActivity().recordStatusViewModel.recordingEvent.observe(viewLifecycleOwner) { recording ->
                if (recording) {
                    it.btnRecordStart.visibility = View.GONE
                    it.btnRecordStop.visibility = View.VISIBLE
                } else {
                    it.btnRecordStart.visibility = View.VISIBLE
                    it.btnRecordStop.visibility = View.GONE
                }
            }
        }
    }

    override fun onBackPressed(): Boolean {
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_main, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        IntentFilter().apply {
            addAction(uploadBroadcastAction)
            requireActivity().registerReceiver(broadcastReceiver, this)
        }
    }

    override fun onPause() {
        requireActivity().unregisterReceiver(broadcastReceiver)
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btn_record_start -> {
                if (requireActivity()
                        .checkSelfPermission(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ),
                        reqCodePermission
                    )
                    return
                }
                if (getRecordService()?.isNotificationEnable() == false) {
                    Snackbar.make(
                        binding.root,
                        R.string.confirm2enable_notification,
                        Snackbar.LENGTH_LONG
                    ).setAction(R.string.sure) {
                        Intent().apply {
                            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                            putExtra(
                                Settings.EXTRA_CHANNEL_ID,
                                requireActivity().applicationInfo.uid
                            )
                            startActivityForResult(this, reqCodeNotificationSetting)
                        }
                    }.addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            if (event != 1) {
                                // 设置了1个Action的话，该Action占用的event为1. 则2为超时自动关闭
                                getMainActivity().requestRecording()
                            }
                            super.onDismissed(transientBottomBar, event)
                        }
                    }).show()
                } else {
                    getMainActivity().requestRecording()
                }
            }
            R.id.btn_record_stop -> {
                getRecordService()?.stop()
            }
            R.id.btn_upload_api -> {
                findNavController().navigate(
                    R.id.action_mainFragment_to_apiManagerFragment
                )
            }
            R.id.btn_video -> {
                findNavController().navigate(
                    R.id.action_mainFragment_to_videoManagerFragment
                )
            }
            R.id.btn_accessibility -> {
                startActivityForResult(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                    reqCodeAccessibility
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            reqCodeNotificationSetting -> {
                getMainActivity().requestRecording()
            }
            reqCodeAccessibility -> {
                getMainActivity().openAccessibilityService()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            reqCodePermission -> {
                var ok = 0
                for (x in grantResults) {
                    if (x == PackageManager.PERMISSION_GRANTED) {
                        ok += 1
                    }
                }
                if (ok == 2) {
                    binding.btnRecordStart.performClick()
                }
            }
            else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

    private fun getMainActivity(): MainActivity {
        return (requireActivity() as MainActivity)
    }

    private fun getRecordService(): MediaRecordService? {
        return getMainActivity().getRecordService()
    }
}