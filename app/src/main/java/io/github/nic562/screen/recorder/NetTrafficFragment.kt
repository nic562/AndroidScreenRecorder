package io.github.nic562.screen.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import io.github.nic562.screen.recorder.base.BaseFragment
import io.github.nic562.screen.recorder.base.SomethingWithNotification
import io.github.nic562.screen.recorder.databinding.FragmentNetTrafficBinding

class NetTrafficFragment : BaseFragment(), View.OnClickListener,
    SomethingWithNetTrafficStatistics,
    SomethingWithNotification {
    private var _binding: FragmentNetTrafficBinding? = null
    private val binding get() = _binding!!
    private val reqCodeNotificationSetting = 101
    private val reqCodeFilePermissionSetting = 102

    override val notificationChannel: String = "test" // 本Fragment暂时不发送通知，因此暂时无用
    override val notificationManager: NotificationManagerCompat by lazy {
        initNotificationManager()
    }
    override val notificationBuilder: NotificationCompat.Builder by lazy {
        initNotificationBuilder()
    }
    private var isStart = false
    private var network = ""
    private val chooseAppPkgList = hashSetOf<String>()
    private val chooseAppNames = hashSetOf<String>()

    private val broadcastAction by lazy {
        getString(R.string.broadcast_receiver_action_net_traffic_statistics)
    }
    private val broadcastReceiver by lazy {
        createNetTrafficStatisticsBroadcastReceiver(getNetTrafficStatisticsBroadcastAction())
    }

    private val adapter: PackageInfoAdapter by lazy {
        PackageInfoAdapter(requireActivity().packageManager, this)
    }

    override fun getContext(): Context {
        return requireActivity()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNetTrafficBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.let {
            it.btnStart.setOnClickListener(this)
            it.rvAppList.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            it.rvAppList.adapter = adapter
            it.tvApps.setOnClickListener(this)
            it.btnPermission.setOnClickListener(this)
            it.etSearch.addTextChangedListener { et ->
                val searchText = et?.toString()?.trim() ?: ""
                adapter.search(searchText)
            }
            checkingFilePermission()
        }
    }

    override fun onNetStatisticsCreate() {
        isStart = true
        binding.tvStatus.setText(R.string.is_idle)
        binding.btnStart.setText(R.string.stop)
    }

    override fun onNetStatisticsDestroy() {
        isStart = false
        binding.tvStatus.setText(R.string.not_start)
        binding.btnStart.setText(R.string.net_traffic_start)
    }

    override fun onNetStatisticsWorking(network: String, downByteSize: Long, uploadByteSize: Long) {
        val downByteSize = getString(
            R.string.download_speed,
            downByteSize / 1024.0
        )
        val upByteSize = getString(
            R.string.upload_speed,
            uploadByteSize / 1024.0
        )
        this.network = network
        isStart = true
        binding.tvStatus.text = "$downByteSize - $upByteSize"
        binding.btnStart.setText(R.string.stop)
    }

    override fun onResume() {
        super.onResume()
        IntentFilter().apply {
            addAction(broadcastAction)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (onNetTrafficStatisticsActivityResult(requestCode, resultCode, data)) {
            return
        }
        when (requestCode) {
            reqCodeNotificationSetting -> {
                openNetworkStatisticsService()
                return
            }
            reqCodeFilePermissionSetting -> {
                checkingFilePermission()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun notify(msg: String) {
        TODO("Not yet implemented")
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btn_start -> {
                if (isStart) {
                    openNetworkStatisticsService()
                    return
                }
                checkNotificationEnable(binding.root, {
                    startActivityForResult(
                        getNotificationSettingsIntent(),
                        reqCodeNotificationSetting
                    )
                }, object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (event != 1) {
                            openNetworkStatisticsService()
                        }
                        super.onDismissed(transientBottomBar, event)
                    }
                }).apply {
                    if (this) {
                        openNetworkStatisticsService()
                    }
                }
            }
            R.id.item_root -> {
                if (isStart) {
                    Snackbar.make(binding.root, R.string.please_stop_first, Snackbar.LENGTH_LONG)
                        .show()
                } else {
                    updateSelectedApp(v.tag as Int)
                }
            }
            R.id.tv_apps -> {
                Snackbar.make(binding.root, R.string.confirm2remove_all, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.sure) {
                        chooseAppNames.clear()
                        chooseAppPkgList.clear()
                        binding.tvApps.setText(R.string.choose_apps)
                    }.show()
            }
            R.id.btn_permission -> {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)
                    startActivityForResult(
                        Intent(ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                        reqCodeFilePermissionSetting
                    )
            }
        }
    }

    private fun checkingFilePermission() {
        binding.btnPermission.visibility =
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q && !Environment.isExternalStorageManager()) View.VISIBLE else View.GONE
    }

    private fun openNetworkStatisticsService() {
        if (isStart) {
            binding.btnStart.setText(R.string.net_traffic_start)
            isStart = false
            stopStatisticsService()
        } else {
            startStatisticsService(chooseAppPkgList.toList())
        }
    }

    private fun updateSelectedApp(idx: Int) {
        val info = adapter.getDataAt(idx)
        val pkg = info.packageName
        val name =
            info.loadLabel(requireActivity().packageManager).toString()
        if (chooseAppPkgList.contains(pkg)) {
            chooseAppPkgList.remove(pkg)
            chooseAppNames.remove(name)
        } else {
            chooseAppPkgList.add(pkg)
            chooseAppNames.add(name)
        }
        if (chooseAppNames.size > 0)
            binding.tvApps.text = chooseAppNames.joinToString("\n")
        else
            binding.tvApps.setText(R.string.choose_apps)
    }

    private class PackageInfoHolder(v: View, onClickListener: View.OnClickListener) :
        RecyclerView.ViewHolder(v) {
        companion object {
            fun init(parent: ViewGroup, onClickListener: View.OnClickListener): PackageInfoHolder {
                return PackageInfoHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.layout_item_app, parent, false),
                    onClickListener
                )
            }
        }

        val root = v
        val ivIcon: ImageView = v.findViewById(R.id.iv_icon)
        val tvName: TextView = v.findViewById(R.id.tv_name)
        val tvPkg: TextView = v.findViewById(R.id.tv_pkg)

        init {
            root.setOnClickListener(onClickListener)
        }
    }

    private class PackageInfoAdapter(
        val packageManager: PackageManager,
        val onClickListener: View.OnClickListener
    ) : RecyclerView.Adapter<PackageInfoHolder>() {
        private val rawData: List<ApplicationInfo> by lazy {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        }
        private val data: ArrayList<ApplicationInfo> by lazy {
            ArrayList(rawData)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackageInfoHolder {
            return PackageInfoHolder.init(parent, onClickListener)
        }

        override fun onBindViewHolder(holder: PackageInfoHolder, position: Int) {
            val d = data[position]
            holder.apply {
                tvName.text = d.loadLabel(packageManager)
                tvPkg.text = d.packageName
                ivIcon.setImageDrawable(d.loadIcon(packageManager))
                root.tag = position
            }
        }

        override fun getItemCount(): Int {
            return data.size
        }

        fun getDataAt(position: Int): ApplicationInfo {
            return data[position]
        }

        @SuppressLint("NotifyDataSetChanged")
        fun search(name: String) {
            data.clear()
            if (name.isBlank()) {
                data.addAll(rawData)
            } else {
                var an: String
                for (a in rawData) {
                    an = a.loadLabel(packageManager).toString()
                    if (an.indexOf(name) != -1 || a.packageName.indexOf(name) != -1) {
                        data.add(a)
                    }
                }
            }
            notifyDataSetChanged()
        }
    }
}