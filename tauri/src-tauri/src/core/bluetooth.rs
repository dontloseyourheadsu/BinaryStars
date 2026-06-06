use tauri::{AppHandle, State, Emitter, Manager};
use bluer::{rfcomm::{Profile, Role}, Session};
use futures::StreamExt;
use tokio::io::{AsyncWriteExt, AsyncBufReadExt, BufReader as TokioBufReader};
use tokio::sync::mpsc;
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use std::fs;
use std::collections::HashMap;
use crate::core::types::{BluetoothMessage, AppState, LinuxBluetoothDevice};
use base64::{Engine as _, engine::general_purpose};

pub fn current_epoch_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or(Duration::from_secs(0))
        .as_millis() as u64
}

fn save_received_file(file_name: &str, base64_str: &str) -> Option<String> {
    if let Ok(bytes) = general_purpose::STANDARD.decode(base64_str) {
        let save_dir = dirs::data_dir()
            .unwrap_or_else(|| std::env::current_dir().unwrap())
            .join("BinaryStars")
            .join("transfers")
            .join("received");
        if fs::create_dir_all(&save_dir).is_ok() {
            let local_path = save_dir.join(format!("{}_{}", current_epoch_ms(), file_name));
            if fs::write(&local_path, bytes).is_ok() {
                return Some(local_path.to_string_lossy().to_string());
            }
        }
    }
    None
}

pub async fn start_server_impl(
    app_handle: AppHandle,
    state: State<'_, AppState>,
    my_device_id: String,
    password: Option<String>,
) -> Result<String, String> {
    let bluetooth = &state.bluetooth;
    {
        let s = bluetooth.session.lock().unwrap();
        if s.is_some() {
            return Ok("Server already running".to_string());
        }
    }

    {
        let mut pwd = bluetooth.password.lock().unwrap();
        *pwd = password.clone();
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
    {
        let mut s = bluetooth.session.lock().unwrap();
        *s = Some(session);
    }

    let state_ptr = state.inner() as *const AppState as usize;
    tokio::spawn(async move {
        while let Some(req) = profile_handle.next().await {
            let app_handle = app_handle.clone();
            let state = unsafe { &*(state_ptr as *const AppState) };
            let my_id = my_device_id.clone();

            if let Ok(stream) = req.accept() {
                let peer_address = stream.peer_addr().ok().map(|a| a.addr.to_string()).unwrap_or_default();

                // Check if already connected (enforce single-client connection)
                if state.bluetooth.connected_device_id.lock().unwrap().is_some() {
                    let mut writer = stream;
                    let _ = writer.write_all(b"ERROR|Host busy: client already connected\n").await;
                    let _ = writer.flush().await;
                    eprintln!("[ERROR] CONNECTION REJECTED (SERVER): Host is already connected");
                    continue;
                }

                let (tx, mut rx) = mpsc::unbounded_channel::<String>();
                let (reader, mut writer) = stream.into_split();
                let mut reader = TokioBufReader::new(reader);

                // Handshake
                let mut identified = false;
                let mut pwd_fail = false;
                let mut peer_id = String::new();
                let mut line = String::new();
                let res = tokio::time::timeout(Duration::from_secs(5), reader.read_line(&mut line)).await;
                if let Ok(Ok(_)) = res {
                    let raw = line.trim();
                    if raw.starts_with("IDENTIFY|") {
                        let parts: Vec<&str> = raw.split('|').collect();
                        if parts.len() >= 2 {
                            peer_id = parts[1].to_string();
                            let client_pwd = parts.get(2).map(|s| s.to_string()).unwrap_or_default();

                            let host_pwd_opt = state.bluetooth.password.lock().unwrap().clone();
                            if let Some(host_pwd) = host_pwd_opt {
                                if !host_pwd.is_empty() && host_pwd != client_pwd {
                                    pwd_fail = true;
                                } else {
                                    identified = true;
                                }
                            } else {
                                identified = true;
                            }
                        }
                    }
                }

                if pwd_fail {
                    let mut writer = writer;
                    let _ = writer.write_all(b"ERROR|Password required or incorrect\n").await;
                    let _ = writer.flush().await;
                    eprintln!("[ERROR] CONNECTION REJECTED (SERVER): Password mismatch from {}", peer_address);
                    continue;
                }

                if !identified {
                    let mut writer = writer;
                    let _ = writer.write_all(b"ERROR|Identity verification failed\n").await;
                    let _ = writer.flush().await;
                    eprintln!("[ERROR] CONNECTION FAILED (SERVER): Handshake validation failed from {}", peer_address);
                    continue;
                }

                let _ = writer.write_all(format!("IDENTIFIED|{}\n", my_id).as_bytes()).await;
                let _ = writer.flush().await;
                println!("[INFO] CONNECTION SUCCESSFUL (SERVER): Connected to peer {} ({})", peer_id, peer_address);

                {
                    let mut state_tx = state.bluetooth.tx.lock().unwrap();
                    *state_tx = Some(tx);
                    let mut state_id = state.bluetooth.connected_device_id.lock().unwrap();
                    *state_id = Some(peer_id.clone());
                    let mut state_addr = state.bluetooth.connected_device_address.lock().unwrap();
                    *state_addr = Some(peer_address.clone());
                }
                
                let _ = app_handle.emit("bluetooth-status", serde_json::json!({ "connected": true, "deviceId": peer_id, "deviceAddress": peer_address }));

                let read_task = {
                    let app_handle = app_handle.clone();
                    let peer_id = peer_id.clone();
                    let state = state;
                    tokio::spawn(async move {
                        let mut line = String::new();
                        while let Ok(n) = reader.read_line(&mut line).await {
                            if n == 0 {
                                break;
                            }
                            let raw = line.trim();
                            let msg = if raw.starts_with("FILE|") {
                                let parts: Vec<&str> = raw.splitn(3, '|').collect();
                                if parts.len() >= 3 {
                                    let file_name = parts[1].to_string();
                                    let file_path = save_received_file(&file_name, parts[2]);
                                    BluetoothMessage {
                                        id: format!("msg-{}", current_epoch_ms()),
                                        sender: peer_id.clone(),
                                        content: format!("Received file: {}", file_name),
                                        is_file: true,
                                        file_name: Some(file_name),
                                        base64_data: None,
                                        file_path,
                                        sent_at: current_epoch_ms(),
                                    }
                                } else {
                                    BluetoothMessage {
                                        id: format!("msg-{}", current_epoch_ms()),
                                        sender: peer_id.clone(),
                                        content: "Error: Malformed file payload received".to_string(),
                                        is_file: false,
                                        file_name: None,
                                        base64_data: None,
                                        file_path: None,
                                        sent_at: current_epoch_ms(),
                                    }
                                }
                            } else {
                                println!("[INFO] MESSAGE RECEIVED: From {} to Me", peer_id);
                                BluetoothMessage {
                                    id: format!("msg-{}", current_epoch_ms()),
                                    sender: peer_id.clone(),
                                    content: raw.to_string(),
                                    is_file: false,
                                    file_name: None,
                                    base64_data: None,
                                    file_path: None,
                                    sent_at: current_epoch_ms(),
                                }
                            };
                            
                            // Save to SQLite database
                            let _ = crate::core::database::insert_message_to_db(&peer_id, &msg);
                            
                            {
                                let mut messages = state.bluetooth.messages.lock().unwrap();
                                messages.push(msg.clone());
                            }
                            let _ = app_handle.emit("bluetooth-message", msg.clone());
                            trigger_notification(&app_handle, &peer_id, &msg.content, msg.is_file);
                            line.clear();
                        }
                    })
                };

                let write_task = tokio::spawn(async move {
                    while let Some(msg) = rx.recv().await {
                        if writer.write_all(msg.as_bytes()).await.is_err() {
                            break;
                        }
                        let _ = writer.flush().await;
                    }
                });

                tokio::select! {
                    _ = read_task => (),
                    _ = write_task => ()
                };

                {
                    let mut state_id = state.bluetooth.connected_device_id.lock().unwrap();
                    *state_id = None;
                    let mut state_addr = state.bluetooth.connected_device_address.lock().unwrap();
                    *state_addr = None;
                    let mut state_tx = state.bluetooth.tx.lock().unwrap();
                    *state_tx = None;
                }
                let _ = app_handle.emit("bluetooth-status", serde_json::json!({ "connected": false, "deviceId": null, "deviceAddress": null }));
            }
        }
    });

    Ok("Server started".to_string())
}

pub async fn connect_impl(
    app_handle: AppHandle,
    state: State<'_, AppState>,
    my_device_id: String,
    device_address: String,
) -> Result<String, String> {
    let bluetooth = &state.bluetooth;
    let addr = device_address.parse::<bluer::Address>().map_err(|e| e.to_string())?;
    let session = Session::new().await.map_err(|e| e.to_string())?;
    let adapter = session.default_adapter().await.map_err(|e| e.to_string())?;
    let _ = adapter.set_powered(true).await;
    
    let uuid = bluer::Uuid::parse_str("00001101-0000-1000-8000-00805F9B34FB").unwrap();
    let profile = Profile {
        uuid,
        name: Some("BinaryStarsClient".to_string()),
        role: Some(Role::Client),
        require_authentication: Some(false),
        require_authorization: Some(false),
        auto_connect: Some(true),
        ..Default::default()
    };
    
    let mut profile_handle = session.register_profile(profile).await.map_err(|e| e.to_string())?;
    
    let dev = adapter.device(addr).map_err(|e| e.to_string())?;
    
    let dev_clone = dev.clone();
    tokio::spawn(async move {
        let _ = dev_clone.connect().await;
        let _ = dev_clone.connect_profile(&uuid).await;
    });

    let stream = match tokio::time::timeout(
        Duration::from_secs(15),
        profile_handle.next()
    ).await {
        Ok(Some(req)) => req.accept().map_err(|e| e.to_string())?,
        _ => return Err("Connection timed out or failed to resolve SPP service channel on target device".to_string()),
    };

    let (rx, tx) = stream.into_split();
    let mut writer = tx;
    let reader = TokioBufReader::new(rx);

    // Handshake
    let mut identified = false;
    let mut peer_id = String::new();
    
    let _ = writer.write_all(format!("IDENTIFY|{}\n", my_device_id).as_bytes()).await;
    let _ = writer.flush().await;

    let mut reader = reader;
    let mut line = String::new();
    let res = tokio::time::timeout(Duration::from_secs(5), reader.read_line(&mut line)).await;
    if let Ok(Ok(_)) = res {
        let raw = line.trim();
        if raw.starts_with("IDENTIFIED|") {
            let parts: Vec<&str> = raw.splitn(2, '|').collect();
            if parts.len() >= 2 {
                identified = true;
                peer_id = parts[1].to_string();
            }
        }
    }

    if !identified {
        eprintln!("[ERROR] CONNECTION FAILED (CLIENT): Handshake verification failed from {}", device_address);
        return Err("Handshake failed".to_string());
    }

    let (write_tx, mut write_rx) = mpsc::unbounded_channel::<String>();

    {
        let mut state_tx = bluetooth.tx.lock().unwrap();
        *state_tx = Some(write_tx);
        let mut state_id = bluetooth.connected_device_id.lock().unwrap();
        *state_id = Some(peer_id.clone());
        let mut state_addr = bluetooth.connected_device_address.lock().unwrap();
        *state_addr = Some(device_address.clone());
    }

    let _ = app_handle.emit("bluetooth-status", serde_json::json!({ "connected": true, "deviceId": peer_id, "deviceAddress": device_address }));
    println!("[INFO] CONNECTION SUCCESSFUL (CLIENT): Connected to peer {} ({})", peer_id, device_address);

    let state_ptr = state.inner() as *const AppState as usize;
    let peer_id_clone = peer_id.clone();
    let app_handle_clone = app_handle.clone();
    
    tokio::spawn(async move {
        let state = unsafe { &*(state_ptr as *const AppState) };
        let app_handle = app_handle_clone;
        let peer_id = peer_id_clone;

        let app_handle_read = app_handle.clone();
        let peer_id_read = peer_id.clone();
        
        let read_task = tokio::spawn(async move {
            let mut line = String::new();
            while let Ok(n) = reader.read_line(&mut line).await {
                if n == 0 {
                    break;
                }
                let raw = line.trim();
                let msg = if raw.starts_with("FILE|") {
                    let parts: Vec<&str> = raw.splitn(3, '|').collect();
                    if parts.len() >= 3 {
                        let file_name = parts[1].to_string();
                        println!("[INFO] FILE RECEIVED: {} From {} to Me", file_name, peer_id);
                        let file_path = save_received_file(&file_name, parts[2]);
                        BluetoothMessage {
                            id: format!("msg-{}", current_epoch_ms()),
                            sender: peer_id_read.clone(),
                            content: format!("Received file: {}", file_name),
                            is_file: true,
                            file_name: Some(file_name),
                            base64_data: None,
                            file_path,
                            sent_at: current_epoch_ms(),
                        }
                    } else {
                        BluetoothMessage {
                            id: format!("msg-{}", current_epoch_ms()),
                            sender: peer_id_read.clone(),
                            content: "Error: Malformed file payload received".to_string(),
                            is_file: false,
                            file_name: None,
                            base64_data: None,
                            file_path: None,
                            sent_at: current_epoch_ms(),
                        }
                    }
                } else {
                    println!("[INFO] MESSAGE RECEIVED: From {} to Me", peer_id_read);
                    BluetoothMessage {
                        id: format!("msg-{}", current_epoch_ms()),
                        sender: peer_id_read.clone(),
                        content: raw.to_string(),
                        is_file: false,
                        file_name: None,
                        base64_data: None,
                        file_path: None,
                        sent_at: current_epoch_ms(),
                    }
                };
                
                // Save to SQLite database
                let _ = crate::core::database::insert_message_to_db(&peer_id_read, &msg);
                
                {
                    let mut messages = state.bluetooth.messages.lock().unwrap();
                    messages.push(msg.clone());
                }
                let _ = app_handle_read.emit("bluetooth-message", msg.clone());
                trigger_notification(&app_handle_read, &peer_id_read, &msg.content, msg.is_file);
                line.clear();
            }
        });

        let mut writer = writer;
        let write_task = tokio::spawn(async move {
            while let Some(msg) = write_rx.recv().await {
                if writer.write_all(msg.as_bytes()).await.is_err() {
                    break;
                }
                let _ = writer.flush().await;
            }
        });

        tokio::select! {
            _ = read_task => (),
            _ = write_task => ()
        };

        {
            let mut state_id = state.bluetooth.connected_device_id.lock().unwrap();
            *state_id = None;
            let mut state_addr = state.bluetooth.connected_device_address.lock().unwrap();
            *state_addr = None;
            let mut state_tx = state.bluetooth.tx.lock().unwrap();
            *state_tx = None;
        }
        let _ = app_handle.emit("bluetooth-status", serde_json::json!({ "connected": false, "deviceId": null, "deviceAddress": null }));
    });

    Ok(peer_id)
}

pub fn trigger_notification(app_handle: &AppHandle, sender: &str, content: &str, is_file: bool) {
    let summary = if is_file {
        format!("File from {}", sender)
    } else {
        format!("New message from {}", sender)
    };

    let body = content.to_string();

    let mut notification = notify_rust::Notification::new();
    notification
        .summary(&summary)
        .body(&body)
        .icon("dialog-information")
        .action("default", "Open Chat");

    match notification.show() {
        Ok(handle) => {
            let app_handle_clone = app_handle.clone();
            let sender_clone = sender.to_string();
            tokio::task::spawn_blocking(move || {
                handle.wait_for_action(move |action| {
                    if action == "default" {
                        if let Some(window) = app_handle_clone.get_webview_window("main") {
                            let _ = window.show();
                            let _ = window.set_focus();
                        }
                        let _ = app_handle_clone.emit("open-chat", sender_clone);
                    }
                });
            });
        }
        Err(e) => {
            eprintln!("[ERROR] Failed to show notification: {:?}", e);
        }
    }
}
