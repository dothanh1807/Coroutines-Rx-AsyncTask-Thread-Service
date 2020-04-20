package com.vllenin.corountinerx

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_all_in_one.*

/**
 * Created by Vllenin on 2020-04-20.
 */
class FragmentAllInOne: Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_all_in_one, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnGenerate.setOnClickListener {
            val ranks = arrayListOf(1, 2, 3, 4, 5)
            val pathVideo = "android.resource://" + context?.packageName + "/" + R.raw.video

            videoThumbnailsCoroutines.generateThumbnailsWithCorountines(pathVideo) { measureTime ->
                if (isVisible) {
                    if (ranks.isNotEmpty()) {
                        timeCorountines.text = "TOP ${ranks[0]}: Time generate thumbnails: $measureTime ms"
                        ranks.removeAt(0)
                    }
                }
            }

            videoThumbnailsRxKotlin.generateThumbnailsWithRx(pathVideo) { measureTime ->
                if (isVisible) {
                    if (ranks.isNotEmpty()) {
                        timeRxKotlin.text = "TOP ${ranks[0]}: Time generate thumbnails: $measureTime ms"
                        ranks.removeAt(0)
                    }
                }
            }

            videoThumbnailsAsyncTask.generateThumbnailsWithAsyncTask(pathVideo) { measureTime ->
                if (isVisible) {
                    if (ranks.isNotEmpty()) {
                        timeAsyncTask.text = "TOP ${ranks[0]}: Time generate thumbnails: $measureTime ms"
                        ranks.removeAt(0)
                    }
                }
            }

            videoThumbnailsThread.generateThumbnailsWithThread(pathVideo) { measureTime ->
                if (isVisible) {
                    if (ranks.isNotEmpty()) {
                        timeThread.text = "TOP ${ranks[0]}: Time generate thumbnails: $measureTime ms"
                        ranks.removeAt(0)
                    }
                }
            }

            videoThumbnailsService.generateThumbnailsWithService(pathVideo) { measureTime ->
                if (isVisible) {
                    if (ranks.isNotEmpty()) {
                        timeService.text = "TOP ${ranks[0]}: Time generate thumbnails: $measureTime ms"
                        ranks.removeAt(0)
                    }
                }
            }
        }

    }

}