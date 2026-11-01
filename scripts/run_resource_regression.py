"""Explicit synthetic resource suite; no Gradle, device commands or real business calls.
Requires the same installed JDK/Kotlin/Android SDK and ABI fixtures as individual tests.
Outputs stay outside the repository. --jobs defaults to 1 to limit memory pressure.
"""
from pathlib import Path
import argparse, concurrent.futures, hashlib, json, subprocess, time

TESTS = '''test_hook_registry_shutdown test_public_api_shutdown test_settings_injector_lifecycle test_selected_senders_shutdown test_selected_send_budget test_selected_sender_admission test_selected_late_publication test_contact_repository_owner test_api_generation_owner test_voice_api_owner test_mars_runtime_owner test_sns_owner_publication test_sns_resolver_owner test_network_dispatcher_shutdown test_message_api_owner test_selected_feature_detach test_header_card_restore test_header_capsule_shutdown test_header_visual_recovery test_header_hook_lifecycle test_home_extension_shutdown test_sns_upload_ownership test_sns_tail_lifecycle test_quote_clear_lifecycle test_quickread_voice_lifecycle test_status_limit_lifecycle test_forward_ringtone_lifecycle test_me_entry_visibility test_block_hook_lifecycle test_background_hook_ownership test_quick_menu_hook_lifecycle test_background_render_gate test_realtail_scheduler_lifecycle test_mention_notice_lifecycle test_mention_notice_order test_mention_notice_span_bounds test_menu_extension_lifecycle test_hide_menu_lifecycle test_groupbar_install_lifecycle test_nickname_width test_protobuf_lifecycle test_monitor_sdk test_floating_shortcut_owner test_custom_bottom_owner test_custom_bottom_icons test_custom_bottom_picker test_step_multiplier_lifecycle test_dex_detached_results test_group_monitor_lifecycle test_pending_notice_tasks test_voice_to_text test_native_group_nickname_gate test_dex_bridge_manager test_dex_query_api test_dex_query_consumers test_dex_finder_gates test_early_dex_gates test_agent_external_idle test_internal_dex_scopes test_fake_location_dex_scope test_fake_location_owner test_fake_location_toggle test_game_emoji_owner test_game_emoji_toggle test_fake_miniprogram_baselib_owner test_miniprogram_video_ads_owner test_splash_ad_dex_scope test_favorite_voice_preparation test_feature_resource_scope test_member_title_lifecycle test_auto_cache_lifecycle test_keyword_notification_lifecycle test_moments_post_notification_lifecycle test_home_side_panel_lifecycle test_qq_music_lifecycle test_text_speech_shutdown test_resource_schedulers test_script_resource_scope test_auto_reply_lifecycle test_preference_runtime_owner test_auto_forward_lifecycle test_monet_export_lifecycle test_runtime_install_ownership test_moments_textvoice_hotstop test_payment_resource_scope test_background_resource_lifecycle test_resource_cache_budgets test_memory_pressure_callbacks test_scheduled_send_state test_background_state_ownership test_input_hint_lifecycle test_manual_send_shutdown test_zombie_shutdown test_pending_send_stop test_custom_notification_epoch test_idle_serial_avatar_budget test_analysis_page_lifecycle test_remaining_runtime_ownership test_emoji_at_choice_queue test_emoji_at_native_passthrough test_home_contacts_batch test_home_extension_idle test_home_music_style_two_sdk test_moments_filter_owner'''.split()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    parser.add_argument('--jobs', type=int, choices=(1, 2), default=1)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = Path(args.output).resolve()
    if output == root or root in output.parents:
        parser.error('Test output must stay outside repository')
    output.mkdir(parents=True, exist_ok=True)
    if (output/'summary.json').exists():
        parser.error('Use a fresh output directory to preserve previous evidence')
    files = sorted((root/'app/src').rglob('*.kt')) + sorted((root/'app/src').rglob('*.java'))
    files += sorted((root/'scripts').glob('*.py'))
    def snapshot():
        return {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    before = snapshot()
    (output/'source-sha256.json').write_text(json.dumps(before, indent=2))
    (output/'head.txt').write_text(subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True))
    def run(name):
        start = time.monotonic()
        print('RUN', name, flush=True)
        try:
            with (output/(name+'.log')).open('w') as log:
                rc = subprocess.run(['python3',str(root/'scripts'/(name+'.py'))],cwd=root,stdout=log,stderr=subprocess.STDOUT,timeout=300).returncode
        except subprocess.TimeoutExpired:
            rc = 124
        return {'test': name, 'exit': rc, 'seconds': round(time.monotonic()-start,1)}
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:
        for result in pool.map(run, TESTS):
            results.append(result)
            (output/'summary.json').write_text(json.dumps(results,indent=2))
            print(result['test'],result['exit'],flush=True)
    stable = before == snapshot()
    failed = sum(x['exit'] != 0 for x in results)
    final = {'total':len(results),'failed':failed,'sourceStable':stable}
    (output/'DONE.json').write_text(json.dumps(final,indent=2))
    print('DONE',final,flush=True)
    return 0 if failed == 0 and stable else 1

if __name__ == '__main__':
    raise SystemExit(main())