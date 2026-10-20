package t.mma.hooks.items.homeextension

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import t.mma.hooks.items.homesidepanel.SidePanelImages
import kotlin.math.*

internal class HomeMusicStyleThree(c:Context,private val full:Boolean,
    private val state:()->State,private val action:(String)->Unit,private val seek:(Float)->Unit):View(c){
    data class State(val title:String,val position:Int,val duration:Int,val playing:Boolean,val spectrum:FloatArray,val lyric:String)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val art=artwork(c,if(full)"player"else"card")
    private var portrait:Bitmap?=null
    private val dw=if(full)1080f else 2048f;private val dh=if(full)2048f else 1150f
    private var scaleY=1f;private var scale=1f;private var ox=0f;private var oy=0f
    private var downX=0f;private var downY=0f;private var tracking=false
    private var sourceLyric="";private var lines=emptyList<Pair<Int,String>>()
    private var displayedIndex=-1;private var scrollIndex=-1f;private var lastPosition=-1
    private val icons=mutableMapOf<String,MusicIcon>()
    private val frame=object:Runnable{override fun run(){invalidate();if(isAttachedToWindow)postDelayed(this,33)}}
    init{isClickable=true;isFocusable=true;contentDescription=if(full)"样式3黑胶音乐播放器"else"样式3黑胶音乐卡片"}
    override fun onAttachedToWindow(){super.onAttachedToWindow();portrait=SidePanelImages.load(context,HomeExtensionSettings.MUSIC_THREE_AVATAR_SLOT)?:artwork(context,"avatar");post(frame)}
    override fun onDetachedFromWindow(){removeCallbacks(frame);portrait=null;super.onDetachedFromWindow()}
    override fun onMeasure(w:Int,h:Int){val width=MeasureSpec.getSize(w);setMeasuredDimension(width,if(full)MeasureSpec.getSize(h)else(width*dh/dw).toInt())}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas);scale=width/dw;scaleY=if(full)height/dh else scale;ox=0f;oy=0f
        val save=canvas.save();canvas.translate(ox,oy);canvas.scale(scale,scaleY)
        paint.alpha=HomeExtensionSettings.preferences(context).getInt(HomeExtensionSettings.MUSIC_ALPHA,100).coerceIn(0,100)*255/100
        art?.let{canvas.drawBitmap(it,null,RectF(0f,0f,dw,dh),paint)};paint.alpha=255
        val cx=if(full)541f else 535f;val cy=if(full)577f else 638f;val radius=if(full)224f else 193f
        portrait?.let{b->val clip=canvas.save();canvas.clipPath(Path().apply{addCircle(cx,cy,radius,Path.Direction.CW)});val side=min(b.width,b.height);canvas.drawBitmap(b,Rect((b.width-side)/2,(b.height-side)/2,(b.width+side)/2,(b.height+side)/2),RectF(cx-radius,cy-radius,cx+radius,cy+radius),paint);canvas.restoreToCount(clip)}
        val s=state();drawSpectrum(canvas,s)
        if(full){drawIcon(canvas,"⌕",981f,151f,79f);drawTitle(canvas,s,540f,1162f,86f,820f,true);paint.textSize=45f;paint.typeface=Typeface.DEFAULT;paint.color=Color.WHITE;centerText(canvas,"${time(s.position)} / ${time(s.duration)}",540f,1240f);drawLyrics(canvas,s)}
        else{drawTitle(canvas,s,976f,479f,94f,800f,false);paint.textSize=48f;paint.typeface=Typeface.DEFAULT;paint.color=Color.WHITE;canvas.drawText("${time(s.position)} / ${time(s.duration)}",976f,549f,paint);drawIcon(canvas,"⌕",1774f,489f,70f)}
        val left=if(full)128f else 980f;val right=if(full)950f else 1802f;val y=if(full)1660f else 641f
        val fraction=if(s.duration>0)s.position.toFloat()/s.duration else 0f;paint.color=0xFF777777.toInt();canvas.drawRoundRect(RectF(left,y-4,right,y+4),4f,4f,paint);paint.color=Color.WHITE;canvas.drawRoundRect(RectF(left,y-4,left+(right-left)*fraction.coerceIn(0f,1f),y+4),4f,4f,paint);canvas.drawCircle(left+(right-left)*fraction.coerceIn(0f,1f),y,if(full)10f else 9f,paint)
        if(full){paint.textSize=26f;paint.color=0xFFCCCCCC.toInt();canvas.drawText(time(s.position),left,y+44,paint);val total=time(s.duration);canvas.drawText(total,right-paint.measureText(total),y+44,paint)}
        val controlY=if(full)1802f else 801f;val playX=if(full)541f else 1396f;val prevX=if(full)309f else 1159f;val nextX=if(full)776f else 1629f
        paint.style=Paint.Style.STROKE;paint.strokeWidth=if(full)4f else 4f;paint.color=Color.WHITE;canvas.drawCircle(playX,controlY,80f,paint);paint.style=Paint.Style.FILL
        drawIcon(canvas,if(s.playing)"Ⅱ"else"▶",playX,controlY,116f);drawIcon(canvas,"|◀",prevX,controlY,105f);drawIcon(canvas,"▶|",nextX,controlY,105f)
        if(full){arrayOf("☷","♫","◷","▤").forEachIndexed{i,key->drawIcon(canvas,key,216f+i*216,1962f,88f)}}else{drawIcon(canvas,"☰",1788f,controlY,65f);drawIcon(canvas,"shuffle",999f,controlY,60f)}
        canvas.restoreToCount(save)
    }
    private fun drawSpectrum(canvas:Canvas,s:State){
        val left=if(full)55f else 180f;val right=if(full)1020f else 1867f;val baseline=if(full)79f else 194f;val height=if(full)57f else 76f
        val colors=intArrayOf(0xFFCCFF00.toInt(),0xFF00FFD0.toInt(),0xFF00DCEE.toInt(),0xFFC5C0DF.toInt(),0xFFFF0088.toInt());paint.shader=LinearGradient(left,0f,right,0f,colors,null,Shader.TileMode.CLAMP)
        val n=96;val width=(right-left)/n
        for(i in 0 until n){val v=if(s.playing)s.spectrum.getOrElse(i*64/n){0f}else 0f;val h=max(2f,v*height);canvas.drawRoundRect(RectF(left+i*width,baseline-h,left+i*width+width*0.74f,baseline),3f,3f,paint)};paint.shader=null
    }
    private fun drawTitle(canvas:Canvas,s:State,x:Float,y:Float,size:Float,width:Float,center:Boolean){paint.color=Color.WHITE;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.textSize=size;val text=android.text.TextUtils.ellipsize(s.title,android.text.TextPaint(paint),width,android.text.TextUtils.TruncateAt.END).toString();canvas.drawText(text,if(center)x-paint.measureText(text)/2 else x,y,paint)}
    private fun centerText(canvas:Canvas,text:String,x:Float,y:Float){canvas.drawText(text,x-paint.measureText(text)/2,y,paint)}
    private fun drawLyrics(canvas:Canvas,s:State){
        if(sourceLyric!=s.lyric){sourceLyric=s.lyric;lines=HomeMusicLyrics.parse(s.lyric);displayedIndex=-1;scrollIndex=-1f;lastPosition=-1}
        val area=RectF(115f,1314f,965f,1564f);val clip=canvas.save();canvas.clipRect(area);paint.typeface=Typeface.DEFAULT;paint.textSize=50f
        if(lines.isEmpty()){paint.color=0xFFDDDDDD.toInt();centerText(canvas,"暂无同步歌词",540f,1430f)}else{
            val index=HomeMusicLyrics.indexAt(lines,s.position);val jumped=lastPosition>=0&&abs(s.position-lastPosition)>1500
            if(displayedIndex<0||jumped||index<displayedIndex||(!s.playing&&index!=displayedIndex)){scrollIndex=index.toFloat()}else if(s.playing){scrollIndex+=(index-scrollIndex)*0.09f;if(abs(index-scrollIndex)<0.002f)scrollIndex=index.toFloat()}
            displayedIndex=index;lastPosition=s.position
            for(i in max(0,floor(scrollIndex).toInt()-1)..min(lines.lastIndex,ceil(scrollIndex).toInt()+2)){val y=1397f+(i-scrollIndex)*108f;paint.color=if(i==index)Color.WHITE else 0xFFCDCDCD.toInt();val text=android.text.TextUtils.ellipsize(lines[i].second,android.text.TextPaint(paint),area.width()-30,android.text.TextUtils.TruncateAt.END).toString();centerText(canvas,text,540f,y)}
        };canvas.restoreToCount(clip)
    }
    private fun drawIcon(canvas:Canvas,key:String,x:Float,y:Float,size:Float){
        if(key=="shuffle"){paint.color=0xFFAAAAAA.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=4f;val path=Path();path.moveTo(x-20,y-15);path.cubicTo(x-4,y-15,x+6,y+15,x+20,y+15);path.moveTo(x-20,y+15);path.cubicTo(x-4,y+15,x+6,y-15,x+20,y-15);canvas.drawPath(path,paint);paint.style=Paint.Style.FILL;return}
        val icon=icons.getOrPut(key){MusicIcon(context,key)};icon.setTextColor(Color.WHITE);val bounds=size/0.52f;icon.layout(0,0,bounds.toInt(),bounds.toInt());val save=canvas.save();canvas.translate(x-bounds/2,y-bounds/2);icon.draw(canvas);canvas.restoreToCount(save)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        val x=(e.x-ox)/scale;val y=(e.y-oy)/scaleY;val py=if(full)1660f else 641f;val left=if(full)128f else 980f;val right=if(full)950f else 1802f
        fun jump(){seek(((x-left)/(right-left)).coerceIn(0f,1f))}
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=x;downY=y;tracking=x in left..right&&abs(y-py)<38;parent?.requestDisallowInterceptTouchEvent(tracking);return true}
            MotionEvent.ACTION_MOVE->{if(tracking)jump();return true}
            MotionEvent.ACTION_CANCEL->{tracking=false;parent?.requestDisallowInterceptTouchEvent(false);return true}
            MotionEvent.ACTION_UP->{parent?.requestDisallowInterceptTouchEvent(false);if(tracking){jump();tracking=false;return true};if(abs(x-downX)>45||abs(y-downY)>45)return true;performClick()
                if(full&&x in 922f..1040f&&y in 101f..201f){action("search");return true}
                if(!full&&x in 1710f..1840f&&y in 426f..554f){action("search");return true}
                if(full&&y in 1910f..2028f){val i=((x-108)/216).toInt();if(i in 0..3)action(arrayOf("versions","quality","timer","lyrics")[i]);return true}
                val cy=if(full)1802f else 801f;if(abs(y-cy)<90){if(full){when{abs(x-309)<82->action("previous");abs(x-541)<90->action("play");abs(x-776)<82->action("next")}}else{when{abs(x-999)<65->action("shuffle");abs(x-1159)<85->action("previous");abs(x-1396)<90->action("play");abs(x-1629)<85->action("next");abs(x-1788)<65->action("favorites")}};return true}
                if(!full)action("open");return true}
        };return true
    }
    override fun performClick():Boolean{super.performClick();return true}
    private fun time(ms:Int)="%02d:%02d".format(ms.coerceAtLeast(0)/60000,ms.coerceAtLeast(0)/1000%60)
    companion object{
        private fun artwork(c:Context,name:String)=HomeMusicArtwork.load(c,3,name)
    }
}

internal object HomeMusicLyrics{
    fun parse(text:String):List<Pair<Int,String>>{
        val offset=Regex("\\[offset:([+-]?\\d+)\\]",RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()?:0
        val pattern=Regex("\\[(\\d+):(\\d{1,2}(?:\\.\\d{1,3})?)\\]")
        return text.lineSequence().flatMap{line->val content=line.replace(Regex("\\[[^]]*\\]"),"").trim();if(content.isEmpty())emptySequence()else pattern.findAll(line).mapNotNull{m->val minute=m.groupValues[1].toLongOrNull()?:return@mapNotNull null;val second=m.groupValues[2].toDoubleOrNull()?:return@mapNotNull null;val time=(minute*60000+second*1000+offset).toLong().coerceIn(0,Int.MAX_VALUE.toLong()).toInt();time to content}}.sortedBy{it.first}.toList()
    }
    fun indexAt(lines:List<Pair<Int,String>>,position:Int):Int{var lo=0;var hi=lines.size;while(lo<hi){val mid=(lo+hi)/2;if(lines[mid].first<=position)lo=mid+1 else hi=mid};return lo-1}
}