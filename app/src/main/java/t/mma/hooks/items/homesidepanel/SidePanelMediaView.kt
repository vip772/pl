package t.mma.hooks.items.homesidepanel

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors
import t.mma.utils.HLog

/** Local-only photo frame. Decoding is off-main; playback exists only while the main drawer is visible. */
internal class SidePanelMediaView(context: Context, private val recoverPlayback: Boolean = false) : FrameLayout(context) {
    private val loader = Executors.newSingleThreadExecutor { task -> Thread(task,"Hchat-PanelMedia").apply { isDaemon=true } }
    private var generation = 0
    private var currentPath: String? = null
    private var currentRevision = -1L
    private var active = false
    private var disposed = false
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var texture: TextureView? = null
    private var animated: Animatable? = null
    private var movieView: GifView? = null
    private var image: ImageView? = null
    private var decodePending = false
    private var restartPending = false
    private var recoveryAttempts = 0
    private var recoveryQueued = false
    private val recovery = Runnable {
        recoveryQueued = false
        if (!disposed && active && isShown && hasWindowFocus() && player == null && texture?.isAvailable == true) startVideo()
    }
    /** Event-driven recovery only: no timer loop or frame-by-frame player probing. */
    fun ensurePlayback(visible: Boolean) {
        val wasActive = active
        setPlayback(visible)
        if (!recoverPlayback) return
        if (!visible) {
            removeCallbacks(recovery); recoveryQueued = false
            if (wasActive) recoveryAttempts = 0
            return
        }
        if (decodePending || disposed) return
        animated?.let { if (!it.isRunning) it.start() }
        movieView?.let { if (!it.playing) it.playing = true }
        if (texture != null && player == null && wasActive) scheduleRecovery()
    }
    private fun scheduleRecovery() {
        if (!recoverPlayback || disposed || !active || recoveryQueued || recoveryAttempts >= 2) return
        recoveryAttempts++
        recoveryQueued = true
        postDelayed(recovery, 500L * recoveryAttempts)
    }

    fun load(file: File?, revision: Long) {
        if (disposed || (currentPath == file?.path && currentRevision == revision)) return
        generation++
        removeCallbacks(recovery); recoveryQueued = false; recoveryAttempts = 0
        decodePending=false
        stopVideo()
        animated?.stop();animated=null;movieView?.playing=false;movieView=null;image=null
        texture=null;removeAllViews()
        currentPath=file?.path;currentRevision=revision
        if(file==null || !file.isFile) return
        if(file.extension == "video") {
            texture=TextureView(context).also { view ->
                view.isOpaque=false
                view.surfaceTextureListener=object:TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(value:SurfaceTexture,w:Int,h:Int) { if(active) startVideo() }
                    override fun onSurfaceTextureSizeChanged(value:SurfaceTexture,w:Int,h:Int) { fitVideo() }
                    override fun onSurfaceTextureDestroyed(value:SurfaceTexture):Boolean { stopVideo();return true }
                    override fun onSurfaceTextureUpdated(value:SurfaceTexture) {}
                }
                addView(view,LayoutParams(-1,-1))
            }
            if(active) startVideo()
            return
        }
        val request=generation
        decodePending=true
        loader.execute {
            var movie: Movie? = null
            var drawable: Drawable? = null
            try {
                if(Build.VERSION.SDK_INT>=28) {
                    drawable=ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder,info,_ ->
                        val scale=minOf(1f,1440f/info.size.width,2400f/info.size.height,
                            kotlin.math.sqrt(2_000_000.0/(info.size.width.toDouble()*info.size.height)).toFloat())
                        decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1))
                    }
                } else {
                    // GIF decoding on API 27; newer Android uses AnimatedImageDrawable for GIF/WebP.
                    movie=Movie.decodeFile(file.path)
                    if(movie==null) {
                        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                        BitmapFactory.decodeFile(file.path,bounds)
                        var sample=1
                        while(bounds.outWidth/sample>1440 || bounds.outHeight/sample>2400 || (bounds.outWidth.toLong()/sample)*(bounds.outHeight/sample)>2_000_000L) sample*=2
                        val bitmap=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample })
                        if(bitmap!=null) drawable=android.graphics.drawable.BitmapDrawable(resources,bitmap)
                    }
                }
            } catch(error:Throwable) { HLog.e("[Hchat:HomeSidePanel] 媒体解码失败",error) }
            post {
                if(disposed || generation!=request) return@post
                decodePending=false
                if(movie!=null) {
                    movieView=GifView(context,movie!!).also { addView(it,LayoutParams(-1,-1)) }
                } else if(drawable!=null) {
                    animated=drawable as? Animatable
                    if(Build.VERSION.SDK_INT>=28 && drawable is android.graphics.drawable.AnimatedImageDrawable)
                        (drawable as android.graphics.drawable.AnimatedImageDrawable).repeatCount=android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE
                    image=ImageView(context).apply { scaleType=ImageView.ScaleType.CENTER_CROP;setImageDrawable(drawable) }
                    addView(image,LayoutParams(-1,-1))
                }
                if(active) resumePlayback(restartPending)
            }
        }
    }
    fun setPlayback(visible:Boolean,restart:Boolean=false) {
        if(disposed) return
        val changed=active!=visible
        active=visible
        if(!visible) {
            restartPending=false
            animated?.stop();movieView?.playing=false;stopVideo()
        } else if(changed || restart) {
            restartPending=restart || changed
            if(!decodePending) resumePlayback(restart || changed)
        }
    }
    private fun resumePlayback(restart:Boolean) {
        if(!active) return
        restartPending=false
        if(restart) { animated?.stop();movieView?.restart() }
        animated?.start();movieView?.playing=true
        if(texture!=null) startVideo()
    }
    private fun startVideo() {
        if(!active || player!=null || texture?.isAvailable!=true) return
        val path=currentPath ?: return
        val output=Surface(texture!!.surfaceTexture)
        surface=output
        val next=MediaPlayer()
        player=next
        try {
            next.setDataSource(path);next.setSurface(output);next.setVolume(0f,0f);next.isLooping=true
            next.setOnPreparedListener { ready ->
                if(player!==ready || !active) return@setOnPreparedListener
                fitVideo();ready.seekTo(0);ready.start()
            }
            next.setOnVideoSizeChangedListener { _,_,_ -> fitVideo() }
            next.setOnErrorListener { failed,what,extra ->
                if(player===failed) { HLog.e("[Hchat:HomeSidePanel] 视频播放失败 $what/$extra");stopVideo();scheduleRecovery() };true
            }
            next.prepareAsync()
        } catch(error:Throwable) { HLog.e("[Hchat:HomeSidePanel] 视频打开失败",error);stopVideo();scheduleRecovery() }
    }
    private fun fitVideo() {
        val view=texture ?: return
        val media=player ?: return
        runCatching {
            val vw=media.videoWidth.toFloat();val vh=media.videoHeight.toFloat()
            if(vw<=0f || vh<=0f || view.width==0 || view.height==0) return
            val scale=maxOf(view.width/vw,view.height/vh)
            view.setTransform(Matrix().apply { setScale(vw*scale/view.width,vh*scale/view.height,view.width/2f,view.height/2f) })
        }
    }
    private fun stopVideo() {
        val old=player;player=null
        runCatching { old?.setOnPreparedListener(null);old?.setOnErrorListener(null);old?.release() }
        surface?.release();surface=null
    }
    fun dispose() {
        if(disposed) return
        setPlayback(false)
        removeCallbacks(recovery); recoveryQueued = false
        disposed=true;generation++;loader.shutdownNow()
        image?.setImageDrawable(null);removeAllViews()
    }
    private class GifView(context:Context,private val movie:Movie):View(context) {
        var playing=false
            set(value){field=value;if(value) invalidate() else removeCallbacks(frame)}
        init { setLayerType(View.LAYER_TYPE_SOFTWARE,null) }
        private var start=0L
        private val frame=Runnable { invalidate() }
        fun restart(){start=0L}
        override fun onDraw(canvas:Canvas) {
            if(movie.width()<=0 || movie.height()<=0) return
            val now=android.os.SystemClock.uptimeMillis()
            if(start==0L) start=now
            movie.setTime(((now-start)%movie.duration().coerceAtLeast(100)).toInt())
            val scale=maxOf(width.toFloat()/movie.width(),height.toFloat()/movie.height())
            val save=canvas.save()
            canvas.translate((width-movie.width()*scale)/2f,(height-movie.height()*scale)/2f)
            canvas.scale(scale,scale);movie.draw(canvas,0f,0f);canvas.restoreToCount(save)
            if(playing && isShown) postDelayed(frame,33L)
        }
    }
}
