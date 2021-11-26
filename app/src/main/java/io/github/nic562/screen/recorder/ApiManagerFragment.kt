package io.github.nic562.screen.recorder

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceDataStore
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import io.github.nic562.screen.recorder.base.BaseFragment
import io.github.nic562.screen.recorder.databinding.FragmentApiManagerBinding
import io.github.nic562.screen.recorder.db.ApiInfo

/**
 * Api管理
 */
class ApiManagerFragment : BaseFragment(), View.OnClickListener {

    private var _binding: FragmentApiManagerBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private val apiList: ArrayList<ApiInfo> = arrayListOf()
    private val adapter: ApiInfoAdapter by lazy {
        ApiInfoAdapter(apiList, getPreference(), this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApiManagerBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        apiList.clear()
        apiList.addAll(getDB().apiInfoDao.queryBuilder().list())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            rvApiList.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            rvApiList.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.api_info_manager, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_add -> {
                openForm(-1L)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openForm(id: Long) {
        findNavController().navigate(
            R.id.action_apiManagerFragment_to_apiFormFragment,
            Bundle().apply {
                putLong("id", id)
            })
    }

    private class ApiInfoHolder(
        v: View,
        onClickListener: View.OnClickListener
    ) : RecyclerView.ViewHolder(v) {
        val root = v
        val tvTitle: TextView = v.findViewById(R.id.tv_title)
        val swChecked: SwitchMaterial = v.findViewById(R.id.sw_checked)

        init {
            swChecked.setOnClickListener(onClickListener)
            root.setOnClickListener(onClickListener)
        }
    }

    private class ApiInfoAdapter(
        val data: List<ApiInfo>,
        val preference: PreferenceDataStore,
        val onClickListener: View.OnClickListener
    ) :
        RecyclerView.Adapter<ApiInfoHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApiInfoHolder {
            val v =
                LayoutInflater.from(parent.context).inflate(R.layout.layout_item_api, parent, false)
            return ApiInfoHolder(v, onClickListener)
        }

        override fun onBindViewHolder(holder: ApiInfoHolder, position: Int) {
            val d = data[position]
            holder.let {
                it.tvTitle.text = d.title
                it.swChecked.isChecked = d.id == preference.getLong("checkApiID", -1)
                it.swChecked.tag = d.id
                it.root.tag = d.id
            }
        }

        override fun getItemCount(): Int {
            return data.size
        }

    }

    override fun onClick(v: View?) {
        v?.apply {
            if (this is SwitchMaterial) {
                try {
                    val id: Long = (this.tag ?: return) as Long
                    val oldID = getPreference().getLong("checkApiID", -1)
                    getPreference().putLong("checkApiID", if (this.isChecked) id else -1)
                    if (id != oldID) {
                        for (i in 0 until apiList.size) {
                            if (apiList[i].id == oldID) {
                                adapter.notifyItemChanged(i)
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.warning("unHandle error: $e")
                }
            } else if (id == R.id.item_api_info) {
                openForm((this.tag ?: return) as Long)
            }
        }
    }
}