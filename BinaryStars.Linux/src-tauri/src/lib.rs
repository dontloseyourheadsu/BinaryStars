use serde::Serialize;
use std::collections::{BTreeMap, HashMap};
use std::convert::TryInto;
use std::io::{BufRead, BufReader, Write};
use std::net::IpAddr;
use std::net::TcpListener;
use std::process::Command;
use std::time::Duration;
use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use rand::{distributions::Alphanumeric, Rng};
use reqwest::blocking::Client;
use serde::Deserialize;
use sha2::{Digest, Sha256};
use url::Url;
use zbus::blocking::{fdo::PropertiesProxy, Connection, Proxy};
use zbus::names::InterfaceName;
use zbus::zvariant::{OwnedObjectPath, OwnedValue};

#[derive(Serialize)]
struct LaunchableAppItem {
    app_id: String,
    name: String,
}

#[derive(Serialize)]
struct RunningAppItem {
    pid: i32,
    name: String,
    command_line: String,
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            get_device_info,
            oauth_get_provider_token,
            get_bluetooth_connected_device_names,
            is_wifi_connected,
            get_native_location,
            perform_local_action,
            get_elevation_status,
            request_elevated_mode
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

#[derive(Serialize)]
pub struct NativeLocation {
    pub latitude: f64,
    pub longitude: f64,
    pub accuracy_meters: Option<f64>,
}

#[tauri::command]
async fn get_native_location() -> Result<NativeLocation, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let connection = Connection::system().map_err(|e| format!("dbus connection failed: {}", e))?;

        let manager = Proxy::new(
            &connection,
            "org.freedesktop.GeoClue2",
            "/org/freedesktop/GeoClue2/Manager",
            "org.freedesktop.GeoClue2.Manager",
        )
        .map_err(|e| format!("geoclue manager unavailable: {}", e))?;

        let client_path: OwnedObjectPath = manager
            .call("GetClient", &())
            .map_err(|e| format!("failed to create geoclue client: {}", e))?;

        let client_props = PropertiesProxy::new(&connection, "org.freedesktop.GeoClue2", client_path.as_str())
            .map_err(|e| format!("failed to create geoclue properties proxy: {}", e))?;

        let client_interface: InterfaceName<'static> = "org.freedesktop.GeoClue2.Client"
            .try_into()
            .map_err(|e| format!("invalid client interface name: {}", e))?;

        client_props
            .set(
                client_interface.clone(),
                "DesktopId",
                "com.tds.binarystars.linux".into(),
            )
            .map_err(|e| format!("failed to set geoclue DesktopId: {}", e))?;

        client_props
            .set(client_interface.clone(), "RequestedAccuracyLevel", 4u32.into())
            .map_err(|e| format!("failed to set geoclue accuracy: {}", e))?;

        let client_proxy = Proxy::new(
            &connection,
            "org.freedesktop.GeoClue2",
            client_path.as_str(),
            "org.freedesktop.GeoClue2.Client",
        )
        .map_err(|e| format!("failed to create geoclue client proxy: {}", e))?;

        client_proxy
            .call::<_, _, ()>("Start", &())
            .map_err(|e| format!("failed to start geoclue client: {}", e))?;

        let mut location_path = String::from("/");
        for _ in 0..20 {
            let location_value: OwnedValue = client_props
                .get(client_interface.clone(), "Location")
                .map_err(|e| format!("failed reading geoclue location path: {}", e))?;

            let next_path: OwnedObjectPath = <OwnedValue as TryInto<OwnedObjectPath>>::try_into(location_value)
                .map_err(|e| format!("unexpected geoclue location path type: {}", e))?;
            let next_path = next_path.to_string();

            if next_path != "/" {
                location_path = next_path;
                break;
            }

            std::thread::sleep(Duration::from_millis(500));
        }

        if location_path == "/" {
            let _ = client_proxy.call::<_, _, ()>("Stop", &());
            let _ = manager.call::<_, _, ()>("DeleteClient", &(client_path.clone()));
            return Err("native location permission denied or no location fix available".to_string());
        }

        let location_props = PropertiesProxy::new(&connection, "org.freedesktop.GeoClue2", location_path.as_str())
            .map_err(|e| format!("failed to create geoclue location proxy: {}", e))?;

        let location_interface: InterfaceName<'static> = "org.freedesktop.GeoClue2.Location"
            .try_into()
            .map_err(|e| format!("invalid location interface name: {}", e))?;

        let latitude: f64 = location_props
            .get(location_interface.clone(), "Latitude")
            .map_err(|e| format!("failed to read latitude: {}", e))?
            .try_into()
            .map_err(|e| format!("invalid latitude value: {}", e))?;

        let longitude: f64 = location_props
            .get(location_interface.clone(), "Longitude")
            .map_err(|e| format!("failed to read longitude: {}", e))?
            .try_into()
            .map_err(|e| format!("invalid longitude value: {}", e))?;

        let accuracy_meters = location_props
            .get(location_interface, "Accuracy")
            .ok()
            .and_then(|value| value.try_into().ok());

        let _ = client_proxy.call::<_, _, ()>("Stop", &());
        let _ = manager.call::<_, _, ()>("DeleteClient", &(client_path));

        Ok(NativeLocation {
            latitude,
            longitude,
            accuracy_meters,
        })
    })
    .await
    .map_err(|e| format!("spawn error: {}", e))?
}

#[tauri::command]
async fn get_bluetooth_connected_device_names() -> Result<Vec<String>, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let output = Command::new("bluetoothctl")
            .args(["devices", "Connected"])
            .output();

        let output = match output {
            Ok(value) => value,
            Err(_) => return Ok(Vec::new()),
        };

        if !output.status.success() {
            return Ok(Vec::new());
        }

        let stdout = String::from_utf8(output.stdout).unwrap_or_default();
        let names = stdout
            .lines()
            .filter_map(|line| {
                if !line.starts_with("Device ") {
                    return None;
                }
                let mut parts = line.split_whitespace();
                let _device = parts.next();
                let _mac = parts.next();
                let name = parts.collect::<Vec<&str>>().join(" ").trim().to_string();
                if name.is_empty() {
                    None
                } else {
                    Some(name)
                }
            })
            .collect::<Vec<String>>();

        Ok(names)
    })
    .await
    .map_err(|e| format!("spawn error: {}", e))?
}

#[tauri::command]
async fn is_wifi_connected() -> Result<bool, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let output = Command::new("nmcli")
            .args(["-t", "-f", "TYPE,STATE", "device"])
            .output();

        if let Ok(value) = output {
            if value.status.success() {
                let stdout = String::from_utf8(value.stdout).unwrap_or_default();
                let has_wifi = stdout.lines().any(|line| {
                    let mut parts = line.split(':');
                    let device_type = parts.next().unwrap_or_default().trim();
                    let state = parts.next().unwrap_or_default().trim();
                    device_type.eq_ignore_ascii_case("wifi") && state.eq_ignore_ascii_case("connected")
                });
                return Ok(has_wifi);
            }
        }

        let interfaces = if_addrs::get_if_addrs().map_err(|e| format!("if_addrs error: {}", e))?;
        let has_wireless = interfaces.into_iter().any(|iface| {
            let name = iface.name.to_lowercase();
            let looks_wireless = name.starts_with("wl") || name.contains("wifi") || name.contains("wlan");
            if !looks_wireless {
                return false;
            }

            match iface.ip() {
                IpAddr::V4(v4) => !v4.is_loopback(),
                IpAddr::V6(v6) => !v6.is_loopback(),
            }
        });

        Ok(has_wireless)
    })
    .await
    .map_err(|e| format!("spawn error: {}", e))?
}

fn try_command(program: &str, args: &[&str]) -> Result<(), String> {
    let output = Command::new(program).args(args).output();
    let output = match output {
        Ok(value) => value,
        Err(error) => {
            if error.kind() == std::io::ErrorKind::NotFound {
                return Err(format!("missing:{}", program));
            }
            return Err(format!("{} failed to start: {}", program, error));
        }
    };

    if output.status.success() {
        return Ok(());
    }

    let stderr = String::from_utf8(output.stderr).unwrap_or_default();
    let stdout = String::from_utf8(output.stdout).unwrap_or_default();
    let detail = if !stderr.trim().is_empty() {
        stderr.trim().to_string()
    } else {
        stdout.trim().to_string()
    };

    Err(format!("{} exited with status {}: {}", program, output.status, detail))
}

fn read_command_output(program: &str, args: &[&str]) -> Result<String, String> {
    let output = Command::new(program).args(args).output();
    let output = match output {
        Ok(value) => value,
        Err(error) => {
            if error.kind() == std::io::ErrorKind::NotFound {
                return Err(format!("missing:{}", program));
            }
            return Err(format!("{} failed to start: {}", program, error));
        }
    };

    if output.status.success() {
        return Ok(String::from_utf8(output.stdout).unwrap_or_default());
    }

    let stderr = String::from_utf8(output.stderr).unwrap_or_default();
    let stdout = String::from_utf8(output.stdout).unwrap_or_default();
    let detail = if !stderr.trim().is_empty() {
        stderr.trim().to_string()
    } else {
        stdout.trim().to_string()
    };

    Err(format!("{} exited with status {}: {}", program, output.status, detail))
}

fn block_screen_linux() -> Result<(), String> {
    let desktop = std::env::var("XDG_CURRENT_DESKTOP").unwrap_or_default().to_ascii_lowercase();
    let session = std::env::var("DESKTOP_SESSION").unwrap_or_default().to_ascii_lowercase();

    let mut errors: Vec<String> = Vec::new();

    let mut candidates: Vec<(&str, Vec<&str>)> = Vec::new();

    if desktop.contains("kde") || session.contains("kde") || session.contains("plasma") {
        candidates.push(("qdbus6", vec!["org.freedesktop.ScreenSaver", "/ScreenSaver", "Lock"]));
        candidates.push(("qdbus", vec!["org.freedesktop.ScreenSaver", "/ScreenSaver", "Lock"]));
    }

    if desktop.contains("gnome") || desktop.contains("unity") || desktop.contains("xfce") || desktop.contains("cinnamon") || session.contains("gnome") {
        candidates.push((
            "dbus-send",
            vec![
                "--session",
                "--dest=org.gnome.ScreenSaver",
                "--type=method_call",
                "/org/gnome/ScreenSaver",
                "org.gnome.ScreenSaver.Lock",
            ],
        ));
    }

    candidates.push((
        "dbus-send",
        vec![
            "--session",
            "--dest=org.freedesktop.ScreenSaver",
            "--type=method_call",
            "/ScreenSaver",
            "org.freedesktop.ScreenSaver.Lock",
        ],
    ));
    candidates.push(("xdg-screensaver", vec!["lock"]));
    candidates.push(("loginctl", vec!["lock-session"]));

    for (program, args) in candidates {
        match try_command(program, &args) {
            Ok(_) => return Ok(()),
            Err(error) => errors.push(error),
        }
    }

    Err(format!(
        "Unable to lock screen on this Linux desktop/session. The window manager/session API may be unavailable or denied this request. Tried GNOME/KDE/freedesktop/loginctl methods. Details: {}",
        errors.join(" | ")
    ))
}

fn power_action_linux(action_type: &str) -> Result<(), String> {
    let mut errors: Vec<String> = Vec::new();

    let dbus_member = if action_type == "shutdown" { "PowerOff" } else { "Reboot" };
    let systemctl_arg = if action_type == "shutdown" { "poweroff" } else { "reboot" };

    let dbus_method = format!("org.freedesktop.login1.Manager.{}", dbus_member);
    match try_command(
        "dbus-send",
        &[
            "--system",
            "--dest=org.freedesktop.login1",
            "--type=method_call",
            "/org/freedesktop/login1",
            dbus_method.as_str(),
            "boolean:true",
        ],
    ) {
        Ok(_) => return Ok(()),
        Err(error) => errors.push(error),
    }

    match try_command("systemctl", &[systemctl_arg]) {
        Ok(_) => return Ok(()),
        Err(error) => errors.push(error),
    }

    match try_command("pkexec", &["systemctl", systemctl_arg]) {
        Ok(_) => return Ok(()),
        Err(error) => errors.push(error),
    }

    Err(format!(
        "Unable to execute {}. Permission denied or unsupported policy/session. Details: {}",
        action_type,
        errors.join(" | ")
    ))
}

fn list_launchable_apps_json() -> Result<String, String> {
    let mut entries: Vec<LaunchableAppItem> = Vec::new();
    let mut seen = std::collections::BTreeSet::new();

    let mut dirs: Vec<std::path::PathBuf> = vec![
        std::path::PathBuf::from("/usr/share/applications"),
        std::path::PathBuf::from("/usr/local/share/applications"),
    ];

    if let Ok(home) = std::env::var("HOME") {
        dirs.push(std::path::PathBuf::from(home).join(".local/share/applications"));
    }

    for dir in dirs {
        let read = std::fs::read_dir(&dir);
        let read = match read {
            Ok(value) => value,
            Err(_) => continue,
        };

        for item in read.flatten() {
            let path = item.path();
            if path.extension().and_then(|ext| ext.to_str()) != Some("desktop") {
                continue;
            }

            let app_id = match path.file_name().and_then(|value| value.to_str()) {
                Some(value) => value.to_string(),
                None => continue,
            };

            if !seen.insert(app_id.clone()) {
                continue;
            }

            let content = match std::fs::read_to_string(&path) {
                Ok(value) => value,
                Err(_) => continue,
            };

            let mut name: Option<String> = None;
            let mut no_display = false;
            let mut hidden = false;

            for line in content.lines() {
                if let Some(value) = line.strip_prefix("Name=") {
                    if !value.trim().is_empty() {
                        name = Some(value.trim().to_string());
                    }
                }

                if let Some(value) = line.strip_prefix("NoDisplay=") {
                    no_display = value.trim().eq_ignore_ascii_case("true");
                }

                if let Some(value) = line.strip_prefix("Hidden=") {
                    hidden = value.trim().eq_ignore_ascii_case("true");
                }
            }

            if no_display || hidden {
                continue;
            }

            entries.push(LaunchableAppItem {
                app_id,
                name: name.unwrap_or_else(|| "Unknown".to_string()),
            });
        }
    }

    entries.sort_by(|left, right| left.name.to_lowercase().cmp(&right.name.to_lowercase()));
    serde_json::to_string(&entries).map_err(|e| format!("failed to serialize launchable apps: {}", e))
}

fn list_running_apps_json() -> Result<String, String> {
    let output = Command::new("ps")
        .args(["-eo", "pid=,comm=,args="])
        .output()
        .map_err(|e| format!("failed to run ps: {}", e))?;

    if !output.status.success() {
        return Err("failed to list running apps (ps returned non-zero)".to_string());
    }

    let stdout = String::from_utf8(output.stdout).unwrap_or_default();
    let mut items: Vec<RunningAppItem> = Vec::new();
    for line in stdout.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() {
            continue;
        }

        let mut pieces = trimmed.split_whitespace();
        let pid_raw = match pieces.next() {
            Some(value) => value,
            None => continue,
        };
        let pid: i32 = match pid_raw.parse() {
            Ok(value) => value,
            Err(_) => continue,
        };
        let name = pieces.next().unwrap_or_default().to_string();
        let command_line = pieces.collect::<Vec<&str>>().join(" ");

        if name.is_empty() {
            continue;
        }

        items.push(RunningAppItem {
            pid,
            name,
            command_line,
        });
    }

    serde_json::to_string(&items).map_err(|e| format!("failed to serialize running apps: {}", e))
}

fn open_app_linux(payload_json: Option<String>) -> Result<(), String> {
    let payload = payload_json.ok_or_else(|| "open_app requires payload".to_string())?;
    let value: serde_json::Value = serde_json::from_str(&payload)
        .map_err(|e| format!("invalid open_app payload: {}", e))?;

    let app_id = value
        .get("appId")
        .and_then(|v| v.as_str())
        .ok_or_else(|| "open_app payload missing appId".to_string())?;

    try_command("gtk-launch", &[app_id]).or_else(|_| {
        let desktop_file = app_id.to_string();
        try_command("gio", &["launch", desktop_file.as_str()])
    })
}

fn close_app_linux(payload_json: Option<String>) -> Result<(), String> {
    let payload = payload_json.ok_or_else(|| "close_app requires payload".to_string())?;
    let value: serde_json::Value = serde_json::from_str(&payload)
        .map_err(|e| format!("invalid close_app payload: {}", e))?;

    if let Some(pid_value) = value.get("pid").and_then(|v| v.as_i64()) {
        let pid_text = pid_value.to_string();
        return try_command("kill", &["-TERM", pid_text.as_str()]);
    }

    if let Some(name_value) = value.get("name").and_then(|v| v.as_str()) {
        return try_command("pkill", &["-f", name_value]);
    }

    Err("close_app payload must include pid or name".to_string())
}

fn list_clipboard_history_json() -> Result<String, String> {
    let mut entries: Vec<String> = Vec::new();

    if let Ok(raw_count) = read_command_output("copyq", &["count"]) {
        let count = raw_count.trim().parse::<usize>().unwrap_or(0);
        let max_items = count.min(20);
        for index in 0..max_items {
            let index_text = index.to_string();
            if let Ok(value) = read_command_output("copyq", &["read", index_text.as_str()]) {
                let normalized = value.trim().to_string();
                if !normalized.is_empty() && !entries.iter().any(|item| item == &normalized) {
                    entries.push(normalized);
                }
            }
        }
    }

    if entries.is_empty() {
        if let Ok(raw_list) = read_command_output("cliphist", &["list"]) {
            for line in raw_list.lines().take(20) {
                let preview = if let Some((_, value)) = line.split_once('\t') {
                    value.trim()
                } else {
                    line.trim()
                };

                if !preview.is_empty() && !entries.iter().any(|item| item == preview) {
                    entries.push(preview.to_string());
                }
            }
        }
    }

    if entries.is_empty() {
        let current = read_command_output("wl-paste", &["-n"])
            .or_else(|_| read_command_output("xclip", &["-selection", "clipboard", "-o"]))
            .or_else(|_| read_command_output("xsel", &["--clipboard", "--output"]));

        if let Ok(value) = current {
            let normalized = value.trim().to_string();
            if !normalized.is_empty() {
                entries.push(normalized);
            }
        }
    }

    serde_json::to_string(&entries).map_err(|e| format!("failed to serialize clipboard history: {}", e))
}

fn is_running_as_root() -> bool {
    let output = Command::new("id").arg("-u").output();
    match output {
        Ok(value) if value.status.success() => {
            let uid = String::from_utf8(value.stdout).unwrap_or_default();
            uid.trim() == "0"
        }
        _ => false,
    }
}

fn open_url_with_fallback(url: &str) -> Result<(), String> {
    if webbrowser::open(url).is_ok() {
        return Ok(());
    }

    let mut errors: Vec<String> = Vec::new();

    let xdg_open = Command::new("xdg-open").arg(url).status();
    match xdg_open {
        Ok(status) if status.success() => return Ok(()),
        Ok(status) => errors.push(format!("xdg-open exited with {}", status)),
        Err(err) => errors.push(format!("xdg-open failed: {}", err)),
    }

    let gio_open = Command::new("gio").args(["open", url]).status();
    match gio_open {
        Ok(status) if status.success() => return Ok(()),
        Ok(status) => errors.push(format!("gio open exited with {}", status)),
        Err(err) => errors.push(format!("gio open failed: {}", err)),
    }

    Err(format!(
        "Could not open browser automatically. Open this URL manually: {}. Details: {}",
        url,
        errors.join(" | ")
    ))
}

#[derive(Serialize)]
pub struct ElevationStatus {
    pub is_root: bool,
}

#[tauri::command]
async fn get_elevation_status() -> Result<ElevationStatus, String> {
    Ok(ElevationStatus {
        is_root: is_running_as_root(),
    })
}

#[tauri::command]
async fn request_elevated_mode() -> Result<(), String> {
    if is_running_as_root() {
        return Ok(());
    }

    Err("Full-app sudo relaunch is deprecated. Run BinaryStars normally and authorize privileged actions (shutdown/reboot) when prompted by PolicyKit.".to_string())
}

#[tauri::command]
async fn perform_local_action(action_type: String, payload_json: Option<String>) -> Result<String, String> {
    tauri::async_runtime::spawn_blocking(move || {
        match action_type.as_str() {
            "block_screen" => {
                block_screen_linux()?;
                Ok("{}".to_string())
            }
            "shutdown" => {
                power_action_linux("shutdown")?;
                Ok("{}".to_string())
            }
            "reboot" | "reset" => {
                power_action_linux("reboot")?;
                Ok("{}".to_string())
            }
            "list_launchable_apps" => list_launchable_apps_json(),
            "list_running_apps" => list_running_apps_json(),
            "open_app" => {
                open_app_linux(payload_json)?;
                Ok("{}".to_string())
            }
            "close_app" => {
                close_app_linux(payload_json)?;
                Ok("{}".to_string())
            }
            "get_clipboard_history" => list_clipboard_history_json(),
            _ => Err("Unsupported local action".to_string()),
        }
    })
    .await
    .map_err(|e| format!("spawn error: {}", e))?
}

#[derive(Deserialize)]
struct OAuthTokenResponse {
    access_token: Option<String>,
    id_token: Option<String>,
    error: Option<String>,
    error_description: Option<String>,
}

#[derive(Clone)]
enum OAuthProvider {
    Google,
    Microsoft,
}

impl OAuthProvider {
    fn parse(provider: &str) -> Result<Self, String> {
        match provider.to_lowercase().as_str() {
            "google" => Ok(Self::Google),
            "microsoft" => Ok(Self::Microsoft),
            _ => Err(format!("Unsupported provider: {}", provider)),
        }
    }
}

#[derive(Serialize)]
pub struct InterfaceInfo {
    pub name: String,
    pub ips: Vec<String>,
    pub rx_bytes_per_sec: u64,
    pub tx_bytes_per_sec: u64,
}

#[derive(Serialize)]
pub struct DeviceInfo {
    pub hostname: String,
    pub ip_address: String,
    pub ipv6_address: String,
    pub battery_level: Option<u8>,
    pub cpu_load_percent: Option<u8>,
    pub memory_load_percent: Option<u8>,
    pub wifi_upload_speed: String,
    pub wifi_download_speed: String,
    pub interfaces: Vec<InterfaceInfo>,
}

fn read_battery_percent() -> Option<u8> {
    let power_supply = std::path::Path::new("/sys/class/power_supply");
    let entries = std::fs::read_dir(power_supply).ok()?;

    for entry in entries.flatten() {
        let capacity_path = entry.path().join("capacity");
        let raw = std::fs::read_to_string(capacity_path).ok()?;
        let parsed = raw.trim().parse::<i32>().ok()?;
        if (0..=100).contains(&parsed) {
            return Some(parsed as u8);
        }
    }

    None
}

fn format_kbps(bytes_per_sec: u64) -> String {
    let kbps = (bytes_per_sec.saturating_mul(8)) / 1000;
    format!("{} kbps", kbps)
}

#[tauri::command]
async fn get_device_info() -> Result<DeviceInfo, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let hostname = match hostname::get() {
            Ok(h) => h.into_string().unwrap_or_else(|_| "unknown".to_string()),
            Err(_) => "unknown".to_string(),
        };

        let ifaces = match if_addrs::get_if_addrs() {
            Ok(v) => v,
            Err(e) => return Err(format!("if_addrs error: {}", e)),
        };

        let mut sys = sysinfo::System::new_all();
        sys.refresh_cpu();
        std::thread::sleep(std::time::Duration::from_millis(250));
        sys.refresh_cpu();
        sys.refresh_memory();

        let cpu_load_percent = {
            let raw = sys.global_cpu_info().cpu_usage();
            if raw.is_finite() {
                Some(raw.round().clamp(0.0, 100.0) as u8)
            } else {
                None
            }
        };

        let memory_load_percent = {
            let total = sys.total_memory();
            let used = sys.used_memory();
            if total == 0 {
                None
            } else {
                let pct = ((used as f64 / total as f64) * 100.0).round();
                Some(pct.clamp(0.0, 100.0) as u8)
            }
        };

        let mut networks = sysinfo::Networks::new_with_refreshed_list();
        let mut initial: HashMap<String, (u64, u64)> = HashMap::new();
        for (name, net) in &networks {
            initial.insert(name.to_string(), (net.received(), net.transmitted()));
        }

        std::thread::sleep(std::time::Duration::from_secs(1));

        networks.refresh();

        let mut addresses_by_interface: BTreeMap<String, Vec<String>> = BTreeMap::new();
        let mut ipv4_address: Option<String> = None;
        let mut ipv6_address: Option<String> = None;

        for iface in ifaces {
            let ip = iface.ip();
            let ip_text = ip.to_string();

            match ip {
                IpAddr::V4(v4) => {
                    if !v4.is_loopback() && ipv4_address.is_none() {
                        ipv4_address = Some(ip_text.clone());
                    }
                }
                IpAddr::V6(v6) => {
                    if !v6.is_loopback() && ipv6_address.is_none() {
                        ipv6_address = Some(ip_text.clone());
                    }
                }
            }

            let entry = addresses_by_interface
                .entry(iface.name)
                .or_default();
            if !entry.iter().any(|candidate| candidate == &ip_text) {
                entry.push(ip_text);
            }
        }

        if ipv4_address.is_none() {
            ipv4_address = Some("127.0.0.1".to_string());
        }

        if ipv6_address.is_none() {
            ipv6_address = Some("::1".to_string());
        }

        let mut interfaces = Vec::new();
        let mut total_rx_bytes_per_sec = 0u64;
        let mut total_tx_bytes_per_sec = 0u64;

        for (name, ips) in addresses_by_interface {
            let (rx, tx) = if let Some((r0, t0)) = initial.get(&name) {
                if let Some(net) = networks.get(&name) {
                    let r1 = net.received();
                    let t1 = net.transmitted();
                    (r1.saturating_sub(*r0), t1.saturating_sub(*t0))
                } else {
                    (0u64, 0u64)
                }
            } else {
                (0u64, 0u64)
            };

            total_rx_bytes_per_sec = total_rx_bytes_per_sec.saturating_add(rx);
            total_tx_bytes_per_sec = total_tx_bytes_per_sec.saturating_add(tx);

            interfaces.push(InterfaceInfo {
                name,
                ips,
                rx_bytes_per_sec: rx,
                tx_bytes_per_sec: tx,
            });
        }

        Ok(DeviceInfo {
            hostname,
            ip_address: ipv4_address.unwrap_or_else(|| "127.0.0.1".to_string()),
            ipv6_address: ipv6_address.unwrap_or_else(|| "::1".to_string()),
            battery_level: read_battery_percent(),
            cpu_load_percent,
            memory_load_percent,
            wifi_upload_speed: format_kbps(total_tx_bytes_per_sec),
            wifi_download_speed: format_kbps(total_rx_bytes_per_sec),
            interfaces,
        })
    })
    .await
    .map_err(|e| format!("spawn error: {}", e))?
}

#[tauri::command]
async fn oauth_get_provider_token(
    provider: String,
    client_id: String,
    redirect_uri: String,
    google_client_secret: Option<String>,
    microsoft_tenant_id: Option<String>,
    microsoft_scope: Option<String>,
) -> Result<String, String> {
    let provider = OAuthProvider::parse(&provider)?;

    if is_running_as_root() {
        return Err("OAuth sign-in is disabled in elevated mode. Start BinaryStars normally, sign in, then enable sudo mode from Actions.".to_string());
    }

    tauri::async_runtime::spawn_blocking(move || {
        let redirect = Url::parse(&redirect_uri)
            .map_err(|e| format!("Invalid redirect URI: {}", e))?;

        if redirect.scheme() != "http" {
            return Err("Redirect URI must use http for loopback desktop OAuth".to_string());
        }

        let host = redirect
            .host_str()
            .ok_or_else(|| "Redirect URI host is required".to_string())?;
        if host != "127.0.0.1" && host != "localhost" {
            return Err("Redirect URI host must be localhost or 127.0.0.1".to_string());
        }

        let port = redirect
            .port_or_known_default()
            .ok_or_else(|| "Redirect URI must include a port (example: http://127.0.0.1:53123/callback)".to_string())?;

        let bind_addr = format!("{}:{}", host, port);
        let listener = TcpListener::bind(&bind_addr)
            .map_err(|e| format!("Cannot bind callback listener on {}: {}", bind_addr, e))?;

        let state: String = rand::thread_rng()
            .sample_iter(&Alphanumeric)
            .take(32)
            .map(char::from)
            .collect();
        let code_verifier: String = rand::thread_rng()
            .sample_iter(&Alphanumeric)
            .take(96)
            .map(char::from)
            .collect();
        let code_challenge = URL_SAFE_NO_PAD.encode(Sha256::digest(code_verifier.as_bytes()));

        let (auth_url, token_url, scope) = match provider {
            OAuthProvider::Google => {
                let scope = "openid email profile".to_string();
                let auth_url = format!(
                    "https://accounts.google.com/o/oauth2/v2/auth?client_id={}&response_type=code&redirect_uri={}&scope={}&code_challenge={}&code_challenge_method=S256&state={}&access_type=offline&prompt=select_account",
                    urlencoding::encode(&client_id),
                    urlencoding::encode(&redirect_uri),
                    urlencoding::encode(&scope),
                    urlencoding::encode(&code_challenge),
                    urlencoding::encode(&state)
                );
                (auth_url, "https://oauth2.googleapis.com/token".to_string(), scope)
            }
            OAuthProvider::Microsoft => {
                let tenant = microsoft_tenant_id
                    .as_ref()
                    .filter(|value| !value.trim().is_empty())
                    .map(|value| value.trim().to_string())
                    .unwrap_or_else(|| "common".to_string());

                let scope = microsoft_scope
                    .as_ref()
                    .filter(|value| !value.trim().is_empty())
                    .map(|value| value.trim().to_string())
                    .unwrap_or_else(|| format!("api://{}/access_as_user openid profile email offline_access", client_id));

                let auth_url = format!(
                    "https://login.microsoftonline.com/{}/oauth2/v2.0/authorize?client_id={}&response_type=code&redirect_uri={}&response_mode=query&scope={}&code_challenge={}&code_challenge_method=S256&state={}",
                    urlencoding::encode(&tenant),
                    urlencoding::encode(&client_id),
                    urlencoding::encode(&redirect_uri),
                    urlencoding::encode(&scope),
                    urlencoding::encode(&code_challenge),
                    urlencoding::encode(&state)
                );
                let token_url = format!("https://login.microsoftonline.com/{}/oauth2/v2.0/token", tenant);
                (auth_url, token_url, scope)
            }
        };

        open_url_with_fallback(&auth_url)?;

        let (mut stream, _) = listener
            .accept()
            .map_err(|e| format!("OAuth callback was not received: {}", e))?;
        stream
            .set_read_timeout(Some(Duration::from_secs(180)))
            .map_err(|e| format!("Cannot set callback timeout: {}", e))?;

        let mut request_line = String::new();
        {
            let mut reader = BufReader::new(&mut stream);
            reader
                .read_line(&mut request_line)
                .map_err(|e| format!("Failed reading OAuth callback: {}", e))?;
        }

        let requested_path = request_line
            .split_whitespace()
            .nth(1)
            .ok_or_else(|| "Malformed OAuth callback request".to_string())?;

        let callback_url = format!("http://localhost{}", requested_path);
        let parsed_callback = Url::parse(&callback_url)
            .map_err(|e| format!("Invalid OAuth callback URL: {}", e))?;

        let mut auth_code: Option<String> = None;
        let mut callback_state: Option<String> = None;
        let mut oauth_error: Option<String> = None;

        for (key, value) in parsed_callback.query_pairs() {
            match key.as_ref() {
                "code" => auth_code = Some(value.to_string()),
                "state" => callback_state = Some(value.to_string()),
                "error" => oauth_error = Some(value.to_string()),
                _ => {}
            }
        }

        let callback_validation_error = if let Some(error) = oauth_error {
            Some(format!("OAuth provider returned error: {}", error))
        } else if let Some(callback_state) = callback_state {
            if callback_state != state {
                Some("OAuth state validation failed".to_string())
            } else if auth_code.is_none() {
                Some("OAuth callback missing authorization code".to_string())
            } else {
                None
            }
        } else {
            Some("OAuth callback missing state".to_string())
        };

        if let Some(error) = callback_validation_error {
            let response_body = "Authentication failed. You can close this window and return to BinaryStars.";
            let response = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                response_body.len(),
                response_body
            );
            stream
                .write_all(response.as_bytes())
                .map_err(|e| format!("Failed writing OAuth callback response: {}", e))?;
            return Err(error);
        }

        let code = auth_code.ok_or_else(|| "OAuth callback missing authorization code".to_string())?;

        let http_client = Client::builder()
            .timeout(Duration::from_secs(45))
            .build()
            .map_err(|e| format!("Failed creating HTTP client: {}", e))?;

        let mut token_form = vec![
            ("grant_type", "authorization_code"),
            ("code", code.as_str()),
            ("redirect_uri", redirect_uri.as_str()),
            ("client_id", client_id.as_str()),
            ("code_verifier", code_verifier.as_str()),
        ];
        if matches!(provider, OAuthProvider::Google) {
            if let Some(secret) = google_client_secret
                .as_ref()
                .map(|value| value.trim())
                .filter(|value| !value.is_empty())
            {
                token_form.push(("client_secret", secret));
            }
        }
        if matches!(provider, OAuthProvider::Microsoft) {
            token_form.push(("scope", scope.as_str()));
        }

        let token_result: Result<String, String> = (|| {
            let token_response = http_client
                .post(token_url)
                .form(&token_form)
                .send()
                .map_err(|e| format!("Token exchange request failed: {}", e))?;

            let token_payload: OAuthTokenResponse = token_response
                .json()
                .map_err(|e| format!("Invalid token exchange response: {}", e))?;

            if let Some(error) = token_payload.error {
                let description = token_payload
                    .error_description
                    .unwrap_or_else(|| "Unknown OAuth token exchange error".to_string());
                return Err(format!("OAuth token exchange failed: {} ({})", error, description));
            }

            match provider {
                OAuthProvider::Google => token_payload
                    .id_token
                    .ok_or_else(|| "Google token exchange did not return id_token".to_string()),
                OAuthProvider::Microsoft => token_payload
                    .access_token
                    .or(token_payload.id_token)
                    .ok_or_else(|| "Microsoft token exchange did not return access_token/id_token".to_string()),
            }
        })();

        let response_body = if token_result.is_ok() {
            "Authentication completed. You can close this window and return to BinaryStars."
        } else {
            "Authentication failed. You can close this window and return to BinaryStars."
        };

        let response = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            response_body.len(),
            response_body
        );
        stream
            .write_all(response.as_bytes())
            .map_err(|e| format!("Failed writing OAuth callback response: {}", e))?;

        token_result
    })
    .await
    .map_err(|e| format!("OAuth task failed: {}", e))?
}
