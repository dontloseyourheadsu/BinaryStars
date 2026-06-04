use rusqlite::{params, Connection};
use std::fs;
use crate::core::types::BluetoothMessage;

pub fn get_db_connection() -> Result<Connection, String> {
    let db_dir = dirs::data_dir()
        .unwrap_or_else(|| std::env::current_dir().unwrap())
        .join("BinaryStars");
        
    if !db_dir.exists() {
        let _ = fs::create_dir_all(&db_dir);
    }
    
    let db_path = db_dir.join("history.db");
    let conn = Connection::open(&db_path).map_err(|e| e.to_string())?;
    
    // Initialize schema
    conn.execute(
        "CREATE TABLE IF NOT EXISTS messages (
            id TEXT PRIMARY KEY,
            peer_id TEXT NOT NULL,
            sender TEXT NOT NULL,
            content TEXT NOT NULL,
            is_file INTEGER NOT NULL,
            file_name TEXT,
            file_path TEXT,
            sent_at INTEGER NOT NULL
        )",
        [],
    ).map_err(|e| e.to_string())?;
    
    Ok(conn)
}

pub fn insert_message_to_db(peer_id: &str, msg: &BluetoothMessage) -> Result<(), String> {
    let conn = get_db_connection()?;
    conn.execute(
        "INSERT INTO messages (id, peer_id, sender, content, is_file, file_name, file_path, sent_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
         ON CONFLICT(id) DO NOTHING",
        params![
            msg.id,
            peer_id,
            msg.sender,
            msg.content,
            if msg.is_file { 1 } else { 0 },
            msg.file_name,
            msg.file_path,
            msg.sent_at as i64
        ],
    ).map_err(|e| e.to_string())?;
    Ok(())
}

pub fn update_message_file_path(msg_id: &str, path: &str) -> Result<(), String> {
    let conn = get_db_connection()?;
    conn.execute(
        "UPDATE messages SET file_path = ?1 WHERE id = ?2",
        params![path, msg_id],
    ).map_err(|e| e.to_string())?;
    Ok(())
}

pub fn get_message_file_path(msg_id: &str) -> Result<Option<String>, String> {
    let conn = get_db_connection()?;
    let mut stmt = conn.prepare("SELECT file_path FROM messages WHERE id = ?1").map_err(|e| e.to_string())?;
    let mut rows = stmt.query(params![msg_id]).map_err(|e| e.to_string())?;
    if let Some(row) = rows.next().map_err(|e| e.to_string())? {
        let path: Option<String> = row.get(0).map_err(|e| e.to_string())?;
        Ok(path)
    } else {
        Ok(None)
    }
}

pub fn query_messages_paged(peer_id: &str, limit: i64, offset: i64) -> Result<Vec<BluetoothMessage>, String> {
    let conn = get_db_connection()?;
    let mut stmt = conn.prepare(
        "SELECT id, sender, content, is_file, file_name, file_path, sent_at 
         FROM messages 
         WHERE peer_id = ?1 
         ORDER BY sent_at DESC 
         LIMIT ?2 OFFSET ?3"
    ).map_err(|e| e.to_string())?;
    
    let rows = stmt.query_map(params![peer_id, limit, offset], |row| {
        let is_file_int: i32 = row.get(3)?;
        let sent_at_i64: i64 = row.get(6)?;
        Ok(BluetoothMessage {
            id: row.get(0)?,
            sender: row.get(1)?,
            content: row.get(2)?,
            is_file: is_file_int == 1,
            file_name: row.get(4)?,
            file_path: row.get(5)?,
            base64_data: None,
            sent_at: sent_at_i64 as u64,
        })
    }).map_err(|e| e.to_string())?;
    
    let mut list = Vec::new();
    for r in rows {
        if let Ok(msg) = r {
            list.push(msg);
        }
    }
    // Reverse because we queried DESC for limits, but want chronological ASC in the UI
    list.reverse();
    Ok(list)
}

pub fn query_recent_chats() -> Result<Vec<crate::core::types::RecentChat>, String> {
    let conn = get_db_connection()?;
    let mut stmt = conn.prepare(
        "SELECT peer_id, content, is_file, file_name, MAX(sent_at) as last_msg_at
         FROM messages
         GROUP BY peer_id
         ORDER BY last_msg_at DESC"
    ).map_err(|e| e.to_string())?;

    let rows = stmt.query_map([], |row| {
        let peer_id: String = row.get(0)?;
        let content: String = row.get(1)?;
        let is_file_int: i32 = row.get(2)?;
        let file_name: Option<String> = row.get(3)?;
        let last_msg_at: i64 = row.get(4)?;

        let last_message = if is_file_int == 1 {
            format!("File: {}", file_name.unwrap_or_else(|| "unnamed".to_string()))
        } else {
            content
        };

        Ok(crate::core::types::RecentChat {
            peer_id,
            last_message,
            last_msg_at: last_msg_at as u64,
        })
    }).map_err(|e| e.to_string())?;

    let mut list = Vec::new();
    for r in rows {
        if let Ok(chat) = r {
            list.push(chat);
        }
    }
    Ok(list)
}

