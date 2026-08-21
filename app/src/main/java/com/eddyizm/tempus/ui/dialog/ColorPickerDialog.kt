package com.eddyizm.tempus.ui.dialog

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.core.graphics.drawable.toDrawable
import com.eddyizm.tempus.R
import com.eddyizm.tempus.databinding.DialogColorPickerBinding
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

class ColorPickerDialog : DialogFragment() {

    private var _binding: DialogColorPickerBinding? = null // memory-leak safe
    private val binding // only valid between onCreateView and onDestroyView.
        get() = _binding!!

    private var dialogTitle: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            dialogTitle = it.getString(ARG_TITLE)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogColorPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialogTitle?.let {
            binding.tvDialogTitle.text = it
        }

        binding.btnCancel.setOnClickListener { dismiss() }

        var selectedHex = "HEX:#B5076B"

        binding.colorPickerView.setColorListener(ColorEnvelopeListener { envelope, fromUser ->
            selectedHex = "HEX:#${envelope.hexCode}"
        })

        binding.btnAccept.setOnClickListener {
            val result = Bundle().apply {
                putString("color_key", selectedHex) // Uses the dynamic color chosen
            }
            parentFragmentManager.setFragmentResult("dialog_result_key", result)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // release from memory
    }

    companion object {
        private const val ARG_TITLE = "arg_title"

        @JvmStatic
        fun newInstance(title: String): ColorPickerDialog =
            ColorPickerDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                }
            }
    }
}