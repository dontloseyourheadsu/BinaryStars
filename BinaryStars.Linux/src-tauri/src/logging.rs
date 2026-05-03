use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::PathBuf;
use std::os::unix::fs::PermissionsExt;

const LOG_ROOT_DIR: &str = "/tmp/binarystarslinux/logs";
const LOG_FILE_NAME: &str = "binarystarslinux.log";
const LOG_MAX_READ_LINES: usize = 1500;

pub fn log_file_path() -> Result<PathBuf, String> {
    let dir = PathBuf::from(LOG_ROOT_DIR);
    if !dir.exists() {
        fs::create_dir_all(&dir).map_err(|e| format!("failed to create log directory: {}", e))?;
    }
    fs::set_permissions(&dir, fs::Permissions::from_mode(0o700))
        .map_err(|e| format!("failed to secure log directory permissions: {}", e))?;
    let file_path = dir.join(LOG_FILE_NAME);
    if !file_path.exists() {
        OpenOptions::new().create(true).append(true).open(&file_path)
            .map_err(|e| format!("failed to create log file: {}", e))?;
    }
    fs::set_permissions(&file_path, fs::Permissions::from_mode(0o600))
        .map_err(|e| format!("failed to secure log file permissions: {}", e))?;
    Ok(file_path)
}

pub fn current_epoch_ms() -> u128 {
    std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_millis()).unwrap_or(0)
}

pub fn append_log_line(level: &str, source: &str, message: &str, details: Option<&str>) -> Result<(), String> {
    let path = log_file_path()?;
    let mut file = OpenOptions::new().create(true).append(true).open(&path)
        .map_err(|e| format!("failed to open log file: {}", e))?;
    let mut line = format!("ts={} level={} source={} msg={}", current_epoch_ms(), level, source, message.replace('\n', " "));
    if let Some(extra) = details {
        if !extra.trim().is_empty() {
            line.push_str(" details=");
            line.push_str(&extra.replace('\n', " "));
        }
    }
    line.push('\n');
    file.write_all(line.as_bytes()).map_err(|e| format!("failed to append log line: {}", e))?;
    Ok(())
}

#[tauri::command]
pub fn append_ui_log(level: String, category: String, message: String, metadata_json: Option<String>) -> Result<(), String> {
    append_log_line(level.trim(), &format!("ui:{}", category.trim()), message.trim(), metadata_json.as_deref())
}

#[tauri::command]
pub fn get_log_file_path() -> Result<String, String> { Ok(log_file_path()?.to_string_lossy().to_string()) }

#[tauri::command]
pub fn read_recent_logs(max_lines: Option<usize>) -> Result<Vec<String>, String> {
    let path = log_file_path()?;
    let content = fs::read_to_string(path).map_err(|e| format!("failed to read log file: {}", e))?;
    let limit = max_lines.unwrap_or(300).min(LOG_MAX_READ_LINES);
    Ok(content.lines().rev().take(limit).map(|l| l.to_string()).collect::<Vec<_>>().into_iter().rev().collect())
}
