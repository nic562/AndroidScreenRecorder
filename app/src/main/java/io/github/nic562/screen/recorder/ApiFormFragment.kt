package io.github.nic562.screen.recorder

import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.view.*
import android.widget.EditText
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputLayout
import io.github.nic562.screen.recorder.base.BaseFormFragment
import io.github.nic562.screen.recorder.databinding.FragmentApiFormBinding
import io.github.nic562.screen.recorder.db.ApiInfo
import java.lang.Exception

/**
 * Api表单
 */
class ApiFormFragment : BaseFormFragment() {

    private var _binding: FragmentApiFormBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var apiInfo: ApiInfo? = null

    private val arrayMethodString by lazy {
        arrayListOf<String>().apply {
            for (x in ApiInfo.Method.ALL) {
                add(x.name)
            }
        }
    }

    private lateinit var requireFieldList: List<Pair<EditText, TextInputLayout>>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApiFormBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            val ll = arrayListOf(
                etTitle to tilTitle,
                atvMethod to tilMethod,
                etUrl to tilUrl,
                etHeader to tilHeader,
                etBody to tilBody,
                etFileArg to tilFileArg
            )
            atvMethod.setAdapter(StringArrayAdapter(requireContext(), arrayMethodString))
            for (x in ll) {
                setOnInputChangeSetNullError(x.first, x.second)
            }
            ll.removeAt(3)
            ll.removeAt(3)
            requireFieldList = ll
        }

        handleBundle(requireArguments())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.api_info_details, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.action_save -> {
                if (submitForm(requireFieldList)) {
                    findNavController().navigate(R.id.action_apiFormFragment_to_apiManagerFragment)
                }
                return true
            }
            R.id.action_delete -> {
                apiInfo?.apply {
                    getDB().apiInfoDao.delete(this)
                }
                findNavController().navigate(R.id.action_apiFormFragment_to_apiManagerFragment)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun handleBundle(data: Bundle) {
        val id: Long = data.getLong("id", -1L)
        if (id <= 0) {
            return
        }
        val info = getDB().apiInfoDao.load(id) ?: return
        apiInfo = info
        binding.apply {
            etTitle.setText(info.title)
            etUrl.setText(info.url)
            atvMethod.setText(info.method.name)
            etHeader.setText(info.header)
            etBody.setText(info.body)
            etFileArg.setText(info.uploadFileArgName)
        }
    }

    override fun submitForm(requireList: List<Pair<EditText, TextInputLayout>>): Boolean {
        val rs = super.submitForm(requireList)
        if (!rs) {
            return false
        }
        val a = apiInfo ?: ApiInfo()
        binding.apply {
            a.title = etTitle.text.toString()
            a.url = etUrl.text.toString()
            a.method = ApiInfo.Method.valueOf(atvMethod.text.toString())
            a.header = etHeader.text.toString()
            a.body = etBody.text.toString()
            a.uploadFileArgName = etFileArg.text.toString()
        }
        try {
            if (a.id == null || a.id == 0L) {
                getDB().apiInfoDao.insert(a)
            } else {
                getDB().apiInfoDao.update(a)
            }
        } catch (e: SQLiteConstraintException) {
            "UNIQUE SQL error: ${e.message}".apply {
                logger.severe(this)
                toastError(this)
            }
            return false
        } catch (e: Exception) {
            "UnKnow error: ${e.message}".apply {
                logger.severe(this)
                toastError(this)
            }
            return false
        }
        return true
    }
}