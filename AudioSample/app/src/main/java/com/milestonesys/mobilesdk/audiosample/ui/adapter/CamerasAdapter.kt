package com.milestonesys.mobilesdk.audiosample.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.milestonesys.mobilesdk.audiosample.data.model.CameraItem
import com.milestonesys.mobilesdk.audiosample.databinding.ItemCameraBinding
import java.util.*

class CamerasAdapter(
    private val cameras: ArrayList<CameraItem?>,
    private val listener: CameraClickListener
) : RecyclerView.Adapter<CamerasAdapter.CameraViewHolder>() {

    inner class CameraViewHolder(
        val binding: ItemCameraBinding,
        private val listener: CameraClickListener
        ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            binding.root.setOnClickListener(this)
        }

        override fun onClick(view: View) {
            listener.onClick(view, bindingAdapterPosition)
        }
    }

    interface CameraClickListener {
        fun onClick(view: View, position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraViewHolder {
        val binding = ItemCameraBinding
                .inflate(LayoutInflater.from(parent.context), parent, false)

        return CameraViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: CameraViewHolder, position: Int) {
        with(holder) {
            cameras[position]?.let {
                binding.textViewCameraName.text = it.cameraName
                binding.root.transitionName = it.cameraId.toString()
            }
        }
    }

    override fun getItemCount(): Int = cameras.size

    fun addData(list: List<CameraItem>) {
        cameras.addAll(list)
    }
}