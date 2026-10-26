package t.mma.hooks.items.homeextension

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import t.mma.hooks.items.homesidepanel.SidePanelImages
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.*

/** Decoration is procedural. Text, portrait, spectrum and controls are separate live layers. */
internal open class HomeMusicPaintedView(c:Context,private val style:Int,private val full:Boolean,
    private val state:()->MusicPaintState,private val action:(String)->Unit,private val seek:(Float)->Unit):View(c){
    private val meteorPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {strokeCap=Paint.Cap.ROUND}
    private val meteorMatrix=Matrix()
    private val meteorShader=LinearGradient(0f,0f,1f,0f,0x00FF7A1A,0xFFFFB02E.toInt(),Shader.TileMode.CLAMP)
    private val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val icons=mutableMapOf<String,MusicIcon>()
    private val hits=mutableListOf<Pair<RectF,String>>()
    private var progress=RectF();private var tracking=false;private var downX=0f;private var downY=0f
    private var decoration:Bitmap?=null;private var decorationStyle=0;private var cardBackground:Bitmap?=null;private var backgroundRevision=-1L;private var backgroundGeneration=0;private var portrait:Bitmap?=null;private var custom=false;private var coverKey="";private var generation=0
    private var animationPhase=0f
    private var animationFrameAt=0L
    internal var homeCompact=false
    private var lastPlaying=false
        private var backgroundLoading=false
    private var backgroundDrawable:CalendarBackgroundDrawable?=null
    private var backgroundTransform:PhotoTransformController?=null
    private var backgroundTouch=false
    private var lyricSource="";private var lines=emptyList<Pair<Int,String>>();private var scroll=-1f;private var lastPosition=-1
    private var sx=1f;private var sy=1f
    private val visibleRect=Rect()
    private var portraitAngle=0f
    private var rotationFrameAt=0L
    private var scheduled=false
    private var needsRefresh=true
    private var customGeneration=0
    private var customLoading=false
    private var cachedState:MusicPaintState?=null
    private val textPaint=android.text.TextPaint()
    private val boldTypeface=Typeface.create("sans-serif",Typeface.BOLD)
    private val changed:()->Unit={
        needsRefresh=true
        if(!state().playing){portraitAngle=0f;rotationFrameAt=0L;animationFrameAt=0L;animationPhase=0f;removeCallbacks(frame);scheduled=false;invalidate()}
        resumeFrames()
    }
    private val scrollListener=android.view.ViewTreeObserver.OnScrollChangedListener{resumeFrames()}
    private var observer:android.view.ViewTreeObserver?=null
    private fun visibleForFrames()=isAttachedToWindow&&isShown&&windowVisibility==VISIBLE&&hasWindowFocus()&&getGlobalVisibleRect(visibleRect)
    private val frame=object:Runnable{
        override fun run(){
            scheduled=false
            if(!visibleForFrames()){rotationFrameAt=0L;animationFrameAt=0L;return}
            val snapshot=state();cachedState=snapshot
            val now=android.os.SystemClock.uptimeMillis()
            if(style in 2..3&&!snapshot.playing){portraitAngle=0f;rotationFrameAt=0L;animationPhase=0f;animationFrameAt=0L}
            else if(style in 2..3){
                if(rotationFrameAt!=0L)portraitAngle=advancePortraitAngle(portraitAngle,now-rotationFrameAt,true)
                if(animationFrameAt!=0L)animationPhase=(animationPhase+(now-animationFrameAt)/1800f).mod(1f)
                rotationFrameAt=now;animationFrameAt=now
            }else rotationFrameAt=0L
            if(lastPlaying&&!snapshot.playing){portraitAngle=0f;animationPhase=0f;rotationFrameAt=0L;animationFrameAt=0L}
            lastPlaying=snapshot.playing
            invalidate();needsRefresh=false
            if(snapshot.playing){scheduled=true;postDelayed(this,33)}
        }
    }
    private fun resumeFrames(){
        if(!visibleForFrames()){removeCallbacks(frame);scheduled=false;rotationFrameAt=0L;animationFrameAt=0L;return}
        if(!scheduled){scheduled=true;postOnAnimation(frame)}
    }
    init{isClickable=true;isFocusable=true;contentDescription="样式${style}音乐播放器"}
    override fun onAttachedToWindow(){
        super.onAttachedToWindow()
        HomeMusic.setListener(changed)
        loadStyleTwoBackground()
        observer=viewTreeObserver.also{it.addOnScrollChangedListener(scrollListener)}
        loadCustomPortrait()
        resumeFrames()
    }
    private fun loadStyleTwoBackground(){
        if(full||style!=2||backgroundLoading)return
        val revision=HomeMusicStyleTwoBackground.revision(context)
        if(revision==backgroundRevision)return
        val request=++backgroundGeneration
        backgroundLoading=true
        worker.execute{
            val bitmap=HomeMusicStyleTwoBackground.load(context)
            post{
                if(!isAttachedToWindow||backgroundGeneration!=request)return@post
                backgroundTransform?.detach();backgroundTransform=null
                cardBackground=bitmap;backgroundRevision=revision;backgroundLoading=false
                backgroundDrawable=bitmap?.let{CalendarBackgroundDrawable(it,14*resources.displayMetrics.density,255)}
                if(bitmap!=null&&!full){
                    backgroundTransform=PhotoTransformController(this,"music_style2_$revision",{bitmap.width to bitmap.height},{backgroundDrawable?.setTransform(it);invalidate()})
                    backgroundTransform?.updateMatrix()
                }
                needsRefresh=true;invalidate()
                if(HomeMusicStyleTwoBackground.revision(context)!=revision)loadStyleTwoBackground()
            }
        }
    }
    private fun loadCustomPortrait(attempt:Int=0){
        val slot=if(style==2)HomeExtensionSettings.MUSIC_AVATAR_SLOT else HomeExtensionSettings.MUSIC_THREE_AVATAR_SLOT
        customLoading=true
        val request=++customGeneration
        worker.execute{
            val exists=SidePanelImages.hasImage(context,slot)
            val bitmap=runCatching{SidePanelImages.load(context,slot)}.getOrNull()
            post{
                if(!isAttachedToWindow||customGeneration!=request)return@post
                if(bitmap!=null){portrait=bitmap;custom=true;coverKey=""}
                else if(!exists&&custom){custom=false;portrait=null;coverKey=""}
                customLoading=false;needsRefresh=true;resumeFrames()
                if(exists&&bitmap==null&&attempt<2)postDelayed({if(isAttachedToWindow&&customGeneration==request)loadCustomPortrait(attempt+1)},1000L*(attempt+1))
            }
        }
    }
    override fun onDetachedFromWindow(){
        backgroundTransform?.detach();backgroundTransform=null;backgroundDrawable=null
        HomeMusic.removeListener(changed)
        observer?.takeIf{it.isAlive}?.removeOnScrollChangedListener(scrollListener);observer=null
        removeCallbacks(frame);scheduled=false;rotationFrameAt=0L;animationFrameAt=0L;generation++;customGeneration++;backgroundGeneration++;customLoading=false;backgroundLoading=false;coverKey="";cachedState=null;decoration=null;cardBackground=null;backgroundRevision=-1L
        super.onDetachedFromWindow()
    }
    override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int){super.onLayout(changed,left,top,right,bottom);resumeFrames()}
    override fun onWindowFocusChanged(hasWindowFocus:Boolean){super.onWindowFocusChanged(hasWindowFocus);if(hasWindowFocus&&isAttachedToWindow){if(!customLoading)loadCustomPortrait();loadStyleTwoBackground()};resumeFrames()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);resumeFrames()}
    override fun onVisibilityChanged(changedView:View,visibility:Int){super.onVisibilityChanged(changedView,visibility);if(isAttachedToWindow)post{resumeFrames()}}
    override fun onMeasure(w:Int,h:Int){val width=MeasureSpec.getSize(w);val desired=if(style==2&&!full)((if(homeCompact)118 else HomeExtensionSettings.MUSIC_STYLE_TWO_HEIGHT_DP)*resources.displayMetrics.density).toInt() else (width*0.58f).toInt();setMeasuredDimension(width,if(full)MeasureSpec.getSize(h)else resolveSize(desired,h))}
    private fun fill(canvas:Canvas,rect:RectF,color:Int,r:Float=0f){p.shader=null;p.style=Paint.Style.FILL;p.color=color;if(r>0)canvas.drawRoundRect(rect,r,r,p)else canvas.drawRect(rect,p)}
    private fun stroke(canvas:Canvas,path:Path,color:Int,width:Float){p.shader=null;p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND;p.strokeWidth=width;p.color=color;canvas.drawPath(path,p);p.style=Paint.Style.FILL}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas);val w=width.toFloat();val h=height.toFloat();if(w<=0||h<=0)return
        // Normalised layout only positions components. Glyphs, discs and portraits always use one scale.
        sx=w/360f;sy=h/(if(full)720f else 209f);hits.clear();val s=cachedState?.takeUnless{needsRefresh}?:state().also{cachedState=it};loadCover(s.coverUrl)
        if(style in 2..3&&!s.playing){portraitAngle=0f;rotationFrameAt=0L}
        val alpha=HomeExtensionSettings.preferences(context).getInt(HomeExtensionSettings.MUSIC_ALPHA,100).coerceIn(0,100)*255/100
        if(!full&&style==2){
            val rect=RectF(0f,0f,w,h)
            val image=cardBackground
            if(image!=null){backgroundDrawable?.let{it.bounds=Rect(0,0,width,height);it.alpha=alpha;it.draw(canvas)}}
            else fill(canvas,rect,Color.argb(alpha,48,51,49),14*sx)
            fill(canvas,RectF(8*sx,8*sy,w-8*sx,h-8*sy),0x33131717,12*sx)
            styleTwoHome(canvas,s)
            return
        }
        if(decoration?.width!=width||decoration?.height!=height||decorationStyle!=style){decoration=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).also{val bg=Canvas(it);if(style==2)graffiti(bg,w,h)else vinylBackground(bg,w,h)};decorationStyle=style};p.alpha=alpha;decoration?.let{canvas.drawBitmap(it,0f,0f,p)};p.alpha=255
        if(full){
            if(style==2){drawPortrait(canvas,112*sx,187*sy,51*sx);text(canvas,s.title,50*sx,524*sy,23*sx,83*sx,Color.BLACK);text(canvas,"${time(s.position)} / ${time(s.duration)}",50*sx,546*sy,12*sx,105*sx,Color.BLACK);spectrum(canvas,s,RectF(159*sx,501*sy,289*sx,546*sy));tools(canvas,s,384*sy,Color.BLACK)}
            else{drawPortrait(canvas,180*sx,204*sy,vinylPortraitRadius());text(canvas,s.title,180*sx,408*sy,29*sx,290*sx,Color.WHITE,true);text(canvas,"${time(s.position)} / ${time(s.duration)}",180*sx,438*sy,15*sx,290*sx,Color.WHITE,true);lyrics(canvas,s);tools(canvas,s,682*sy,Color.WHITE)}
            icon(canvas,"⌕","search",326*sx,if(style==3)57*sy else 35*sy,23*sx,Color.WHITE)
            if(style==3)spectrum(canvas,s,RectF(18*sx,9*sy,342*sx,29*sy))
            val py=if(style==2)568*sy else 582*sy;drawProgress(canvas,s,43*sx,317*sx,py,style==2)
            val cy=if(style==2)605*sy else 634*sy;controls(canvas,s,cy,style==2)
        }else{
            drawPortrait(canvas,if(style==2)89*sx else 93*sx,if(style==2)105*sy else 116*sy,if(style==2)32*sx else vinylPortraitRadius())
            val left=if(style==2)143*sx else 171*sx;val top=if(style==2)86*sy else 87*sy
            text(canvas,s.title,left,top,if(style==2)15*sx else 17*sx,if(style==2)53*sx else 134*sx,if(style==2)Color.BLACK else Color.WHITE)
            text(canvas,"${time(s.position)} / ${time(s.duration)}",left,top+15*sx,9*sx,136*sx,if(style==2)Color.BLACK else Color.WHITE)
            spectrum(canvas,s,if(style==2)RectF(202*sx,68*sy,292*sx,99*sy)else RectF(31*sx,22*sy,329*sx,36*sy))
            drawProgress(canvas,s,left,317*sx,if(style==2)109*sy else 116*sy,style==2)
            val y=if(style==2)133*sy else 145*sy;val color=if(style==2)Color.BLACK else Color.WHITE
            icon(canvas,"|◀","previous",if(style==2)187*sx else 204*sx,y,19*sx,color)
            val x=if(style==2)220*sx else 246*sx;playIcon(canvas,s,x,y,if(style==2)20*sx else 18*sx,style==2)
            icon(canvas,"▶|","next",if(style==2)253*sx else 288*sx,y,19*sx,color)
            if(style==2)icon(canvas,"☰","favorites",281*sx,y,15*sx,color)
            else {
                icon(canvas,"▤","lyrics",204*sx,184*sy,17*sx,color)
                favoriteIcon(canvas,s,246*sx,184*sy,17*sx,color)
                icon(canvas,"☰","favorites",288*sx,184*sy,17*sx,color)
            }
        }
    }
    private fun styleTwoHome(canvas:Canvas,s:MusicPaintState){
        val coverCx=64*sx;val coverCy=height*0.40f;val radius=31*sx
        drawPortrait(canvas,coverCx,coverCy,radius)
        canvas.save();canvas.rotate(portraitAngle,coverCx,coverCy)
        p.color=0x66FFFFFF; p.style=Paint.Style.STROKE; p.strokeWidth=1.2f*sx
        canvas.drawCircle(coverCx,coverCy,radius+2*sx,p);p.style=Paint.Style.FILL
        canvas.restore()
        val infoLeft=108*sx;val infoRight=width-112*sx
        text(canvas,s.title,infoLeft,coverCy-10*sy,15*sx,(infoRight-infoLeft).coerceAtLeast(20f),Color.WHITE)
        text(canvas,s.artist.ifBlank{"${time(s.position)} / ${time(s.duration)}"},infoLeft,coverCy+12*sy,11*sx,(infoRight-infoLeft).coerceAtLeast(20f),0xFFE2E6E4.toInt())
        drawElectricArc(canvas,s,infoLeft,infoRight,coverCy+45*sy)
        val controlX=width-82*sx;val gap=25*sx;val controlY=coverCy
        icon(canvas,"|◀","previous",controlX-gap,controlY,15*sx,Color.WHITE)
        playIcon(canvas,s,controlX,controlY,15*sx,false)
        icon(canvas,"▶|","next",controlX+gap,controlY,15*sx,Color.WHITE)
        val py=height-22*sy
        val left=35*sx;val right=width-35*sx
        drawProgress(canvas,s,left,right,py,true)
        text(canvas,time(s.position),left,py-8*sy,9*sx,42*sx,Color.WHITE)
        text(canvas,time(s.duration),right-42*sx,py-8*sy,9*sx,42*sx,Color.WHITE)
        if(s.playing){
            val x=left+(right-left)*(if(s.duration>0)s.position.toFloat()/s.duration else 0f).coerceIn(0f,1f)
            val tail=(18+6*sin(animationPhase*2*PI).toFloat())*sx
            meteorMatrix.setScale(tail+3*sx,1f);meteorMatrix.postTranslate(x-tail,py);meteorShader.setLocalMatrix(meteorMatrix)
            meteorPaint.shader=meteorShader;meteorPaint.strokeWidth=5*sx;meteorPaint.alpha=(180+75*sin(animationPhase*2*PI)).toInt().coerceIn(0,255)
            canvas.save();canvas.clipRect(left,py-8*sx,right,py+8*sx);canvas.drawLine(x-tail,py,x+3*sx,py,meteorPaint);canvas.restore()
            p.shader=null;p.color=0xFFFFA12A.toInt();canvas.drawCircle(x,py,3.5f*sx,p)
        }
    }
    private val arcPath=Path()
    private fun drawElectricArc(canvas:Canvas,s:MusicPaintState,left:Float,right:Float,y:Float){
        if(!s.playing)return
        arcPath.reset();var peak=0f
        for(i in 0 until 32){
            val amplitude=s.spectrum.getOrElse(i*2){0f}.coerceIn(0f,1f);peak=max(peak,amplitude)
            val x=left+(right-left)*i/31f
            val dy=sin(i*2.31f+animationPhase*2*PI).toFloat()*amplitude*11*sy
            if(i==0)arcPath.moveTo(x,y+dy)else arcPath.lineTo(x,y+dy)
        }
        if(peak<=0.01f)return
        stroke(canvas,arcPath,0x44FF9D32,5*sx)
        stroke(canvas,arcPath,0xFFFFA838.toInt(),1.4f*sx)
        stroke(canvas,arcPath,0xFFFFF1CF.toInt(),0.5f*sx)
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);backgroundTransform?.updateMatrix()}
    private fun graffiti(c:Canvas,w:Float,h:Float){
        fill(c,RectF(0f,0f,w,h),0xFF303331.toInt(),14*sx)
        texture(c,w,h,0x225E625D)
        val save=c.save();c.scale(sx,sy)
        // Fixed paths form the paint and paper decoration, never sample player components.
        val pink=Path().apply{moveTo(29f,if(full)102f else 48f);cubicTo(78f,if(full)149f else 76f,130f,if(full)90f else 34f,169f,if(full)139f else 74f);cubicTo(105f,if(full)112f else 45f,65f,if(full)200f else 117f,24f,if(full)155f else 89f)}
        stroke(c,pink,0xFFFF16AB.toInt(),if(full)18f else 12f)
        val cyan=Path().apply{moveTo(111f,if(full)116f else 48f);cubicTo(238f,if(full)124f else 84f,287f,if(full)343f else 168f,180f,if(full)355f else 146f);cubicTo(229f,if(full)254f else 79f,70f,if(full)262f else 136f,68f,if(full)305f else 148f)}
        stroke(c,cyan,0xFF00F2E7.toInt(),if(full)24f else 15f)
        val rnd=java.util.Random(19);for(i in 0..95){p.color=if(i%2==0)0xFFFC149B.toInt()else 0xFF02D6CF.toInt();val x=20+rnd.nextFloat()*315;val y=if(full)80+rnd.nextFloat()*310 else 35+rnd.nextFloat()*135;c.drawCircle(x,y,0.5f+rnd.nextFloat()*2,p)}
        val paper=Path().apply{moveTo(27f,if(full)94f else 46f);lineTo(132f,if(full)79f else 35f);lineTo(170f,if(full)279f else 149f);lineTo(146f,if(full)285f else 154f);lineTo(137f,if(full)279f else 150f);lineTo(128f,if(full)289f else 158f);lineTo(40f,if(full)307f else 165f);close()};p.color=0xFFDFE2D6.toInt();c.drawPath(paper,p)
        p.color=0xFF66695C.toInt();for(i in 0..65){val x=40+rnd.nextFloat()*80;val y=if(full)104+rnd.nextFloat()*174 else 53+rnd.nextFloat()*93;c.drawCircle(x,y,0.35f,p)}
        fill(c,if(full)RectF(37f,463f,322f,648f)else RectF(139f,62f,323f,161f),0xAAB3BBB3.toInt(),if(full)19f else 10f)
        p.style=Paint.Style.STROKE;p.strokeWidth=0.8f;p.color=0x88FFFFFF.toInt();c.drawRoundRect(if(full)RectF(37f,463f,322f,648f)else RectF(139f,62f,323f,161f),if(full)19f else 10f,if(full)19f else 10f,p);p.style=Paint.Style.FILL
        // Neon brush mark and hand-drawn signature flourish are purely decoration.
        val lime=Path().apply{moveTo(280f,if(full)631f else 150f);lineTo(313f,if(full)614f else 137f);lineTo(295f,if(full)652f else 166f);lineTo(337f,if(full)633f else 145f)};stroke(c,lime,0xFFBDFF00.toInt(),if(full)10f else 7f)
        val ink=Path().apply{moveTo(17f,if(full)361f else 162f);cubicTo(37f,if(full)305f else 128f,45f,if(full)388f else 189f,65f,if(full)345f else 157f);cubicTo(64f,if(full)383f else 191f,80f,if(full)323f else 151f,104f,if(full)362f else 174f)};stroke(c,ink,Color.WHITE,2f)
        c.restoreToCount(save)
    }
    private fun vinylBackground(c:Canvas,w:Float,h:Float){
        fill(c,RectF(0f,0f,w,h),0xFF111313.toInt(),12*sx);texture(c,w,h,0x333E4140)
        val cx=if(full)180*sx else 93*sx;val cy=if(full)204*sy else 116*sy;val radius=if(full)min(153*sx,153*sy) else 68*sx
        p.shader=RadialGradient(cx,cy,radius,intArrayOf(0xFF343635.toInt(),0xFF070808.toInt(),0xFF333534.toInt(),0xFF060707.toInt()),floatArrayOf(0f,0.49f,0.73f,1f),Shader.TileMode.CLAMP);c.drawCircle(cx,cy,radius,p);p.shader=null
        p.style=Paint.Style.STROKE;p.strokeWidth=0.6f*sx;for(i in 0..68){p.color=if(i%3==0)0xFF424543.toInt()else 0xFF252827.toInt();c.drawCircle(cx,cy,radius*(0.48f+i*0.0073f),p)};p.style=Paint.Style.FILL

        if(full){fill(c,RectF(34*sx,373*sy,326*sx,555*sy),0x773E4140,19*sx);p.color=0x447B817C;p.style=Paint.Style.STROKE;p.strokeWidth=sx;c.drawRoundRect(RectF(34*sx,373*sy,326*sx,555*sy),19*sx,19*sx,p);p.style=Paint.Style.FILL}
    }
    private fun texture(c:Canvas,w:Float,h:Float,color:Int){val r=java.util.Random(71);p.color=color;for(i in 0..1800){val x=r.nextFloat()*w;val y=r.nextFloat()*h;c.drawCircle(x,y,0.25f+0.35f*sx,p)}}
    // The retained upper arm ends at the first bend. Account for its round cap,
    // then overlap its visible edge by 2 physical dp rather than scaled artwork units.
    private fun vinylPortraitRadius():Float {
        val cx=if(full)180*sx else 93*sx;val cy=if(full)204*sy else 116*sy
        val ex=(if(full)315*sx else 149*sx)-2*sx
        val ey=(if(full)91*sy else 61*sy)+27*sx
        return hypot(ex-cx,ey-cy)-1.5f*sx+2f*resources.displayMetrics.density
    }
    private fun drawVinylArm(c:Canvas){
        val tx=if(full)315*sx else 149*sx;val ty=if(full)91*sy else 61*sy
        val arm=Path().apply{moveTo(tx,ty);lineTo(tx-2*sx,ty+27*sx)}
        stroke(c,arm,0xFF949694.toInt(),3*sx);p.color=0xFF686B68.toInt();c.drawCircle(tx,ty,5*sx,p)
    }
    private fun drawPortrait(c:Canvas,x:Float,y:Float,r:Float){val save=c.save();c.clipPath(Path().apply{addCircle(x,y,r,Path.Direction.CW)});if(style==3||style==2&&!full)c.rotate(portraitAngle,x,y);val b=portrait;if(b!=null){val n=min(b.width,b.height);p.color=Color.WHITE;c.drawBitmap(b,Rect((b.width-n)/2,(b.height-n)/2,(b.width+n)/2,(b.height+n)/2),RectF(x-r,y-r,x+r,y+r),p)}else{p.color=0xFF535A57.toInt();c.drawCircle(x,y,r,p);p.color=0xFFB7C0BB.toInt();c.drawCircle(x,y-r*0.23f,r*0.25f,p);c.drawOval(RectF(x-r*0.55f,y+r*0.08f,x+r*0.55f,y+r*0.86f),p)};c.restoreToCount(save);if(style==3)drawVinylArm(c)}
    private fun loadCover(url:String){
        if(custom)return
        // Cache lookup is immediate, even while the optional custom photo is checked.
        // This lets a new player window draw the existing album art on its first frame.
        covers.get(url)?.let{bitmap->
            if(!custom){portrait=bitmap;coverKey=url}
            return
        }
        if(customLoading||url==coverKey)return
        coverKey=url
        val request=++generation
        if(url.isBlank())return
        worker.execute{
            val bitmap=runCatching{
                val conn=URL(url).openConnection() as HttpURLConnection
                try{conn.connectTimeout=8000;conn.readTimeout=8000;conn.inputStream.use{BitmapFactory.decodeStream(it)}}
                finally{conn.disconnect()}
            }.getOrNull()
            // Publish before posting to the original view, which may already be detached.
            if(bitmap!=null)covers.put(url,bitmap)
            post{if(isAttachedToWindow&&generation==request&&!custom){if(bitmap!=null)portrait=bitmap;invalidate()}}
        }
    }

    private fun text(c:Canvas,s:String,x:Float,y:Float,size:Float,max:Float,color:Int,center:Boolean=false){p.shader=null;p.color=color;p.typeface=boldTypeface;p.textSize=size;textPaint.set(p);val t=android.text.TextUtils.ellipsize(s,textPaint,max,android.text.TextUtils.TruncateAt.END).toString();c.drawText(t,if(center)x-p.measureText(t)/2 else x,y,p)}
    private fun spectrum(c:Canvas,s:MusicPaintState,area:RectF){p.shader=LinearGradient(area.left,0f,area.right,0f,if(style==2)intArrayOf(0xFFFF109A.toInt(),0xFF08F6DE.toInt())else intArrayOf(0xFFC2FF00.toInt(),0xFF00FFD9.toInt(),0xFFFF139C.toInt()),null,Shader.TileMode.CLAMP);for(i in 0..63){val value=if(s.playing)s.spectrum.getOrElse(i){0f}else 0f;val bw=area.width()/64;val hh=max(sx,value*area.height());c.drawRect(area.left+i*bw,area.bottom-hh,area.left+(i+0.65f)*bw,area.bottom,p)};p.shader=null}
    private fun drawProgress(c:Canvas,s:MusicPaintState,left:Float,right:Float,y:Float,lime:Boolean){
        progress=RectF(left,y-14*sx,right,y+14*sx)
        p.shader=null;p.color=if(style==2&&!full)0xAAFFFFFF.toInt()else 0xFF777D79.toInt()
        c.drawRoundRect(RectF(left,y-sx,right,y+sx),sx,sx,p)
        val x=left+(right-left)*(if(s.duration>0)s.position.toFloat()/s.duration else 0f).coerceIn(0f,1f)
        p.color=if(style==2&&!full)Color.WHITE else if(lime)0xFFABFF00.toInt()else Color.WHITE
        c.drawRoundRect(RectF(left,y-sx,x,y+sx),sx,sx,p);c.drawCircle(x,y,2.5f*sx,p)
        if(full){text(c,time(s.position),left,y+16*sx,9*sx,70*sx,Color.LTGRAY);text(c,time(s.duration),right-35*sx,y+16*sx,9*sx,45*sx,Color.LTGRAY)}
    }

    private fun icon(c:Canvas,key:String,act:String,x:Float,y:Float,size:Float,color:Int){val bounds=size/0.52f;val v=icons.getOrPut(key){MusicIcon(context,key)};v.setTextColor(color);if(v.width!=bounds.toInt()||v.height!=bounds.toInt())v.layout(0,0,bounds.toInt(),bounds.toInt());val save=c.save();c.translate(x-bounds/2,y-bounds/2);v.draw(c);c.restoreToCount(save);val hit=max(18*sx,size*0.7f);hits+=RectF(x-hit,y-hit,x+hit,y+hit) to act}
    private fun playIcon(c:Canvas,s:MusicPaintState,x:Float,y:Float,r:Float,dark:Boolean){p.color=if(dark)Color.BLACK else Color.WHITE;if(dark)c.drawCircle(x,y,r,p)else{p.style=Paint.Style.STROKE;p.strokeWidth=1.5f*sx;c.drawCircle(x,y,r,p);p.style=Paint.Style.FILL};icon(c,if(s.playing)"Ⅱ"else"▶","play",x,y,r*0.9f,if(dark)0xFFB4FF00.toInt()else Color.WHITE)}
    private fun favoriteIcon(c:Canvas,s:MusicPaintState,x:Float,y:Float,size:Float,color:Int){
        icon(c,if(s.favorite)"♥" else "♡","favorite",x,y,size,if(s.favorite)Color.RED else color)
    }
    private fun controls(c:Canvas,s:MusicPaintState,y:Float,dark:Boolean){
        val color=if(dark)Color.BLACK else Color.WHITE
        icon(c,"|◀","previous",(if(style==2)64f else 107f)*sx,y,25*sx,color)
        playIcon(c,s,(if(style==2)132f else 180f)*sx,y,25*sx,dark)
        icon(c,"▶|","next",(if(style==2)200f else 253f)*sx,y,25*sx,color)
        if(style==2){favoriteIcon(c,s,252*sx,y,21*sx,color);icon(c,"☰","favorites",300*sx,y,18*sx,color)}
    }
    private fun tools(c:Canvas,s:MusicPaintState,y:Float,color:Int){
        if(style==2){
            val xs=floatArrayOf(178f,216f,254f,292f)
            arrayOf("☷","♫","◷","▤").forEachIndexed{i,key->icon(c,key,arrayOf("versions","quality","timer","lyrics")[i],xs[i]*sx,y,21*sx,color)}
        }else{
            val keys=arrayOf("☷","♫","♡","◷","▤")
            val actions=arrayOf("versions","quality","favorite","timer","lyrics")
            keys.forEachIndexed{i,key->val x=(60f+i*60f)*sx
                if(actions[i]=="favorite")favoriteIcon(c,s,x,y,21*sx,color)else icon(c,key,actions[i],x,y,21*sx,color)
            }
        }
    }
    private fun lyrics(c:Canvas,s:MusicPaintState){if(lyricSource!=s.lyric){lyricSource=s.lyric;lines=HomeMusicLyrics.parse(s.lyric);scroll=-1f;lastPosition=-1};val save=c.save();c.clipRect(43*sx,461*sy,317*sx,544*sy);if(lines.isEmpty())text(c,"暂无同步歌词",180*sx,495*sy,16*sx,260*sx,Color.LTGRAY,true)else{val index=HomeMusicLyrics.indexAt(lines,s.position);if(lastPosition<0||abs(lastPosition-s.position)>1500||index<scroll||!s.playing)scroll=index.toFloat()else scroll+=(index-scroll)*0.09f;lastPosition=s.position;for(i in max(0,floor(scroll).toInt()-1)..min(lines.lastIndex,ceil(scroll).toInt()+2))text(c,lines[i].second,180*sx,489*sy+(i-scroll)*35*sy,17*sx,263*sx,if(i==index)Color.WHITE else Color.LTGRAY,true)};c.restoreToCount(save)}
    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(!full&&backgroundTransform!=null){
            if(e.actionMasked==MotionEvent.ACTION_DOWN)backgroundTouch=!progress.contains(e.x,e.y)&&hits.none{it.first.contains(e.x,e.y)}
            if(backgroundTouch){
                val editing=backgroundTransform?.isEditing()==true
                backgroundTransform?.onTouchEvent(e)
                if(editing||backgroundTransform?.isEditing()==true){if(e.actionMasked==MotionEvent.ACTION_UP||e.actionMasked==MotionEvent.ACTION_CANCEL)backgroundTouch=false;return true}
            }
        }
        when(e.actionMasked){MotionEvent.ACTION_DOWN->{downX=e.x;downY=e.y;tracking=progress.contains(e.x,e.y);parent?.requestDisallowInterceptTouchEvent(tracking);return true};MotionEvent.ACTION_MOVE->{if(tracking)seek(((e.x-progress.left)/progress.width()).coerceIn(0f,1f));return true};MotionEvent.ACTION_CANCEL->{tracking=false;parent?.requestDisallowInterceptTouchEvent(false);return true};MotionEvent.ACTION_UP->{parent?.requestDisallowInterceptTouchEvent(false);if(tracking){seek(((e.x-progress.left)/progress.width()).coerceIn(0f,1f));tracking=false;return true};if(abs(e.x-downX)>12*sx||abs(e.y-downY)>12*sx)return true;performClick();val hit=hits.filter{it.first.contains(e.x,e.y)}.minByOrNull{val dx=e.x-it.first.centerX();val dy=e.y-it.first.centerY();dx*dx+dy*dy};if(hit!=null)action(hit.second)else if(!full)action("open");return true}};return true}
    override fun performClick():Boolean{super.performClick();return true}
    private fun time(ms:Int)="%02d:%02d".format(ms.coerceAtLeast(0)/60000,ms.coerceAtLeast(0)/1000%60)
    companion object{
        private val covers=object:android.util.LruCache<String,Bitmap>(12*1024*1024){
            override fun sizeOf(key:String,value:Bitmap)=value.byteCount
        }
        private val worker=Executors.newFixedThreadPool(2){Thread(it,"Hchat-MusicCover").apply{isDaemon=true}}}
}
internal data class MusicPaintState(val title:String,val position:Int,val duration:Int,val playing:Boolean,val spectrum:FloatArray,val lyric:String="",val coverUrl:String="",val favorite:Boolean=false,val artist:String="")

/** Paused portraits are immediately upright; playback keeps the 12-second turn. */
internal fun advancePortraitAngle(angle:Float,elapsedMs:Long,playing:Boolean):Float =
    if(playing)(angle+elapsedMs.coerceAtLeast(0).toFloat()*360f/12000f)%360f else 0f
