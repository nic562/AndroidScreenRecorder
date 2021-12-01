package io.github.nic562.screen.recorder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.github.nic562.screen.recorder.base.BaseFragment
import io.github.nic562.screen.recorder.databinding.FragmentLogBinding

class LogFragment : BaseFragment() {
    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        handleBundle(requireArguments())
    }

    private fun handleBundle(data: Bundle) {
        val log = data.getString("log")
        binding.tvLog.text = log
    }
}