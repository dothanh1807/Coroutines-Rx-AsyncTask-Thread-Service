package com.vllenin.corountinerx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.AsyncTask
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Size
import android.view.View
import android.view.WindowManager
import com.vllenin.corountinerx.Values.DISTANCE_TIME_THUMBNAILS
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Created by Vllenin on 2020-04-18.
 */
class VideoThumbnails constructor(
    context: Context,
    attrs: AttributeSet
) : View(context, attrs) {

    private var timeStart = 0L
    private var thumbWidth = 0
    private var thumbHeight = 0
    private var widthScreen = 0
    private var listThumbnails = arrayListOf<Bitmap?>()

    private val disposables = CompositeDisposable()
    private val corountineScope = CoroutineScope(Dispatchers.Default)
    private val mapTask =
        mutableMapOf<String, AsyncTask<Any, Bitmap, String>>()
    private val mapThread =
        mutableMapOf<String, ThumbnailsFactoryThread>()

    init {
        val extSizeAttr = intArrayOf(android.R.attr.layout_height)
        val typeArray = context.obtainStyledAttributes(attrs, extSizeAttr)
        val displayMetrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        widthScreen = displayMetrics.widthPixels
        thumbHeight = typeArray.getDimensionPixelSize(0, -1)
        thumbWidth = thumbHeight
        typeArray.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (listThumbnails.isNotEmpty()) {
            var x = 0f

            for (i in 0 until listThumbnails.size) {
                val bitmap = listThumbnails.getOrNull(i)
                bitmap?.let {
                    canvas.drawBitmap(bitmap, x, 0f, null)
                    x += bitmap.width
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        disposables.clear()
        disposables.dispose()
        corountineScope.cancel()
        mapTask.values.forEach { task ->
            task.cancel(true)
        }
        mapTask.clear()
        mapThread.values.forEach { thread ->
            thread.forceStop()
        }
        mapThread.clear()
    }


    /********************************* Use RxKotlin *********************************************/


    fun generateThumbnailsWithRx(path: String, callback: (measureTime: Long) -> Unit) {
        timeStart = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.parse(path))
        val durationVideo =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        val amountThumbnails = (durationVideo / DISTANCE_TIME_THUMBNAILS).toInt()
        val distanceTime = durationVideo * 1000 / amountThumbnails

        val newLayoutParams = layoutParams
        newLayoutParams.width = thumbWidth * amountThumbnails
        layoutParams = newLayoutParams

        thumbnailsFactoryRx(retriever, amountThumbnails, distanceTime)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnComplete {
                callback.invoke(System.currentTimeMillis() - timeStart)
            }
            .subscribe({ thumbnail ->
                listThumbnails.add(thumbnail)
                invalidate()
            }, { exception ->
                exception.printStackTrace()
            })
            .addTo(disposables)
    }

    private fun thumbnailsFactoryRx(retriever: MediaMetadataRetriever,
                                    amountThumbnails: Int,
                                    distanceTime: Long): Observable<Bitmap> = Observable.create { emitter ->
        var frameVideo: Bitmap? = null
        var centerCropBitmap: Bitmap? = null
        try {
            (0 until amountThumbnails).forEach { index ->
                frameVideo = retriever.getFrameAtTime(index * distanceTime,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                frameVideo?.let { bitmapOrigin ->
                    centerCropBitmap = if (bitmapOrigin.height > bitmapOrigin.width) {
                        Bitmap.createBitmap(bitmapOrigin,
                            0,
                            bitmapOrigin.height / 2 - bitmapOrigin.width / 2,
                            bitmapOrigin.width,
                            bitmapOrigin.width)
                    } else {
                        Bitmap.createBitmap(bitmapOrigin,
                            bitmapOrigin.width / 2 - bitmapOrigin.height / 2,
                            0,
                            bitmapOrigin.height,
                            bitmapOrigin.height)
                    }

                    centerCropBitmap?.let { copped ->
                        val bitmapScaled =
                            Bitmap.createScaledBitmap(copped, thumbWidth, thumbHeight, false)
                        emitter.onNext(bitmapScaled)/**~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~~~~*/
                    }
                }
            }
            emitter.onComplete()/**~~~~~~~~~~~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } finally {
            retriever.release()
            frameVideo?.recycle()
            centerCropBitmap?.recycle()
        }
    }


    /******************************** Use Kotlin Coroutines **************************************/


    fun generateThumbnailsWithCorountines(path: String, callback: (measureTime: Long) -> Unit) {
        timeStart = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.parse(path))
        val durationVideo =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        val amountThumbnails = (durationVideo / DISTANCE_TIME_THUMBNAILS).toInt()
        val distanceTime = durationVideo * 1000 / amountThumbnails

        val newLayoutParams = layoutParams
        newLayoutParams.width = thumbWidth * amountThumbnails
        layoutParams = newLayoutParams

        corountineScope.launch(Dispatchers.Main) {
            thumbnailsFactoryCorountine(retriever, amountThumbnails, distanceTime)
                .flowOn(Dispatchers.Default)
                .onCompletion {
                    callback.invoke(System.currentTimeMillis() - timeStart)
                }
                .collect { thumbnail ->
                    listThumbnails.add(thumbnail)
                    invalidate()
                }
        }

    }

    private fun thumbnailsFactoryCorountine(retriever: MediaMetadataRetriever,
                                            amountThumbnails: Int,
                                            distanceTime: Long): Flow<Bitmap> = flow {
        var frameVideo: Bitmap? = null
        var centerCropBitmap: Bitmap? = null
        try {
            (0 until amountThumbnails).forEach { index ->
                frameVideo = retriever.getFrameAtTime(index * distanceTime,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                frameVideo?.let { bitmapOrigin ->
                    centerCropBitmap = if (bitmapOrigin.height > bitmapOrigin.width) {
                        Bitmap.createBitmap(bitmapOrigin,
                            0,
                            bitmapOrigin.height / 2 - bitmapOrigin.width / 2,
                            bitmapOrigin.width,
                            bitmapOrigin.width)
                    } else {
                        Bitmap.createBitmap(bitmapOrigin,
                            bitmapOrigin.width / 2 - bitmapOrigin.height / 2,
                            0,
                            bitmapOrigin.height,
                            bitmapOrigin.height)
                    }

                    centerCropBitmap?.let { copped ->
                        val bitmapScaled =
                            Bitmap.createScaledBitmap(copped, thumbWidth, thumbHeight, false)
                        emit(bitmapScaled)/**~~~~~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~~~~~~~~~~*/
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } finally {
            retriever.release()
            frameVideo?.recycle()
            centerCropBitmap?.recycle()
        }
    }


    /********************************** Use AsyncTask *******************************************/


    fun generateThumbnailsWithAsyncTask(path: String, callback: (measureTime: Long) -> Unit) {
        timeStart = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.parse(path))
        val durationVideo =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        val amountThumbnails = (durationVideo / DISTANCE_TIME_THUMBNAILS).toInt()
        val distanceTime = durationVideo * 1000 / amountThumbnails

        val newLayoutParams = layoutParams
        newLayoutParams.width = thumbWidth * amountThumbnails
        layoutParams = newLayoutParams

        mapTask["AsyncTask1"] = ThumbnailsFactoryTask { value ->
            if (value is Bitmap) {
                listThumbnails.add(value)
                invalidate()
            } else if (value is String){
                callback.invoke(System.currentTimeMillis() - timeStart)
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
            retriever, amountThumbnails, distanceTime, Size(thumbWidth, thumbHeight))
    }

    class ThumbnailsFactoryTask(
        private val callback: (bitmap: Any) -> Unit
    ): AsyncTask<Any, Bitmap, String>() {

        override fun doInBackground(vararg params: Any): String {
            val retriever = params[0] as MediaMetadataRetriever
            val amountThumbnails = params[1] as Int
            val distanceTime = params[2] as Long
            val sizeThumbnail = params[3] as Size

            var frameVideo: Bitmap? = null
            var centerCropBitmap: Bitmap? = null
            try {
                (0 until amountThumbnails).forEach { index ->
                    frameVideo = retriever.getFrameAtTime(index * distanceTime,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                    frameVideo?.let { bitmapOrigin ->
                        centerCropBitmap = if (bitmapOrigin.height > bitmapOrigin.width) {
                            Bitmap.createBitmap(bitmapOrigin,
                                0,
                                bitmapOrigin.height / 2 - bitmapOrigin.width / 2,
                                bitmapOrigin.width,
                                bitmapOrigin.width)
                        } else {
                            Bitmap.createBitmap(bitmapOrigin,
                                bitmapOrigin.width / 2 - bitmapOrigin.height / 2,
                                0,
                                bitmapOrigin.height,
                                bitmapOrigin.height)
                        }

                        centerCropBitmap?.let { copped ->
                            val bitmapScaled = Bitmap.createScaledBitmap(copped,
                                sizeThumbnail.width, sizeThumbnail.height, false)

                            publishProgress(bitmapScaled)/**~~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~*/
                        }
                    }
                }
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            } finally {
                retriever.release()
                frameVideo?.recycle()
                centerCropBitmap?.recycle()
            }

            return "Done"
        }

        override fun onProgressUpdate(vararg values: Bitmap) {
            callback.invoke(values[0])
        }

        override fun onPostExecute(result: String) {
            callback.invoke(result)
        }

    }


    /************************************ Use Thread *********************************************/


    fun generateThumbnailsWithThread(path: String, callback: (measureTime: Long) -> Unit) {
        timeStart = System.currentTimeMillis()
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, Uri.parse(path))
        val durationVideo =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        val amountThumbnails = (durationVideo / DISTANCE_TIME_THUMBNAILS).toInt()
        val distanceTime = durationVideo * 1000 / amountThumbnails

        val newLayoutParams = layoutParams
        newLayoutParams.width = thumbWidth * amountThumbnails
        layoutParams = newLayoutParams

        mapThread["Thread1"] = ThumbnailsFactoryThread(Handler { message ->
            val value = message.obj
            if (value is Bitmap) {
                listThumbnails.add(value)
                invalidate()
            } else if (value is String){
                callback.invoke(System.currentTimeMillis() - timeStart)
            }

            true
        }, retriever, amountThumbnails, distanceTime, Size(thumbWidth, thumbHeight))
        mapThread.values.forEach { thread ->
            thread.start()
        }
    }

    class ThumbnailsFactoryThread(
        private val handlerUI: Handler,
        vararg params: Any
    ): HandlerThread("Thread-${System.currentTimeMillis()}") {

        private var isActive = false

        private val retriever = params[0] as MediaMetadataRetriever
        private val amountThumbnails = params[1] as Int
        private val distanceTime = params[2] as Long
        private val sizeThumbnail = params[3] as Size

        override fun run() {
            isActive = true
            var frameVideo: Bitmap? = null
            var centerCropBitmap: Bitmap? = null
            try {
                (0 until amountThumbnails).forEach { index ->
                    if (!isActive) {
                        return
                    }
                    frameVideo = retriever.getFrameAtTime(index * distanceTime,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                    frameVideo?.let { bitmapOrigin ->
                        centerCropBitmap = if (bitmapOrigin.height > bitmapOrigin.width) {
                            Bitmap.createBitmap(bitmapOrigin,
                                0,
                                bitmapOrigin.height / 2 - bitmapOrigin.width / 2,
                                bitmapOrigin.width,
                                bitmapOrigin.width)
                        } else {
                            Bitmap.createBitmap(bitmapOrigin,
                                bitmapOrigin.width / 2 - bitmapOrigin.height / 2,
                                0,
                                bitmapOrigin.height,
                                bitmapOrigin.height)
                        }

                        centerCropBitmap?.let { copped ->
                            val bitmapScaled = Bitmap.createScaledBitmap(copped,
                                sizeThumbnail.width, sizeThumbnail.height, false)
                            val message = Message()
                            message.obj = bitmapScaled
                            handlerUI.sendMessage(message)/**~~~~~~~~~~~~~~~~~ emit ~~~~~~~~~~~~~*/
                        }
                    }
                }
                if (isActive) {
                    val mes = Message()
                    mes.obj = "Done"
                    handlerUI.sendMessage(mes)
                }
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            } finally {
                retriever.release()
                frameVideo?.recycle()
                centerCropBitmap?.recycle()
            }
        }

        fun forceStop() {
            isActive = false
        }

    }
}