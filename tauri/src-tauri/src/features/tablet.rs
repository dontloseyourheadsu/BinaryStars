use tauri::{AppHandle, State, Manager};
use crate::core::types::{AppState, SystemMonitor};
use crate::core::bluetooth::send_raw_bluetooth_message;

#[tauri::command]
pub fn get_available_screens(app: AppHandle) -> Result<Vec<SystemMonitor>, String> {
    let window = app.get_webview_window("main")
        .ok_or_else(|| "Main window not found".to_string())?;
    
    let monitors = window.available_monitors().map_err(|e| e.to_string())?;
    
    let mut screens = Vec::new();
    for (i, m) in monitors.iter().enumerate() {
        let name = m.name().unwrap_or(&format!("Screen {}", i)).to_string();
        let size = m.size();
        let pos = m.position();
        screens.push(SystemMonitor {
            name,
            width: size.width,
            height: size.height,
            x: pos.x,
            y: pos.y,
            scale_factor: m.scale_factor(),
        });
    }
    
    Ok(screens)
}

#[tauri::command]
pub fn set_mapped_screen(
    state: State<'_, AppState>,
    name: String,
    x: i32,
    y: i32,
    width: u32,
    height: u32,
) -> Result<(), String> {
    {
        let mut sx = state.bluetooth.selected_monitor_x.lock().unwrap();
        let mut sy = state.bluetooth.selected_monitor_y.lock().unwrap();
        let mut sw = state.bluetooth.selected_monitor_width.lock().unwrap();
        let mut sh = state.bluetooth.selected_monitor_height.lock().unwrap();
        let mut sname = state.bluetooth.selected_monitor_name.lock().unwrap();
        
        *sx = x;
        *sy = y;
        *sw = width;
        *sh = height;
        *sname = name;
    }
    
    // Send updated ratio to client
    let ratio_msg = format!("TABLET_RATIO|{}|{}\n", width, height);
    let _ = send_raw_bluetooth_message(&state, ratio_msg);
    
    Ok(())
}
