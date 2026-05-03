use std::process::Command;

#[tauri::command]
pub async fn is_wifi_connected() -> Result<bool, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let output = Command::new("nmcli").args(["-t", "-f", "TYPE,STATE", "device"]).output();
        match output {
            Ok(val) if val.status.success() => {
                let stdout = String::from_utf8_lossy(&val.stdout);
                Ok(stdout.lines().any(|line| {
                    let mut parts = line.split(':');
                    let dt = parts.next().unwrap_or_default().trim();
                    let st = parts.next().unwrap_or_default().trim();
                    dt.eq_ignore_ascii_case("wifi") && st.eq_ignore_ascii_case("connected")
                }))
            }
            _ => Ok(false),
        }
    }).await.map_err(|e| format!("spawn error: {}", e))?
}
