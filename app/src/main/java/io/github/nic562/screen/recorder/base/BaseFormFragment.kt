package io.github.nic562.screen.recorder.base

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputLayout
import io.github.nic562.screen.recorder.R

abstract class BaseFormFragment : BaseFragment() {

    protected class StringArrayAdapter(context: Context, array: ArrayList<String>) :
        ArrayAdapter<String>(context, R.layout.layout_popup_item, array)

    protected fun setOnInputChangeSetNullError(et: EditText, til: TextInputLayout) {
        et.apply {
            addTextChangedListener {
                if (it != null) {
                    til.error = null
                }
            }
        }
    }

    protected open fun submitForm(requireList: List<Pair<EditText, TextInputLayout>>): Boolean {
        var ok = true
        for (x in requireList) {
            if (x.first.text.isNullOrBlank()) {
                ok = false
                x.second.error = getString(R.string.input_require)
            }
        }
        return ok
    }
}