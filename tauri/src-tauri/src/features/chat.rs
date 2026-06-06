use tauri::{AppHandle, State, Emitter};
use base64::{Engine as _, engine::general_purpose};
use std::fs;
use crate::core::types::{AppState, BluetoothMessage};
use crate::core::bluetooth::current_epoch_ms;
use crate::core::database::{insert_message_to_db, update_message_file_path, query_messages_paged};

#[tauri::command]
pub async fn send_bluetooth_message(
    app_handle: AppHandle,
    state: State<'_, AppState>,
    content: String
) -> Result<(), String> {
    let clean = content.replace('\n', " ");
    let pwd_opt = state.bluetooth.password.lock().unwrap().clone();
    let payload = if let Some(pwd) = pwd_opt {
        if !pwd.is_empty() {
            let encrypted = crate::core::crypto::encrypt(clean.as_bytes(), &pwd)?;
            format!("ENC|{}\n", encrypted)
        } else {
            format!("{}\n", clean)
        }
    } else {
        format!("{}\n", clean)
    };

    let mut sent = false;

    // Send to single client if connected as a client
    {
        let tx = state.bluetooth.tx.lock().unwrap();
        if let Some(tx) = &*tx {
            tx.send(payload.clone()).map_err(|e| e.to_string())?;
            sent = true;
        }
    }

    // Broadcast to all clients if hosting
    let is_hosting = state.bluetooth.session.lock().unwrap().is_some();
    if is_hosting {
        let clients = state.bluetooth.clients.lock().unwrap();
        for client in clients.values() {
            let _ = client.tx.send(payload.clone());
        }
        sent = true;
    }

    if !sent {
        return Err("No active Bluetooth connection".to_string());
    }

    let peer_id = if is_hosting {
        "Group Chat Session".to_string()
    } else {
        state.bluetooth.connected_device_id.lock().unwrap().clone().unwrap_or_else(|| "Unknown".to_string())
    };

    println!("[INFO] MESSAGE SENT: From Me to peer {}", peer_id);
    
    let msg = BluetoothMessage {
        id: format!("msg-{}", current_epoch_ms()),
        sender: "Me".to_string(),
        content,
        is_file: false,
        file_name: None,
        base64_data: None,
        file_path: None,
        sent_at: current_epoch_ms(),
    };

    // Save to SQLite
    let _ = insert_message_to_db(&peer_id, &msg);
    
    // Notify frontend
    let _ = app_handle.emit("bluetooth-message", msg.clone());
    
    let mut m = state.bluetooth.messages.lock().unwrap();
    m.push(msg);
    Ok(())
}

#[tauri::command]
pub async fn send_bluetooth_file(
    app_handle: AppHandle,
    state: State<'_, AppState>,
    name: String,
    base64_data: String
) -> Result<(), String> {
    let pwd_opt = state.bluetooth.password.lock().unwrap().clone();
    let payload = if let Some(pwd) = pwd_opt {
        if !pwd.is_empty() {
            let encrypted_base64 = crate::core::crypto::encrypt(base64_data.as_bytes(), &pwd)?;
            format!("ENC_FILE|{}|{}\n", name, encrypted_base64)
        } else {
            format!("FILE|{}|{}\n", name, base64_data)
        }
    } else {
        format!("FILE|{}|{}\n", name, base64_data)
    };

    let mut sent = false;

    // Send to single client if connected as a client
    {
        let tx = state.bluetooth.tx.lock().unwrap();
        if let Some(tx) = &*tx {
            tx.send(payload.clone()).map_err(|e| e.to_string())?;
            sent = true;
        }
    }

    // Broadcast to all clients if hosting
    let is_hosting = state.bluetooth.session.lock().unwrap().is_some();
    if is_hosting {
        let clients = state.bluetooth.clients.lock().unwrap();
        for client in clients.values() {
            let _ = client.tx.send(payload.clone());
        }
        sent = true;
    }

    if !sent {
        return Err("No active Bluetooth connection".to_string());
    }

    let peer_id = if is_hosting {
        "Group Chat Session".to_string()
    } else {
        state.bluetooth.connected_device_id.lock().unwrap().clone().unwrap_or_else(|| "Unknown".to_string())
    };

    println!("[INFO] FILE SENT: {} From Me to peer {}", name, peer_id);
    
    // Save the sent file locally
    let decoded = general_purpose::STANDARD.decode(&base64_data).map_err(|e| e.to_string())?;
    let save_dir = dirs::data_dir()
        .unwrap_or_else(|| std::env::current_dir().unwrap())
        .join("BinaryStars")
        .join("transfers")
        .join("sent");
    let _ = fs::create_dir_all(&save_dir);
    let local_path = save_dir.join(format!("{}_{}", current_epoch_ms(), name));
    let _ = fs::write(&local_path, decoded);
    let local_path_str = local_path.to_string_lossy().to_string();

    let msg = BluetoothMessage {
        id: format!("msg-{}", current_epoch_ms()),
        sender: "Me".to_string(),
        content: format!("Sent file: {}", name),
        is_file: true,
        file_name: Some(name.clone()),
        base64_data: None,
        file_path: Some(local_path_str),
        sent_at: current_epoch_ms(),
    };

    // Save to SQLite
    let _ = insert_message_to_db(&peer_id, &msg);
    
    // Notify frontend
    let _ = app_handle.emit("bluetooth-message", msg.clone());
    
    let mut m = state.bluetooth.messages.lock().unwrap();
    m.push(msg);
    Ok(())
}

#[tauri::command]
pub async fn download_bluetooth_file(msg_id: String) -> Result<String, String> {
    let path_opt = crate::core::database::get_message_file_path(&msg_id)?;
    if let Some(src_path) = path_opt {
        if src_path.is_empty() {
            return Err("File path is empty in database".to_string());
        }
        let src_file = std::path::Path::new(&src_path);
        if !src_file.exists() {
            return Err("File no longer exists on disk".to_string());
        }
        let file_name = src_file.file_name().ok_or("Invalid file name")?;
        let file_name_str = file_name.to_string_lossy().to_string();
        
        // Remove timestamp prefix from filename to clean it up for the Download directory
        let clean_name = if let Some(idx) = file_name_str.find('_') {
            &file_name_str[idx + 1..]
        } else {
            &file_name_str
        };
        
        let dest_dir = dirs::download_dir().ok_or("Cannot locate system Downloads folder")?;
        let dest_path = dest_dir.join(clean_name);
        
        std::fs::copy(&src_path, &dest_path).map_err(|e| e.to_string())?;
        
        let dest_path_str = dest_path.to_string_lossy().to_string();
        // Update database file path to point to the user's Downloads folder
        let _ = update_message_file_path(&msg_id, &dest_path_str);
        
        Ok(dest_path_str)
    } else {
        Err("Message file not found in history".to_string())
    }
}

#[tauri::command]
pub async fn get_messages_paged(peer_id: String, limit: i64, offset: i64) -> Result<Vec<BluetoothMessage>, String> {
    query_messages_paged(&peer_id, limit, offset)
}

#[tauri::command]
pub async fn save_file_to_custom_path(msg_id: String) -> Result<String, String> {
    let path_opt = crate::core::database::get_message_file_path(&msg_id)?;
    if let Some(src_path) = path_opt {
        if src_path.is_empty() {
            return Err("File path is empty in database".to_string());
        }
        let src_file = std::path::Path::new(&src_path);
        if !src_file.exists() {
            return Err("File no longer exists on disk".to_string());
        }
        let file_name = src_file.file_name().ok_or("Invalid file name")?;
        let file_name_str = file_name.to_string_lossy().to_string();
        
        let clean_name = if let Some(idx) = file_name_str.find('_') {
            &file_name_str[idx + 1..]
        } else {
            &file_name_str
        };
        
        let clean_name_clone = clean_name.to_string();
        let dialog_result = tokio::task::spawn_blocking(move || {
            rfd::FileDialog::new()
                .set_file_name(&clean_name_clone)
                .set_title("Save File As")
                .save_file()
        }).await.map_err(|e| e.to_string())?;

        if let Some(dest_path) = dialog_result {
            std::fs::copy(&src_path, &dest_path).map_err(|e| e.to_string())?;
            let dest_path_str = dest_path.to_string_lossy().to_string();
            let _ = update_message_file_path(&msg_id, &dest_path_str);
            Ok(dest_path_str)
        } else {
            Ok("".to_string())
        }
    } else {
        Err("Message file not found in history".to_string())
    }
}

#[tauri::command]
pub async fn get_recent_chats() -> Result<Vec<crate::core::types::RecentChat>, String> {
    crate::core::database::query_recent_chats()
}

