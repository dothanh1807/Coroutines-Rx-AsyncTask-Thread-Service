package com.vllenin.corountinerx

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.vllenin.corountinerx.Values.LINK_ONE
import com.vllenin.corountinerx.Values.LINK_TWO
import kotlinx.android.synthetic.main.fragment.*
import java.io.*
import java.net.URL

/**
 * Created by Vllenin on 2020-04-19.
 */
class FragmentThread: Fragment() {

    private val mapThread =
        mutableMapOf<String, DownloadImageThread>()

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
        textView.text = FragmentThread::class.java.simpleName
        btnDownload.setOnClickListener {
            val pathVideo = "android.resource://" + context?.packageName + "/" + R.raw.video
            videoThumbnails.generateThumbnailsWithThread(pathVideo) { measureTime ->
                if (isVisible) {
                    textViewMeasureTime.text = "Time generate thumbnails: $measureTime ms"
                }
            }

            /**
             * Do 1 [Thread] chỉ có 1 [Looper], 1 [Looper] có thể có nhiều [Handler]
             * Vì vậy mỗi [DownloadImageThread] mình truyền 1 [Handler] mới cũng k sao, vì [Handler]
             * này nó mặc định lấy [Looper] của Thread khởi tạo nó, ở đây là mainThread.
             */
            mapThread[LINK_ONE] = DownloadImageThread(Handler { message ->
                val value = message.obj
                Log.d("XXX", "handlerMessage: $value - ${Thread.currentThread().name}")
                if (value is Int && isVisible) {
                    seekBar1.progress = value
                } else if (value is Bitmap && isVisible) {
                    imageView1.setImageBitmap(value)
                } else if (value is String && isVisible) {
                    timeSeekbar1.text = value
                }

                true
            }, LINK_ONE)

            mapThread[LINK_TWO] = DownloadImageThread(Handler { message ->
                val value = message.obj
                Log.d("XXX", "handlerMessage: $value - ${Thread.currentThread().name}")
                if (value is Int && isVisible) {
                    seekBar2.progress = value
                } else if (value is Bitmap && isVisible) {
                    imageView2.setImageBitmap(value)
                } else if (value is String && isVisible) {
                    timeSeekbar2.text = value
                }

                true
            }, LINK_TWO)

            mapThread.values.forEach { thread ->
                thread.start()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        mapThread.values.forEach { thread ->
            thread.forceStop()
        }
        mapThread.clear()
    }

    class DownloadImageThread(
        private val handlerUI: Handler,
        private val path: String
    ): HandlerThread("Thread-${System.currentTimeMillis()}") {

        private var isActive = false
        private var timeStartDownloadImageOne = 0L
        private var timeStartDownloadImageTwo = 0L

        override fun run() {
            isActive = true
            if (path.contains(LINK_ONE)) {
                timeStartDownloadImageOne = System.currentTimeMillis()
            } else {
                timeStartDownloadImageTwo = System.currentTimeMillis()
            }
            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            try {
                val url = URL(path)
                val connection = url.openConnection()
                val sizeFile = connection?.contentLength ?: -1
                inputStream = BufferedInputStream(url.openStream())
                val byteArrayOutputStream = ByteArrayOutputStream()
                outputStream = BufferedOutputStream(byteArrayOutputStream)

                val dataType = ByteArray(1024)
                var data: Int
                var totalData: Long = 0
                var percent = 0
                while (true) {
                    if (!isActive) {
                        return
                    }
                    data = inputStream.read(dataType)
                    if (data > 0) {
//                        Thread.sleep(DISTANCE_DELAY)
                        totalData += data.toLong()
                        outputStream.write(dataType, 0, data)
                        if ((totalData * 100 / sizeFile).toInt() - percent >= 1) {
                            percent = (totalData * 100 / sizeFile).toInt()
                            Log.d("XXX", "emitting: $percent - ${Thread.currentThread().name}")
                            val message = Message()
                            message.obj = percent
                            handlerUI.sendMessage(message)/**~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~*/
                        }
                    } else {
                        break
                    }
                }
                outputStream.flush()
                if (isActive) {
                    val dataCompleted = byteArrayOutputStream.toByteArray()
                    val bitmap = BitmapFactory.decodeByteArray(dataCompleted, 0,
                        dataCompleted.size, BitmapFactory.Options())
                    val message = Message()
                    message.obj = bitmap
                    handlerUI.sendMessage(message)/**~~~~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~~~~*/

                    val mes = Message()
                    mes.obj = if (path.contains(LINK_ONE)) {
                        "${System.currentTimeMillis() - timeStartDownloadImageOne}"
                    } else {
                        "${System.currentTimeMillis() - timeStartDownloadImageTwo}"
                    }
                    handlerUI.sendMessage(mes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                /**
                 * Khối finally luôn luôn được gọi dù run() dừng lại do bất cứ nguyên nhân gì, dù
                 * có return ở trên thì finally vẫn được gọi.
                 */
                try {
                    inputStream?.close()
                    outputStream?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        /**
         * Thread chết khi nó chạy xong run(), vì vậy [isActive] = false thì trong block run() sẽ
         * return luôn, tức là chạy xong luôn, khi đó Thread cũng đc chết.
         */
        fun forceStop() {
            isActive = false
        }
    }
}