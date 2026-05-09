use tauri::{AppHandle, State, Emitter};
use bluer::{rfcomm::{Profile, Role}, Session};
use futures::StreamExt;
use tokio::io::{AsyncWriteExt, AsyncBufReadExt, BufReader as TokioBufReader};
use tokio::sync::mpsc;
use crate::logging::append_log_line;
use base64::{Engine as _, engine::general_purpose};
use std::time::Duration;
use std::fs;
use std::process::Command;
use std::collections::HashMap;
use crate::types::{BluetoothMessage, AppState, LinuxBluetoothDevice, CompatibilityReport};
use crate::logging::{current_epoch_ms};

#[tauri::command]
pub async fn get_bluetooth_status(state: State<'_, AppState>) -> Result<serde_json::Value, String> {
    let session = state.bluetooth.session.lock().unwrap();
    let connected_id = state.bluetooth.connected_device_id.lock().unwrap();
    Ok(serde_json::json!({
        "serverRunning": session.is_some(),
        "connectedDeviceId": *connected_id
    }))
}

#[tauri::command]
pub async fn start_bluetooth_server(app_handle: AppHandle, state: State<'_, AppState>, my_device_id: String, allowed_device_ids: Vec<String>) -> Result<String, String> {
    let bluetooth = &state.bluetooth;
    {
        let s = bluetooth.session.lock().unwrap();
        if s.is_some() { return Ok("Server already running".to_string()); }
    }

    let session = Session::new().await.map_err(|e| e.to_string())?;
    let profile = Profile {
        uuid: "00001101-0000-1000-8000-00805F9B34FB".parse().unwrap(),
        role: Some(Role::Server),
        name: Some("BinaryStarsSPP".to_string()),
        channel: Some(1),
        ..Default::default()
    };
    let mut profile_handle = session.register_profile(profile).await.map_err(|e| e.to_string())?;
    { let mut s = bluetooth.session.lock().unwrap(); *s = Some(session); }

    let state_ptr = state.inner() as *const AppState as usize;
    tokio::spawn(async move {
        while let Some(req) = profile_handle.next().await {
            let app_handle = app_handle.clone();
            let state = unsafe { &*(state_ptr as *const AppState) };
            let allowed_ids = allowed_device_ids.clone();
            let my_id = my_device_id.clone();

            if let Ok(stream) = req.accept() {
                let (tx, mut rx) = mpsc::unbounded_channel::<String>();
                let (reader, mut writer) = stream.into_split();
                let mut reader = TokioBufReader::new(reader);

                // Handshake
                let mut identified = false;
                let mut peer_id = String::new();
                let mut line = String::new();
                let res = tokio::time::timeout(Duration::from_secs(5), reader.read_line(&mut line)).await;
                if let Ok(Ok(_)) = res {
                    let raw = line.trim();
                    if raw.starts_with("IDENTIFY|") {
                        let parts: Vec<&str> = raw.splitn(2, '|').collect();
                        if parts.len() >= 2 && allowed_ids.contains(&parts[1].to_string()) {
                            identified = true;
                            peer_id = parts[1].to_string();
                        }
                    }
                }

                if !identified {
                    let _ = writer.write_all(b"ERROR|Identity verification failed\n").await;
                    continue;
                }

                let _ = writer.write_all(format!("IDENTIFIED|{}\n", my_id).as_bytes()).await;
                let _ = writer.flush().await;

                {
                    let mut state_tx = state.bluetooth.tx.lock().unwrap(); *state_tx = Some(tx);
                    let mut state_id = state.bluetooth.connected_device_id.lock().unwrap(); *state_id = Some(peer_id.clone());
                }
                let _ = app_handle.emit("bluetooth-status", serde_json::json!({ "connected": true, "deviceId": peer_id }));

                let read_task = {
                    let app_handle = app_handle.clone();
                    let peer_id = peer_id.clone();
                    let state = state;
                    tokio::spawn(async move {
                        let mut line = String::new();
                        while let Ok(n) = reader.read_line(&mut line).await {
                            if n == 0 { break; }
                            let raw = line.trim();
                            let msg = if raw.starts_with("FILE|") {
                                let parts: Vec<&str> = raw.splitn(3, '|').collect();
                                if parts.len() >= 3 {
                                    let file_name = parts[1].to_string();
                                    let data_len = parts[2].len();
                                    let _ = append_log_line("info", "rust:bluetooth", &format!("Received file header: {} ({} chars base64)", file_name, data_len), None);
                                    BluetoothMessage { 
                                        sender: peer_id.clone(), content: format!("Received file: {}", file_name), 
                                        is_file: true, file_name: Some(file_name), base64_data: Some(parts[2].to_string()),
                                        sent_at: current_epoch_ms()
                                    }
                                } else {
                                    let _ = append_log_line("error", "rust:bluetooth", "Received malformed FILE payload", Some(&raw));
                                    BluetoothMessage { sender: peer_id.clone(), content: "Error: Malformed file received".to_string(), is_file: false, file_name: None, base64_data: None, sent_at: current_epoch_ms() }
                                }
                            } else {
                                BluetoothMessage { sender: peer_id.clone(), content: raw.to_string(), is_file: false, file_name: None, base64_data: None, sent_at: current_epoch_ms() }
                            };
                            { let mut messages = state.bluetooth.messages.lock().unwrap(); messages.push(msg.clone()); }
                            let _ = app_handle.emit("bluetooth-message", msg);
                            line.clear();
                        }
                    })
                };

                let write_task = tokio::spawn(async move {
                    while let Some(msg) = rx.recv().await {
                        // Protocol line already ends with \n from individual commands
                        if writer.write_all(msg.as_bytes()).await.is_err() { break; }
                        let _ = writer.flush().await;
                    }
                });

                tokio::select! { _ = read_task => (), _ = write_task => () };
                {
                    let mut state_id = state.bluetooth.connected_device_id.lock().unwrap(); *state_id = None;
                    let mut state_tx = state.bluetooth.tx.lock().unwrap(); *state_tx = None;
                }
                let _ = app_handle.emit("bluetooth-status", serde_json::json!({ "connected": false, "deviceId": null }));
            }
        }
    });
    Ok("Server started".to_string())
}

#[tauri::command]
pub async fn stop_bluetooth_server(state: State<'_, AppState>) -> Result<(), String> {
    let mut session = state.bluetooth.session.lock().unwrap(); *session = None;
    let mut tx = state.bluetooth.tx.lock().unwrap(); *tx = None;
    let mut id = state.bluetooth.connected_device_id.lock().unwrap(); *id = None;
    let mut m = state.bluetooth.messages.lock().unwrap(); m.clear();
    Ok(())
}

#[tauri::command]
pub async fn send_bluetooth_message(state: State<'_, AppState>, content: String) -> Result<(), String> {
    let tx = state.bluetooth.tx.lock().unwrap();
    if let Some(tx) = &*tx {
        // Raw message, sanitize and add protocol line
        let clean = content.replace('\n', " ");
        tx.send(format!("{}\n", clean)).map_err(|e| e.to_string())?;
        let mut m = state.bluetooth.messages.lock().unwrap();
        m.push(BluetoothMessage { sender: "Me".to_string(), content, is_file: false, file_name: None, base64_data: None, sent_at: current_epoch_ms() });
        Ok(())
    } else { Err("No device connected".to_string()) }
}

#[tauri::command]
pub async fn send_bluetooth_file(state: State<'_, AppState>, name: String, base64_data: String) -> Result<(), String> {
    let tx = state.bluetooth.tx.lock().unwrap();
    if let Some(tx) = &*tx {
        // File payload is already newline-free if NO_WRAP was used
        let payload = format!("FILE|{}|{}\n", name, base64_data);
        tx.send(payload).map_err(|e| e.to_string())?;
        let mut m = state.bluetooth.messages.lock().unwrap();
        m.push(BluetoothMessage { sender: "Me".to_string(), content: format!("Sent file: {}", name), is_file: true, file_name: Some(name), base64_data: Some(base64_data), sent_at: current_epoch_ms() });
        Ok(())
    } else { Err("No device connected".to_string()) }
}

#[tauri::command]
pub async fn download_bluetooth_file(state: State<'_, AppState>, file_name: String) -> Result<String, String> {
    let mut messages = state.bluetooth.messages.lock().unwrap();
    if let Some(index) = messages.iter().position(|m| m.is_file && m.file_name.as_ref() == Some(&file_name)) {
        let msg = messages.remove(index);
        if let Some(base64) = msg.base64_data {
            let decoded = general_purpose::STANDARD.decode(base64).map_err(|e| e.to_string())?;
            let path = dirs::download_dir().unwrap_or_else(|| std::env::current_dir().unwrap()).join(&file_name);
            fs::write(&path, decoded).map_err(|e| e.to_string())?;
            return Ok(path.to_string_lossy().to_string());
        }
    }
    Err("File not found".to_string())
}

#[tauri::command]
pub async fn get_bluetooth_devices() -> Result<Vec<LinuxBluetoothDevice>, String> {
    let _session = Session::new().await.map_err(|e| e.to_string())?;
    let adapter = _session.default_adapter().await.map_err(|e| e.to_string())?;
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
    let _session = Session::new().await.map_err(|e| e.to_string())?;
    let adapter = _session.default_adapter().await.map_err(|e| e.to_string())?;
    adapter.set_powered(true).await.map_err(|e| e.to_string())?;
    let _discovery = adapter.discover_devices().await.map_err(|e| e.to_string())?;
    tokio::time::sleep(Duration::from_secs(5)).await;
    Ok(())
}

#[tauri::command]
pub async fn verify_bluetooth_identity(device_address: String, sender_device_id: String, _sender_device_name: String, expected_target_device_id: String) -> Result<bool, String> {
    let _session = Session::new().await.map_err(|e| e.to_string())?;
    let addr = device_address.parse::<bluer::Address>().map_err(|e| e.to_string())?;
    let stream = match tokio::time::timeout(Duration::from_secs(10), bluer::rfcomm::Stream::connect(bluer::rfcomm::SocketAddr { addr, channel: 1 })).await {
        Ok(Ok(s)) => s,
        _ => return Ok(false),
    };
    let (reader, mut writer) = stream.into_split();
    let mut reader = TokioBufReader::new(reader);
    let _ = writer.write_all(format!("IDENTIFY|{}\n", sender_device_id).as_bytes()).await;
    let _ = writer.flush().await;
    let mut line = String::new();
    match tokio::time::timeout(Duration::from_secs(5), reader.read_line(&mut line)).await {
        Ok(Ok(_)) => {
            let raw = line.trim();
            if raw.starts_with("IDENTIFIED|") {
                let parts: Vec<&str> = raw.splitn(2, '|').collect();
                return Ok(parts.len() >= 2 && parts[1] == expected_target_device_id);
            }
        }
        _ => {}
    }
    Ok(false)
}

#[tauri::command]
pub async fn check_bluetooth_compatibility() -> Result<CompatibilityReport, String> {
    let sdptool = Command::new("which").arg("sdptool").output().map(|o| o.status.success()).unwrap_or(false);
    let bluez = Command::new("systemctl").args(["status", "bluetooth"]).output().map(|o| String::from_utf8_lossy(&o.stdout).contains("--compat")).unwrap_or(false);
    Ok(CompatibilityReport { sdptool_installed: sdptool, bluez_compat_mode: bluez, can_open_socket: true })
}

#[tauri::command]
pub async fn get_bluetooth_connected_device_names() -> Result<Vec<String>, String> {
    let _session = Session::new().await.map_err(|e| e.to_string())?;
    let adapter = _session.default_adapter().await.map_err(|e| e.to_string())?;
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

#[tauri::command]
pub async fn get_active_bluetooth_peers(state: State<'_, AppState>) -> Result<HashMap<String, String>, String> {
    let id = state.bluetooth.connected_device_id.lock().unwrap();
    let mut map = HashMap::new();
    if let Some(device_id) = &*id { map.insert(device_id.clone(), "Connected".to_string()); }
    Ok(map)
}

#[tauri::command]
pub async fn send_file_via_bluetooth(state: State<'_, AppState>, _device_address: String, _sender_device_id: String, _sender_device_name: String, target_device_id: String, _target_device_name: String, file_name: String, content_base64: String) -> Result<serde_json::Value, String> {
    let tx = state.bluetooth.tx.lock().unwrap();
    if let Some(tx) = &*tx {
        let payload = format!("FILE|{}|{}\n", file_name, content_base64);
        tx.send(payload).map_err(|e| e.to_string())?;
        return Ok(serde_json::json!({ "id": format!("bt-{}", current_epoch_ms()), "status": "Downloaded" }));
    }
    Err("No active Bluetooth connection".to_string())
}

#[tauri::command]
pub async fn send_chat_message_via_bluetooth(state: State<'_, AppState>, _device_address: String, _sender_device_id: String, _sender_device_name: String, _target_device_id: String, _target_device_name: String, body: String) -> Result<serde_json::Value, String> {
    let tx = state.bluetooth.tx.lock().unwrap();
    if let Some(tx) = &*tx {
        let clean = body.replace('\n', " ");
        tx.send(format!("{}\n", clean)).map_err(|e| e.to_string())?;
        Ok(serde_json::json!({ "id": format!("bt-msg-{}", current_epoch_ms()), "senderDeviceId": "Me", "body": body, "sentAt": "now" }))
    } else { Err("No active Bluetooth connection".to_string()) }
}
