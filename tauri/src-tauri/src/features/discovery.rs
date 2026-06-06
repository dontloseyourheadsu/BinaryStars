use tauri::{AppHandle, State};
use bluer::Session;
use std::time::Duration;
use crate::core::types::{AppState, LinuxBluetoothDevice};
use crate::core::bluetooth::{start_server_impl, connect_impl};

#[tauri::command]
pub async fn get_bluetooth_status(state: State<'_, AppState>) -> Result<serde_json::Value, String> {
    let connected_id = state.bluetooth.connected_device_id.lock().unwrap();
    let connected_addr = state.bluetooth.connected_device_address.lock().unwrap();
    let session = state.bluetooth.session.lock().unwrap();
    
    Ok(serde_json::json!({
        "serverRunning": session.is_some(),
        "connectedDeviceId": *connected_id,
        "connectedDeviceAddress": *connected_addr
    }))
}

#[tauri::command]
pub async fn start_bluetooth_server(
    app_handle: AppHandle,
    state: State<'_, AppState>,
    my_device_id: String,
    password: Option<String>,
) -> Result<String, String> {
    start_server_impl(app_handle, state, my_device_id, password).await
}

#[tauri::command]
pub async fn stop_bluetooth_server(state: State<'_, AppState>) -> Result<(), String> {
    let mut session = state.bluetooth.session.lock().unwrap();
    *session = None;
    
    let mut tx = state.bluetooth.tx.lock().unwrap();
    *tx = None;
    
    let mut id = state.bluetooth.connected_device_id.lock().unwrap();
    *id = None;
    
    let mut addr = state.bluetooth.connected_device_address.lock().unwrap();
    *addr = None;
    
    let mut m = state.bluetooth.messages.lock().unwrap();
    m.clear();
    
    let mut pwd = state.bluetooth.password.lock().unwrap();
    *pwd = None;
    
    let mut clients = state.bluetooth.clients.lock().unwrap();
    clients.clear();
    
    Ok(())
}

#[tauri::command]
pub async fn connect_bluetooth_device(
    app_handle: AppHandle,
    state: State<'_, AppState>,
    my_device_id: String,
    device_address: String,
    password: Option<String>,
) -> Result<String, String> {
    connect_impl(app_handle, state, my_device_id, device_address, password).await
}

#[tauri::command]
pub async fn get_bluetooth_devices() -> Result<Vec<LinuxBluetoothDevice>, String> {
    let session = Session::new().await.map_err(|e| e.to_string())?;
    let adapter = session.default_adapter().await.map_err(|e| e.to_string())?;
    let addrs = adapter.device_addresses().await.map_err(|e| e.to_string())?;
    let mut result = Vec::new();
    for addr in addrs {
        if let Ok(dev) = adapter.device(addr) {
            result.push(LinuxBluetoothDevice {
                name: dev.name().await.ok().flatten().unwrap_or_else(|| addr.to_string()),
                address: addr.to_string(),
                connected: dev.is_connected().await.unwrap_or(false),
                paired: dev.is_paired().await.unwrap_or(false),
            });
        }
    }
    Ok(result)
}

#[tauri::command]
pub async fn scan_bluetooth_devices() -> Result<(), String> {
    let session = Session::new().await.map_err(|e| e.to_string())?;
    let adapter = session.default_adapter().await.map_err(|e| e.to_string())?;
    adapter.set_powered(true).await.map_err(|e| e.to_string())?;
    let _discovery = adapter.discover_devices().await.map_err(|e| e.to_string())?;
    tokio::time::sleep(Duration::from_secs(5)).await;
    Ok(())
}

#[tauri::command]
pub async fn get_bluetooth_connected_device_names() -> Result<Vec<String>, String> {
    let session = Session::new().await.map_err(|e| e.to_string())?;
    let adapter = session.default_adapter().await.map_err(|e| e.to_string())?;
    let addrs = adapter.device_addresses().await.map_err(|e| e.to_string())?;
    let mut names = Vec::new();
    for addr in addrs {
        if let Ok(dev) = adapter.device(addr) {
            if dev.is_connected().await.unwrap_or(false) {
                names.push(dev.name().await.ok().flatten().unwrap_or_else(|| addr.to_string()));
            }
        }
    }
    Ok(names)
}
