pub mod core;
pub mod features;

use crate::core::types::{AppState, BluetoothState};
use crate::features::discovery::{
    get_bluetooth_status,
    start_bluetooth_server,
    stop_bluetooth_server,
    connect_bluetooth_device,
    get_bluetooth_devices,
    scan_bluetooth_devices,
    get_bluetooth_connected_device_names,
};
use crate::features::chat::{
    send_bluetooth_message,
    send_bluetooth_file,
    download_bluetooth_file,
    get_messages_paged,
    save_file_to_custom_path,
    get_recent_chats,
};

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .manage(AppState {
            bluetooth: BluetoothState::new(),
        })
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            get_bluetooth_status,
            start_bluetooth_server,
            stop_bluetooth_server,
            connect_bluetooth_device,
            get_bluetooth_devices,
            scan_bluetooth_devices,
            get_bluetooth_connected_device_names,
            send_bluetooth_message,
            send_bluetooth_file,
            download_bluetooth_file,
            get_messages_paged,
            save_file_to_custom_path,
            get_recent_chats
        ])
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                let _ = window.hide();
                api.prevent_close();
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
