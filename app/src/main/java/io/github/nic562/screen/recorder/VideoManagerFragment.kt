package io.github.nic562.screen.recorder

import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.FileProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import io.github.nic562.screen.recorder.base.BaseFragment
import io.github.nic562.screen.recorder.base.SomethingWithImageLoader
import io.github.nic562.screen.recorder.databinding.FragmentVideoManagerBinding
import io.github.nic562.screen.recorder.db.VideoInfo
import io.github.nic562.screen.recorder.tools.DateUtil
import java.io.File

class VideoManagerFragment : BaseFragment(), View.OnClickListener {
    private var _binding: FragmentVideoManagerBinding? = null
    private val binding get() = _binding!!
    private val videoList: ArrayList<VideoInfo> = arrayListOf()
    private val uploadStatus = hashMapOf<Long, Int>()
    private val adapter: VideoInfoAdapter by lazy {
        VideoInfoAdapter(videoList, uploadStatus, this)
    }

    private val svIntent by lazy {
        Intent(requireContext(), UploadService::class.java)
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra("id", -1L)
            when (val act = intent?.getStringExtra("action")) {
                "start" -> {
                    id?.let {
                        uploadStatus[it] = 0
                        notifyVideoItemChangedByID(it)
                    }
                }
                "progress" -> {
                    id?.let {
                        uploadStatus[it] = intent.getIntExtra("progress", 0)
                        notifyVideoItemChangedByID(it)
                    }
                }
                "finish" -> {
                    id?.let {
                        uploadStatus[it] = -2
                        notifyVideoItemChangedByID(it)
                    }
                }
                "error" -> {
                    id?.let {
                        uploadStatus.remove(it)
                        notifyVideoItemChangedByID(it)
                    }
                    intent.getStringExtra("error")?.let {
                        findNavController().navigate(
                            R.id.action_videoManagerFragment_to_logFragment,
                            Bundle().apply {
                                putString("log", it)
                            })
                    }
                }
                else -> {
                    logger.warning("unKnow broadcastReceiver action: $act")
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        videoList.clear()
        videoList.addAll(getDB().videoInfoDao.queryBuilder().list())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            rvVideoList.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            rvVideoList.adapter = adapter
        }
    }

    override fun onResume() {
        super.onResume()
        IntentFilter().apply {
            addAction(getString(R.string.broadcast_receiver_action_upload_manager))
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

    override fun onDestroy() {
        super.onDestroy()
    }

    private class VideoInfoHolder(
        v: View,
        onClickListener: View.OnClickListener
    ) : RecyclerView.ViewHolder(v) {
        val root = v
        val ivPreview: AppCompatImageView = v.findViewById(R.id.iv_preview)
        val tvTitle: TextView = v.findViewById(R.id.tv_title)
        val tvUploadStatus: TextView = v.findViewById(R.id.tv_upload_status)
        val ivDelete: AppCompatImageView = v.findViewById(R.id.iv_delete)
        val ivUpload: AppCompatImageView = v.findViewById(R.id.iv_upload)
        val progressBar: ProgressBar = v.findViewById(R.id.pb_upload)

        init {
            root.setOnClickListener(onClickListener)
            ivDelete.setOnClickListener(onClickListener)
            ivUpload.setOnClickListener(onClickListener)
        }
    }

    private class VideoInfoAdapter(
        val data: List<VideoInfo>,
        val upStatus: Map<Long, Int>,
        val onClickListener: View.OnClickListener
    ) :
        RecyclerView.Adapter<VideoInfoHolder>(), SomethingWithImageLoader {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoInfoHolder {
            val v =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.layout_item_video, parent, false)
            return VideoInfoHolder(v, onClickListener)
        }

        override fun onBindViewHolder(holder: VideoInfoHolder, position: Int) {
            val d = data[position]
            holder.let {
                it.root.tag = d.filePath
                it.tvTitle.text = DateUtil.dateTimeToStr(d.createTime.time)
                loadImage(
                    it.ivPreview,
                    d.previewPath,
                    onErrorImgID = R.drawable.ic_baseline_broken_image_24
                )
                it.ivUpload.tag = d
                it.ivDelete.tag = d.id
                val p = upStatus[d.id]
                it.tvUploadStatus.text = null
                if (p == null) {
                    it.progressBar.visibility = View.GONE
                } else {
                    when (p) {
                        -1 -> {
                            it.progressBar.apply {
                                isIndeterminate = true
                                visibility = View.VISIBLE
                            }
                            it.tvUploadStatus.setText(R.string.upload_wait)
                        }
                        -2 -> {
                            it.tvUploadStatus.setText(R.string.upload_finish)
                            it.progressBar.visibility = View.GONE
                        }
                        else -> {
                            it.progressBar.apply {
                                isIndeterminate = false
                                visibility = View.VISIBLE
                                progress = p
                            }
                        }
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return data.size
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.root -> {
                val fp = v.tag as String
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().applicationContext.packageName}.provider",
                    File(fp)
                )
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setDataAndType(uri, "video/*")
                })
            }
            R.id.iv_upload -> {
                val vd = v.tag as VideoInfo
                val st = uploadStatus[vd.id]
                if (st != null && st != -2) {
                    toast(getString(R.string.uploading_wait))
                    return
                }
                openApiDialog(vd)
            }
            R.id.iv_delete -> {
                Snackbar.make(
                    binding.root,
                    R.string.confirm2delete_video,
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.sure) {
                    val id = v.tag as Long
                    for (p in 0 until videoList.size) {
                        val d = videoList[p]
                        if (d.id == id) {
                            File(d.filePath).delete()
                            File(d.previewPath).delete()
                            getDB().videoInfoDao.delete(d)
                            videoList.removeAt(p)
                            adapter.notifyItemRemoved(p)
                        }
                    }
                }.show()
            }
        }
    }

    private fun openApiDialog(videoInfo: VideoInfo) {
        val defID = Config.getDefaultApiID()
        val api = getDB().apiInfoDao.loadAll()
        if (api.size == 0) {
            toast(getString(R.string.no_api))
            return
        }
        val names = arrayListOf<String>()
        for (x in api) {
            if (x.id == defID) {
                names.add("${x.title}(默认)")
            } else {
                names.add(x.title)
            }
        }
        AlertDialog.Builder(requireContext()).setTitle(R.string.select_an_upload_api)
            .setSingleChoiceItems(StringArrayAdapter(requireContext(), names), 0) { dialog, which ->
                val apiInfo = api[which]
                dialog.dismiss()
                requireActivity().startService(svIntent.apply {
                    putExtra("apiID", apiInfo.id)
                    putExtra("videoID", videoInfo.id)
                })
                uploadStatus[videoInfo.id] = -1
                notifyVideoItemChangedByID(videoInfo.id)
            }
            .show()
    }

    private fun notifyVideoItemChangedByID(id: Long) {
        findVideoIndexByID(id).let { idx ->
            if (idx >= 0) {
                adapter.notifyItemChanged(idx)
            }
        }
    }

    private fun findVideoIndexByID(id: Long): Int {
        for (i in 0 until videoList.size) {
            if (videoList[i].id == id) {
                return i
            }
        }
        return -1
    }
}