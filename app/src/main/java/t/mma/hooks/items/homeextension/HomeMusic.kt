package t.mma.hooks.items.homeextension

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import t.mma.hooks.items.musicorder.QQMusicClient
import t.mma.hooks.items.musicorder.QQMusicSearchResult
import t.mma.hooks.items.musicorder.QQMusicTrack
import t.mma.preferences.HchatStorage
import java.net.URL
import java.util.concurrent.Executors

internal data class HomeMusicSong(val title:String,val artist:String,val playUrl:String,val coverUrl:String,val lyric:String,val source:String="QQ音乐",val sourceId:String="")

internal object HomeMusic {
    private val worker=Executors.newFixedThreadPool(4){Thread(it,"Hchat-HomeMusic").apply{isDaemon=true}}
    private val main=Handler(Looper.getMainLooper()); private val qq=QQMusicClient()
    private var player:MediaPlayer?=null; private var current:HomeMusicSong?=null; private var queue=mutableListOf<HomeMusicSong>()
    private var shuffle=false;private var visualizer:android.media.audiofx.Visualizer?=null;@Volatile private var spectrum=FloatArray(64);private var spectrumWarned=false;private var selection=0; private var preparing=false; private var playing=false; private var liked=false; private var timerTask:Runnable?=null; private var lyricWindow:android.view.WindowManager?=null; private var lyricView:TextView?=null
    private val listeners=mutableSetOf<()->Unit>()
    private fun prefs(c:Context)=HchatStorage.preferences(c,HomeExtensionSettings.PREFS)
    fun style(c:Context)=prefs(c).getInt(HomeExtensionSettings.MUSIC_STYLE,1).let{if(it in 2..3)it else 1}
    fun setListener(l:()->Unit){listeners+=l}; fun removeListener(l:()->Unit){listeners-=l}
    private fun changed()=main.post{playerSampleAt=0L;listeners.toList().forEach{it()}}
    private fun dp(c:Context,n:Int)=(n*c.resources.displayMetrics.density).toInt()
    private fun rounded(c:Context,color:Int,r:Int=18)=GradientDrawable().apply{setColor(color);cornerRadius=dp(c,r).toFloat()}
    private fun txt(c:Context,s:String,size:Float,color:Int=Color.WHITE)=TextView(c).apply{text=s;textSize=size;setTextColor(color);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
    private fun storeSong(c:Context,s:HomeMusicSong){prefs(c).edit().putString("title",s.title).putString("artist",s.artist).putString("url",s.playUrl).putString("cover",s.coverUrl).putString("lyric",s.lyric).putString("source",s.source).putString("source_id",s.sourceId).apply()}
    private fun load(c:Context):HomeMusicSong?=prefs(c).let{p->p.getString("url","").orEmpty().takeIf{it.isNotBlank()}?.let{HomeMusicSong(p.getString("title","音乐").orEmpty(),p.getString("artist","").orEmpty(),it,p.getString("cover","").orEmpty(),p.getString("lyric","").orEmpty(),p.getString("source","QQ音乐").orEmpty(),p.getString("source_id","").orEmpty())}}
    private fun favorites(c:Context):MutableList<HomeMusicSong>{val j=runCatching{org.json.JSONArray(prefs(c).getString("music_favorites","[]"))}.getOrDefault(org.json.JSONArray());return (0 until j.length()).mapNotNull{i->runCatching{val o=j.getJSONObject(i);HomeMusicSong(o.getString("title"),o.getString("artist"),o.getString("url"),o.optString("cover"),o.optString("lyric"),o.optString("source"),o.optString("id"))}.getOrNull()}.toMutableList()}
    private var favoriteRevision=0L
    private var paintedFavoriteRevision=-1L
    private var paintedFavoriteSong:HomeMusicSong?=null
    private var paintedFavorite=false
    private fun saveFavorites(c:Context,list:List<HomeMusicSong>){val j=org.json.JSONArray();list.forEach{s->j.put(org.json.JSONObject().put("title",s.title).put("artist",s.artist).put("url",s.playUrl).put("cover",s.coverUrl).put("lyric",s.lyric).put("source",s.source).put("id",s.sourceId))};prefs(c).edit().putString("music_favorites",j.toString()).apply();favoriteRevision++}
    private fun sameSong(a:HomeMusicSong,b:HomeMusicSong?)=b!=null&&a.source==b.source&&(if(a.sourceId.isNotBlank()&&b.sourceId.isNotBlank())a.sourceId==b.sourceId else a.title==b.title&&a.artist==b.artist)
    private fun isFavorite(c:Context,s:HomeMusicSong)=favorites(c).any{sameSong(it,s)}
    private fun toggleFavorite(c:Context,s:HomeMusicSong){val list=favorites(c);if(list.any{sameSong(it,s)})list.removeAll{sameSong(it,s)}else list.add(0,s);saveFavorites(c,list);changed()}
    private fun loadCover(v:ImageView,url:String){if(url.isBlank())return;worker.execute{runCatching{URL(url).openStream().use{BitmapFactory.decodeStream(it)}}.getOrNull()?.let{b->main.post{v.setImageBitmap(b)}}}}
    private fun qqSong(t:QQMusicTrack)=HomeMusicSong(t.title,t.singer,t.playUrl,t.coverUrl,t.lyric,"QQ音乐",t.mid)
    private fun searchSource(query:String,source:String,done:(List<HomeMusicSong>)->Unit){
        worker.execute{
            val songs=runCatching{
                if(source=="QQ音乐") (qq.search(query) as? QQMusicSearchResult.Success)?.track?.let{listOf(qqSong(it))}?:emptyList()
                else HomeMusicSources.searchMany(query,source)
            }.getOrDefault(emptyList()).filter{it.source==source}.distinctBy{it.sourceId.ifBlank{it.title+"\u0000"+it.artist}}
            main.post{done(songs)}
        }
    }
    private fun searchAll(query:String,done:(List<HomeMusicSong>)->Unit){
        val result=java.util.Collections.synchronizedList(mutableListOf<HomeMusicSong>());val pending=java.util.concurrent.atomic.AtomicInteger(4)
        listOf<()->List<HomeMusicSong>>({(qq.search(query) as? QQMusicSearchResult.Success)?.track?.let{listOf(qqSong(it))}?:emptyList()}, {HomeMusicSources.searchMany(query,"网易云")},{HomeMusicSources.searchMany(query,"酷我")},{HomeMusicSources.searchMany(query,"咪咕")}).forEach{task->worker.execute{runCatching{result.addAll(task())};if(pending.decrementAndGet()==0){val songs=result.toList().distinctBy{it.source+it.sourceId}.sortedBy{if(it.source=="QQ音乐")0 else 1};main.post{done(songs)}}}}
    }
    fun search(c:Context,query:String,callback:(HomeMusicSong?)->Unit)=searchAll(query){songs->songs.firstOrNull()?.let{storeSong(c,it)};callback(songs.firstOrNull())}
    private fun play(c:Context,s:HomeMusicSong){val request=++selection;if(s.playUrl.isBlank()){toast(c,"正在获取播放地址…");worker.execute{val resolved=HomeMusicSources.resolve(s);main.post{if(request!=selection)return@post;if(resolved==null)toast(c,"该歌曲暂无可用播放地址")else play(c,resolved)}};return};if(sameSong(s,current)&&player!=null){if(preparing)return;runCatching{if(playing){player?.pause();playing=false}else if(player?.isPlaying==false){player?.start();playing=true;if(visualizer==null)player?.let{captureSpectrum(c,it)}};changed()}.onFailure{toast(c,"播放器正在准备，请稍后再试")};return};releaseSpectrum();runCatching{player?.release()};current=s;storeSong(c,s);changed();val qi=queue.indexOfFirst{sameSong(it,s)};if(qi<0)queue.add(s)else queue[qi]=s;playing=false;preparing=true;val mp=MediaPlayer();player=mp;runCatching{if(Build.VERSION.SDK_INT>=21)mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());mp.setDataSource(s.playUrl);mp.setOnSeekCompleteListener{if(player===it)changed()};mp.setOnPreparedListener{if(player!==it)return@setOnPreparedListener;preparing=false;it.start();playing=true;captureSpectrum(c,it);changed();updateLyric(s)};mp.setOnCompletionListener{releaseSpectrum();playing=false;changed();if(queue.size>1)next(c)};mp.setOnErrorListener{failed,_,_->if(player!==failed)return@setOnErrorListener true;preparing=false;playing=false;releaseSpectrum();player?.release();player=null;changed();toast(c,"该音源暂时无法播放");true};mp.prepareAsync()}.onFailure{preparing=false;player=null;mp.release();toast(c,"播放地址失效或网络不可用")}}
    private fun toast(c:Context,s:String){main.post{Toast.makeText(c,s,Toast.LENGTH_SHORT).show()}}
    private fun iconButton(c:Context,s:String,action:()->Unit)=MusicIcon(c,s).apply{contentDescription=when(s){"☷"->"其他版本";"♫"->"音质";"◷"->"定时";"▤"->"悬浮歌词";"☰"->"收藏夹";"⌕"->"搜索";else->s};background=rounded(c,0x22FFFFFF,12);setOnClickListener{action()}}
    private fun spaced(c:Context)=LinearLayout.LayoutParams(0,dp(c,56),1f).apply{setMargins(dp(c,4),0,dp(c,4),0)}
    fun card(c:Context,compact:Boolean=false):LinearLayout{val alpha=prefs(c).getInt(HomeExtensionSettings.MUSIC_ALPHA,100).coerceIn(0,100)*255/100;val root=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14));background=rounded(c,if(style(c) in 2..3)Color.TRANSPARENT else Color.argb(alpha,29,29,31),22)};if(style(c) in 2..3)root.setPadding(0,0,0,0);root.setOnClickListener{showPlayer(c)};val render={if(style(c) in 2..3&&root.childCount>0){root.invalidate()}else{root.removeAllViews();root.addView(when(style(c)){2->styleTwo(c,false,root);3->styleThree(c,false,root);else->styleOne(c,compact,root)})}};render();root.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{override fun onViewAttachedToWindow(v:View){setListener(render)};override fun onViewDetachedFromWindow(v:View){removeListener(render)}});return root}
    private fun releaseSpectrum(){runCatching{visualizer?.release()};visualizer=null;spectrum=FloatArray(64)}
    private fun captureSpectrum(c:Context,mp:MediaPlayer){
        releaseSpectrum();if(style(c) !in 2..3)return
        try{
            if(c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
                if(!spectrumWarned){spectrumWarned=true;toast(c,"真实律动需要微信的录音权限，请在系统应用权限中授权后重新播放");val activity=c as? android.app.Activity;if(activity!=null)activity.requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO),0x6D52)};return
            }
            val effect=android.media.audiofx.Visualizer(mp.audioSessionId);visualizer=effect
            effect.enabled=false;effect.captureSize=android.media.audiofx.Visualizer.getCaptureSizeRange()[1]
            effect.scalingMode=android.media.audiofx.Visualizer.SCALING_MODE_AS_PLAYED
            effect.setDataCaptureListener(object:android.media.audiofx.Visualizer.OnDataCaptureListener{
                override fun onWaveFormDataCapture(v:android.media.audiofx.Visualizer,data:ByteArray,rate:Int){}
                override fun onFftDataCapture(v:android.media.audiofx.Visualizer,data:ByteArray,rate:Int){
                    if(!playing){spectrum=FloatArray(64);return};val old=spectrum;val bins=data.size/2;val next=FloatArray(64)
                    for(i in next.indices){val start=kotlin.math.exp(kotlin.math.ln((bins-1).toDouble())*i/64).toInt().coerceAtLeast(1);val end=kotlin.math.exp(kotlin.math.ln((bins-1).toDouble())*(i+1)/64).toInt().coerceIn(start+1,bins);var peak=0.0
                        for(k in start until end){val re=data[k*2].toInt();val im=data[k*2+1].toInt();peak=maxOf(peak,kotlin.math.sqrt((re*re+im*im).toDouble()))}
                        val value=(kotlin.math.ln(1+peak)/kotlin.math.ln(182.0)).toFloat().coerceIn(0f,1f);next[i]=maxOf(value,old[i]*0.78f)
                    };spectrum=next
                }
            },minOf(30000,android.media.audiofx.Visualizer.getMaxCaptureRate()),false,true);effect.enabled=true
        }catch(e:Exception){releaseSpectrum();if(!spectrumWarned){spectrumWarned=true;toast(c,"当前设备未允许捕获播放频谱，真实律动暂不可用")}}
    }
    private var sampledPlayer:MediaPlayer?=null
    private var playerSampleAt=0L
    private var sampledPosition=0
    private var sampledDuration=0
    private fun paintState(c:Context):MusicPaintState{
        val now=android.os.SystemClock.uptimeMillis();val mp=player
        if(mp!==sampledPlayer||now-playerSampleAt>=200L||playerSampleAt==0L){
            sampledPlayer=mp;playerSampleAt=now
            sampledPosition=if(preparing)0 else runCatching{mp?.currentPosition?:0}.getOrDefault(0)
            sampledDuration=if(preparing)0 else runCatching{mp?.duration?:0}.getOrDefault(0)
        }
        val song=current?:load(c)
        if(paintedFavoriteRevision!=favoriteRevision||paintedFavoriteSong!=song){
            paintedFavoriteSong=song;paintedFavoriteRevision=favoriteRevision
            paintedFavorite=song!=null&&isFavorite(c,song)
        }
        return MusicPaintState(song?.title?:"音乐",sampledPosition,sampledDuration,playing,spectrum,song?.lyric.orEmpty(),song?.coverUrl.orEmpty(),paintedFavorite)
    }
    private fun styleTwo(c:Context,full:Boolean,host:LinearLayout):View=HomeMusicPaintedView(c,2,full,{
        paintState(c)
    },{key->val song=current?:load(c);when(key){
        "play"->if(song!=null)play(c,song)else searchSheet(c,host)
        "previous"->previous(c);"next"->next(c);"open"->showPlayer(c);"search"->searchSheet(c,host)
        "favorite"->song?.let{toggleFavorite(c,it)}
        "favorites"->openSheet(c,favorites(c),"我的收藏");"versions"->versions(c);"quality"->quality(c,song);"timer"->timer(c);"lyrics"->toggleLyricWindow(c,song)
        "shuffle"->{shuffle=!shuffle;toast(c,if(shuffle)"随机播放已开启"else"顺序播放已开启")}
    }},{fraction->if(!preparing)runCatching{player?.let{if(it.duration>0){it.seekTo((it.duration*fraction).toInt());changed()}}}})
    private fun showStyleTwoPlayer(c:Context){if(playing&&visualizer==null)player?.let{captureSpectrum(c,it)};val dialog=Dialog(c);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);val host=LinearLayout(c).apply{setBackgroundColor(0xFF242625.toInt());gravity=Gravity.CENTER};host.addView(styleTwo(c,true,host),LinearLayout.LayoutParams(-1,-1));dialog.setContentView(host);dialog.show();dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.window?.setLayout(-1,-1)}
    private fun styleThree(c:Context,full:Boolean,host:LinearLayout):View=HomeMusicPaintedView(c,3,full,{
        paintState(c)
    },{key->val song=current?:load(c);when(key){
        "play"->if(song!=null)play(c,song)else searchSheet(c,host)
        "previous"->previous(c);"next"->next(c);"open"->showPlayer(c);"search"->searchSheet(c,host)
        "favorite"->song?.let{toggleFavorite(c,it)}
        "favorites"->openSheet(c,favorites(c),"我的收藏",allowRemove=true);"versions"->versions(c);"quality"->quality(c,song);"timer"->timer(c);"lyrics"->toggleLyricWindow(c,song)
        "shuffle"->{shuffle=!shuffle;toast(c,if(shuffle)"随机播放已开启"else"顺序播放已开启")}
    }},{fraction->if(!preparing)runCatching{player?.let{if(it.duration>0){it.seekTo((it.duration*fraction).toInt());changed()}}}})
    private fun showStyleThreePlayer(c:Context){if(playing&&visualizer==null)player?.let{captureSpectrum(c,it)};val dialog=Dialog(c);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);val host=LinearLayout(c).apply{setBackgroundColor(Color.BLACK);gravity=Gravity.CENTER};host.addView(styleThree(c,true,host),LinearLayout.LayoutParams(-1,-1));dialog.setContentView(host);dialog.show();dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.window?.setLayout(-1,-1)}
    private fun styleOne(c:Context,compact:Boolean,root:LinearLayout):View{val s=current?:load(c);val box=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL};val header=LinearLayout(c).apply{gravity=Gravity.CENTER_VERTICAL};header.addView(txt(c,s?.title?:"点击搜索音乐",if(compact)19f else 24f),LinearLayout.LayoutParams(0,-2,1f));header.addView(iconButton(c,"⌕"){searchSheet(c,root)},LinearLayout.LayoutParams(dp(c,44),dp(c,44)));header.addView(txt(c,"${s?.artist.orEmpty()}  ·  ${s?.source?:"QQ音乐"}",13f,0xFFAAAAAE.toInt()));box.addView(header);val row=LinearLayout(c).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(c,12),0,dp(c,8))};val art=ImageView(c).apply{scaleType=ImageView.ScaleType.CENTER_CROP;background=rounded(c,0xFF343438.toInt(),14)};val size=if(compact)64 else 96;row.addView(art,LinearLayout.LayoutParams(dp(c,size),dp(c,size)).apply{rightMargin=dp(c,14)});s?.let{loadCover(art,it.coverUrl)};val info=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL};info.addView(txt(c,s?.title?:"搜索并播放音乐",if(compact)17f else 21f));info.addView(txt(c,s?.artist?:"",13f,0xFFAAAAAE.toInt()));row.addView(info,LinearLayout.LayoutParams(0,-2,1f));box.addView(row);val controls=LinearLayout(c).apply{gravity=Gravity.CENTER;setPadding(0,dp(c,8),0,dp(c,8))};controls.addView(iconButton(c,"|◀"){previous(c)},LinearLayout.LayoutParams(dp(c,48),dp(c,48)).apply{rightMargin=dp(c,18)});controls.addView(iconButton(c,if(playing)"Ⅱ" else "▶"){if(s!=null)play(c,s)else searchSheet(c,root)},LinearLayout.LayoutParams(dp(c,58),dp(c,58)));controls.addView(iconButton(c,"▶|"){next(c)},LinearLayout.LayoutParams(dp(c,48),dp(c,48)).apply{leftMargin=dp(c,18)});box.addView(controls);val seek=SeekBar(c).apply{progressTintList=android.content.res.ColorStateList.valueOf(Color.WHITE);thumbTintList=android.content.res.ColorStateList.valueOf(Color.WHITE)};bindSeek(seek,null);box.addView(seek);val footer=LinearLayout(c).apply{gravity=Gravity.CENTER_VERTICAL};footer.addView(txt(c,if(playing)"播放中" else "已暂停",12f,0xFFAAAAAE.toInt()),LinearLayout.LayoutParams(0,-2,1f));footer.addView(iconButton(c,"☰"){openSheet(c,favorites(c),"我的收藏")},LinearLayout.LayoutParams(dp(c,46),dp(c,42)));box.addView(footer);return box}
    private fun playerActions(c:Context,s:HomeMusicSong?,host:LinearLayout):View{val wrap=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL};val action=LinearLayout(c).apply{gravity=Gravity.CENTER};action.addView(iconButton(c,"☷"){versions(c)},spaced(c));action.addView(iconButton(c,"♫"){quality(c,s)},spaced(c));action.addView(iconButton(c,"◷"){timer(c)},spaced(c));action.addView(iconButton(c,"▤"){toggleLyricWindow(c,s)},spaced(c));wrap.addView(action);val seek=SeekBar(c).apply{progressTintList=android.content.res.ColorStateList.valueOf(Color.WHITE);thumbTintList=android.content.res.ColorStateList.valueOf(Color.WHITE)};bindSeek(seek,c);wrap.addView(seek);val times=LinearLayout(c).apply{gravity=Gravity.CENTER_VERTICAL};val now=txt(c,"00:00",12f);val total=txt(c,"00:00",12f);times.addView(now,LinearLayout.LayoutParams(0,-2,1f));times.addView(total.apply{gravity=Gravity.END},LinearLayout.LayoutParams(0,-2,1f));wrap.addView(times);val controls=LinearLayout(c).apply{gravity=Gravity.CENTER};controls.addView(iconButton(c,if(s!=null&&isFavorite(c,s))"♥" else "♡"){s?.let{toggleFavorite(c,it)}}.apply{setTextColor(if(s!=null&&isFavorite(c,s))Color.RED else Color.WHITE)} ,spaced(c));controls.addView(iconButton(c,"|◀"){previous(c)},spaced(c));controls.addView(iconButton(c,if(playing)"Ⅱ" else "▶"){s?.let{play(c,it)}},spaced(c));controls.addView(iconButton(c,"▶|"){next(c)},spaced(c));controls.addView(iconButton(c,"☰"){openSheet(c,favorites(c),"我的收藏")},spaced(c));wrap.addView(controls);val tick=object:Runnable{override fun run(){val p=player;if(p!=null&&runCatching{p.isPlaying||!playing}.getOrDefault(false)){runCatching{if(p.duration>0){now.text=time(p.currentPosition);total.text=time(p.duration)}}};if(wrap.isAttachedToWindow)main.postDelayed(this,500)}};wrap.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{override fun onViewAttachedToWindow(v:View){main.post(tick)};override fun onViewDetachedFromWindow(v:View){main.removeCallbacks(tick)}});return wrap}
    private fun bindSeek(seek:SeekBar,c:Context?){
        var dragging=false;seek.max=1000;seek.progressBackgroundTintList=android.content.res.ColorStateList.valueOf(0x55FFFFFF)
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(v:SeekBar?,p:Int,user:Boolean){}
            override fun onStartTrackingTouch(v:SeekBar?){dragging=true;seek.tag=true}
            override fun onStopTrackingTouch(v:SeekBar?){val duration=runCatching{player?.duration?:0}.getOrDefault(0);if(duration>0)runCatching{player?.seekTo((duration.toLong()*seek.progress/seek.max).toInt());changed()};dragging=false;seek.tag=false}
        })
        val tick=object:Runnable{override fun run(){if(!dragging&&!preparing)runCatching{val p=player;if(p!=null&&p.duration>0)seek.progress=(p.currentPosition.toLong()*seek.max/p.duration).toInt()};if(seek.isAttachedToWindow)main.postDelayed(this,500)}}
        seek.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{override fun onViewAttachedToWindow(v:View){main.post(tick)};override fun onViewDetachedFromWindow(v:View){main.removeCallbacks(tick)}})
    }
    private fun time(ms:Int):String{val t=(ms/1000).coerceAtLeast(0);return "%02d:%02d".format(t/60,t%60)}
    private fun openSheet(c:Context,songs:List<HomeMusicSong>,title:String,allowRemove:Boolean=false){
        val dialog=Dialog(c);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(c,18),dp(c,16),dp(c,18),dp(c,20));background=rounded(c,0xFF102330.toInt(),22)}
        val head=LinearLayout(c).apply{gravity=Gravity.CENTER_VERTICAL}
        head.addView(txt(c,title,20f),LinearLayout.LayoutParams(0,-2,1f))
        head.addView(iconButton(c,"×"){dialog.dismiss()},LinearLayout.LayoutParams(dp(c,44),dp(c,44)));root.addView(head)
        val list=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL}
        val displayed=songs.toMutableList()
        fun render(){
            list.removeAllViews()
            if(displayed.isEmpty())list.addView(txt(c,"暂无歌曲",15f,0xFFB7C2C9.toInt()))
            displayed.forEach{song->
                val startPlayback={storeSong(c,song);queue=displayed.toMutableList();play(c,song);dialog.dismiss()}
                val row=LinearLayout(c).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(c,8),dp(c,10),dp(c,8),dp(c,10));setOnClickListener{startPlayback()}}
                val text=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL}
                text.addView(txt(c,song.title,16f));text.addView(txt(c,"${song.artist} · ${song.source}",12f,0xFFB7C2C9.toInt()))
                row.addView(text,LinearLayout.LayoutParams(0,-2,1f))
                row.addView(iconButton(c,"▶"){startPlayback()},LinearLayout.LayoutParams(dp(c,44),dp(c,48)))
                if(allowRemove)row.addView(iconButton(c,"remove"){
                    val latest=favorites(c);latest.removeAll{sameSong(it,song)};saveFavorites(c,latest)
                    displayed.removeAll{sameSong(it,song)};changed();render()
                }.apply{contentDescription="移出收藏夹"},LinearLayout.LayoutParams(dp(c,44),dp(c,48)))
                list.addView(row)
            }
        }
        render()
        root.addView(ScrollView(c).apply{addView(list)},LinearLayout.LayoutParams(-1,0,1f))
        dialog.setContentView(root);dialog.show();dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(-1,(c.resources.displayMetrics.heightPixels*0.82f).toInt());dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.window?.attributes=dialog.window?.attributes?.apply{windowAnimations=android.R.style.Animation_InputMethod}
    }
    private class SearchSongAction(c:Context,private val song:HomeMusicSong,private val favorite:Boolean):View(c){
        private val ink=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val shape=android.graphics.Path()
        private var active=false
        private val refresh:()->Unit={
            val next=if(favorite)isFavorite(context,song) else sameSong(song,current)&&playing&&!preparing&&player!=null
            if(active!=next){active=next;invalidate()}
            contentDescription=if(favorite){if(active)"取消收藏" else "收藏"}else{if(active)"暂停" else "播放"}
        }
        init{isFocusable=true;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES;refresh()}
        override fun onAttachedToWindow(){super.onAttachedToWindow();setListener(refresh);refresh()}
        override fun onDetachedFromWindow(){removeListener(refresh);super.onDetachedFromWindow()}
        override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){
            super.onSizeChanged(w,h,oldw,oldh)
            shape.reset()
            if(favorite){
                shape.moveTo(0f,8f);shape.cubicTo(-2f,5f,-11f,0f,-10f,-5f)
                shape.cubicTo(-9f,-11f,-3f,-12f,0f,-6f)
                shape.cubicTo(3f,-12f,9f,-11f,10f,-5f)
                shape.cubicTo(11f,0f,2f,5f,0f,8f);shape.close()
            }else{shape.moveTo(-6f,-10f);shape.lineTo(10f,0f);shape.lineTo(-6f,10f);shape.close()}
        }
        override fun onDraw(canvas:android.graphics.Canvas){
            super.onDraw(canvas)
            val save=canvas.save();canvas.translate(width/2f,height/2f);val unit=resources.displayMetrics.density;canvas.scale(unit,unit)
            ink.color=if(favorite&&active)Color.RED else Color.WHITE
            ink.style=if(favorite&&!active)android.graphics.Paint.Style.STROKE else android.graphics.Paint.Style.FILL
            ink.strokeWidth=1.8f;ink.strokeJoin=android.graphics.Paint.Join.ROUND
            if(!favorite&&active){canvas.drawRoundRect(-8f,-10f,-3f,10f,1f,1f,ink);canvas.drawRoundRect(3f,-10f,8f,10f,1f,1f,ink)}else canvas.drawPath(shape,ink)
            canvas.restoreToCount(save)
        }
    }
    private fun searchSongRow(c:Context,song:HomeMusicSong,songs:List<HomeMusicSong>):View{
        val startPlayback={queue=songs.toMutableList();play(c,if(sameSong(song,current)&&player!=null)current!! else song)}
        return LinearLayout(c).apply{
            gravity=Gravity.CENTER_VERTICAL;setPadding(dp(c,12),dp(c,10),dp(c,6),dp(c,10));background=rounded(c,0xFF203744.toInt(),12)
            val info=LinearLayout(c).apply{
                orientation=LinearLayout.VERTICAL
                addView(txt(c,song.title,15f))
                addView(txt(c,"${song.artist} · ${song.source}",12f,0xFFB7C2C9.toInt()).apply{setPadding(0,dp(c,4),0,0)})
                setOnClickListener{startPlayback()}
            }
            addView(info,LinearLayout.LayoutParams(0,-2,1f))
            addView(SearchSongAction(c,song,false).apply{setOnClickListener{startPlayback()}},LinearLayout.LayoutParams(dp(c,44),dp(c,48)).apply{leftMargin=dp(c,4)})
            addView(SearchSongAction(c,song,true).apply{setOnClickListener{toggleFavorite(c,if(sameSong(song,current))current!! else song)}},LinearLayout.LayoutParams(dp(c,44),dp(c,48)).apply{leftMargin=dp(c,4)})
        }
    }
    private fun searchSheet(c:Context,host:LinearLayout){
        val dialog=Dialog(c);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val page=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(c,18),dp(c,16),dp(c,18),dp(c,18));background=rounded(c,0xFF102330.toInt(),22)}
        val sourceKeys=listOf("QQ音乐","网易云","酷我","咪咕")
        val sourceLabels=listOf("QQ音乐","网易云音乐","酷我音乐","咪咕音乐")
        var selectedSource=0
        val sourceRow=LinearLayout(c).apply{gravity=Gravity.CENTER_VERTICAL}
        val sourceButtons=sourceLabels.mapIndexed { index,label ->
            txt(c,label,12f).apply{
                gravity=Gravity.CENTER;setPadding(dp(c,2),0,dp(c,2),0)
                sourceRow.addView(this,LinearLayout.LayoutParams(0,dp(c,42),1f).apply{if(index>0)leftMargin=dp(c,8)})
            }
        }
        fun updateSourceButtons(){sourceButtons.forEachIndexed{index,button->
            val active=index==selectedSource
            button.isSelected=active
            button.background=rounded(c,if(active)0xFF35677F.toInt() else 0xFF203744.toInt(),12)
            button.setTextColor(if(active)Color.WHITE else 0xFFB7C2C9.toInt())
        }}
        updateSourceButtons()
        page.addView(sourceRow,LinearLayout.LayoutParams(-1,dp(c,42)).apply{bottomMargin=dp(c,12)})
        val input=EditText(c).apply{hint="搜索歌曲或歌手";setSingleLine();setTextColor(Color.WHITE);setHintTextColor(0xFF9AAAB3.toInt());background=null;setPadding(dp(c,16),dp(c,10),dp(c,4),dp(c,10));imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH}
        val searchButton=object:View(c){
            private val iconPaint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;style=android.graphics.Paint.Style.STROKE;strokeWidth=dp(c,2).toFloat();strokeCap=android.graphics.Paint.Cap.ROUND}
            override fun onDraw(canvas:android.graphics.Canvas){
                super.onDraw(canvas)
                val unit=resources.displayMetrics.density;val cx=width/2f-2*unit;val cy=height/2f-2*unit
                canvas.drawCircle(cx,cy,7*unit,iconPaint)
                canvas.drawLine(cx+5*unit,cy+5*unit,cx+12*unit,cy+12*unit,iconPaint)
            }
        }.apply{contentDescription="搜索";isFocusable=true;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES}
        val bar=LinearLayout(c).apply{gravity=Gravity.CENTER_VERTICAL;background=rounded(c,0xFF203744.toInt(),24)}
        bar.addView(input,LinearLayout.LayoutParams(0,dp(c,48),1f))
        bar.addView(searchButton,LinearLayout.LayoutParams(dp(c,48),dp(c,48)))
        page.addView(bar,LinearLayout.LayoutParams(-1,dp(c,48)))
        val status=txt(c,"搜索 QQ音乐 的歌曲",13f,0xFFB7C2C9.toInt()).apply{setPadding(0,dp(c,12),0,dp(c,8))};page.addView(status)
        val results=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL};page.addView(ScrollView(c).apply{addView(results)},LinearLayout.LayoutParams(-1,0,1f))
        var generation=0
        sourceButtons.forEachIndexed{index,button->button.setOnClickListener{
            if(selectedSource!=index){selectedSource=index;generation++;updateSourceButtons();results.removeAllViews();status.text="搜索 ${sourceLabels[index]} 的歌曲"}
        }}
        dialog.setOnDismissListener{generation++}
        val doSearch={val request=++generation;val source=sourceKeys[selectedSource];val label=sourceLabels[selectedSource];val q=input.text.toString().trim();if(q.isBlank()){input.error="请输入歌曲名或歌手"}else{status.text="正在搜索 $label…";results.removeAllViews();searchSource(q,source){songs->if(!dialog.isShowing||request!=generation)return@searchSource;status.text=if(songs.isEmpty())"没有可用结果" else "$label · ${songs.size} 首歌曲，选择一首播放";songs.forEach{song->val row=searchSongRow(c,song,songs);val lp=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(c,4),0,dp(c,4))};results.addView(row,lp)}}}}
        searchButton.setOnClickListener{doSearch()};input.setOnEditorActionListener{_,_,_->doSearch();true}
        dialog.setContentView(page);dialog.show();dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.window?.setLayout(-1,(c.resources.displayMetrics.heightPixels*0.88f).toInt());dialog.window?.setGravity(Gravity.BOTTOM);dialog.window?.attributes=dialog.window?.attributes?.apply{windowAnimations=android.R.style.Animation_InputMethod}
    }
    private fun previous(c:Context){if(queue.size<2){toast(c,"请先从搜索结果选择播放列表");return};val i=queue.indexOfFirst{sameSong(it,current)};play(c,queue[(i-1+queue.size)%queue.size])}
    private fun next(c:Context){if(queue.size<2){toast(c,"请先从搜索结果选择播放列表");return};val i=queue.indexOfFirst{sameSong(it,current)};play(c,queue[if(shuffle)(queue.indices.filter{it!=i}.randomOrNull()?:0)else(i+1)%queue.size])}
    private fun quality(c:Context,s:HomeMusicSong?){
        if(s==null)return
        val labels=if(s.source=="QQ音乐")arrayOf("当前音源（实际码率以播放流为准）")else arrayOf("标准","高品质","无损")
        AlertDialog.Builder(c).setTitle("请求可用音质").setItems(labels){_,index->
            if(s.source=="QQ音乐"){toast(c,"QQ 接口当前仅返回一种播放地址");return@setItems}
            val position=runCatching{player?.currentPosition?:0}.getOrDefault(0)
            worker.execute{val resolved=HomeMusicSources.resolve(s,index);main.post{if(resolved==null)toast(c,"当前音源未返回该音质的可用地址")else{releaseSpectrum();runCatching{player?.release()};player=null;play(c,resolved);val mp=player;mp?.setOnPreparedListener{if(player===it){preparing=false;it.seekTo(position);it.start();playing=true;captureSpectrum(c,it);changed()}}}}}
        }.show()
    }
    private fun versions(c:Context){val s=current?:load(c);if(s==null){toast(c,"请先选择歌曲");return};toast(c,"正在搜索其他版本…");searchAll(s.title){openSheet(c,it,"${s.title} · 其他版本")}}
    private fun showPlayer(c:Context){if(style(c)==3){showStyleThreePlayer(c);return};if(style(c)==2){showStyleTwoPlayer(c);return};
        val dialog=Dialog(c);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val page=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(c,18),dp(c,12),dp(c,18),dp(c,20));setBackgroundColor(0xFF102330.toInt())}
        val render={page.removeAllViews();val s=current?:load(c);val top=LinearLayout(c).apply{gravity=Gravity.CENTER_VERTICAL};top.addView(iconButton(c,"×"){dialog.dismiss()},LinearLayout.LayoutParams(dp(c,48),dp(c,48)));top.addView(txt(c,s?.title?:"音乐播放器",20f),LinearLayout.LayoutParams(0,-2,1f));top.addView(iconButton(c,"⌕"){searchSheet(c,page)},LinearLayout.LayoutParams(dp(c,48),dp(c,48)));page.addView(top)
            val cover=ImageView(c).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setPadding(dp(c,12),dp(c,16),dp(c,12),dp(c,16))};s?.let{loadCover(cover,it.coverUrl)};page.addView(cover,LinearLayout.LayoutParams(-1,0,1f));page.addView(txt(c,s?.artist.orEmpty(),16f).apply{gravity=Gravity.CENTER});page.addView(playerActions(c,s,page))}
        render();setListener(render);dialog.setOnDismissListener{removeListener(render)};dialog.setContentView(page);dialog.show();dialog.window?.setLayout(-1,-1)
    }
    private fun timer(c:Context){val options=arrayOf("关闭定时","5分钟","10分钟","15分钟","30分钟","45分钟","60分钟");AlertDialog.Builder(c).setTitle("停止播放倒计时").setItems(options){_,i->timerTask?.let{main.removeCallbacks(it)};timerTask=null;if(i>0){val r=Runnable{runCatching{player?.pause()};playing=false;changed();toast(c,"定时结束，已停止播放")};timerTask=r;toast(c,"将在${options[i]}后停止播放");main.postDelayed(r,intArrayOf(0,5,10,15,30,45,60)[i]*60_000L)}}.show()}
    private fun toggleLyricWindow(c:Context,s:HomeMusicSong?){
        if(lyricView!=null){runCatching{lyricWindow?.removeView(lyricView)};lyricView=null;lyricWindow=null;main.removeCallbacks(lyricTick);toast(c,"悬浮歌词已关闭");return}
        if(!Settings.canDrawOverlays(c)){
            val intent=Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching{c.startActivity(intent)};toast(c,"请授予悬浮窗权限后再次点击歌词");return
        }
        val wm=c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view=FloatingMusicLyricView(c).apply{
            text=s?.title?:"暂无歌词";textSize=16f;gravity=Gravity.CENTER
            setPadding(dp(c,18),dp(c,8),dp(c,18),dp(c,8));isSingleLine=true
            ellipsize=android.text.TextUtils.TruncateAt.MARQUEE;marqueeRepeatLimit=-1;isSelected=true
        }
        val display=android.graphics.Point().also{wm.defaultDisplay.getSize(it)}
        val density=c.resources.displayMetrics.density
        val lp=WindowManager.LayoutParams((display.x*0.8f).toInt().coerceAtLeast(1),dp(c,44),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT).apply{
            gravity=Gravity.TOP or Gravity.LEFT
            x=(prefs(c).getInt(HomeExtensionSettings.LYRIC_X,16)*density).toInt()
            y=(prefs(c).getInt(HomeExtensionSettings.LYRIC_Y,28)*density).toInt()
        }
        fun clamp(){
            wm.defaultDisplay.getSize(display)
            lp.width=(display.x*0.8f).toInt().coerceAtLeast(1)
            lp.x=lp.x.coerceIn(0,(display.x-lp.width).coerceAtLeast(0))
            lp.y=lp.y.coerceIn(0,(display.y-lp.height).coerceAtLeast(0))
        }
        clamp()
        var startX=0;var startY=0;var touchX=0f;var touchY=0f
        view.setOnTouchListener{_,event->
            when(event.actionMasked){
                android.view.MotionEvent.ACTION_DOWN->{startX=lp.x;startY=lp.y;touchX=event.rawX;touchY=event.rawY;true}
                android.view.MotionEvent.ACTION_MOVE->{
                    lp.x=startX+(event.rawX-touchX).toInt();lp.y=startY+(event.rawY-touchY).toInt();clamp()
                    runCatching{wm.updateViewLayout(view,lp)};true
                }
                android.view.MotionEvent.ACTION_UP,android.view.MotionEvent.ACTION_CANCEL->{
                    prefs(c).edit().putInt(HomeExtensionSettings.LYRIC_X,(lp.x/density).toInt())
                        .putInt(HomeExtensionSettings.LYRIC_Y,(lp.y/density).toInt()).apply();true
                }
                else->true
            }
        }
        view.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->
            val oldX=lp.x;val oldY=lp.y;val oldWidth=lp.width;clamp()
            if(oldX!=lp.x||oldY!=lp.y||oldWidth!=lp.width)runCatching{wm.updateViewLayout(view,lp)}
        }
        runCatching{wm.addView(view,lp);lyricWindow=wm;lyricView=view;main.post(lyricTick)}.onFailure{toast(c,"悬浮歌词窗口无法显示")}
    }

    private val lyricTick=object:Runnable{override fun run(){val view=lyricView?:return;val song=current;val position=runCatching{player?.currentPosition?:0}.getOrDefault(0);val lines=mutableListOf<Pair<Int,String>>();song?.lyric?.lineSequence()?.forEach{line->val matches=Regex("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]").findAll(line).toList();val text=line.replace(Regex("\\[[^]]*\\]"),"").trim();matches.forEach{m->lines+=(m.groupValues[1].toInt()*60000+(m.groupValues[2].toDouble()*1000).toInt()) to text}};view.text=lines.sortedBy{it.first}.lastOrNull{it.first<=position}?.second?:if(lines.isEmpty())"${song?.title?:"音乐"} · 暂无同步歌词" else song?.title;main.postDelayed(this,250)}}
    private fun updateLyric(s:HomeMusicSong){main.removeCallbacks(lyricTick);if(lyricView!=null)main.post(lyricTick)}
}

internal class MusicIcon(c:Context,private val symbol:String):TextView(c){
    init{setTextColor(Color.WHITE);isFocusable=true}
    private val pen=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas:android.graphics.Canvas){
        val size=minOf(width,height)*0.52f;if(size<=0)return
        val save=canvas.save();canvas.translate((width-size)/2,(height-size)/2);canvas.scale(size/24,size/24)
        pen.color=currentTextColor;pen.strokeWidth=1.8f;pen.strokeCap=android.graphics.Paint.Cap.ROUND;pen.strokeJoin=android.graphics.Paint.Join.ROUND;pen.style=android.graphics.Paint.Style.STROKE
        fun line(a:Float,b:Float,c:Float,d:Float)=canvas.drawLine(a,b,c,d,pen)
        fun path(vararg points:Float){val p=android.graphics.Path();p.moveTo(points[0],points[1]);for(i in 2 until points.size step 2)p.lineTo(points[i],points[i+1]);canvas.drawPath(p,pen)}
        when(symbol){
            "⌕"->{canvas.drawCircle(10f,10f,6f,pen);line(14.5f,14.5f,21f,21f)}
            "×"->{line(6f,6f,18f,18f);line(18f,6f,6f,18f)}
            "remove"->{line(4f,6f,20f,6f);path(9f,6f,9f,3f,15f,3f,15f,6f);path(6f,6f,7f,21f,17f,21f,18f,6f);line(10f,10f,10f,17f);line(14f,10f,14f,17f)}
            "◷"->{canvas.drawCircle(12f,13f,8f,pen);line(12f,13f,12f,8f);line(12f,13f,16f,15f);line(9f,2f,15f,2f)}
            "♫"->{for(i in 0..4){val x=4f+i*4;val h=floatArrayOf(5f,10f,16f,10f,5f)[i];line(x,12-h/2,x,12+h/2)}}
            "☷"->{for(i in 0..2){val y=6f+i*6;canvas.drawCircle(4f,y,0.8f,pen);line(8f,y,21f,y)}}
            "▤"->{canvas.drawRoundRect(2f,4f,22f,19f,3f,3f,pen);line(6f,9f,18f,9f);line(6f,14f,14f,14f);line(8f,22f,16f,22f)}
            "☰"->{path(3f,7f,3f,20f,21f,20f,21f,6f,12f,6f,10f,3f,3f,3f,3f,7f);line(8f,12f,16f,12f);line(12f,9f,12f,15f)}
            "♥","♡"->{val p=android.graphics.Path();p.moveTo(12f,21f);p.cubicTo(9f,18f,2f,13f,2f,8f);p.cubicTo(2f,1f,10f,1f,12f,7f);p.cubicTo(14f,1f,22f,1f,22f,8f);p.cubicTo(22f,13f,15f,18f,12f,21f);if(symbol=="♥")pen.style=android.graphics.Paint.Style.FILL;canvas.drawPath(p,pen)}
            "Ⅱ"->{pen.strokeWidth=4f;line(8f,4f,8f,20f);line(16f,4f,16f,20f)}
            "|◀","▶|"->{val prev=symbol=="|◀";val p=android.graphics.Path();p.moveTo(if(prev)17f else 7f,4f);p.lineTo(if(prev)6f else 18f,12f);p.lineTo(if(prev)17f else 7f,20f);p.close();canvas.drawPath(p,pen);line(if(prev)4f else 20f,4f,if(prev)4f else 20f,20f)}
            else->{val p=android.graphics.Path();p.moveTo(7f,3f);p.lineTo(21f,12f);p.lineTo(7f,21f);p.close();canvas.drawPath(p,pen)}
        };canvas.restoreToCount(save)
    }
}
