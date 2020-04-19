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
import com.vllenin.corountinerx.Values.LINK_ONE
import com.vllenin.corountinerx.Values.LINK_TWO
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment.*
import java.io.*
import java.net.URL

/**
 * Created by Vllenin on 2020-04-18.
 */
class FragmentRx: Fragment() {

    /**
     * Các Observable cần phải được add vào CompositeDisposable này, để khi [onStop] sẽ clear
     * tránh memory leak.
     */
    private val compositeDisposable = CompositeDisposable()

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
        textView.text = FragmentRx::class.java.simpleName
        btnDownload.setOnClickListener {
            val pathVideo = "android.resource://" + context?.packageName + "/" + R.raw.video
            videoThumbnails.generateThumbnailsWithRx(pathVideo) { measureTime ->
                if (isVisible) {
                    textViewMeasureTime.text = "Time generate thumbnails: $measureTime ms"
                }
            }

            compositeDisposable.plusAssign(
                downloadImage(LINK_ONE)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ value ->
                        /**
                         * Code ở đây sẽ chạy trên thread được quy định bởi observeOn(..)
                         * Trường hợp này là observeOn(AndroidSchedulers.mainThread()) tức là
                         * chạy trên mainThread.
                         */
                        if (value is Int && isVisible) {
                            Log.d("XXX", "subscribe: $value - ${Thread.currentThread().name}")
                            seekBar1.progress = value
                        } else if (value is Bitmap && isVisible) {
                            imageView1.setImageBitmap(value)
                            timeSeekbar1.text = "${System.currentTimeMillis() - timeStartDownloadImageOne} ms"
                        }
                    }, { exception ->
                        /**
                         * Nếu trong quá trình Observalble(tức là code trong function [downloadImage])
                         * chạy gặp exception mà ta quên chưa xử lý thì sẽ exception đó sẽ được
                         * bắn vào đây.
                         */
                        exception.printStackTrace()
                        Log.d("XXX", "onError: - ${Thread.currentThread().name} $exception")
                    })
            )

            downloadImage(LINK_TWO)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ value ->
                    if (value is Int && isVisible) {
                        seekBar2.progress = value
                    } else if (value is Bitmap && isVisible) {
                        imageView2.setImageBitmap(value)
                        timeSeekbar2.text = "${System.currentTimeMillis() - timeStartDownloadImageTwo} ms"
                    }
                }, {

                }).addTo(compositeDisposable)

        }
    }

    override fun onStop() {
        super.onStop()
        compositeDisposable.clear()
        compositeDisposable.dispose()
    }

    /**
     * Code ở function này sẽ chạy trên thread được quy định tại subscribeOn(..)
     * Trường hợp này là: subscribeOn(Schedulers.io()) tức là sẽ chạy ở thread khác, k phải
     * mainThread.
     */
    private fun downloadImage(path: String): Observable<Any> = Observable.create { emitter ->
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
                if (emitter.isDisposed) {
                    return@create
                }
                data = inputStream.read(dataType)
                if (data > 0) {
//                    Thread.sleep(DISTANCE_DELAY)
                    totalData += data.toLong()
                    outputStream.write(dataType, 0, data)
                    if ((totalData * 100 / sizeFile).toInt() - percent >= 1) {
                        percent = (totalData * 100 / sizeFile).toInt()
                        Log.d("XXX", "emitting: $percent - ${Thread.currentThread().name}")
                        emitter.onNext(percent)/**~~~~~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~~~~~~~*/
                    }
                } else {
                    break
                }
            }
            outputStream.flush()
            val dataCompleted = byteArrayOutputStream.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(dataCompleted, 0,
                dataCompleted.size, BitmapFactory.Options())

            emitter.onNext(bitmap)/**~~~~~~~~~~~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            /**
             * Khối finally luôn luôn được gọi dù Observable dừng lại do bất cứ nguyên nhân gì.
             */
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}