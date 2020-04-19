package com.vllenin.corountinerx

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.vllenin.corountinerx.Values.DISTANCE_DELAY_ONE
import com.vllenin.corountinerx.Values.DISTANCE_DELAY_TWO
import com.vllenin.corountinerx.Values.LINK_ONE
import com.vllenin.corountinerx.Values.LINK_TWO
import kotlinx.android.synthetic.main.fragment.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.MalformedURLException
import java.net.URL
import kotlin.coroutines.CoroutineContext

/**
 * Created by Vllenin on 2020-04-17.
 */
class FragmentCoroutine : Fragment() {

    /**
     * Các coroutines đều cần được chạy với scope này để khi [onStop] sẽ cancel các coroutines,
     * tránh memory leak.
     */
    private val coroutineScopeInThisFragment =
        CoroutineScope(Dispatchers.Default + CoroutineName("CoroutinesDefault"))

    private var timeStartDownloadImageOne = 0L
    private var timeStartDownloadImageTwo = 0L

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
        textView.text = FragmentCoroutine::class.java.simpleName
        btnDownload.setOnClickListener {
            val pathVideo = "android.resource://" + context?.packageName + "/" + R.raw.video
            videoThumbnails.generateThumbnailsWithCorountines(pathVideo) { measureTime ->
                if (isVisible) {
                    textViewMeasureTime.text = "Time generate thumbnails: $measureTime ms"
                }
            }
            /**
             * Coroutine parent, launch with [CoroutineContext] = Dispatchers.Main
             */
            coroutineScopeInThisFragment.launch(Dispatchers.Main) {
                // Coroutine children
                downloadImage(LINK_ONE, DISTANCE_DELAY_ONE)
                    .flowOn(Dispatchers.Default)
                    .catch { exception ->
                        /**
                         * Nếu trong quá trình "A cold asynchronous data stream that sequentially emits"
                         * (tức là code trong function [downloadImage] chạy) bị exception ta quên
                         * chưa xử lý, thì exception sẽ được bắn vào đây.
                         */
                        exception.printStackTrace()
                        Log.d("XXX", "onError: - ${Thread.currentThread().name} $exception")
                    }
                    .onCompletion { exception ->
                        /** When cancel() coroutineScope at [onStop] then onCompletion still called,
                         * but variable [isActive] = false
                         */
                        if (exception == null) {
                            if (isActive) {
                                timeSeekbar1.text = "${System.currentTimeMillis() - timeStartDownloadImageOne} ms"
                                Log.d("XXX", "onCompleted: - ${Thread.currentThread().name}")
                            }
                        } else {
                            Log.d("XXX", "onFailed: - ${Thread.currentThread().name} $exception")
                        }
                    }
                    .onEach { value ->
                        if (value is Int && isVisible) {
                            Log.d("XXX", "onEach: $value - ${Thread.currentThread().name}")
                            seekBar1.progress = value
                        } else if (value is Bitmap && isVisible) {
                            imageView1.setImageBitmap(value)
                        }
                    }
                    .launchIn(this)/** Dùng [launchIn] để các coroutines ở dưới được chạy
                                            song song(cùng lúc), k phải đợi thằng này xong.
                                            Nếu dùng [collect] thì các coroutines ở dưới phải đợi
                                            thằng này chạy xong thì mới được chạy -> Mất nhiều
                                            tgian hơn.

                                            [onCompletion] and [onEach] and [collect] chạy trên
                                            CoroutineContext nào phụ thuộc vào scope của thằng
                                            [launchIn]: ở đây điền this tức là corountine parent.
                                            Nếu dùng [collect] thì mặc định là chạy với context của
                                            coroutine parent */

                // Coroutine children
                downloadImage(LINK_TWO, DISTANCE_DELAY_TWO)
                    .flowOn(Dispatchers.Default)
                    .onCompletion {
                        timeSeekbar2.text = "${System.currentTimeMillis() - timeStartDownloadImageTwo} ms"
                    }
                    .onEach { value ->
                        if (value is Int && isVisible) {
                            seekBar2.progress = value
                        } else if (value is Bitmap && isVisible) {
                            imageView2.setImageBitmap(value)
                        }
                    }
                    .launchIn(this)/** Nếu thay this = CoroutineScope(Dispatchers.Main)
                                            thì khi cancel ở [onStop] coroutine này sẽ k bị huỷ
                                            mà vẫn chạy tiếp được, vì nó chạy với scope riêng*/
            }
        }
    }

    override fun onStop() {
        super.onStop()
        coroutineScopeInThisFragment.cancel()
    }

    /**
     * Thằng [flow] này được gọi là: "A cold asynchronous data stream that sequentially emits" ~ Luồng
     * phát dữ liệu không đồng bộ.
     * Code in this function will run with CoroutineContext is contex at [flowOn].
     * This case run with flowOn( [CoroutineContext] = Dispatchers.Default )
     *
     * Để loại object là [Any] (trong Java thì để là Objects) thì ta có thể emit bất cứ thứ gì,
     * nhưng trong những thằng nhận như [filter], [map], [transform], [onEach], [collect],
     * [flatMap],... thì phải check kiểu dữ liệu bằng operator 'is'.
     */
    private fun downloadImage(path: String, timeDelay: Long): Flow<Any> = flow {
        if (timeDelay == DISTANCE_DELAY_ONE) {
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
            while (true) {
                data = inputStream.read(dataType)
                if (data > 0) {
                    totalData += data.toLong()
                    Log.d("XXX", "emitting: $totalData - ${Thread.currentThread().name}")
//                    delay(timeDelay)
                    emit((totalData * 100 / sizeFile).toInt())/**~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~*/
                    outputStream.write(dataType, 0, data)
                } else {
                    break
                }
            }
            outputStream.flush()
            val bytes = byteArrayOutputStream.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0,
                bytes.size, BitmapFactory.Options())

            emit(bitmap)/**~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
        } catch (e: MalformedURLException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            /**
             * When cancel() coroutineScrop at [onStop] then block finally still called.
             */
            Log.d("XXX", "finally")
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

}