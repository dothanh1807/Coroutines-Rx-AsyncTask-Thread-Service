package com.vllenin.corountinerx

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.vllenin.corountinerx.Values.LINK_ONE
import com.vllenin.corountinerx.Values.LINK_TWO
import kotlinx.android.synthetic.main.fragment.*

/**
 * Created by Vllenin on 2020-04-19.
 */
class FragmentService: Fragment() {

    private lateinit var serviceConnection: ServiceConnection

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textView.text = FragmentService::class.java.simpleName
        btnDownload.setOnClickListener {
            val pathVideo = "android.resource://" + context?.packageName + "/" + R.raw.video
            videoThumbnails.generateThumbnailsWithService(pathVideo) { measureTime ->
                if (isVisible) {
                    textViewMeasureTime.text = "Time generate thumbnails: $measureTime ms"
                }
            }

            val intentService = Intent(activity, DownloadImageService::class.java)
            serviceConnection = object : ServiceConnection {
                override fun onServiceDisconnected(p0: ComponentName?) {}

                override fun onServiceConnected(p0: ComponentName?, binder: IBinder) {
                    val service =
                        (binder as DownloadImageService.DownLoadImageBinder).getService()

                    service.registerDownloadImage(LINK_ONE, object : Observer {
                        override fun notifyData(value: Any) {
                            if (value is Int && isVisible) {
                                seekBar1.progress = value
                            } else if (value is Bitmap && isVisible) {
                                imageView1.setImageBitmap(value)
                            } else if (value is String && isVisible) {
                                timeSeekbar1.text = value
                            }
                        }
                    })

                    service.registerDownloadImage(LINK_TWO, object : Observer {
                        override fun notifyData(value: Any) {
                            if (value is Int && isVisible) {
                                seekBar2.progress = value
                            } else if (value is Bitmap && isVisible) {
                                imageView2.setImageBitmap(value)
                            } else if (value is String && isVisible) {
                                timeSeekbar2.text = value
                            }
                        }
                    })
                }
            }
            activity?.bindService(intentService, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.unbindService(serviceConnection)
    }

    class DownloadImageService: Service() {

        private val mapThread =
            mutableMapOf<String, FragmentThread.DownloadImageThread>()

        private lateinit var downloadImageBinder: DownLoadImageBinder

        inner class DownLoadImageBinder: Binder() {
            fun getService(): DownloadImageService {
                return this@DownloadImageService
            }
        }

        override fun onCreate() {
            downloadImageBinder = DownLoadImageBinder()
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            return START_NOT_STICKY
        }

        override fun onBind(p0: Intent?): IBinder? {
            return downloadImageBinder
        }

        override fun onDestroy() {
            super.onDestroy()
            mapThread.values.forEach { thread ->
                thread.forceStop()
            }
            mapThread.clear()
            Log.d("XXX", "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~DownloadImageService onDestroy")
        }

        fun registerDownloadImage(path: String, observer: Observer) {
            mapThread[path] = FragmentThread.DownloadImageThread(Handler { message ->
                val value = message.obj
                Log.d("XXX", "handlerMessage: $value - ${Thread.currentThread().name}")
                observer.notifyData(value)

                true
            }, path)
            mapThread[path]?.start()
        }

    }

    interface Observer {
        fun notifyData(value: Any)
    }
}