package io.github.nic562.screen.recorder

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import io.github.nic562.screen.recorder.base.BaseFragment
import io.github.nic562.screen.recorder.databinding.FragmentMainBinding
import io.github.nic562.screen.recorder.db.VideoInfo
import io.github.nic562.screen.recorder.tools.Video
import java.io.File
import java.lang.Exception
import java.util.*

/**
 * Created by Nic on 2021/11/27.
 */
class MainFragment : BaseFragment(), View.OnClickListener {
    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val reqCodeScreenRecord = 101
    private val reqCodeNotificationSetting = 201
    private val reqCodePermission = 301

    private val dm by lazy {
        resources.displayMetrics
    }

    private val mediaProjectionManager: MediaProjectionManager by lazy {
        requireContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var sv: MediaRecordService? = null
    private val svCallback = object : MediaRecordService.Callback {
        override fun onRecordStart() {
            binding.btnRecordStart.visibility = View.GONE
            binding.btnRecordStop.visibility = View.VISIBLE
        }

        override fun onRecordError(e: Throwable) {
            Snackbar.make(
                binding.root,
                e.localizedMessage ?: e.message ?: e.toString(),
                Snackbar.LENGTH_LONG
            ).show()
        }

        override fun onRecordStop(dstVideoPath: String?) {
            binding.btnRecordStop.visibility = View.GONE
            binding.btnRecordStart.visibility = View.VISIBLE
            dstVideoPath?.let {
                val img = File(
                    requireActivity().getExternalFilesDir("img"),
                    "${System.currentTimeMillis()}.jpg"
                )
                val pv: File? = try {
                    Video.getThumb2File(dstVideoPath, img.absolutePath)
                        ?: throw Exception("return null File")
                } catch (e: Exception) {
                    logger.severe("[${dstVideoPath}] is an invalid video file? Get thumb Error: $e")
                    null
                }
                pv?.let { previewFile ->
                    VideoInfo().apply {
                        createTime = Date()
                        filePath = it
                        previewPath = previewFile.absolutePath
                        getDB().videoInfoDao.insert(this)
                    }
                }

            }
        }

    }

    private val svIntent by lazy {
        Intent(requireContext(), MediaRecordService::class.java)
    }

    private val svConn by lazy {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                sv = (service as MediaRecordService.SvBinder).getService().apply {
                    setCallback(svCallback)
                    checkRecorderStatus()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                sv?.stop()
                sv = null
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
        }

        requireActivity().bindService(svIntent, svConn, Context.BIND_AUTO_CREATE)
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

    override fun onStart() {
        super.onStart()
        checkRecorderStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        requireActivity().unbindService(svConn)
        super.onDestroy()
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
                if (sv?.isNotificationEnable() == false) {
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
                                requestRecording()
                            }
                            super.onDismissed(transientBottomBar, event)
                        }
                    }).show()
                } else {
                    requestRecording()
                }
            }
            R.id.btn_record_stop -> {
                sv?.stop()
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
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            reqCodeNotificationSetting -> {
                requestRecording()
            }
            reqCodeScreenRecord -> {
                if (resultCode == AppCompatActivity.RESULT_OK) {
                    data?.let {
                        val file = File(
                            requireActivity().getExternalFilesDir("screen"),
                            "${System.currentTimeMillis()}.mp4"
                        )
                        svIntent.apply {
                            putExtra("width", dm.widthPixels)
                            putExtra("height", dm.heightPixels)
                            putExtra("dpi", dm.densityDpi)
                            putExtra("dstPath", file.absolutePath)
                            putExtra("resultCode", resultCode)
                            putExtra("data", it)
                            requireActivity().startForegroundService(this)
                        }
                    }
                }
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

    private fun checkRecorderStatus() {
        if (sv?.isRecording() == true) {
            svCallback.onRecordStart()
        } else {
            svCallback.onRecordStop(null)
        }
    }

    private fun requestRecording() {
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, reqCodeScreenRecord)
    }
}