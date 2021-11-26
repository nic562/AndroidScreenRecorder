package io.github.nic562.screen.recorder

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.preference.PreferenceDataStore
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.nic562.screen.recorder.base.BaseFragment
import io.github.nic562.screen.recorder.databinding.FragmentVideoManagerBinding
import io.github.nic562.screen.recorder.db.VideoInfo
import java.io.File

class VideoManagerFragment : BaseFragment(), View.OnClickListener {
    private var _binding: FragmentVideoManagerBinding? = null
    private val binding get() = _binding!!
    private val videoList: ArrayList<VideoInfo> = arrayListOf()
    private val adapter: VideoInfoAdapter by lazy {
        VideoInfoAdapter(videoList, this)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class VideoInfoHolder(
        v: View,
        onClickListener: View.OnClickListener
    ) : RecyclerView.ViewHolder(v) {
        val root = v
        val ivPreview: AppCompatImageView = v.findViewById(R.id.iv_preview)
        val tvTitle: TextView = v.findViewById(R.id.tv_title)
        val ivDelete: AppCompatImageView = v.findViewById(R.id.iv_delete)
        val ivUpload: AppCompatImageView = v.findViewById(R.id.iv_upload)

        init {
            root.setOnClickListener(onClickListener)
            ivDelete.setOnClickListener(onClickListener)
            ivUpload.setOnClickListener(onClickListener)
        }
    }

    private class VideoInfoAdapter(
        val data: List<VideoInfo>,
        val onClickListener: View.OnClickListener
    ) :
        RecyclerView.Adapter<VideoInfoHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoInfoHolder {
            val v =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.layout_item_video, parent, false)
            return VideoInfoHolder(v, onClickListener)
        }

        override fun onBindViewHolder(holder: VideoInfoHolder, position: Int) {
            val d = data[position]
            holder.let {
                it.root.tag = d.id
                it.tvTitle.text = d.createTime.toString()
                it.ivPreview.setImageURI(Uri.fromFile(File(d.filePath)))
                it.ivUpload.setOnClickListener(onClickListener)
                it.ivDelete.setOnClickListener(onClickListener)
                it.ivDelete.tag = position
            }
        }

        override fun getItemCount(): Int {
            return data.size
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.iv_upload -> {

            }
            R.id.iv_delete -> {
                val p = v.tag as Int
                val d = videoList[p]
                getDB().videoInfoDao.delete(d)
                videoList.removeAt(p)
                adapter.notifyItemRemoved(p)
            }
        }
    }
}