package t.mma.hooks.items.homesidepanel

import android.content.Context
import android.graphics.*
import android.view.View

internal class SidePanelShortcutIcon(context:Context,private val item:HomeSidePanelSettings.Shortcut):View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=1.7f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
    override fun onDraw(canvas:Canvas) {
        paint.color=if((resources.configuration.uiMode and 0x30)==0x20) Color.rgb(220,230,222) else Color.rgb(50,80,62)
        val save=canvas.save();canvas.scale(width/24f,height/24f)
        fun line(x:Float,y:Float,a:Float,b:Float)=canvas.drawLine(x,y,a,b,paint)
        fun box(x:Float,y:Float,a:Float,b:Float)=canvas.drawRoundRect(x,y,a,b,1f,1f,paint)
        when(item) {
            HomeSidePanelSettings.Shortcut.QR_CODE -> { box(3f,3f,9f,9f);box(15f,3f,21f,9f);box(3f,15f,9f,21f);box(14f,14f,17f,17f);line(20f,14f,20f,20f);line(14f,21f,17f,21f) }
            HomeSidePanelSettings.Shortcut.SCAN -> {line(3f,8f,3f,3f);line(3f,3f,8f,3f);line(16f,3f,21f,3f);line(21f,3f,21f,8f);line(3f,16f,3f,21f);line(3f,21f,8f,21f);line(16f,21f,21f,21f);line(21f,21f,21f,16f);line(5f,12f,19f,12f)}
            HomeSidePanelSettings.Shortcut.FAVORITE -> {val path=Path();for(i in 0..9){val angle=Math.PI*i/5-Math.PI/2;val r=if(i%2==0)9f else 4f;val x=12+ kotlin.math.cos(angle).toFloat()*r;val y=12+kotlin.math.sin(angle).toFloat()*r;if(i==0)path.moveTo(x,y) else path.lineTo(x,y)};path.close();canvas.drawPath(path,paint)}
            HomeSidePanelSettings.Shortcut.PAY -> {box(3f,4f,21f,20f);line(3f,9f,21f,9f);line(7f,15f,10f,15f);line(15f,14f,15f,17f);line(18f,14f,18f,17f)}
            HomeSidePanelSettings.Shortcut.SERVICE -> {box(3f,5f,21f,21f);box(8f,2f,16f,6f);line(3f,11f,21f,11f);line(10f,10f,14f,10f)}
            HomeSidePanelSettings.Shortcut.WALLET -> {box(3f,5f,21f,20f);box(14f,10f,22f,16f);canvas.drawCircle(17f,13f,.6f,paint);line(5f,3f,18f,3f)}
            HomeSidePanelSettings.Shortcut.CLEAR_UNREAD -> {box(3f,4f,21f,17f);line(5f,20f,9f,17f);line(7f,10f,11f,13f);line(11f,13f,17f,7f)}
            HomeSidePanelSettings.Shortcut.SETTINGS -> {canvas.drawCircle(12f,12f,7f,paint);canvas.drawCircle(12f,12f,2.5f,paint);for(i in 0..7){val a=i*Math.PI/4;line(12+ kotlin.math.cos(a).toFloat()*8,12+kotlin.math.sin(a).toFloat()*8,12+kotlin.math.cos(a).toFloat()*10,12+kotlin.math.sin(a).toFloat()*10)}}
        }
        canvas.restoreToCount(save)
    }
}