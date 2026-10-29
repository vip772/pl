package t.mma.ui

import android.content.Context
import t.mma.hooks.items.conversationgroup.ConversationGroupStore
import t.mma.hooks.items.conversationanalysis.ConversationAnalysisFeature
import t.mma.hooks.items.conversationanalysis.ConversationAnalysisSettings

import t.mma.hooks.items.hchatextra.HchatExtraSettings
import t.mma.hooks.items.momentsfake.MomentsFakeInteractionSettings
import t.mma.hooks.items.specialmessage.SpecialMessageFeature
import t.mma.hooks.items.profileid.ProfileGroupJoinTimeSettings
import t.mma.hooks.items.profileid.ProfileGroupInviterSettings
import t.mma.hooks.items.profileid.ProfileIdSettings
import t.mma.hooks.items.profileid.ProfileAddTimeSettings
import t.mma.preferences.HchatStorage

/** Reads the same primary toggle used by each feature settings page. */
object FeatureToggleRegistry {
    private data class Toggle(val prefs: String, val key: String, val defaultValue: Boolean = false)

    private val toggles = mapOf(
        "home_extension" to Toggle("Hchat_home_extension_config", "enable"),
        "chat_toolbar" to Toggle("Hchat_chat_toolbar_config", "enable"),
        "upload_transparent_avatar" to Toggle("Hchat_upload_transparent_avatar_config", "upload_transparent_avatar_enable"),
        "conversation_group_bar" to Toggle("Hchat_conversation_group_bar_config", "enable"),
        "group_nickname_width" to Toggle("Hchat_group_nickname_width_config", "enable"),
        "step_multiplier" to Toggle("Hchat_step_multiplier", "enable"),
        "auto_cache" to Toggle("Hchat_auto_cache_config", "enable"),
        "home_side_panel" to Toggle("Hchat_home_side_panel", "enable"),
        "anti_recall" to Toggle("Hchat_anti_recall_config", "anti_recall_enable"),
        "block_at_all_notification" to Toggle("Hchat_block_at_all_notification_config", "block_at_all_notification_enable"),
        "auto_message_forward" to Toggle("Hchat_auto_message_forward_config", "enabled"),
        "auto_original_image" to Toggle("Hchat_auto_original_image_config", "auto_original_image_enable"),
        "auto_reply" to Toggle("Hchat_auto_reply_config", "auto_reply_enable"),
        "auto_view_original" to Toggle("Hchat_auto_view_original_config", "auto_view_original_enable"),
        "background_beauty" to Toggle("Hchat_background_beauty_config", "enable"),
        "call_media_limit" to Toggle("Hchat_call_media_limit_config", "call_media_limit_enable"),
        "custom_bottom_bar" to Toggle("Hchat_native_bottom_bar_config", "enable"),
        "dynamic_badge" to Toggle("Hchat_dynamic_badge_config", "dynamic_badge_enable"),
        "call_ringtone_block" to Toggle("Hchat_call_media_limit_config", "call_media_limit_enable"),
        "custom_friend_avatar" to Toggle("Hchat_custom_friend_avatar_config", "enable"),
        "custom_notification" to Toggle("Hchat_custom_notification", "custom_notification_enable"),
        "edit_message" to Toggle("Hchat_edit_message_config", "edit_message_enable"),
        "emoji_save" to Toggle("Hchat_emoji_save_config", "emoji_save_enable"),
        "fake_location" to Toggle("Hchat_fake_location_config", "fake_location_enable"),
        "fake_scan_camera" to Toggle("Hchat_fake_scan_camera_config", "fake_scan_camera_enable"),
        "fake_voice_duration" to Toggle("Hchat_fake_voice_duration_config", "fake_voice_duration_enable"),
        "floating_shortcut_menu" to Toggle("floating_shortcut_menu", "enable"),
        "remove_forward_limit" to Toggle("Hchat_remove_forward_limit_config", "remove_forward_limit_enable"),
        "quick_group_chat_label" to Toggle("Hchat_quick_group_chat_label_config", "enable"),
        "group_leave_monitor" to Toggle("Hchat_group_leave_monitor_config", "group_leave_monitor_enable"),
        "group_nickname_color" to Toggle("Hchat_group_nickname_color_config", "enable"),
        "message_menu_extension" to Toggle("Hchat_message_menu_extension_config", "message_menu_extension_enable"),
        "hide_chat_menu" to Toggle("Hchat_hide_chat_menu_config", "hide_chat_menu_enable"),
        "home_text_color" to Toggle("Hchat_home_text_color_config", "home_text_color_enable"),
        "disable_hot_update" to Toggle("Hchat_disable_hot_update_config", "disable_hot_update_enable"),
        "input_hint" to Toggle("Hchat_input_hint_config", "input_hint_enable"),
        "wechat_keep_alive" to Toggle("Hchat_wechat_keep_alive_config", "wechat_keep_alive_enable"),
        "keyword_notification" to Toggle("Hchat_keyword_notification_config", "keyword_notify_enable"),
        "member_title" to Toggle("Hchat_member_title_config", "enable"),
        "message_affix" to Toggle("Hchat_message_affix_config", "message_affix_enable"),
        "message_block" to Toggle("Hchat_message_block_config", "message_block_enable"),
        "message_bubble" to Toggle("Hchat_message_bubble_config", "message_bubble_enable"),
        "message_forward" to Toggle("Hchat_message_forward_config", "message_forward_enable"),
        "message_text_color" to Toggle("Hchat_message_text_color_config", "message_text_color_enable"),
        "mention_highlight" to Toggle("Hchat_mention_highlight_config", "mention_highlight_enable"),
        "fake_mini_program_base_lib" to Toggle("Hchat_fake_mini_program_base_lib_config", "fake_mini_program_base_lib_enable"),
        "skip_global_mini_program_splash_ads" to Toggle("Hchat_skip_global_mini_program_splash_ads_config", "skip_global_mini_program_splash_ads_enable"),
        "skip_mini_program_video_ads" to Toggle("Hchat_skip_mini_program_video_ads_config", "skip_mini_program_video_ads_enable"),
        "moments_auto_comment" to Toggle("Hchat_moments_auto_comment_config", "comment_enable"),
        "moments_auto_forward" to Toggle("Hchat_moments_auto_forward_config", "enable"),
        "moments_auto_like" to Toggle("Hchat_moments_auto_like_config", "enable"),
        "moments_auto_refresh" to Toggle("Hchat_moments_auto_refresh_config", "enable"),
        "moments_bottom_detail" to Toggle("Hchat_moments_bottom_detail_config", "moments_bottom_detail_enable"),
        "moments_contact_filter" to Toggle("Hchat_moments_contact_filter_config", "moments_contact_filter_enable"),
        "moments_keyword_block" to Toggle("Hchat_moments_keyword_block_config", "moments_keyword_block_enable"),
        "moments_post_notification" to Toggle("Hchat_moments_post_notification_config", "enable"),
        "moments_upload_tail" to Toggle("Hchat_moments_upload_tail_config", "enable"),
        "original_moments_upload" to Toggle("Hchat_original_moments_upload_config", "original_moments_upload_enable"),
        "remove_moments_ads" to Toggle("Hchat_remove_moments_ads_config", "remove_moments_ads_enable"),
        "sns_anti_recall" to Toggle("Hchat_sns_anti_recall_config", "sns_anti_recall_enable"),
        "multi_recall" to Toggle("Hchat_multi_recall_config", "multi_recall_enable"),
        "qq_music_order" to Toggle("Hchat_qq_music_order_config", "qq_music_order_enable"),
        "disable_pat" to Toggle("Hchat_disable_pat_config", "disable_pat_enable"),
        "profile_id" to Toggle(ProfileIdSettings.PREFS_NAME, ProfileIdSettings.KEY_ENABLE),
        "protobuf_packet" to Toggle("Hchat_protobuf_packet_config", "protobuf_packet_enable"),
        "quick_contact_edit" to Toggle("Hchat_quick_contact_edit_config", "quick_contact_edit_enable"),
        "quick_moments" to Toggle("Hchat_quick_moments_config", "quick_moments_enable"),
        "quick_terminate" to Toggle("Hchat_quick_terminate_config", "quick_terminate_enable"),
        "quote_delete_clear" to Toggle("Hchat_quote_delete_clear_config", "quote_delete_clear_enable"),
        "real_name_tail" to Toggle("Hchat_real_name_tail_config", "enable"),
        "round_avatar" to Toggle("Hchat_round_avatar_config", "round_avatar_enable"),
        "scheduled_task" to Toggle("Hchat_scheduled_task_config", "scheduled_task_enable"),
        "script_plugin" to Toggle("Hchat_script_plugin_config", "script_plugin_enable"),
        "selected_messages" to Toggle("Hchat_selected_messages_config", "selected_messages_enable", true),
        "finder_media_download" to Toggle("Hchat_finder_media_download", "enable"),
        "status_text_limit" to Toggle("Hchat_status_text_limit_config", "status_text_limit_enable"),
        "swipe_quote" to Toggle("Hchat_swipe_quote_config", "swipe_quote_enable"),
        "wechat_tablet" to Toggle("Hchat_wechat_tablet_config", "tablet_enable"),
        "text_speech" to Toggle("Hchat_text_speech_config", "text_speech_enable"),
        "block_typing_report" to Toggle("Hchat_block_typing_report_config", "block_typing_report_enable"),
        "voice_forward" to Toggle("Hchat_voice_forward_config", "voice_forward_enable"),
        "voice_to_text" to Toggle("Hchat_voice_to_text_config", "enable"),
        "voice_preview" to Toggle("Hchat_voice_preview_config", "voice_preview_enable"),
        "zombie_check" to Toggle("Hchat_zombie_check_config", "zombie_check_enable"),
        "auto_redpacket" to Toggle("Hchat_redpacket_config", "hb_auto_enable"),
        "fake_wallet_balance" to Toggle("Hchat_fake_wallet_balance_config", "fake_wallet_balance_enable"),
        "auto_transfer" to Toggle("Hchat_transfer_config", "transfer_auto_enable")
    )

    fun isEnabled(context: Context, featureId: String): Boolean {
        return when (featureId) {
            "me_entry_visibility" -> {
                val sp = HchatStorage.preferences(context, t.mma.hooks.items.backgroundbeauty.BackgroundBeautySettings.PREFS_NAME)
                t.mma.hooks.items.backgroundbeauty.MeEntryVisibility.Entry.values().any { sp.getBoolean(it.key, false) }
            }
            "floating_chat_header" -> global(context, featureId)
            "floating_bottom_bar" -> boolean(context, "Hchat_custom_bottom_bar_config", "custom_bottom_bar_enable") &&
                string(context, "Hchat_custom_bottom_bar_config", "custom_bottom_bar_style") == "floating"
            "mention_extension" -> boolean(context, "Hchat_hidden_at_config", "enable") ||
                boolean(context, "Hchat_emoji_at_config", "enable") ||
                boolean(context, "Hchat_mention_highlight_config", "mention_highlight_enable") ||
                t.mma.hooks.items.mentionextension.MentionNoticeSettings.enabled(context)
            "special_message" -> {
                val sp = HchatStorage.preferences(context, SpecialMessageFeature.PREFS)
                sp.getBoolean("enabled", false) || sp.getBoolean("link", false)
            }
            "conversation_group" -> boolean(context, ConversationGroupStore.PREFS_NAME, ConversationGroupStore.KEY_ENABLE, ConversationGroupStore.DEFAULT_ENABLE)
            ConversationAnalysisFeature.ID -> ConversationAnalysisSettings.isEnabled(context)
            "group_chat_label" -> boolean(context, "Hchat_group_chat_labels", "enabled")
            "group_rename_monitor" -> boolean(context, "Hchat_group_rename_monitor_config", "group_rename_notice_enable") ||
                boolean(context, "Hchat_group_rename_monitor_config", "group_rename_send_enable")
            "hchat_group_member_history" -> boolean(context, HchatExtraSettings.PREFS_NAME, HchatExtraSettings.KEY_GROUP_MEMBER_HISTORY)
            "hchat_message_time" -> boolean(context, HchatExtraSettings.PREFS_NAME, HchatExtraSettings.KEY_MESSAGE_DETAILS)
            "hchat_red_packet_details" -> boolean(context, HchatExtraSettings.PREFS_NAME, HchatExtraSettings.KEY_RED_PACKET_DETAILS)
            "hchat_skip_web_risk" -> boolean(context, HchatExtraSettings.PREFS_NAME, HchatExtraSettings.KEY_SKIP_WEB_RISK)
            "moments_fake_like", "moments_fake_comment", "moments_fake_forward" -> {
                val key = when (featureId) {
                    "moments_fake_like" -> MomentsFakeInteractionSettings.KEY_FAKE_LIKE_ENABLE
                    "moments_fake_forward" -> MomentsFakeInteractionSettings.KEY_FAKE_FORWARD_ENABLE
                    else -> MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_ENABLE
                }
                boolean(context, MomentsFakeInteractionSettings.PREFS_NAME, key)
            }
            "profile_id" -> boolean(context, ProfileIdSettings.PREFS_NAME, ProfileIdSettings.KEY_ENABLE) ||
                boolean(context, ProfileAddTimeSettings.PREFS_NAME, ProfileAddTimeSettings.KEY_ENABLE)
            "profile_group_info" -> boolean(context, ProfileGroupJoinTimeSettings.PREFS_NAME, ProfileGroupJoinTimeSettings.KEY_ENABLE) ||
                boolean(context, ProfileGroupInviterSettings.PREFS_NAME, ProfileGroupInviterSettings.KEY_ENABLE)
            "moments_fake_interaction" -> {
                val sp = HchatStorage.preferences(context, MomentsFakeInteractionSettings.PREFS_NAME)
                sp.getBoolean(MomentsFakeInteractionSettings.KEY_FAKE_LIKE_ENABLE, false) ||
                    sp.getBoolean(MomentsFakeInteractionSettings.KEY_FAKE_COMMENT_ENABLE, false) ||
                    sp.getBoolean(MomentsFakeInteractionSettings.KEY_FAKE_FORWARD_ENABLE, false)
            }
            "text_voice" -> boolean(context, "Hchat_text_voice_config", "text_voice_send_enable") ||
                boolean(context, "Hchat_text_voice_config", "text_voice_play_enable")
            "hide_chat_avatar" -> boolean(context, "Hchat_hide_chat_avatar_config", "hide_chat_avatar_self") ||
                boolean(context, "Hchat_hide_chat_avatar_config", "hide_chat_avatar_other")
            "audio_transform" -> boolean(context, "Hchat_audio_transform_config", "audio_transform_split_enabled")
            "chat_time_style" -> string(context, "Hchat_chat_time_style_config", "chat_time_mode") in setOf("custom", "hidden")
            "quick_mark_read" -> boolean(context, "Hchat_quick_mark_read_config", "drag_read_enable") ||
                boolean(context, "Hchat_quick_mark_read_config", "plus_menu_read_enable")
            "game_emoji_result" -> boolean(context, "Hchat_game_emoji_config", "game_emoji_fixed_result") ||
                boolean(context, "Hchat_game_emoji_config", "game_emoji_pick_before_send")
            // Unknown tools/pages do not become enabled from a guessed global key.
            else -> toggles[featureId]?.let { toggle -> boolean(context, toggle.prefs, toggle.key, toggle.defaultValue) } ?: false
        }
    }

    private fun global(context: Context, featureId: String): Boolean =
        boolean(context, "Hchat_global_config", "${featureId}_enabled")

    private fun boolean(context: Context, prefsName: String, key: String, defaultValue: Boolean = false): Boolean =
        HchatStorage.preferences(context, prefsName).getBoolean(key, defaultValue)

    private fun string(context: Context, prefsName: String, key: String): String =
        HchatStorage.preferences(context, prefsName).getString(key, null).orEmpty()

}
