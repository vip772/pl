package t.mma.hooks.items.homeextension

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import t.mma.hooks.items.homesidepanel.SidePanelImages
import kotlin.math.min

/** Reference-sized composition. All interactive elements remain live Android drawing. */
internal class HomeMusicStyleTwo(
    c:Context,private val full:Boolean,
    private val state:()->State,
    private val action:(String)->Unit,
    private val seek:(Float)->Unit
):View(c){
    data class State(val title:String,val position:Int,val duration:Int,val playing:Boolean,val spectrum:FloatArray)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val art=artwork(c,if(full)"player" else "card")
    private var portrait:Bitmap?=null
    private var downX=0f;private var downY=0f;private var tracking=false
    private val designW=if(full)1150f else 2048f
    private val designH=if(full)2048f else 1150f
    private var scaleY=1f;private var scale=1f;private var ox=0f;private var oy=0f
    private val icons=mutableMapOf<String,MusicIcon>()
    private val frame=object:Runnable{override fun run(){invalidate();if(isAttachedToWindow)postDelayed(this,33)}}
    init{isClickable=true;isFocusable=true;contentDescription=if(full)"样式2音乐播放器" else "样式2涂鸦音乐卡片"}
    override fun onAttachedToWindow(){super.onAttachedToWindow();portrait=SidePanelImages.load(context,HomeExtensionSettings.MUSIC_AVATAR_SLOT)?:artwork(context,"avatar");post(frame)}
    override fun onDetachedFromWindow(){removeCallbacks(frame);portrait=null;super.onDetachedFromWindow()}
    override fun onMeasure(w:Int,h:Int){val width=MeasureSpec.getSize(w);val height=if(full)MeasureSpec.getSize(h)else (width*designH/designW).toInt();setMeasuredDimension(width,height)}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas);scale=width/designW;scaleY=if(full)height/designH else scale;ox=0f;oy=0f
        val save=canvas.save();canvas.translate(ox,oy);canvas.scale(scale,scaleY)
        val alpha=HomeExtensionSettings.preferences(context).getInt(HomeExtensionSettings.MUSIC_ALPHA,100).coerceIn(0,100)*255/100
        paint.alpha=alpha;art?.let{canvas.drawBitmap(it,null,RectF(0f,0f,designW,designH),paint)};paint.alpha=255
        val cx=if(full)329f else 516f;val cy=if(full)536f else 570f;val radius=if(full)149f else 181f
        portrait?.let{b->val n=canvas.save();val circle=Path().apply{addCircle(cx,cy,radius,Path.Direction.CW)};canvas.clipPath(circle);val side=min(b.width,b.height);val source=Rect((b.width-side)/2,(b.height-side)/2,(b.width+side)/2,(b.height+side)/2);canvas.drawBitmap(b,source,RectF(cx-radius,cy-radius,cx+radius,cy+radius),paint);canvas.restoreToCount(n)}
        val s=state();val left=if(full)225f else 817f;val titleY=if(full)1495f else 474f
        paint.color=Color.BLACK;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.textSize=if(full)57f else 70f
        val title=android.text.TextUtils.ellipsize(s.title,android.text.TextPaint(paint),if(full)230f else 295f,android.text.TextUtils.TruncateAt.END).toString();canvas.drawText(title,left,titleY,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=if(full)40f else 46f;canvas.drawText("${time(s.position)} / ${time(s.duration)}",left,titleY+56f,paint)
        val spectrum=if(full)RectF(466f,1422f,887f,1562f)else RectF(1115f,384f,1660f,558f)
        val barWidth=spectrum.width()/64
        for(i in 0 until 64){val value=if(s.playing)s.spectrum.getOrElse(i){0f}else 0f;paint.color=if(i<26)0xFFFF1598.toInt() else 0xFF00EBD7.toInt();val barH=(value*spectrum.height()).coerceIn(2f,spectrum.height());canvas.drawRect(spectrum.left+i*barWidth,spectrum.bottom-barH,spectrum.left+i*barWidth+barWidth*0.54f,spectrum.bottom,paint)}
        val progress=if(full)RectF(228f,1580f,900f,1588f)else RectF(819f,580f,1681f,589f)
        paint.color=0xFF686E6C.toInt();canvas.drawRoundRect(progress,5f,5f,paint);paint.color=0xFF9AFF00.toInt();val fraction=if(s.duration>0)s.position.toFloat()/s.duration else 0f
        canvas.drawRoundRect(RectF(progress.left,progress.top,progress.left+progress.width()*fraction.coerceIn(0f,1f),progress.bottom),5f,5f,paint)
        paint.color=0xFF545958.toInt();paint.textSize=if(full)20f else 22f;canvas.drawText(time(s.position),progress.left,progress.bottom+32,paint);val total=time(s.duration);canvas.drawText(total,progress.right-paint.measureText(total),progress.bottom+32,paint)
        val y=if(full)1677f else 698f;val xs=if(full)floatArrayOf(307f,434f,574f,719f,837f)else floatArrayOf(918f,1074f,1259f,1443f,1598f)
        val playRadius=if(full)49f else 61f;paint.color=Color.BLACK;canvas.drawCircle(xs[2],y,playRadius,paint)
        val symbols=arrayOf("shuffle","|◀",if(s.playing)"Ⅱ" else "▶","▶|","☰")
        xs.forEachIndexed{i,x->drawIcon(canvas,symbols[i],x,y,if(i==2)playRadius else if(full)48f else 58f,if(i==2)0xFF9AFF00.toInt()else Color.BLACK)}
        if(full){drawIcon(canvas,"⌕",1030f,140f,88f,Color.WHITE);val toolX=floatArrayOf(548f,655f,762f,869f);arrayOf("☷","♫","◷","▤").forEachIndexed{i,key->drawIcon(canvas,key,toolX[i],1140f,62f,Color.BLACK)}}
        canvas.restoreToCount(save)
    }
    private fun drawIcon(canvas:Canvas,key:String,x:Float,y:Float,size:Float,color:Int){
        if(key=="shuffle"){paint.color=color;paint.style=Paint.Style.STROKE;paint.strokeWidth=5f;paint.strokeCap=Paint.Cap.ROUND;val p=Path();p.moveTo(x-20,y-16);p.cubicTo(x-5,y-16,x+5,y+16,x+22,y+16);p.moveTo(x-20,y+16);p.cubicTo(x-5,y+16,x+5,y-16,x+22,y-16);canvas.drawPath(p,paint);paint.style=Paint.Style.FILL;return}
        val v=icons.getOrPut(key){MusicIcon(context,key)};v.setTextColor(color);val bounds=size/0.52f;v.layout(0,0,bounds.toInt(),bounds.toInt());val save=canvas.save();canvas.translate(x-bounds/2,y-bounds/2);v.draw(canvas);canvas.restoreToCount(save)
    }
    override fun onTouchEvent(event:MotionEvent):Boolean{
        val x=(event.x-ox)/scale;val y=(event.y-oy)/scaleY
        val left=if(full)228f else 819f;val right=if(full)900f else 1681f;val progressY=if(full)1584f else 585f
        fun jump(){seek(((x-left)/(right-left)).coerceIn(0f,1f))}
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=x;downY=y;tracking=x in left..right&&kotlin.math.abs(y-progressY)<48;parent?.requestDisallowInterceptTouchEvent(tracking);return true}
            MotionEvent.ACTION_MOVE->{if(tracking)jump();return true}
            MotionEvent.ACTION_CANCEL->{tracking=false;parent?.requestDisallowInterceptTouchEvent(false);return true}
            MotionEvent.ACTION_UP->{parent?.requestDisallowInterceptTouchEvent(false);if(tracking){jump();tracking=false;return true};if(kotlin.math.abs(x-downX)>45||kotlin.math.abs(y-downY)>45)return true
                performClick();if(full&&x in 972f..1090f&&y in 80f..200f){action("search");return true};val controlY=if(full)1677f else 698f
                if(kotlin.math.abs(y-controlY)<85){val xs=if(full)floatArrayOf(307f,434f,574f,719f,837f)else floatArrayOf(918f,1074f,1259f,1443f,1598f);val index=xs.indices.minByOrNull{kotlin.math.abs(x-xs[it])}?:2;if(kotlin.math.abs(x-xs[index])<85)action(arrayOf("shuffle","previous","play","next","favorites")[index]);return true}
                if(full&&y in 1080f..1205f&&x in 490f..930f){val index=((x-494)/107).toInt().coerceIn(0,3);action(arrayOf("versions","quality","timer","lyrics")[index]);return true}
                if(!full)action("open");return true}
        };return true
    }
    override fun performClick():Boolean{super.performClick();return true}
    private fun time(ms:Int)="%02d:%02d".format((ms/60000).coerceAtLeast(0),(ms/1000).coerceAtLeast(0)%60)
    companion object{
        private fun artwork(c:Context,name:String)=HomeMusicArtwork.load(c,2,name)
    }
}
