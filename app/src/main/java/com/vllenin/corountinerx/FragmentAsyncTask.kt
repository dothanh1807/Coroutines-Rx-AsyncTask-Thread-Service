package com.vllenin.corountinerx

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Bundle
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
 * Created by Vllenin on 2020-04-18.
 * Note: Size thread pool, size queue [AsyncTask] refer:
 * https://stackoverflow.com/questions/4068984/running-multiple-asynctasks-at-the-same-time-not-possible
 */
class FragmentAsyncTask: Fragment() {

    private val mapTask =
        mutableMapOf<String, AsyncTask<String, Any, String>>()

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
        textView.text = FragmentAsyncTask::class.java.simpleName
        btnDownload.setOnClickListener {
            val pathVideo = "android.resource://" + context?.packageName + "/" + R.raw.video
            videoThumbnails.generateThumbnailsWithAsyncTask(pathVideo) { measureTime ->
                if (isVisible) {
                    textViewMeasureTime.text = "Time generate thumbnails: $measureTime ms"
                }
            }

            mapTask[LINK_ONE] = DownloadImageTask(LINK_ONE) { value ->
                if (value is Int && isVisible) {
                    seekBar1.progress = value
                } else if (value is Bitmap && isVisible) {
                    imageView1.setImageBitmap(value)
                } else if (value is String && isVisible) {
                    timeSeekbar1.text = "$value ms"
                    mapTask.remove(LINK_ONE)
                    Log.d("XXX", "~~~~~~~~~~~~~~~~~~~~~~ mapTask.remove ${mapTask.size}")
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)/** Nếu dùng
                                        execute(..) thì chỉ chạy được 1 AsyncTask trong 1
                                        thời điểm. Vậy nên muốn nhiều AsyncTask được chạy đồng
                                        thời thì phải dùng executeOnExecutor(..).

                                        Từ Android 4.4 trở đi, số [AsyncTask] có thể chạy cùng lúc
                                        CORE_POOL_SIZE = (số core CPU + 1). Queue = 128.
                                        Ví dụ máy có thể chạy 5 [AsyncTask] cùng lúc (tức là
                                        thread pool có CORE_POOL_SIZE = 5). Nếu ta chạy 1 lúc
                                        nhiều hơn 5 [AsyncTask] thì những thằng sau sẽ được xếp vào
                                        queue, mỗi khi có 1 thằng [AsyncTask] trong thread pool
                                        xong việc, thì 1 thằng trong queue sẽ được đẩy vào
                                        thread pool để chạy, cứ như vậy cho đến khi queue rỗng.
                                        Queue có size = 128 vậy nên nếu chạy đồng thời 1 lúc
                                        5+129 [AsyncTask] cùng lúc sẽ bị RejectedExecutionException
                                        vì 5 task chạy trong thread pool, nhưng còn 129 task kia
                                        vượt quá size(128) của queue. */

            mapTask[LINK_TWO] = DownloadImageTask(LINK_TWO) { value ->
                if (value is Int && isVisible) {
                    seekBar2.progress = value
                } else if (value is Bitmap && isVisible) {
                    imageView2.setImageBitmap(value)
                } else if (value is String && isVisible) {
                    timeSeekbar2.text = "$value ms"
                    mapTask.remove(LINK_TWO)
                    Log.d("XXX", "~~~~~~~~~~~~~~~~~~~~~~ mapTask.remove ${mapTask.size}")
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        }
    }

    override fun onStop() {
        super.onStop()
        mapTask.values.forEach { task ->
            /**
             * Nếu cancel(true) thì lập trức [AsyncTask] sẽ dừng hẳn luôn.
             * Nếu cancel(false) thì doInBackground sẽ vẫn chạy cho đến khi xong,
             * nhưng onProgressUpdate và onPostExcute sẽ k đc chạy.
             */
            task.cancel(true)
            Log.d("XXX", "AsyncTask cancel")
        }
        mapTask.values.clear()
    }

    class DownloadImageTask(
        private val path: String,
        private val callback: (any: Any) -> Unit
    ) : AsyncTask<String, Any, String>() {

        private var timeStartDownloadImageOne = 0L
        private var timeStartDownloadImageTwo = 0L

        override fun onPreExecute() {
            if (path.contains(LINK_ONE)) {
                timeStartDownloadImageOne = System.currentTimeMillis()
            } else {
                timeStartDownloadImageTwo = System.currentTimeMillis()
            }
        }

        /**
         * Chạy trên các thread nằm trong thread pool.
         */
        override fun doInBackground(vararg paths: String): String {
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
                    data = inputStream.read(dataType)
                    if (data > 0) {
//                        Thread.sleep(DISTANCE_DELAY)
                        totalData += data.toLong()
                        outputStream.write(dataType, 0, data)
                        if ((totalData * 100 / sizeFile).toInt() - percent >= 1) {
                            percent = (totalData * 100 / sizeFile).toInt()
                            Log.d("XXX", "emitting: $percent - ${Thread.currentThread().name}")
                            publishProgress(percent)/**~~~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~~*/
                        }
                    } else {
                        break
                    }
                }
                outputStream.flush()
                val dataCompleted = byteArrayOutputStream.toByteArray()
                val bitmap = BitmapFactory.decodeByteArray(dataCompleted, 0,
                    dataCompleted.size, BitmapFactory.Options())

                publishProgress(bitmap)/**~~~~~~~~~~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~~~~~~~~~*/
            } catch (e: Exception) {
                e.printStackTrace()
                return "Failed"
            } finally {
                /**
                 * Khối finally luôn luôn được gọi dù AsyncTask dừng lại do bất cứ nguyên nhân gì.
                 */
                try {
                    inputStream?.close()
                    outputStream?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            return "Done"
        }

        override fun onProgressUpdate(vararg values: Any) {
            /**
             * Không callback.invoke() ngay trong [doInBackground] vì callback này để cập nhật
             * lên UI, mà [doInBackground] không chạy trên mainThread.
             */
            Log.d("XXX", "onProgressUpdate")
            callback.invoke(values[0])
        }

        override fun onPostExecute(result: String) {
            Log.d("XXX", "onPostExecute")
            if (path.contains(LINK_ONE)) {
                callback.invoke("${System.currentTimeMillis() - timeStartDownloadImageOne}")
            } else {
                callback.invoke("${System.currentTimeMillis() - timeStartDownloadImageTwo}")
            }
        }
    }
}