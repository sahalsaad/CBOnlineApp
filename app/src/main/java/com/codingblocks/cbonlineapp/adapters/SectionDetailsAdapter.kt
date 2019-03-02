package com.codingblocks.cbonlineapp.adapters

import android.app.Activity
import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.codingblocks.cbonlineapp.CBOnlineApp
import com.codingblocks.cbonlineapp.DownloadStarter
import com.codingblocks.cbonlineapp.R
import com.codingblocks.cbonlineapp.Utils.retrofitCallback
import com.codingblocks.cbonlineapp.activities.PdfActivity
import com.codingblocks.cbonlineapp.activities.QuizActivity
import com.codingblocks.cbonlineapp.activities.VideoPlayerActivity
import com.codingblocks.cbonlineapp.database.*
import com.codingblocks.cbonlineapp.utils.Animations.collapse
import com.codingblocks.cbonlineapp.utils.Animations.expand
import com.codingblocks.cbonlineapp.utils.MediaUtils
import com.codingblocks.cbonlineapp.utils.getDistinct
import com.codingblocks.onlineapi.Clients
import com.codingblocks.onlineapi.models.Contents
import com.codingblocks.onlineapi.models.Progress
import com.codingblocks.onlineapi.models.RunAttemptsModel
import kotlinx.android.synthetic.main.item_section.view.*
import org.jetbrains.anko.*
import java.io.File
import kotlin.concurrent.thread


class SectionDetailsAdapter(private var sectionData: ArrayList<CourseSection>?,
                            private var activity: LifecycleOwner,
                            private var starter: DownloadStarter
) : RecyclerView.Adapter<SectionDetailsAdapter.CourseViewHolder>(), AnkoLogger {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var contentDao: ContentDao
    private var premium: Boolean = false
    private lateinit var courseStartDate: String

    private lateinit var sectionWithContentDao: SectionWithContentsDao
    lateinit var arrowAnimation: RotateAnimation


    fun setData(sectionData: ArrayList<CourseSection>, premium: Boolean, crStart: String) {
        this.sectionData = sectionData
        this.premium = premium
        this.courseStartDate = crStart
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        holder.bindView(sectionData!![position], starter)
    }


    override fun getItemCount(): Int {

        return sectionData!!.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        context = parent.context
        database = AppDatabase.getInstance(context)
        contentDao = database.contentDao()
        sectionWithContentDao = database.sectionWithContentsDao()


        return CourseViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_section, parent, false))
    }

    inner class CourseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bindView(data: CourseSection, starter: DownloadStarter) {

            itemView.title.text = data.name
            sectionWithContentDao.getContentWithSectionId(data.id).getDistinct().observe(activity, Observer<List<CourseContent>> { it ->

                val ll = itemView.findViewById<LinearLayout>(R.id.sectionContents)
                if (ll.visibility == View.VISIBLE) {
                    ll.removeAllViews()
                    expand(ll)
                } else {
                    ll.removeAllViews()
                    ll.visibility = View.GONE
                }
                itemView.lectures.text = "0/${it.size} Lectures Completed"
                var duration: Long = 0
                var sectionComplete = 0
                for (content in it) {

                    val factory = LayoutInflater.from(context)
                    val inflatedView = factory.inflate(R.layout.item_section_detailed_info, ll, false)
                    val subTitle = inflatedView.findViewById(R.id.textView15) as TextView
                    val downloadBtn = inflatedView.findViewById(R.id.downloadBtn) as ImageView
                    val contentType = inflatedView.findViewById(R.id.contentType) as ImageView

                    if (content.progress == "DONE") {
                        subTitle.textColor = context.resources.getColor(R.color.green)
                        downloadBtn.setImageDrawable(context.getDrawable(R.drawable.ic_status_done))
                        sectionComplete++
                    }
                    if (content.contentable == "lecture")
                        duration += content.contentLecture.lectureDuration
                    else if (content.contentable == "video") {
                        duration += content.contentVideo.videoDuration
                    }
                    val hour = duration / (1000 * 60 * 60) % 24
                    val minute = duration / (1000 * 60) % 60

                    if (minute >= 1 && hour == 0L)
                        itemView.lectureTime.text = ("$minute Mins")
                    else if (hour >= 1) {
                        itemView.lectureTime.text = ("$hour Hours")
                    } else
                        itemView.lectureTime.text = ("---")
                    subTitle.text = content.title

                    if (!data.premium || premium && ((courseStartDate.toLong() * 1000) < System.currentTimeMillis())) {
                        if (sectionComplete == it.size) {
                            itemView.lectures.text = "$sectionComplete/${it.size} Lectures Completed"
                            itemView.lectures.textColor = context.resources.getColor(R.color.green)
                        } else {
                            itemView.lectures.text = "$sectionComplete/${it.size} Lectures Completed"
                            itemView.lectures.textColor = context.resources.getColor(R.color.black)
                        }
                        when {
                            content.contentable == "lecture" -> {
                                contentType.setImageDrawable(context.getDrawable(R.drawable.ic_lecture))
                                if (!content.contentLecture.lectureUid.isNullOrEmpty()) {
                                    val url = content.contentLecture.lectureUrl.substring(38, (content.contentLecture.lectureUrl.length - 11))
                                    ll.addView(inflatedView)
                                    if (content.contentLecture.isDownloaded == "false") {
                                        downloadBtn.setImageDrawable(null)
                                        downloadBtn.background = context.getDrawable(android.R.drawable.stat_sys_download)
                                        inflatedView.setOnClickListener {
                                            it.context.startActivity(it.context.intentFor<VideoPlayerActivity>("FOLDER_NAME" to content.contentLecture.lectureUrl, "attemptId" to content.attempt_id, "contentId" to content.id, "downloaded" to false).singleTop())

                                        }
                                        downloadBtn.setOnClickListener {
                                            if (MediaUtils.checkPermission(context)) {
                                                starter.startDownload(content.contentLecture.lectureUrl, data.id, content.contentLecture.lectureContentId, content.title, content.attempt_id, content.id)
                                                downloadBtn.isEnabled = false
                                                (downloadBtn.background as AnimationDrawable).start()
                                            } else {
                                                MediaUtils.isStoragePermissionGranted(context)
                                            }
                                        }
                                    } else {
                                        downloadBtn.setOnClickListener {

                                            (context as Activity).alert("This lecture will be deleted !!!") {
                                                yesButton {
                                                    val file = context.getExternalFilesDir(Environment.getDataDirectory().absolutePath)
                                                    val folderFile = File(file, "/$url")
                                                    deleteRecursive(folderFile)
                                                    contentDao.updateContent(data.id, content.contentLecture.lectureContentId, "false")
                                                }
                                                noButton { it.dismiss() }
                                            }.show()

                                        }
                                        inflatedView.setOnClickListener {
                                            if (content.progress == "UNDONE") {
                                                if (content.progressId.isEmpty())
                                                    setProgress(content.id, content.attempt_id, content.contentable, data.id, content.contentLecture.lectureContentId)
                                                else
                                                    updateProgress(content.id, content.attempt_id, content.progressId, "DONE", content.contentable, data.id, content.contentLecture.lectureContentId)
                                            }
                                            it.context.startActivity(it.context.intentFor<VideoPlayerActivity>("FOLDER_NAME" to url, "attemptId" to content.attempt_id, "contentId" to content.id, "downloaded" to true).singleTop())
                                        }
                                    }
                                }

                            }
                            content.contentable == "document" -> {
                                contentType.setImageDrawable(context.getDrawable(R.drawable.ic_document))
                                ll.addView(inflatedView)
                                inflatedView.setOnClickListener {
                                    if (content.progress == "UNDONE") {
                                        if (content.progressId.isEmpty())
                                            setProgress(content.id, content.attempt_id, content.contentable, data.id, content.contentDocument.documentContentId)
                                        else
                                            updateProgress(content.id, content.attempt_id, content.progressId, "DONE", content.contentable, data.id, content.contentDocument.documentContentId)
                                    }
                                    it.context.startActivity(it.context.intentFor<PdfActivity>("fileUrl" to content.contentDocument.documentPdfLink, "fileName" to content.contentDocument.documentName + ".pdf").singleTop())

                                }
                            }
                            content.contentable == "video" -> {
                                contentType.setImageDrawable(context.getDrawable(R.drawable.ic_youtube_video))
                                ll.addView(inflatedView)
                                inflatedView.setOnClickListener {
                                    if (content.progress == "UNDONE") {
                                        if (content.progressId.isEmpty())
                                            setProgress(content.id, content.attempt_id, content.contentable, data.id, content.contentVideo.videoContentId)
                                        else
                                            updateProgress(content.id, content.attempt_id, content.progressId, "DONE", content.contentable, data.id, content.contentVideo.videoContentId)
                                    }
                                    it.context.startActivity(it.context.intentFor<VideoPlayerActivity>("videoUrl" to content.contentVideo.videoUrl, "attemptId" to content.attempt_id, "contentId" to content.id).singleTop())

                                }
                            }
                            content.contentable == "qna" -> {
                                contentType.setImageDrawable(context.getDrawable(R.drawable.ic_quiz))
                                ll.addView(inflatedView)
                                inflatedView.setOnClickListener {
                                    if (content.progress == "UNDONE") {
                                        if (content.progressId.isEmpty())
                                            setProgress(content.id, content.attempt_id, content.contentable, data.id, content.contentQna.qnaContentId)
                                        else
                                            updateProgress(content.id, content.attempt_id, content.progressId, "DONE", content.contentable, data.id, content.contentLecture.lectureContentId)
                                    }
                                    it.context.startActivity(it.context.intentFor<QuizActivity>("quizId" to content.contentQna.qnaQid.toString(), "attemptId" to content.attempt_id).singleTop())

                                }
                            }

                        }
                    } else {
                        contentType.visibility = View.GONE
                        downloadBtn.setImageDrawable(context.getDrawable(R.drawable.ic_lock_outline_black_24dp))
                        ll.addView(inflatedView)
                    }

                    itemView.setOnClickListener {
                        showOrHide(ll, it)
                    }

                    itemView.arrow.setOnClickListener {
                        showOrHide(ll, it)
                    }
                }
            })
        }
    }


    fun showOrHide(ll: View, itemView: View) {
        if (ll.visibility == View.GONE) {
            expand(ll)
            arrowAnimation = RotateAnimation(0f, 180f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                    0.5f)
            arrowAnimation.fillAfter = true
            arrowAnimation.duration = 350
            itemView.arrow.startAnimation(arrowAnimation)
        } else {
            collapse(ll)
            arrowAnimation = RotateAnimation(180f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                    0.5f)
            arrowAnimation.fillAfter = true
            arrowAnimation.duration = 350
            itemView.arrow.startAnimation(arrowAnimation)
        }
    }

    fun setProgress(id: String, attempt_id: String, contentable: String, sectionId: String, contentId: String) {
        val p = Progress()
        val runAttempts = RunAttemptsModel()
        val contents = Contents()
        runAttempts.id = attempt_id
        contents.id = id
        p.status = "DONE"
        p.runs = runAttempts
        p.content = contents
        Clients.onlineV2JsonApi.setProgress(p).enqueue(retrofitCallback { throwable, response ->

            response?.body().let {
                val progressId = it?.id
                when (contentable) {
                    "lecture" -> thread {
                        contentDao.updateProgressLecture(sectionId, contentId, "DONE", progressId
                                ?: "")
                    }

                    "document" ->
                        thread {
                            contentDao.updateProgressDocuemnt(sectionId, contentId, "DONE", progressId
                                    ?: "")
                        }
                    "video" ->
                        thread {
                            contentDao.updateProgressVideo(sectionId, contentId, "DONE", progressId
                                    ?: "")
                        }
                    "qna" ->
                        thread {
                            contentDao.updateProgressQna(sectionId, contentId, "DONE", progressId
                                    ?: "")
                        }
                    else -> {
                    }
                }

            }
        })
    }

    private fun updateProgress(id: String, attempt_id: String, progressId: String, status: String, contentable: String, sectionId: String, contentId: String) {
        val p = Progress()
        val runAttempts = RunAttemptsModel()
        val contents = Contents()
        runAttempts.id = attempt_id
        contents.id = id
        p.id = progressId
        p.status = status
        p.runs = runAttempts
        p.content = contents
        Clients.onlineV2JsonApi.updateProgress(progressId, p).enqueue(retrofitCallback { throwable, response ->
            if (response != null) {
                if (response.isSuccessful) {
                    when (contentable) {
                        "lecture" -> thread {
                            contentDao.updateProgressLecture(sectionId, contentId, status, progressId)
                        }

                        "document" ->
                            thread {
                                contentDao.updateProgressDocuemnt(sectionId, contentId, status, progressId)
                            }
                        "video" ->
                            thread {
                                contentDao.updateProgressVideo(sectionId, contentId, status, progressId)
                            }
                        "qna" ->
                            thread {
                                contentDao.updateProgressQna(sectionId, contentId, status, progressId)
                            }
                    }


                }
            }
        })
    }

    fun deleteRecursive(fileOrDirectory: File) {

        if (fileOrDirectory.isDirectory) {
            for (child in fileOrDirectory.listFiles()) {
                deleteRecursive(child)
            }
        }

        fileOrDirectory.delete()
    }

}