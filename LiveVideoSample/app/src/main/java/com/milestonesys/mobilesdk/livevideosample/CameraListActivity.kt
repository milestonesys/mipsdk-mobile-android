package com.milestonesys.mobilesdk.livevideosample

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.milestonesys.mipsdkmobile.communication.CommunicationItem

/**
 *  Activity showing all the available cameras in a list
 */
class CameraListActivity : AppCompatActivity() {

    private var applicationObject: SDKSampleApplication? = null
    private var cameraListView: ListView? = null
    private var camerasAdapter: CameraAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_list)

        applicationObject = application as SDKSampleApplication?
        cameraListView = findViewById(R.id.cameraList)
        camerasAdapter = CameraAdapter(this, applicationObject!!.allAvailableCameras)
        cameraListView?.adapter = camerasAdapter

        cameraListView?.setOnItemClickListener { adapterView: AdapterView<*>, _: View, i: Int, _: Long ->
            val cameraPressed: CommunicationItem = adapterView.getItemAtPosition(i) as CommunicationItem

            val b = Bundle()
            b.putString(PARAM_CAMERA_NAME, cameraPressed.name)
            b.putString(PARAM_CAMERA_ID, cameraPressed.id.toString())
            val intent = Intent(
                this,
                LiveActivity::class.java
            )
            intent.putExtras(b)
            startActivity(intent)
        }
    }
}

class CameraAdapter(context: Context, cameras: ArrayList<CommunicationItem>?) : ArrayAdapter<CommunicationItem>(context, 0, cameras!!) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.camera_list_item, parent, false);
        }

        val cameraName: TextView = view?.findViewById(R.id.camera_name) as TextView
        cameraName.text = getItem(position)?.name
        return view
    }
}