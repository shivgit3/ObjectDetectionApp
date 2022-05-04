package com.example.collegeproject1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import kotlinx.android.synthetic.main.fragment_home.view.*

class HomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_home, container, false)


        view.fromStorageBtn.setOnClickListener {
            Navigation.findNavController(view)
                .navigate(R.id.action_homeFragment_to_mainStorageFragment)
        }

        view.toLiveFragment.setOnClickListener {
            Navigation.findNavController(view).navigate(R.id.action_homeFragment_to_liveFragment)
        }

        return view
    }

}