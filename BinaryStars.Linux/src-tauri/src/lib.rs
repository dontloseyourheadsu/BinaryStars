use serde::Serialize;
use std::collections::{BTreeMap, HashMap, HashSet};
use std::convert::TryInto;
use std::fs::{self, OpenOptions};
use std::io::{BufRead, BufReader, Read, Write};
use std::net::IpAddr;
use std::net::TcpListener;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant};
use base64::{engine::general_purpose::{STANDARD, URL_SAFE_NO_PAD}, Engine as _};
use freedesktop_desktop_entry::DesktopEntry;
use rand::{distributions::Alphanumeric, Rng};
use reqwest::blocking::Client;
use serde::Deserialize;
use sha2::{Digest, Sha256};
use sysinfo::System;
use url::Url;
use zbus::blocking::{fdo::PropertiesProxy, Connection, Proxy};
use zbus::names::InterfaceName;
use zbus::zvariant::{OwnedObjectPath, OwnedValue};

const LOG_ROOT_DIR: &str = "/tmp/binarystarslinux/logs";
const LOG_FILE_NAME: &str = "binarystarslinux.log";
const LOG_MAX_READ_LINES: usize = 1500;
static INSTALLED_APPS_JSON_CACHE: OnceLock<Mutex<Option<String>>> = OnceLock::new();

fn installed_apps_cache() -> &'static Mutex<Option<String>> {
    INSTALLED_APPS_JSON_CACHE.get_or_init(|| Mutex::new(None))
}

fn log_file_path() -> Result<PathBuf, String> {
    let dir = PathBuf::from(LOG_ROOT_DIR);
    if !dir.exists() {
        fs::create_dir_all(&dir).map_err(|e| format!("failed to create log directory: {}", e))?;
    }

    fs::set_permissions(&dir, fs::Permissions::from_mode(0o700))
        .map_err(|e| format!("failed to secure log directory permissions: {}", e))?;

    let file_path = dir.join(LOG_FILE_NAME);
    if !file_path.exists() {
        OpenOptions::new()
            .create(true)
            .append(true)
            .open(&file_path)
            .map_err(|e| format!("failed to create log file: {}", e))?;
    }

    fs::set_permissions(&file_path, fs::Permissions::from_mode(0o600))
        .map_err(|e| format!("failed to secure log file permissions: {}", e))?;

    Ok(file_path)
}

fn current_epoch_ms() -> u128 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|duration| duration.as_millis())
        .unwrap_or(0)
}

fn append_log_line(level: &str, source: &str, message: &str, details: Option<&str>) -> Result<(), String> {
    let path = log_file_path()?;
    let mut file = OpenOptions::new()
        .create(true)
        .append(true)
        .open(&path)
        .map_err(|e| format!("failed to open log file: {}", e))?;

    let mut line = format!(
        "ts={} level={} source={} msg={}",
        current_epoch_ms(),
        level,
        source,
        message.replace('\n', " ")
    );

    if let Some(extra) = details {
        if !extra.trim().is_empty() {
            line.push_str(" details=");
            line.push_str(&extra.replace('\n', " "));
        }
    }

    line.push('\n');
    file.write_all(line.as_bytes())
        .map_err(|e| format!("failed to append log line: {}", e))?;

    Ok(())
}

#[tauri::command]
fn append_ui_log(
    level: String,
    category: String,
    message: String,
    metadata_json: Option<String>,
) -> Result<(), String> {
    append_log_line(
        level.trim(),
        &format!("ui:{}", category.trim()),
        message.trim(),
        metadata_json.as_deref(),
    )
}

#[tauri::command]
fn get_log_file_path() -> Result<String, String> {
    Ok(log_file_path()?.to_string_lossy().to_string())
}

#[tauri::command]
fn read_recent_logs(max_lines: Option<usize>) -> Result<Vec<String>, String> {
    let path = log_file_path()?;
    let content = fs::read_to_string(path).map_err(|e| format!("failed to read log file: {}", e))?;
    let limit = max_lines.unwrap_or(300).min(LOG_MAX_READ_LINES);

    let lines = content
        .lines()
        .rev()
        .take(limit)
        .collect::<Vec<&str>>()
        .into_iter()
        .rev()
        .map(|line| line.to_string())
        .collect::<Vec<String>>();

    Ok(lines)
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct InstalledAppItem {
    name: String,
    exec: String,
    icon: Option<String>,
    categories: Option<String>,
    no_display: bool,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RunningAppItem {
    main_pid: i32,
    pid: i32,
    name: String,
    exe: String,
    cpu_usage: f32,
    memory_mb: f64,
    command_line: String,
    process_count: usize,
    pids: Vec<i32>,
    has_visible_window: bool,
}

#[derive(Clone)]
struct RunningProcessSnapshot {
    pid: u32,
    ppid: u32,
    name: String,
    exe: String,
    cpu_usage: f32,
    memory_mb: f64,
    command_line: String,
}

#[tauri::command]
fn show_notification(title: String, body: String) -> Result<(), String> {
    let _ = append_log_line("info", "rust:notification", "show_notification called", Some(&format!("title={}", title)));
    std::process::Command::new("notify-send")
        .arg(&title)
        .arg(&body)
        .spawn()
        .map_err(|e| e.to_string())?;
    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let _ = append_log_line("info", "rust:startup", "launching tauri runtime", None);

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            append_ui_log,
            get_log_file_path,
            read_recent_logs,
            show_notification,
            get_device_info,
            oauth_get_provider_token,
            get_bluetooth_connected_device_names,
            get_bluetooth_devices,
            send_file_via_bluetooth,
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
pub struct LinuxBluetoothDevice {
    pub name: String,
    pub address: String,
    pub connected: bool,
    pub paired: bool,
}

fn parse_bluetooth_device_line(line: &str) -> Option<(String, String)> {
    if !line.starts_with("Device ") {
        return None;
    }

    let mut parts = line.split_whitespace();
    let _device = parts.next()?;
    let address = parts.next()?.trim().to_string();
    let name = parts.collect::<Vec<&str>>().join(" ").trim().to_string();

    if address.is_empty() || name.is_empty() {
        return None;
    }

    Some((address, name))
}

#[tauri::command]
async fn get_bluetooth_devices() -> Result<Vec<LinuxBluetoothDevice>, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let paired_output = Command::new("bluetoothctl").args(["devices"]).output();
        let connected_output = Command::new("bluetoothctl").args(["devices", "Connected"]).output();

        let mut devices: HashMap<String, LinuxBluetoothDevice> = HashMap::new();

        if let Ok(output) = paired_output {
            if output.status.success() {
                let stdout = String::from_utf8(output.stdout).unwrap_or_default();
                for line in stdout.lines() {
                    if let Some((address, name)) = parse_bluetooth_device_line(line) {
                        devices.insert(
                            address.clone(),
                            LinuxBluetoothDevice {
                                name,
                                address,
                                connected: false,
                                paired: true,
                            },
                        );
                    }
                }
            }
        }

        if let Ok(output) = connected_output {
            if output.status.success() {
                let stdout = String::from_utf8(output.stdout).unwrap_or_default();
                for line in stdout.lines() {
                    if let Some((address, name)) = parse_bluetooth_device_line(line) {
                        if let Some(existing) = devices.get_mut(&address) {
                            existing.connected = true;
                            if existing.name.is_empty() {
                                existing.name = name;
                            }
                        } else {
                            devices.insert(
                                address.clone(),
                                LinuxBluetoothDevice {
                                    name,
                                    address,
                                    connected: true,
                                    paired: false,
                                },
                            );
                        }
                    }
                }
            }
        }

        let mut result = devices.into_values().collect::<Vec<LinuxBluetoothDevice>>();
        result.sort_by(|left, right| left.name.to_lowercase().cmp(&right.name.to_lowercase()));
        Ok(result)
    })
    .await
    .map_err(|e| format!("spawn error: {}", e))?
}

fn sanitize_file_name(file_name: &str) -> String {
    let sanitized = file_name
        .chars()
        .map(|ch| {
            if ch.is_ascii_alphanumeric() || ch == '.' || ch == '_' || ch == '-' {
                ch
            } else {
                '_'
            }
        })
        .collect::<String>();

    if sanitized.is_empty() {
        "transfer.bin".to_string()
    } else {
        sanitized
    }
}

#[tauri::command]
async fn send_file_via_bluetooth(
    device_address: String,
    file_name: String,
    content_base64: String,
) -> Result<(), String> {
    let _ = append_log_line(
        "info",
        "rust:bluetooth",
        "send_file_via_bluetooth called",
        Some(&format!("device_address={} file_name={}", device_address, file_name)),
    );

    tauri::async_runtime::spawn_blocking(move || {
        let payload = STANDARD
            .decode(content_base64.as_bytes())
            .map_err(|e| format!("invalid file payload: {}", e))?;

        if payload.is_empty() {
            return Err("file payload is empty".to_string());
        }

        let mut temp_dir = PathBuf::from("/tmp/binarystars-bluetooth");
        std::fs::create_dir_all(&temp_dir)
            .map_err(|e| format!("failed to create temp bluetooth directory: {}", e))?;

        let random_suffix = rand::thread_rng()
            .sample_iter(&Alphanumeric)
            .take(8)
            .map(char::from)
            .collect::<String>();
        let safe_name = sanitize_file_name(&file_name);
        temp_dir.push(format!("{}_{}", random_suffix, safe_name));

        std::fs::write(&temp_dir, payload)
            .map_err(|e| format!("failed to stage bluetooth file: {}", e))?;

        let device_arg = format!("--device={}", device_address.trim());
        let output = Command::new("bluetooth-sendto")
            .arg(device_arg)
            .arg(&temp_dir)
            .output()
            .map_err(|e| {
                if e.kind() == std::io::ErrorKind::NotFound {
                    "bluetooth-sendto is not installed. Install the bluez tools package.".to_string()
                } else {
                    format!("failed to start bluetooth-sendto: {}", e)
                }
            })?;

        if !output.status.success() {
            let stderr = String::from_utf8(output.stderr).unwrap_or_default();
            let stdout = String::from_utf8(output.stdout).unwrap_or_default();
            let detail = if !stderr.trim().is_empty() { stderr } else { stdout };
            return Err(format!("bluetooth-sendto failed: {}", detail.trim()));
        }

        Ok(())
    })
    .await
    .map_err(|e| format!("spawn error: {}", e))?
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
    let desktop = current_desktop();

    let mut errors: Vec<String> = Vec::new();

    // Best first attempt on modern Linux desktops (Wayland and X11).
    if try_command("loginctl", &["lock-session"]).is_ok() {
        return Ok(());
    }

    let mut candidates: Vec<(&str, Vec<&str>)> = Vec::new();
    if desktop.contains("gnome") {
        candidates.push((
            "gdbus",
            vec![
                "call",
                "--session",
                "--dest",
                "org.gnome.ScreenSaver",
                "--object-path",
                "/org/gnome/ScreenSaver",
                "--method",
                "org.gnome.ScreenSaver.Lock",
            ],
        ));
    }

    if desktop.contains("kde") || desktop.contains("plasma") {
        candidates.push((
            "qdbus",
            vec![
                "org.kde.screensaver",
                "/ScreenSaver",
                "org.kde.screensaver.Lock",
            ],
        ));
        candidates.push((
            "qdbus6",
            vec![
                "org.kde.screensaver",
                "/ScreenSaver",
                "org.kde.screensaver.Lock",
            ],
        ));
    }

    candidates.push(("xdg-screensaver", vec!["lock"]));

    for (program, args) in candidates {
        match try_command(program, &args) {
            Ok(_) => return Ok(()),
            Err(error) => errors.push(error),
        }
    }

    Err(format!(
        "Unable to lock screen on this Linux desktop/session. The session API may be unavailable or denied this request. Tried loginctl + desktop fallbacks. Details: {}",
        errors.join(" | ")
    ))
}

fn current_desktop() -> String {
    std::env::var("XDG_CURRENT_DESKTOP")
        .unwrap_or_default()
        .to_ascii_lowercase()
}

fn shutdown_linux() -> Result<(), String> {
    try_command("systemctl", &["poweroff"]).map_err(|error| {
        format!(
            "Unable to execute shutdown in no-root mode. Requires an active desktop session and logind/polkit policy. Details: {}",
            error
        )
    })
}

fn reboot_linux() -> Result<(), String> {
    try_command("systemctl", &["reboot"]).map_err(|error| {
        format!(
            "Unable to execute reboot in no-root mode. Requires an active desktop session and logind/polkit policy. Details: {}",
            error
        )
    })
}

fn list_installed_apps_json() -> Result<String, String> {
    const LIST_COLLECTION_TIMEOUT: Duration = Duration::from_secs(4);

    let started_at = Instant::now();
    let (sender, receiver) = std::sync::mpsc::channel::<Result<String, String>>();

    std::thread::spawn(move || {
        let _ = sender.send(collect_installed_apps_json());
    });

    match receiver.recv_timeout(LIST_COLLECTION_TIMEOUT) {
        Ok(Ok(json)) => {
            if let Ok(mut cache) = installed_apps_cache().lock() {
                *cache = Some(json.clone());
            }

            return Ok(json);
        }
        Ok(Err(error)) => {
            let _ = append_log_line(
                "warn",
                "rust:actions",
                "list_installed_apps_json primary collection failed",
                Some(&format!("error={}", error)),
            );
        }
        Err(_) => {
            let _ = append_log_line(
                "warn",
                "rust:actions",
                "list_installed_apps_json collection timed out",
                Some(&format!("timeout_ms={}", LIST_COLLECTION_TIMEOUT.as_millis())),
            );
        }
    }

    if let Ok(cache) = installed_apps_cache().lock() {
        if let Some(cached_json) = cache.clone() {
            let _ = append_log_line(
                "info",
                "rust:actions",
                "list_installed_apps_json served from cache",
                Some(&format!("elapsed_ms={}", started_at.elapsed().as_millis())),
            );
            return Ok(cached_json);
        }
    }

    Ok("[]".to_string())
}

fn collect_installed_apps_json() -> Result<String, String> {
    const MAX_ENUM_DURATION: Duration = Duration::from_secs(6);
    const MAX_ITEMS: usize = 700;
    const MAX_DESKTOP_FILES: usize = 1200;
    const MAX_DESKTOP_FILE_BYTES: u64 = 512 * 1024;

    let started_at = Instant::now();
    let mut entries: Vec<InstalledAppItem> = Vec::new();
    let mut desktop_files: Vec<PathBuf> = Vec::new();
    let mut visited_dirs: HashSet<PathBuf> = HashSet::new();

    let mut candidate_dirs: Vec<PathBuf> = Vec::new();
    if let Ok(home) = std::env::var("HOME") {
        candidate_dirs.push(PathBuf::from(home).join(".local/share/applications"));
    }

    candidate_dirs.push(PathBuf::from("/usr/local/share/applications"));
    candidate_dirs.push(PathBuf::from("/usr/share/applications"));

    for dir in candidate_dirs {
        if started_at.elapsed() >= MAX_ENUM_DURATION || desktop_files.len() >= MAX_DESKTOP_FILES {
            break;
        }

        if !dir.exists() || !visited_dirs.insert(dir.clone()) {
            continue;
        }

        let Ok(read_dir) = fs::read_dir(&dir) else {
            continue;
        };

        for file in read_dir.flatten() {
            if started_at.elapsed() >= MAX_ENUM_DURATION || desktop_files.len() >= MAX_DESKTOP_FILES {
                break;
            }

            let Ok(file_type) = file.file_type() else {
                continue;
            };

            // Skip symlinks and special files to avoid blocking reads.
            if file_type.is_symlink() || !file_type.is_file() {
                continue;
            }

            let path = file.path();
            if path.extension().and_then(|ext| ext.to_str()) != Some("desktop") {
                continue;
            }

            desktop_files.push(path);
        }
    }

    let mut seen_execs: HashSet<String> = HashSet::new();

    for path in desktop_files {
        if started_at.elapsed() >= MAX_ENUM_DURATION || entries.len() >= MAX_ITEMS {
            break;
        }

        let Ok(metadata) = fs::metadata(&path) else {
            continue;
        };

        if metadata.len() == 0 || metadata.len() > MAX_DESKTOP_FILE_BYTES {
            continue;
        }

        let Ok(file) = fs::File::open(&path) else {
            continue;
        };

        let reader = BufReader::new(file);
        let mut content = String::new();
        if reader.take(MAX_DESKTOP_FILE_BYTES).read_to_string(&mut content).is_err() {
            continue;
        }

        if content.trim().is_empty() {
            continue;
        }

        let Ok(entry) = DesktopEntry::decode(&path, content.as_str()) else {
            continue;
        };

        if entry.type_() != Some("Application") {
            continue;
        }

        if entry.no_display() {
            continue;
        }

        let Some(raw_name) = entry.name(None) else {
            continue;
        };

        let Some(raw_exec) = entry.exec() else {
            continue;
        };

        let name = raw_name.trim().to_string();
        let exec = raw_exec.trim().to_string();
        if name.is_empty() || exec.is_empty() {
            continue;
        }

        if !seen_execs.insert(exec.clone()) {
            continue;
        }

        entries.push(InstalledAppItem {
            name,
            exec,
            icon: entry.icon().map(str::to_string),
            categories: entry.categories().map(str::to_string),
            no_display: false,
        });
    }

    if entries.is_empty() {
        match collect_installed_apps_from_gio_python() {
            Ok(gio_entries) if !gio_entries.is_empty() => {
                entries = gio_entries;
            }
            Ok(_) => {}
            Err(error) => {
                let _ = append_log_line(
                    "warn",
                    "rust:actions",
                    "gio fallback returned error",
                    Some(&format!("error={}", error)),
                );
            }
        }
    }

    entries.sort_by(|left, right| left.name.to_lowercase().cmp(&right.name.to_lowercase()));
    let elapsed_ms = started_at.elapsed().as_millis();
    let _ = append_log_line(
        "info",
        "rust:actions",
        "list_installed_apps_json completed",
        Some(&format!("elapsed_ms={} app_count={}", elapsed_ms, entries.len())),
    );
    serde_json::to_string(&entries).map_err(|e| format!("failed to serialize installed apps: {}", e))
}

fn collect_installed_apps_from_gio_python() -> Result<Vec<InstalledAppItem>, String> {
    let script = r#"
import json
try:
    import gi
    gi.require_version('Gio', '2.0')
    from gi.repository import Gio
except Exception as e:
    raise SystemExit('gio-import-failed:' + str(e))

apps = []
seen = set()
for app in Gio.AppInfo.get_all():
    try:
        if not app.should_show():
            continue
        name = (app.get_display_name() or app.get_name() or '').strip()
        executable = (app.get_executable() or '').strip()
        app_id = (app.get_id() or '').strip()
        if not name:
            continue
        key = app_id or executable or name
        if key in seen:
            continue
        seen.add(key)
        apps.append({
            'name': name,
            'exec': executable,
            'icon': None,
            'categories': None,
            'no_display': False
        })
    except Exception:
        continue

print(json.dumps(apps))
"#;

    let output = Command::new("python3")
        .arg("-c")
        .arg(script)
        .output()
        .map_err(|e| format!("python3 gio fallback failed to execute: {}", e))?;

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr).trim().to_string();
        return Err(format!("python3 gio fallback exited non-zero: {}", stderr));
    }

    let stdout = String::from_utf8_lossy(&output.stdout);
    let parsed = serde_json::from_str::<Vec<InstalledAppItem>>(stdout.trim())
        .map_err(|e| format!("failed to parse gio fallback payload: {}", e))?;

    Ok(parsed)
}

fn is_generic_exec_token(token: &str) -> bool {
    matches!(
        token,
        "sh"
            | "bash"
            | "env"
            | "flatpak"
            | "python"
            | "python3"
            | "java"
            | "systemd"
            | "gdbus"
            | "dbus-daemon"
    )
}

fn exec_primary_token(exec: &str) -> Option<String> {
    let cleaned = clean_exec(exec);
    let parts = shlex::split(&cleaned)?;
    let program = parts.first()?;
    let token = Path::new(program)
        .file_name()
        .and_then(|value| value.to_str())?
        .trim()
        .to_lowercase();

    if token.is_empty() || token.len() < 3 || is_generic_exec_token(token.as_str()) {
        return None;
    }

    Some(token)
}

fn process_identity_tokens(process: &RunningProcessSnapshot) -> HashSet<String> {
    let mut tokens: HashSet<String> = HashSet::new();

    let process_name = process.name.trim().to_lowercase();
    if !process_name.is_empty() {
        tokens.insert(process_name);
    }

    let process_exe_basename = Path::new(&process.exe)
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or_default()
        .trim()
        .to_lowercase();
    if !process_exe_basename.is_empty() {
        tokens.insert(process_exe_basename);
    }

    let cmd_parts = shlex::split(&process.command_line)
        .unwrap_or_else(|| process.command_line.split_whitespace().map(|part| part.to_string()).collect());

    for part in cmd_parts {
        let raw = part.trim().to_lowercase();
        if raw.is_empty() {
            continue;
        }

        let basename = Path::new(&raw)
            .file_name()
            .and_then(|value| value.to_str())
            .unwrap_or_default()
            .trim()
            .to_lowercase();

        if !basename.is_empty() {
            tokens.insert(basename);
        }
    }

    tokens
}

fn build_token_grouped_running_apps(processes: &[RunningProcessSnapshot]) -> Option<Vec<RunningAppItem>> {
    let payload = list_installed_apps_json().ok()?;
    let installed_apps = serde_json::from_str::<Vec<InstalledAppItem>>(&payload).ok()?;

    let mut token_to_name: HashMap<String, String> = HashMap::new();
    for app in installed_apps {
        if let Some(token) = exec_primary_token(&app.exec) {
            token_to_name.entry(token).or_insert(app.name);
        }
    }

    if token_to_name.is_empty() {
        return None;
    }

    let mut grouped: HashMap<String, Vec<RunningProcessSnapshot>> = HashMap::new();
    for process in processes {
        let identities = process_identity_tokens(process);
        let mut matched_tokens: HashSet<String> = HashSet::new();

        for identity in identities {
            if token_to_name.contains_key(&identity) {
                matched_tokens.insert(identity);
            }
        }

        if matched_tokens.is_empty() {
            continue;
        }

        for token in matched_tokens {
            grouped.entry(token).or_default().push(process.clone());
        }
    }

    let mut items: Vec<RunningAppItem> = grouped
        .into_iter()
        .filter_map(|(token, mut members)| {
            if members.is_empty() {
                return None;
            }

            members.sort_by_key(|member| member.pid);
            let main = members.first()?.clone();
            let mut pids: Vec<i32> = members.iter().map(|member| member.pid as i32).collect();
            pids.sort_unstable();
            pids.dedup();

            let cpu_usage = members.iter().map(|member| member.cpu_usage).sum::<f32>();
            let memory_mb = members.iter().map(|member| member.memory_mb).sum::<f64>();

            Some(RunningAppItem {
                main_pid: main.pid as i32,
                pid: main.pid as i32,
                name: token_to_name
                    .get(&token)
                    .cloned()
                    .unwrap_or_else(|| main.name.clone()),
                exe: main.exe,
                cpu_usage,
                memory_mb,
                command_line: main.command_line,
                process_count: pids.len(),
                pids,
                has_visible_window: false,
            })
        })
        .collect();

    if items.is_empty() {
        return None;
    }

    items.sort_by(|left, right| left.name.to_lowercase().cmp(&right.name.to_lowercase()));
    Some(items)
}

fn list_running_apps_json() -> Result<String, String> {
    let mut system = System::new_all();
    system.refresh_processes();
    let current_uid = read_current_uid();

    let processes: Vec<RunningProcessSnapshot> = system
        .processes()
        .values()
        .filter(|process| {
            let Some(uid) = current_uid else {
                return false;
            };

            read_process_uid(process.pid().as_u32()) == Some(uid)
        })
        .map(|process| RunningProcessSnapshot {
            pid: process.pid().as_u32(),
            ppid: read_process_ppid(process.pid().as_u32()).unwrap_or(0),
            name: process.name().to_string(),
            exe: process
                .exe()
                .map(|path| path.to_string_lossy().into_owned())
                .unwrap_or_default(),
            cpu_usage: process.cpu_usage(),
            memory_mb: process.memory() as f64 / 1024.0 / 1024.0,
            command_line: process
                .cmd()
                .iter()
                .map(|part| part.to_string())
                .collect::<Vec<String>>()
                .join(" "),
        })
        .collect();

    let parent_by_pid: HashMap<u32, u32> = processes
        .iter()
        .map(|process| (process.pid, process.ppid))
        .collect();

    let process_by_pid: HashMap<u32, RunningProcessSnapshot> = processes
        .iter()
        .map(|process| (process.pid, process.clone()))
        .collect();

    if let Some(items) = build_token_grouped_running_apps(&processes) {
        let _ = append_log_line(
            "info",
            "rust:actions",
            "list_running_apps_json using token grouping",
            Some(&format!("app_count={}", items.len())),
        );
        return serde_json::to_string(&items)
            .map_err(|e| format!("failed to serialize token-grouped running apps: {}", e));
    }

    let window_owner_pids = detect_window_owner_pids();

    let mut grouped_by_leader: HashMap<u32, Vec<RunningProcessSnapshot>> = HashMap::new();

    for process in processes {
        let leader = resolve_group_leader_pid(process.pid, &parent_by_pid, &window_owner_pids);
        grouped_by_leader.entry(leader).or_default().push(process);
    }

    let mut items: Vec<RunningAppItem> = grouped_by_leader
        .into_iter()
        .map(|(leader_pid, mut members)| {
            members.sort_by_key(|member| member.pid);
            let leader = process_by_pid
                .get(&leader_pid)
                .cloned()
                .unwrap_or_else(|| members[0].clone());

            let mut pids: Vec<i32> = members.iter().map(|member| member.pid as i32).collect();
            pids.sort_unstable();
            pids.dedup();

            let cpu_usage = members.iter().map(|member| member.cpu_usage).sum::<f32>();
            let memory_mb = members.iter().map(|member| member.memory_mb).sum::<f64>();

            RunningAppItem {
                main_pid: leader.pid as i32,
                pid: leader.pid as i32,
                name: leader.name,
                exe: leader.exe,
                cpu_usage,
                memory_mb,
                command_line: leader.command_line,
                process_count: pids.len(),
                pids,
                has_visible_window: window_owner_pids.contains(&leader.pid),
            }
        })
        .collect();

    if items.iter().any(|item| item.has_visible_window) {
        items.retain(|item| item.has_visible_window);
    }

    items.sort_by(|left, right| {
        right
            .has_visible_window
            .cmp(&left.has_visible_window)
            .then_with(|| left.name.to_lowercase().cmp(&right.name.to_lowercase()))
    });

    let _ = append_log_line(
        "info",
        "rust:actions",
        "list_running_apps_json using window/group fallback",
        Some(&format!("app_count={} visible_window_count={}", items.len(), items.iter().filter(|item| item.has_visible_window).count())),
    );

    serde_json::to_string(&items).map_err(|e| format!("failed to serialize running apps: {}", e))
}

fn clean_exec(exec: &str) -> String {
    let mut out = String::new();
    let mut chars = exec.chars().peekable();

    while let Some(ch) = chars.next() {
        if ch == '%' {
            let _ = chars.next();
            continue;
        }

        out.push(ch);
    }

    out.trim().to_string()
}

fn launch_app_linux(payload_json: Option<String>) -> Result<(), String> {
    let launch_started = Instant::now();
    let payload = payload_json.ok_or_else(|| "launch_app requires payload".to_string())?;
    let value: serde_json::Value = serde_json::from_str(&payload)
        .map_err(|e| format!("invalid launch_app payload: {}", e))?;

    let exec = value
        .get("exec")
        .and_then(|v| v.as_str())
        .ok_or_else(|| "launch_app payload missing exec".to_string())?;

    let cleaned = clean_exec(exec);
    if cleaned.is_empty() {
        return Err("launch_app payload exec is empty after sanitization".to_string());
    }

    let _ = append_log_line(
        "info",
        "rust:actions",
        "launch_app_linux parsed payload",
        Some(&format!("exec={} cleaned_exec={}", exec, cleaned)),
    );

    let parts = shlex::split(&cleaned).ok_or_else(|| "invalid launch_app exec format".to_string())?;
    if parts.is_empty() {
        return Err("launch_app payload exec is empty".to_string());
    }

    let program = &parts[0];
    let args = &parts[1..];

    let _ = append_log_line(
        "info",
        "rust:actions",
        "launch_app_linux spawning process",
        Some(&format!("program={} args_count={}", program, args.len())),
    );

    Command::new(program)
        .args(args)
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .spawn()
        .map_err(|e| format!("failed to launch '{}': {}", program, e))?;

    let _ = append_log_line(
        "info",
        "rust:actions",
        "launch_app_linux spawn completed",
        Some(&format!("elapsed_ms={}", launch_started.elapsed().as_millis())),
    );

    Ok(())
}

fn read_current_uid() -> Option<u32> {
    let raw = std::fs::read_to_string("/proc/self/status").ok()?;
    for line in raw.lines() {
        if let Some(rest) = line.strip_prefix("Uid:") {
            let uid_text = rest.split_whitespace().next()?;
            return uid_text.parse::<u32>().ok();
        }
    }

    None
}

fn read_process_uid(pid: u32) -> Option<u32> {
    let path = format!("/proc/{}/status", pid);
    let raw = std::fs::read_to_string(path).ok()?;
    for line in raw.lines() {
        if let Some(rest) = line.strip_prefix("Uid:") {
            let uid_text = rest.split_whitespace().next()?;
            return uid_text.parse::<u32>().ok();
        }
    }

    None
}

fn read_process_ppid(pid: u32) -> Option<u32> {
    let path = format!("/proc/{}/status", pid);
    let raw = std::fs::read_to_string(path).ok()?;
    for line in raw.lines() {
        if let Some(rest) = line.strip_prefix("PPid:") {
            let ppid_text = rest.split_whitespace().next()?;
            return ppid_text.parse::<u32>().ok();
        }
    }

    None
}

fn detect_window_owner_pids() -> HashSet<u32> {
    let mut result: HashSet<u32> = HashSet::new();
    let output = Command::new("wmctrl").args(["-lp"]).output();

    let Ok(output) = output else {
        return result;
    };

    if !output.status.success() {
        return result;
    }

    let stdout = String::from_utf8_lossy(&output.stdout);
    for line in stdout.lines() {
        let mut parts = line.split_whitespace();
        let _window_id = parts.next();
        let _desktop = parts.next();
        let pid_text = parts.next();
        let Some(pid_text) = pid_text else {
            continue;
        };

        if let Ok(pid) = pid_text.parse::<u32>() {
            if pid > 0 {
                result.insert(pid);
            }
        }
    }

    result
}

fn resolve_group_leader_pid(
    pid: u32,
    parent_by_pid: &HashMap<u32, u32>,
    window_owner_pids: &HashSet<u32>,
) -> u32 {
    if window_owner_pids.contains(&pid) {
        return pid;
    }

    let mut current = pid;
    let mut last_known = pid;
    let mut guard = 0usize;

    while guard < 64 {
        guard += 1;
        let Some(parent) = parent_by_pid.get(&current).copied() else {
            break;
        };

        if parent == 0 || parent == current {
            break;
        }

        last_known = parent;
        if window_owner_pids.contains(&parent) {
            return parent;
        }

        current = parent;
    }

    last_known
}

fn resolve_close_targets_by_app_name(app_name: &str) -> Result<Vec<u32>, String> {
    let requested = app_name.trim().to_lowercase();
    if requested.is_empty() {
        return Ok(Vec::new());
    }

    let payload = list_running_apps_json()?;
    let groups = serde_json::from_str::<Vec<RunningAppItem>>(&payload)
        .map_err(|e| format!("failed to parse running app groups: {}", e))?;

    let mut candidates: Vec<&RunningAppItem> = groups
        .iter()
        .filter(|group| {
            let name = group.name.trim().to_lowercase();
            if name.is_empty() {
                return false;
            }

            name == requested || name.contains(&requested) || requested.contains(&name)
        })
        .collect();

    if candidates.is_empty() {
        candidates = groups
            .iter()
            .filter(|group| {
                let cmd = group.command_line.to_lowercase();
                let exe = group.exe.to_lowercase();
                cmd.contains(&requested) || exe.contains(&requested)
            })
            .collect();
    }

    let mut targets: Vec<u32> = Vec::new();
    for candidate in candidates {
        if candidate.pids.is_empty() {
            let fallback = candidate.main_pid.max(candidate.pid);
            if fallback > 0 {
                targets.push(fallback as u32);
            }
            continue;
        }

        for pid in &candidate.pids {
            if *pid > 0 {
                targets.push(*pid as u32);
            }
        }
    }

    targets.sort_unstable();
    targets.dedup();
    Ok(targets)
}

fn close_app_linux(payload_json: Option<String>) -> Result<(), String> {
    let payload = payload_json.ok_or_else(|| "close_app requires payload".to_string())?;
    let value: serde_json::Value = serde_json::from_str(&payload)
        .map_err(|e| format!("invalid close_app payload: {}", e))?;

    let force = value
        .get("force")
        .and_then(|v| v.as_bool())
        .unwrap_or(false);

    let mut pid_targets: Vec<u32> = value
        .get("pids")
        .and_then(|v| v.as_array())
        .map(|values| {
            values
                .iter()
                .filter_map(|entry| entry.as_u64())
                .filter(|entry| *entry > 0)
                .map(|entry| entry as u32)
                .collect::<Vec<u32>>()
        })
        .unwrap_or_default();

    if pid_targets.is_empty() {
        if let Some(app_name) = value.get("appName").and_then(|v| v.as_str()) {
            let resolved = resolve_close_targets_by_app_name(app_name)?;
            if !resolved.is_empty() {
                pid_targets.extend(resolved);
            }
        }
    }

    if pid_targets.is_empty() {
        let pid = value
            .get("pid")
            .and_then(|v| v.as_u64())
            .ok_or_else(|| "close_app payload missing pid, pids, or appName".to_string())? as u32;
        pid_targets.push(pid);
    }

    pid_targets.sort_unstable();
    pid_targets.dedup();

    let mut failures: Vec<String> = Vec::new();
    for pid in pid_targets {
        if let Err(error) = close_single_pid_linux(pid, force) {
            failures.push(error);
        }
    }

    if failures.is_empty() {
        Ok(())
    } else {
        Err(format!("close_app failed: {}", failures.join(" | ")))
    }
}

fn close_single_pid_linux(pid: u32, force: bool) -> Result<(), String> {
    let current_uid = read_current_uid().ok_or_else(|| "failed to determine current uid".to_string())?;
    let process_uid = read_process_uid(pid).ok_or_else(|| format!("no process found with PID {}", pid))?;

    if process_uid != current_uid {
        return Err("no-root mode allows closing only processes owned by the current user".to_string());
    }

    // Use OS-level signal delivery for stronger PID termination behavior on desktop apps.
    let signal = if force { "-KILL" } else { "-TERM" };
    let output = Command::new("kill")
        .arg(signal)
        .arg(pid.to_string())
        .output()
        .map_err(|e| format!("failed to invoke kill for PID {}: {}", pid, e))?;

    if !output.status.success() {
        if read_process_uid(pid).is_none() {
            return Ok(());
        }

        let stderr = String::from_utf8_lossy(&output.stderr).trim().to_string();
        let stdout = String::from_utf8_lossy(&output.stdout).trim().to_string();
        let detail = if !stderr.is_empty() {
            stderr
        } else if !stdout.is_empty() {
            stdout
        } else {
            format!("kill exited with status {}", output.status)
        };

        return Err(format!(
            "close_app failed for PID {} using {}: {}",
            pid, signal, detail
        ));
    }

    // Wait briefly for process termination to avoid returning success for stale/ignored signals.
    let verify_timeout = if force { Duration::from_secs(3) } else { Duration::from_secs(1) };
    let verify_started = Instant::now();
    loop {
        if read_process_uid(pid).is_none() {
            return Ok(());
        }

        if verify_started.elapsed() >= verify_timeout {
            if force {
                return Err(format!(
                    "close_app failed for PID {}: process still running after {}",
                    pid, signal
                ));
            }

            // Escalate TERM to KILL if process is still alive.
            let escalate_output = Command::new("kill")
                .arg("-KILL")
                .arg(pid.to_string())
                .output()
                .map_err(|e| format!("failed to invoke kill -KILL for PID {}: {}", pid, e))?;

            if !escalate_output.status.success() {
                if read_process_uid(pid).is_none() {
                    return Ok(());
                }

                let stderr = String::from_utf8_lossy(&escalate_output.stderr).trim().to_string();
                let stdout = String::from_utf8_lossy(&escalate_output.stdout).trim().to_string();
                let detail = if !stderr.is_empty() {
                    stderr
                } else if !stdout.is_empty() {
                    stdout
                } else {
                    format!("kill -KILL exited with status {}", escalate_output.status)
                };

                return Err(format!(
                    "close_app failed for PID {} while escalating to -KILL: {}",
                    pid, detail
                ));
            }

            let final_started = Instant::now();
            while final_started.elapsed() < Duration::from_secs(2) {
                if read_process_uid(pid).is_none() {
                    return Ok(());
                }
                std::thread::sleep(Duration::from_millis(50));
            }

            return Err(format!(
                "close_app failed for PID {}: process still running after -KILL",
                pid
            ));
        }

        std::thread::sleep(Duration::from_millis(50));
    }
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

#[cfg(test)]
mod tests {
    use super::*;
    use std::thread;
    use std::time::{Duration, Instant};

    fn parse_installed_apps() -> Vec<InstalledAppItem> {
        let payload = list_installed_apps_json().expect("list_installed_apps_json should return payload");
        serde_json::from_str::<Vec<InstalledAppItem>>(&payload)
            .expect("installed apps payload should be valid json array")
    }

    fn executable_token(exec: &str) -> Option<String> {
        let cleaned = clean_exec(exec);
        let parts = shlex::split(&cleaned)?;
        parts.first().cloned()
    }

    fn is_launch_candidate(app: &InstalledAppItem) -> bool {
        let Some(token) = executable_token(&app.exec) else {
            return false;
        };

        if token.trim().is_empty() {
            return false;
        }

        // Skip generic wrappers and Steam-style entries for integration safety.
        let lower = token.to_lowercase();
        if lower == "sh" || lower == "bash" || lower == "env" || lower == "flatpak" {
            return false;
        }

        let name = app.name.to_lowercase();
        let exec = app.exec.to_lowercase();
        if name.contains("steam")
            || exec.contains("steam")
            || exec.contains("proton")
            || name.contains("heroic")
            || exec.contains("heroic")
            || name.contains("lutris")
            || exec.contains("lutris")
        {
            return false;
        }

        true
    }

    fn is_files_candidate(app: &InstalledAppItem) -> bool {
        let name = app.name.to_lowercase();
        let exec = app.exec.to_lowercase();

        name == "files"
            || exec.contains("nautilus")
            || name.contains("dolphin")
            || exec.contains("dolphin")
    }

    fn is_calculator_candidate(app: &InstalledAppItem) -> bool {
        let name = app.name.to_lowercase();
        let exec = app.exec.to_lowercase();

        name.contains("calculator") || exec.contains("calculator")
    }

    fn files_preference_score(app: &InstalledAppItem) -> usize {
        let name = app.name.to_lowercase();
        let exec = app.exec.to_lowercase();

        // GNOME Files first, KDE Dolphin fallback.
        if name == "files" || exec.contains("nautilus") {
            0
        } else if name.contains("dolphin") || exec.contains("dolphin") {
            1
        } else {
            100
        }
    }

    fn pick_files_from_listing(apps: &[InstalledAppItem]) -> Option<InstalledAppItem> {
        let mut candidates: Vec<InstalledAppItem> = apps
            .iter()
            .filter(|app| is_launch_candidate(app) && is_files_candidate(app))
            .cloned()
            .collect();

        candidates.sort_by(|left, right| {
            files_preference_score(left)
                .cmp(&files_preference_score(right))
                .then_with(|| left.name.to_lowercase().cmp(&right.name.to_lowercase()))
        });

        candidates.into_iter().next()
    }

    fn pick_close_test_app_from_listing(apps: &[InstalledAppItem]) -> Option<InstalledAppItem> {
        let mut calculator_candidates: Vec<InstalledAppItem> = apps
            .iter()
            .filter(|app| is_launch_candidate(app) && is_calculator_candidate(app))
            .cloned()
            .collect();

        calculator_candidates
            .sort_by(|left, right| left.name.to_lowercase().cmp(&right.name.to_lowercase()));

        if let Some(candidate) = calculator_candidates.into_iter().next() {
            return Some(candidate);
        }

        pick_files_from_listing(apps)
    }

    fn process_matches_app(process: &sysinfo::Process, token: &str) -> bool {
        let process_name = process.name().to_lowercase();
        let cmdline = process
            .cmd()
            .iter()
            .map(|part| part.to_lowercase())
            .collect::<Vec<String>>()
            .join(" ");

        let token_basename = std::path::Path::new(token)
            .file_name()
            .and_then(|value| value.to_str())
            .unwrap_or(token)
            .to_lowercase();

        process_name.contains(&token_basename) || cmdline.contains(&token_basename)
    }

    fn count_matching_processes(system: &System, token: &str) -> usize {
        system
            .processes()
            .values()
            .filter(|process| process_matches_app(process, token))
            .count()
    }

    fn parse_running_apps() -> Vec<RunningAppItem> {
        let payload = list_running_apps_json().expect("list_running_apps_json should return payload");
        serde_json::from_str::<Vec<RunningAppItem>>(&payload)
            .expect("running apps payload should be valid json array")
    }

    fn find_running_group_for_app(app: &InstalledAppItem, groups: &[RunningAppItem]) -> Option<RunningAppItem> {
        let app_name = app.name.to_lowercase();
        let app_exec_token = executable_token(&app.exec)
            .unwrap_or_default()
            .to_lowercase();
        let app_exec_basename = std::path::Path::new(&app_exec_token)
            .file_name()
            .and_then(|value| value.to_str())
            .unwrap_or_default()
            .to_lowercase();

        groups.iter().find_map(|group| {
            let group_name = group.name.to_lowercase();
            let group_cmd = group.command_line.to_lowercase();
            let group_exe = group.exe.to_lowercase();

            let name_match = !app_name.is_empty() && (group_name.contains(&app_name) || app_name.contains(&group_name));
            let token_match = !app_exec_basename.is_empty()
                && (group_name.contains(&app_exec_basename)
                    || group_exe.contains(&app_exec_basename)
                    || group_cmd.contains(&app_exec_basename));

            if name_match || token_match {
                Some(RunningAppItem {
                    main_pid: group.main_pid,
                    pid: group.pid,
                    name: group.name.clone(),
                    exe: group.exe.clone(),
                    cpu_usage: group.cpu_usage,
                    memory_mb: group.memory_mb,
                    command_line: group.command_line.clone(),
                    process_count: group.process_count,
                    pids: group.pids.clone(),
                    has_visible_window: group.has_visible_window,
                })
            } else {
                None
            }
        })
    }

    fn close_group_with_graceful_then_force(group: &RunningAppItem) -> Result<(), String> {
        let pids: Vec<i32> = if group.pids.is_empty() {
            vec![group.main_pid.max(group.pid)]
        } else {
            group.pids.clone()
        };

        let payload = serde_json::json!({
            "pid": group.main_pid.max(group.pid),
            "pids": pids,
            "force": false
        })
        .to_string();
        close_app_linux(Some(payload))
    }

    #[test]
    fn list_installed_apps_returns_valid_json_array() {
        let parsed = parse_installed_apps();
        let preview = parsed
            .iter()
            .take(10)
            .map(|item| format!("{} ({})", item.name, item.exec))
            .collect::<Vec<String>>();

        eprintln!("[test:list_installed_apps] total={} first10={:?}", parsed.len(), preview);

        assert!(!parsed.is_empty(), "installed apps list should not be empty on a desktop Linux machine");
        assert!(parsed.len() <= 700, "installed apps should respect safety cap");
    }

    #[test]
    fn open_and_close_files_from_listed_apps_only() {
        let apps = parse_installed_apps();
        let listed_preview = apps
            .iter()
            .take(10)
            .map(|item| format!("{} ({})", item.name, item.exec))
            .collect::<Vec<String>>();
        eprintln!("[test:open-close-files] listed_first10={:?}", listed_preview);

        let target_app = pick_close_test_app_from_listing(&apps)
            .expect("expected Calculator or Files app from listed installed apps");

        eprintln!(
            "[test:open-close-files] selected_files='{}' exec='{}'",
            target_app.name,
            target_app.exec
        );

        let token = executable_token(&target_app.exec)
            .expect("selected app should have executable token");

        let mut system = System::new_all();
        system.refresh_processes();
        let baseline_count = count_matching_processes(&system, &token);

        let launch_payload = serde_json::json!({ "exec": target_app.exec }).to_string();
        launch_app_linux(Some(launch_payload))
            .unwrap_or_else(|error| panic!("failed to launch '{}' for close-app test: {}", target_app.name, error));

        let group_lookup_timeout = Duration::from_secs(12);
        let group_lookup_started = Instant::now();
        let matched_group = loop {
            let running_groups = parse_running_apps();
            if let Some(group) = find_running_group_for_app(&target_app, &running_groups) {
                break group;
            }

            if group_lookup_started.elapsed() >= group_lookup_timeout {
                panic!("failed to find running app group for '{}' after launch", target_app.name);
            }

            thread::sleep(Duration::from_millis(250));
        };

        eprintln!(
            "[test:open-close-files] matched_group name='{}' main_pid={} process_count={} pids={:?}",
            matched_group.name,
            matched_group.main_pid,
            matched_group.process_count,
            matched_group.pids
        );

        if let Err(error) = close_group_with_graceful_then_force(&matched_group) {
            eprintln!("[test:open-close-files] close failed='{}'", error);
            assert!(
                error.contains("close_app failed")
                    || error.contains("process still running")
                    || error.contains("persisted"),
                "files close failures must be actionable"
            );
            return;
        }

        let verify_timeout = Duration::from_secs(8);
        let started = Instant::now();
        let mut recovered_to_baseline = false;
        while started.elapsed() < verify_timeout {
            system.refresh_processes();
            let current_count = count_matching_processes(&system, &token);
            if current_count <= baseline_count {
                recovered_to_baseline = true;
                break;
            }
            thread::sleep(Duration::from_millis(200));
        }

        assert!(
            recovered_to_baseline,
            "app-level close should return matching process count to baseline"
        );

        eprintln!("[test:open-close-files] close succeeded for app token='{}'", token);
    }

    #[test]
    fn action_parser_supports_block_shutdown_and_restart_aliases() {
        assert_eq!(
            parse_local_action_type("block_screen").expect("block_screen should be supported"),
            LocalActionType::BlockScreen
        );
        assert_eq!(
            parse_local_action_type("shutdown").expect("shutdown should be supported"),
            LocalActionType::Shutdown
        );
        assert_eq!(
            parse_local_action_type("reboot").expect("reboot should be supported"),
            LocalActionType::Reboot
        );
        assert_eq!(
            parse_local_action_type("reset").expect("reset should map to reboot"),
            LocalActionType::Reboot
        );
    }

    #[test]
    fn action_parser_rejects_unknown_action() {
        let error = parse_local_action_type("unknown_action")
            .expect_err("unknown action should be rejected");
        assert_eq!(error, "Unsupported local action");
    }

    #[test]
    fn perform_local_action_rejects_unknown_action() {
        let result = tauri::async_runtime::block_on(perform_local_action(
            "unknown_action".to_string(),
            None,
        ));
        assert!(result.is_err(), "unknown action should return an error");
        assert_eq!(
            result.expect_err("unknown action must fail"),
            "Unsupported local action"
        );
    }

    // Manual test: this exercises the same command path used by the app UI.
    #[test]
    #[ignore = "manual test only: this will lock/suspend the current desktop session"]
    fn block_screen_action_manual_smoke_test() {
        let result = tauri::async_runtime::block_on(perform_local_action(
            "block_screen".to_string(),
            None,
        ));
        assert!(result.is_ok(), "block_screen action should succeed when desktop APIs allow it");
    }

    // Disabled by default so `cargo test` never powers off a developer machine unexpectedly.
    #[test]
    #[ignore = "manual test only: this can power off the computer"]
    fn shutdown_action_manual_smoke_test() {
        // Call the app command path, not the low-level helper directly.
        let result = tauri::async_runtime::block_on(perform_local_action(
            "shutdown".to_string(),
            None,
        ));
        assert!(result.is_ok(), "shutdown action should succeed when policy allows it");
    }

    // Disabled by default so `cargo test` never reboots a developer machine unexpectedly.
    #[test]
    #[ignore = "manual test only: this can reboot the computer"]
    fn reboot_action_manual_smoke_test() {
        // Call the app command path, not the low-level helper directly.
        let result = tauri::async_runtime::block_on(perform_local_action(
            "reboot".to_string(),
            None,
        ));
        assert!(result.is_ok(), "reboot action should succeed when policy allows it");
    }
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

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
enum LocalActionType {
    BlockScreen,
    Shutdown,
    Reboot,
    ListInstalledApps,
    ListRunningApps,
    LaunchApp,
    CloseApp,
    GetClipboardHistory,
}

fn parse_local_action_type(action_type: &str) -> Result<LocalActionType, String> {
    match action_type {
        "block_screen" => Ok(LocalActionType::BlockScreen),
        "shutdown" => Ok(LocalActionType::Shutdown),
        "reboot" | "reset" => Ok(LocalActionType::Reboot),
        "list_installed_apps" => Ok(LocalActionType::ListInstalledApps),
        "list_running_apps" => Ok(LocalActionType::ListRunningApps),
        "launch_app" => Ok(LocalActionType::LaunchApp),
        "close_app" => Ok(LocalActionType::CloseApp),
        "get_clipboard_history" => Ok(LocalActionType::GetClipboardHistory),
        _ => Err("Unsupported local action".to_string()),
    }
}

#[tauri::command]
async fn perform_local_action(action_type: String, payload_json: Option<String>) -> Result<String, String> {
    let started_at = Instant::now();
    let _ = append_log_line(
        "info",
        "rust:actions",
        "perform_local_action requested",
        Some(&format!("action_type={}", action_type)),
    );

    let action_result = match parse_local_action_type(action_type.as_str())? {
        LocalActionType::BlockScreen => {
            block_screen_linux()?;
            Ok("{}".to_string())
        }
        LocalActionType::Shutdown => {
            shutdown_linux()?;
            Ok("{}".to_string())
        }
        LocalActionType::Reboot => {
            reboot_linux()?;
            Ok("{}".to_string())
        }
        LocalActionType::ListInstalledApps => list_installed_apps_json(),
        LocalActionType::ListRunningApps => list_running_apps_json(),
        LocalActionType::LaunchApp => {
            launch_app_linux(payload_json)?;
            Ok("{}".to_string())
        }
        LocalActionType::CloseApp => close_app_linux(payload_json)
            .map_err(|e| format!("close_app failed: {}", e))
            .map(|_| "{}".to_string()),
        LocalActionType::GetClipboardHistory => list_clipboard_history_json(),
    };

    if let Err(error) = &action_result {
        let _ = append_log_line(
            "error",
            "rust:actions",
            "perform_local_action failed",
            Some(&format!("error={} elapsed_ms={}", error, started_at.elapsed().as_millis())),
        );
    } else {
        let _ = append_log_line(
            "info",
            "rust:actions",
            "perform_local_action completed",
            Some(&format!("elapsed_ms={}", started_at.elapsed().as_millis())),
        );
    }

    action_result
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
