package t.mma.hooks.items.homeextension
internal object HomeMusicLyrics{
    fun parse(text:String):List<Pair<Int,String>>{
        val offset=Regex("\\[offset:([+-]?\\d+)\\]",RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toIntOrNull()?:0
        val pattern=Regex("\\[(\\d+):(\\d{1,2}(?:\\.\\d{1,3})?)\\]")
        return text.lineSequence().flatMap{line->val content=line.replace(Regex("\\[[^]]*\\]"),"").trim();if(content.isEmpty())emptySequence()else pattern.findAll(line).mapNotNull{m->val minute=m.groupValues[1].toLongOrNull()?:return@mapNotNull null;val second=m.groupValues[2].toDoubleOrNull()?:return@mapNotNull null;val time=(minute*60000+second*1000+offset).toLong().coerceIn(0,Int.MAX_VALUE.toLong()).toInt();time to content}}.sortedBy{it.first}.toList()
    }
    fun indexAt(lines:List<Pair<Int,String>>,position:Int):Int{var lo=0;var hi=lines.size;while(lo<hi){val mid=(lo+hi)/2;if(lines[mid].first<=position)lo=mid+1 else hi=mid};return lo-1}
}