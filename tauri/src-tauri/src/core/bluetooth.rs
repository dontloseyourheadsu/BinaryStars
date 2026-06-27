use tauri::{AppHandle, State, Emitter, Manager};
use bluer::{rfcomm::{Profile, Role}, Session};
use futures::StreamExt;
use tokio::io::{AsyncWriteExt, AsyncBufReadExt, BufReader as TokioBufReader};
use tokio::sync::mpsc;
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use std::fs;
use crate::core::types::{BluetoothMessage, AppState};
use base64::{Engine as _, engine::general_purpose};

pub fn current_epoch_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or(Duration::from_secs(0))
        .as_millis() as u64
}

fn map_key_name(name: &str) -> Option<enigo::Key> {
    match name {
        "ctrl" => Some(enigo::Key::Control),
        "alt" => Some(enigo::Key::Alt),
        "shift" => Some(enigo::Key::Shift),
        "meta" => Some(enigo::Key::Meta),
        "backspace" => Some(enigo::Key::Backspace),
        "enter" => Some(enigo::Key::Return),
        "tab" => Some(enigo::Key::Tab),
        "escape" => Some(enigo::Key::Escape),
        "space" => Some(enigo::Key::Space),
        "home" => Some(enigo::Key::Home),
        "end" => Some(enigo::Key::End),
        "pgup" => Some(enigo::Key::PageUp),
        "pgdn" => Some(enigo::Key::PageDown),
        "ins" => Some(enigo::Key::Insert),
        "del" => Some(enigo::Key::Delete),
        "left" => Some(enigo::Key::LeftArrow),
        "right" => Some(enigo::Key::RightArrow),
        "up" => Some(enigo::Key::UpArrow),
        "down" => Some(enigo::Key::DownArrow),
        _ => None,
    }
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

pub fn broadcast_to_clients(state: &AppState, sender_id: &str, payload: String) {
    let clients = state.bluetooth.clients.lock().unwrap();
    for (cid, client) in clients.iter() {
        if cid != sender_id {
            let _ = client.tx.send(payload.clone());
        }
    }
}

pub fn send_raw_bluetooth_message(state: &AppState, payload: String) -> Result<(), String> {
    let mut sent = false;
    {
        let tx = state.bluetooth.tx.lock().unwrap();
        if let Some(tx) = &*tx {
            let _ = tx.send(payload.clone());
            sent = true;
        }
    }
    {
        let clients = state.bluetooth.clients.lock().unwrap();
        for client in clients.values() {
            let _ = client.tx.send(payload.clone());
            sent = true;
        }
    }
    if sent {
        Ok(())
    } else {
        Err("No active Bluetooth connection".to_string())
    }
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

                tokio::spawn(async move {
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
                        return;
                    }

                    if !identified {
                        let mut writer = writer;
                        let _ = writer.write_all(b"ERROR|Identity verification failed\n").await;
                        let _ = writer.flush().await;
                        eprintln!("[ERROR] CONNECTION FAILED (SERVER): Handshake validation failed from {}", peer_address);
                        return;
                    }

                    let _ = writer.write_all(format!("IDENTIFIED|{}\n", my_id).as_bytes()).await;
                    let _ = writer.flush().await;
                    println!("[INFO] CONNECTION SUCCESSFUL (SERVER): Connected to peer {} ({})", peer_id, peer_address);

                    {
                        let mut clients = state.bluetooth.clients.lock().unwrap();
                        clients.insert(peer_id.clone(), crate::core::types::ConnectedClient {
                            peer_id: peer_id.clone(),
                            peer_address: peer_address.clone(),
                            tx: tx.clone(),
                        });
                    }

                    {
                        let mut state_id = state.bluetooth.connected_device_id.lock().unwrap();
                        *state_id = Some("Group Chat Session".to_string());
                        let mut state_addr = state.bluetooth.connected_device_address.lock().unwrap();
                        *state_addr = Some(peer_address.clone());
                    }

                    let app_handle_read = app_handle.clone();
                    let peer_id_read = peer_id.clone();

                    let read_task = tokio::spawn(async move {
                        use enigo::{Enigo, MouseControllable, MouseButton, KeyboardControllable, Key};
                        let mut enigo = Enigo::new();
                        let mut line = String::new();
                        while let Ok(n) = reader.read_line(&mut line).await {
                            if n == 0 {
                                break;
                            }
                            let raw = line.trim();
                            if raw.starts_with("MOUSE|") {
                                let parts: Vec<&str> = raw.split('|').collect();
                                if parts.len() >= 3 {
                                    let action = parts[1];
                                    match action {
                                        "move" => {
                                            if parts.len() >= 4 {
                                                let dx: i32 = parts[2].parse().unwrap_or(0);
                                                let dy: i32 = parts[3].parse().unwrap_or(0);
                                                enigo.mouse_move_relative(dx, dy);
                                            }
                                        }
                                        "click" => {
                                            let btn = parts[2];
                                            if btn == "left" {
                                                enigo.mouse_click(MouseButton::Left);
                                            } else if btn == "right" {
                                                enigo.mouse_click(MouseButton::Right);
                                            }
                                        }
                                        "down" => {
                                            let btn = parts[2];
                                            if btn == "left" {
                                                enigo.mouse_down(MouseButton::Left);
                                            } else if btn == "right" {
                                                enigo.mouse_down(MouseButton::Right);
                                            }
                                        }
                                        "up" => {
                                            let btn = parts[2];
                                            if btn == "left" {
                                                enigo.mouse_up(MouseButton::Left);
                                            } else if btn == "right" {
                                                enigo.mouse_up(MouseButton::Right);
                                            }
                                        }
                                        _ => {}
                                    }
                                }
                                line.clear();
                                continue;
                            } else if raw.starts_with("KEY|") {
                                let parts: Vec<&str> = raw.split('|').collect();
                                if parts.len() >= 3 {
                                    let action = parts[1];
                                    let val = parts[2];
                                    match action {
                                        "click" => {
                                            if let Some(key) = map_key_name(val) {
                                                enigo.key_click(key);
                                            }
                                        }
                                        "down" => {
                                            if let Some(key) = map_key_name(val) {
                                                enigo.key_down(key);
                                            }
                                        }
                                        "up" => {
                                            if let Some(key) = map_key_name(val) {
                                                enigo.key_up(key);
                                            }
                                        }
                                        "char" => {
                                            enigo.key_sequence(val);
                                        }
                                        _ => {}
                                    }
                                }
                                line.clear();
                                continue;
                            } else if raw.starts_with("TABLET|") {
                                let parts: Vec<&str> = raw.split('|').collect();
                                if parts.len() >= 4 {
                                    let action = parts[1];
                                    let x: f64 = parts[2].parse().unwrap_or(0.0);
                                    let y: f64 = parts[3].parse().unwrap_or(0.0);
                                    let (mx, my, mw, mh) = {
                                        let sx = state.bluetooth.selected_monitor_x.lock().unwrap();
                                        let sy = state.bluetooth.selected_monitor_y.lock().unwrap();
                                        let sw = state.bluetooth.selected_monitor_width.lock().unwrap();
                                        let sh = state.bluetooth.selected_monitor_height.lock().unwrap();
                                        (*sx, *sy, *sw, *sh)
                                    };
                                    let target_x = mx + (x * mw as f64) as i32;
                                    let target_y = my + (y * mh as f64) as i32;
                                    
                                    enigo.mouse_move_to(target_x, target_y);
                                    match action {
                                        "down" => {
                                            enigo.mouse_down(MouseButton::Left);
                                        }
                                        "up" => {
                                            enigo.mouse_up(MouseButton::Left);
                                        }
                                        _ => {}
                                    }
                                }
                                line.clear();
                                continue;
                            } else if raw == "TABLET_RATIO_REQ" {
                                let (mw, mh) = {
                                    let sw = state.bluetooth.selected_monitor_width.lock().unwrap();
                                    let sh = state.bluetooth.selected_monitor_height.lock().unwrap();
                                    (*sw, *sh)
                                };
                                let ratio_msg = format!("TABLET_RATIO|{}|{}\n", mw, mh);
                                let _ = send_raw_bluetooth_message(state, ratio_msg);
                                line.clear();
                                continue;
                            }

                            let msg = if raw.starts_with("ENC_FILE|") {
                                let parts: Vec<&str> = raw.splitn(3, '|').collect();
                                if parts.len() >= 3 {
                                    let file_name = parts[1].to_string();
                                    let encrypted_data = parts[2];
                                    
                                    let broadcast_payload = format!("GROUP_ENC_FILE|{}|{}|{}\n", peer_id_read, file_name, encrypted_data);
                                    broadcast_to_clients(state, &peer_id_read, broadcast_payload);

                                    let pwd_opt = state.bluetooth.password.lock().unwrap().clone();
                                    let decrypted_res = if let Some(pwd) = pwd_opt {
                                        crate::core::crypto::decrypt(encrypted_data, &pwd)
                                    } else {
                                        Err("No decryption password available".to_string())
                                    };
                                    
                                    match decrypted_res {
                                        Ok(decrypted_bytes) => {
                                            let decrypted_base64 = String::from_utf8(decrypted_bytes).unwrap_or_default();
                                            let file_path = save_received_file(&file_name, &decrypted_base64);
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
                                        }
                                        Err(e) => {
                                            BluetoothMessage {
                                                id: format!("msg-{}", current_epoch_ms()),
                                                sender: peer_id_read.clone(),
                                                content: format!("Error decrypting file: {}", e),
                                                is_file: false,
                                                file_name: None,
                                                base64_data: None,
                                                file_path: None,
                                                sent_at: current_epoch_ms(),
                                            }
                                        }
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
                            } else if raw.starts_with("FILE|") {
                                let parts: Vec<&str> = raw.splitn(3, '|').collect();
                                if parts.len() >= 3 {
                                    let file_name = parts[1].to_string();
                                    let base64_data = parts[2];

                                    let broadcast_payload = format!("GROUP_FILE|{}|{}|{}\n", peer_id_read, file_name, base64_data);
                                    broadcast_to_clients(state, &peer_id_read, broadcast_payload);

                                    let file_path = save_received_file(&file_name, base64_data);
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
                            } else if raw.starts_with("ENC|") {
                                let encrypted_data = &raw[4..];

                                let broadcast_payload = format!("GROUP_ENC|{}|{}\n", peer_id_read, encrypted_data);
                                broadcast_to_clients(state, &peer_id_read, broadcast_payload);

                                let pwd_opt = state.bluetooth.password.lock().unwrap().clone();
                                let decrypted_res = if let Some(pwd) = pwd_opt {
                                    crate::core::crypto::decrypt(encrypted_data, &pwd)
                                } else {
                                    Err("No decryption password available".to_string())
                                };
                                
                                match decrypted_res {
                                    Ok(decrypted_bytes) => {
                                        let decrypted_text = String::from_utf8(decrypted_bytes).unwrap_or_default();
                                        BluetoothMessage {
                                            id: format!("msg-{}", current_epoch_ms()),
                                            sender: peer_id_read.clone(),
                                            content: decrypted_text,
                                            is_file: false,
                                            file_name: None,
                                            base64_data: None,
                                            file_path: None,
                                            sent_at: current_epoch_ms(),
                                        }
                                    }
                                    Err(e) => {
                                        BluetoothMessage {
                                            id: format!("msg-{}", current_epoch_ms()),
                                            sender: peer_id_read.clone(),
                                            content: format!("Error decrypting message: {}", e),
                                            is_file: false,
                                            file_name: None,
                                            base64_data: None,
                                            file_path: None,
                                            sent_at: current_epoch_ms(),
                                        }
                                    }
                                }
                            } else {
                                println!("[INFO] MESSAGE RECEIVED: From {} to Me", peer_id_read);

                                let broadcast_payload = format!("GROUP_MSG|{}|{}\n", peer_id_read, raw);
                                broadcast_to_clients(state, &peer_id_read, broadcast_payload);

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
                            
                            let _ = crate::core::database::insert_message_to_db("Group Chat Session", &msg);
                            
                            {
                                let mut messages = state.bluetooth.messages.lock().unwrap();
                                messages.push(msg.clone());
                            }
                            let _ = app_handle_read.emit("bluetooth-message", msg.clone());
                            trigger_notification(&app_handle_read, &msg.sender, &msg.content, msg.is_file);

                            // Check for incoming command
                            let content_clone = msg.content.clone();
                            let app_handle_clone = app_handle_read.clone();
                            tokio::spawn(async move {
                                crate::features::chat::check_and_handle_incoming_command(&app_handle_clone, state, &content_clone).await;
                            });

                            line.clear();
                        }
                        enigo.key_up(Key::Control);
                        enigo.key_up(Key::Alt);
                        enigo.key_up(Key::Shift);
                        enigo.key_up(Key::Meta);
                        enigo.mouse_up(MouseButton::Left);
                        enigo.mouse_up(MouseButton::Right);
                    });

                    let mut writer = writer;
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
                        let mut clients = state.bluetooth.clients.lock().unwrap();
                        clients.remove(&peer_id);
                    }
                });
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
    password: Option<String>,
) -> Result<String, String> {
    let bluetooth = &state.bluetooth;
    
    {
        let mut pwd = bluetooth.password.lock().unwrap();
        *pwd = password.clone();
    }

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
    
    let pwd_str = password.clone().unwrap_or_default();
    let identify_msg = if pwd_str.is_empty() {
        format!("IDENTIFY|{}\n", my_device_id)
    } else {
        format!("IDENTIFY|{}|{}\n", my_device_id, pwd_str)
    };
    let _ = writer.write_all(identify_msg.as_bytes()).await;
    let _ = writer.flush().await;

    let mut reader = reader;
    let mut line = String::new();
    let res = tokio::time::timeout(Duration::from_secs(5), reader.read_line(&mut line)).await;
    let mut err_msg = "Handshake failed".to_string();
    if let Ok(Ok(_)) = res {
        let raw = line.trim();
        if raw.starts_with("IDENTIFIED|") {
            let parts: Vec<&str> = raw.splitn(2, '|').collect();
            if parts.len() >= 2 {
                identified = true;
                peer_id = parts[1].to_string();
            }
        } else if raw.starts_with("ERROR|") {
            err_msg = raw[6..].to_string();
        }
    }

    if !identified {
        eprintln!("[ERROR] CONNECTION FAILED (CLIENT): Handshake verification failed from {}: {}", device_address, err_msg);
        return Err(err_msg);
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
            use enigo::{Enigo, MouseControllable, MouseButton, KeyboardControllable, Key};
            let mut enigo = Enigo::new();
            let mut line = String::new();
            while let Ok(n) = reader.read_line(&mut line).await {
                if n == 0 {
                    break;
                }
                let raw = line.trim();
                if raw.starts_with("MOUSE|") {
                    let parts: Vec<&str> = raw.split('|').collect();
                    if parts.len() >= 3 {
                        let action = parts[1];
                        match action {
                            "move" => {
                                if parts.len() >= 4 {
                                    let dx: i32 = parts[2].parse().unwrap_or(0);
                                    let dy: i32 = parts[3].parse().unwrap_or(0);
                                    enigo.mouse_move_relative(dx, dy);
                                }
                            }
                            "click" => {
                                let btn = parts[2];
                                if btn == "left" {
                                    enigo.mouse_click(MouseButton::Left);
                                } else if btn == "right" {
                                    enigo.mouse_click(MouseButton::Right);
                                }
                            }
                            "down" => {
                                let btn = parts[2];
                                if btn == "left" {
                                    enigo.mouse_down(MouseButton::Left);
                                } else if btn == "right" {
                                    enigo.mouse_down(MouseButton::Right);
                                }
                            }
                            "up" => {
                                let btn = parts[2];
                                if btn == "left" {
                                    enigo.mouse_up(MouseButton::Left);
                                } else if btn == "right" {
                                    enigo.mouse_up(MouseButton::Right);
                                }
                            }
                            _ => {}
                        }
                    }
                    line.clear();
                    continue;
                } else if raw.starts_with("KEY|") {
                    let parts: Vec<&str> = raw.split('|').collect();
                    if parts.len() >= 3 {
                        let action = parts[1];
                        let val = parts[2];
                        match action {
                            "click" => {
                                if let Some(key) = map_key_name(val) {
                                    enigo.key_click(key);
                                }
                            }
                            "down" => {
                                if let Some(key) = map_key_name(val) {
                                    enigo.key_down(key);
                                }
                            }
                            "up" => {
                                if let Some(key) = map_key_name(val) {
                                    enigo.key_up(key);
                                }
                            }
                            "char" => {
                                enigo.key_sequence(val);
                            }
                            _ => {}
                        }
                    }
                    line.clear();
                    continue;
                } else if raw.starts_with("TABLET|") {
                    let parts: Vec<&str> = raw.split('|').collect();
                    if parts.len() >= 4 {
                        let action = parts[1];
                        let x: f64 = parts[2].parse().unwrap_or(0.0);
                        let y: f64 = parts[3].parse().unwrap_or(0.0);
                        let (mx, my, mw, mh) = {
                            let sx = state.bluetooth.selected_monitor_x.lock().unwrap();
                            let sy = state.bluetooth.selected_monitor_y.lock().unwrap();
                            let sw = state.bluetooth.selected_monitor_width.lock().unwrap();
                            let sh = state.bluetooth.selected_monitor_height.lock().unwrap();
                            (*sx, *sy, *sw, *sh)
                        };
                        let target_x = mx + (x * mw as f64) as i32;
                        let target_y = my + (y * mh as f64) as i32;
                        
                        enigo.mouse_move_to(target_x, target_y);
                        match action {
                            "down" => {
                                enigo.mouse_down(MouseButton::Left);
                            }
                            "up" => {
                                enigo.mouse_up(MouseButton::Left);
                            }
                            _ => {}
                        }
                    }
                    line.clear();
                    continue;
                } else if raw == "TABLET_RATIO_REQ" {
                    let (mw, mh) = {
                        let sw = state.bluetooth.selected_monitor_width.lock().unwrap();
                        let sh = state.bluetooth.selected_monitor_height.lock().unwrap();
                        (*sw, *sh)
                    };
                    let ratio_msg = format!("TABLET_RATIO|{}|{}\n", mw, mh);
                    let _ = send_raw_bluetooth_message(state, ratio_msg);
                    line.clear();
                    continue;
                }

                let msg = if raw.starts_with("GROUP_MSG|") {
                    let parts: Vec<&str> = raw.splitn(3, '|').collect();
                    if parts.len() >= 3 {
                        let sender_id = parts[1].to_string();
                        let content = parts[2].to_string();
                        BluetoothMessage {
                            id: format!("msg-{}", current_epoch_ms()),
                            sender: sender_id,
                            content,
                            is_file: false,
                            file_name: None,
                            base64_data: None,
                            file_path: None,
                            sent_at: current_epoch_ms(),
                        }
                    } else {
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
                    }
                } else if raw.starts_with("GROUP_ENC|") {
                    let parts: Vec<&str> = raw.splitn(3, '|').collect();
                    if parts.len() >= 3 {
                        let sender_id = parts[1].to_string();
                        let encrypted_data = parts[2];
                        let pwd_opt = state.bluetooth.password.lock().unwrap().clone();
                        let decrypted_res = if let Some(pwd) = pwd_opt {
                            crate::core::crypto::decrypt(encrypted_data, &pwd)
                        } else {
                            Err("No decryption password available".to_string())
                        };
                        match decrypted_res {
                            Ok(decrypted_bytes) => {
                                let decrypted_text = String::from_utf8(decrypted_bytes).unwrap_or_default();
                                BluetoothMessage {
                                    id: format!("msg-{}", current_epoch_ms()),
                                    sender: sender_id,
                                    content: decrypted_text,
                                    is_file: false,
                                    file_name: None,
                                    base64_data: None,
                                    file_path: None,
                                    sent_at: current_epoch_ms(),
                                }
                            }
                            Err(e) => {
                                BluetoothMessage {
                                    id: format!("msg-{}", current_epoch_ms()),
                                    sender: sender_id,
                                    content: format!("Error decrypting message: {}", e),
                                    is_file: false,
                                    file_name: None,
                                    base64_data: None,
                                    file_path: None,
                                    sent_at: current_epoch_ms(),
                                }
                            }
                        }
                    } else {
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
                    }
                } else if raw.starts_with("GROUP_FILE|") {
                    let parts: Vec<&str> = raw.splitn(4, '|').collect();
                    if parts.len() >= 4 {
                        let sender_id = parts[1].to_string();
                        let file_name = parts[2].to_string();
                        let base64_data = parts[3];
                        let file_path = save_received_file(&file_name, base64_data);
                        BluetoothMessage {
                            id: format!("msg-{}", current_epoch_ms()),
                            sender: sender_id,
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
                            content: raw.to_string(),
                            is_file: false,
                            file_name: None,
                            base64_data: None,
                            file_path: None,
                            sent_at: current_epoch_ms(),
                        }
                    }
                } else if raw.starts_with("GROUP_ENC_FILE|") {
                    let parts: Vec<&str> = raw.splitn(4, '|').collect();
                    if parts.len() >= 4 {
                        let sender_id = parts[1].to_string();
                        let file_name = parts[2].to_string();
                        let encrypted_data = parts[3];
                        let pwd_opt = state.bluetooth.password.lock().unwrap().clone();
                        let decrypted_res = if let Some(pwd) = pwd_opt {
                            crate::core::crypto::decrypt(encrypted_data, &pwd)
                        } else {
                            Err("No decryption password available".to_string())
                        };
                        match decrypted_res {
                            Ok(decrypted_bytes) => {
                                let decrypted_base64 = String::from_utf8(decrypted_bytes).unwrap_or_default();
                                let file_path = save_received_file(&file_name, &decrypted_base64);
                                BluetoothMessage {
                                    id: format!("msg-{}", current_epoch_ms()),
                                    sender: sender_id,
                                    content: format!("Received file: {}", file_name),
                                    is_file: true,
                                    file_name: Some(file_name),
                                    base64_data: None,
                                    file_path,
                                    sent_at: current_epoch_ms(),
                                }
                            }
                            Err(e) => {
                                BluetoothMessage {
                                    id: format!("msg-{}", current_epoch_ms()),
                                    sender: sender_id,
                                    content: format!("Error decrypting file: {}", e),
                                    is_file: false,
                                    file_name: None,
                                    base64_data: None,
                                    file_path: None,
                                    sent_at: current_epoch_ms(),
                                }
                            }
                        }
                    } else {
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
                    }
                } else if raw.starts_with("ENC_FILE|") {
                    let parts: Vec<&str> = raw.splitn(3, '|').collect();
                    if parts.len() >= 3 {
                        let file_name = parts[1].to_string();
                        let encrypted_data = parts[2];
                        let pwd_opt = state.bluetooth.password.lock().unwrap().clone();
                        let decrypted_res = if let Some(pwd) = pwd_opt {
                            crate::core::crypto::decrypt(encrypted_data, &pwd)
                        } else {
                            Err("No decryption password available".to_string())
                        };
                        
                        match decrypted_res {
                            Ok(decrypted_bytes) => {
                                let decrypted_base64 = String::from_utf8(decrypted_bytes).unwrap_or_default();
                                println!("[INFO] FILE RECEIVED AND DECRYPTED: {} From {} to Me", file_name, peer_id_read);
                                let file_path = save_received_file(&file_name, &decrypted_base64);
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
                            }
                            Err(e) => {
                                BluetoothMessage {
                                    id: format!("msg-{}", current_epoch_ms()),
                                    sender: peer_id_read.clone(),
                                    content: format!("Error decrypting file: {}", e),
                                    is_file: false,
                                    file_name: None,
                                    base64_data: None,
                                    file_path: None,
                                    sent_at: current_epoch_ms(),
                                }
                            }
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
                } else if raw.starts_with("FILE|") {
                    let parts: Vec<&str> = raw.splitn(3, '|').collect();
                    if parts.len() >= 3 {
                        let file_name = parts[1].to_string();
                        println!("[INFO] FILE RECEIVED: {} From {} to Me", file_name, peer_id_read);
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
                } else if raw.starts_with("ENC|") {
                    let encrypted_data = &raw[4..];
                    let pwd_opt = state.bluetooth.password.lock().unwrap().clone();
                    let decrypted_res = if let Some(pwd) = pwd_opt {
                        crate::core::crypto::decrypt(encrypted_data, &pwd)
                    } else {
                        Err("No decryption password available".to_string())
                    };
                    
                    match decrypted_res {
                        Ok(decrypted_bytes) => {
                            let decrypted_text = String::from_utf8(decrypted_bytes).unwrap_or_default();
                            println!("[INFO] MESSAGE RECEIVED AND DECRYPTED: From {} to Me", peer_id_read);
                            BluetoothMessage {
                                id: format!("msg-{}", current_epoch_ms()),
                                sender: peer_id_read.clone(),
                                content: decrypted_text,
                                is_file: false,
                                  file_name: None,
                                base64_data: None,
                                file_path: None,
                                sent_at: current_epoch_ms(),
                            }
                        }
                        Err(e) => {
                            BluetoothMessage {
                                id: format!("msg-{}", current_epoch_ms()),
                                sender: peer_id_read.clone(),
                                content: format!("Error decrypting message: {}", e),
                                is_file: false,
                                file_name: None,
                                base64_data: None,
                                file_path: None,
                                sent_at: current_epoch_ms(),
                            }
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
                trigger_notification(&app_handle_read, &msg.sender, &msg.content, msg.is_file);

                // Check for incoming command
                let content_clone = msg.content.clone();
                let app_handle_clone = app_handle_read.clone();
                tokio::spawn(async move {
                    crate::features::chat::check_and_handle_incoming_command(&app_handle_clone, state, &content_clone).await;
                });

                line.clear();
            }
            enigo.key_up(Key::Control);
            enigo.key_up(Key::Alt);
            enigo.key_up(Key::Shift);
            enigo.key_up(Key::Meta);
            enigo.mouse_up(MouseButton::Left);
            enigo.mouse_up(MouseButton::Right);
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
