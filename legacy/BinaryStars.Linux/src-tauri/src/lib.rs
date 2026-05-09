mod types;
mod logging;
mod bluetooth;
mod system;
mod location;
mod network;
mod oauth;

use std::sync::Mutex;
use tauri::{Builder, generate_context, generate_handler};
use crate::types::{AppState, BluetoothState};

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let _ = logging::append_log_line("info", "rust:startup", "launching tauri runtime", None);

    Builder::default()
        .manage(AppState {
            bluetooth: BluetoothState {
                messages: Mutex::new(Vec::new()),
                tx: Mutex::new(None),
                session: Mutex::new(None),
                connected_device_id: Mutex::new(None),
            }
        })
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(generate_handler![
            logging::append_ui_log,
            logging::get_log_file_path,
            logging::read_recent_logs,
            system::perform_local_action,
            system::get_device_info,
            system::show_notification,
            system::get_elevation_status,
            system::request_elevated_mode,
            location::get_native_location,
            network::is_wifi_connected,
            oauth::oauth_get_provider_token,
            oauth::oauth_get_auth_url,
            oauth::oauth_get_refresh_token,
            oauth::oauth_logout,
            bluetooth::get_bluetooth_status,
            bluetooth::start_bluetooth_server,
            bluetooth::stop_bluetooth_server,
            bluetooth::send_bluetooth_message,
            bluetooth::send_bluetooth_file,
            bluetooth::download_bluetooth_file,
            bluetooth::get_bluetooth_devices,
            bluetooth::scan_bluetooth_devices,
            bluetooth::verify_bluetooth_identity,
            bluetooth::check_bluetooth_compatibility,
            bluetooth::get_bluetooth_connected_device_names,
            bluetooth::get_active_bluetooth_peers,
            bluetooth::send_file_via_bluetooth,
            bluetooth::send_chat_message_via_bluetooth,
        ])
        .run(generate_context!())
        .expect("error while running tauri application");
}
