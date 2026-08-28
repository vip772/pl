package h.Hchat.hooks.items.analysis

data class AnalysisMember(val id: String, val name: String, val count: Int)

data class AnalysisSnapshot(
    val talker: String,
    val days: Int,
    val total: Int,
    val textCount: Int,
    val imageCount: Int,
    val voiceCount: Int,
    val videoCount: Int,
    val members: List<AnalysisMember>,
    val hourly: List<Int>
) {
    val speakerCount: Int get() = members.size
}
