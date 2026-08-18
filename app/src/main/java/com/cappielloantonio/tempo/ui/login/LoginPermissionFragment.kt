package com.cappielloantonio.tempo.ui.login

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.cappielloantonio.tempo.R

private const val ARG_SINGLE_PAGE_MODE = "single_page_mode"

class LoginPermissionFragment : Fragment() {
    private var singlePageMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            singlePageMode = it.getBoolean(ARG_SINGLE_PAGE_MODE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_login_permission, container, false)
    }

    companion object {
        @JvmStatic
        fun newInstance(singlePageMode: Boolean = false): LoginPermissionFragment =
            LoginPermissionFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_SINGLE_PAGE_MODE, singlePageMode)
                }
            }
    }
}