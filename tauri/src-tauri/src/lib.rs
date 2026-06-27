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
use crate::features::tablet::{
    get_available_screens,
    set_mapped_screen,
};

use tauri::{
    menu::{MenuBuilder, MenuItemBuilder},
    tray::{TrayIconBuilder, TrayIconEvent},
    Manager,
};

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .manage(AppState {
            bluetooth: BluetoothState::new(),
        })
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let show_i = MenuItemBuilder::with_id("show", "Restore").build(app)?;
            let quit_i = MenuItemBuilder::with_id("quit", "Quit").build(app)?;
            let menu = MenuBuilder::new(app)
                .items(&[&show_i, &quit_i])
                .build()?;

            let tray_builder = TrayIconBuilder::new()
                .menu(&menu)
                .on_menu_event(|app, event| {
                    match event.id().as_ref() {
                        "quit" => {
                            app.exit(0);
                        }
                        "show" => {
                            if let Some(window) = app.get_webview_window("main") {
                                let _ = window.show();
                                let _ = window.set_focus();
                            }
                        }
                        _ => {}
                    }
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click { button: tauri::tray::MouseButton::Left, .. } = event {
                        let app = tray.app_handle();
                        if let Some(window) = app.get_webview_window("main") {
                            let _ = window.show();
                            let _ = window.set_focus();
                        }
                    }
                });

            let tray_builder = if let Some(icon) = app.default_window_icon().cloned() {
                tray_builder.icon(icon)
            } else {
                tray_builder
            };

            let _tray = tray_builder.build(app)?;
            Ok(())
        })
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
            get_recent_chats,
            get_available_screens,
            set_mapped_screen

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
