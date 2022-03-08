package pttsample.mobile.milestonesys.com.pushtotalksample

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import com.milestonesys.mipsdkmobile.communication.CommunicationItem
import kotlinx.android.synthetic.main.activity_camera_list.*
import pttsample.mobile.milestonesys.com.pushtotalksample.PushToTalkSampleApplication.Companion.PARAM_CAMERA_ID
import pttsample.mobile.milestonesys.com.pushtotalksample.PushToTalkSampleApplication.Companion.PARAM_CAMERA_NAME

/**
 *  Activity showing all the available cameras in a list
 */
class CameraListActivity : AppCompatActivity() {

    private var applicationObject: PushToTalkSampleApplication? = null
    private var camerasAdapter: CameraAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_list)

        setupViews()
    }

    private fun setupViews() {
        applicationObject = application as PushToTalkSampleApplication?
        camerasAdapter = CameraAdapter(this, applicationObject!!.camerasWithSpeakers)
        cameraListView?.adapter = camerasAdapter

        cameraListView?.setOnItemClickListener { adapterView: AdapterView<*>, _: View, i: Int, _: Long ->

            val cameraPressed: CommunicationItem =
                adapterView.getItemAtPosition(i) as CommunicationItem
            val bundle = Bundle()

            bundle.putString(PARAM_CAMERA_NAME, cameraPressed.name)
            bundle.putString(PARAM_CAMERA_ID, cameraPressed.id.toString())
            val intent = Intent(
                this,
                PushToTalkActivity::class.java
            )

            intent.putExtras(bundle)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Thread(Runnable { applicationObject?.mipSdkMobile?.closeCommunication() }).start()
    }
}

class CameraAdapter(context: Context, cameras: MutableList<CommunicationItem>) :
    ArrayAdapter<CommunicationItem>(context, 0, cameras) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var innerConvertView = convertView
        if (innerConvertView == null) {
            innerConvertView =
                LayoutInflater.from(context).inflate(R.layout.camera_list_item, parent, false)
        }

        val cameraName: TextView = innerConvertView?.findViewById(R.id.cameraName) as TextView
        cameraName.text = getItem(position)?.name
        return innerConvertView
    }
}
