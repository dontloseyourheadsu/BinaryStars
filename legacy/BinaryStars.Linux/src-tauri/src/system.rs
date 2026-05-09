use std::collections::{HashMap, HashSet};
use std::fs;
use std::net::IpAddr;
use std::path::Path;
use std::process::Command;
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant};
use freedesktop_desktop_entry::DesktopEntry;
use sysinfo::{System, Networks};
use crate::types::{InstalledAppItem, RunningAppItem, DeviceInfo, InterfaceInfo, ElevationStatus, RunningProcessSnapshot};
use crate::logging::{append_log_line};

static INSTALLED_APPS_JSON_CACHE: OnceLock<Mutex<Option<String>>> = OnceLock::new();

pub fn installed_apps_cache() -> &'static Mutex<Option<String>> {
    INSTALLED_APPS_JSON_CACHE.get_or_init(|| Mutex::new(None))
}

pub fn current_desktop() -> String {
    std::env::var("XDG_CURRENT_DESKTOP")
        .unwrap_or_default()
        .to_ascii_lowercase()
}

pub fn try_command(program: &str, args: &[&str]) -> Result<(), String> {
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
    if output.status.success() { Ok(()) }
    else {
        let stderr = String::from_utf8(output.stderr).unwrap_or_default();
        let stdout = String::from_utf8(output.stdout).unwrap_or_default();
        let detail = if !stderr.trim().is_empty() { stderr.trim().to_string() } else { stdout.trim().to_string() };
        Err(format!("{} exited with status {}: {}", program, output.status, detail))
    }
}

pub fn read_command_output(program: &str, args: &[&str]) -> Result<String, String> {
    let output = Command::new(program).args(args).output();
    let output = match output {
        Ok(value) => value,
        Err(error) => {
            if error.kind() == std::io::ErrorKind::NotFound { return Err(format!("missing:{}", program)); }
            return Err(format!("{} failed to start: {}", program, error));
        }
    };
    if output.status.success() { Ok(String::from_utf8(output.stdout).unwrap_or_default()) }
    else {
        let stderr = String::from_utf8(output.stderr).unwrap_or_default();
        let stdout = String::from_utf8(output.stdout).unwrap_or_default();
        let detail = if !stderr.trim().is_empty() { stderr.trim().to_string() } else { stdout.trim().to_string() };
        Err(format!("{} exited with status {}: {}", program, output.status, detail))
    }
}

pub fn block_screen_linux() -> Result<(), String> {
    if try_command("loginctl", &["lock-session"]).is_ok() { return Ok(()); }
    let desktop = current_desktop();
    let mut candidates = vec![("xdg-screensaver", vec!["lock"])];
    if desktop.contains("gnome") {
        candidates.push(("gdbus", vec!["call", "--session", "--dest", "org.gnome.ScreenSaver", "--object-path", "/org/gnome/ScreenSaver", "--method", "org.gnome.ScreenSaver.Lock"]));
    }
    if desktop.contains("kde") || desktop.contains("plasma") {
        candidates.push(("qdbus", vec!["org.kde.screensaver", "/ScreenSaver", "org.kde.screensaver.Lock"]));
    }
    for (prog, args) in candidates {
        if try_command(prog, &args).is_ok() { return Ok(()); }
    }
    Err("failed to lock screen".to_string())
}

pub fn shutdown_linux() -> Result<(), String> { try_command("systemctl", &["poweroff"]) }
pub fn reboot_linux() -> Result<(), String> { try_command("systemctl", &["reboot"]) }

fn read_battery_percent() -> Option<u8> {
    let power_supply = Path::new("/sys/class/power_supply");
    let entries = fs::read_dir(power_supply).ok()?;
    for entry in entries.flatten() {
        let capacity_path = entry.path().join("capacity");
        let raw = fs::read_to_string(capacity_path).ok()?;
        let parsed = raw.trim().parse::<i32>().ok()?;
        if (0..=100).contains(&parsed) { return Some(parsed as u8); }
    }
    None
}

fn format_kbps(bytes_per_sec: u64) -> String {
    let kbps = (bytes_per_sec.saturating_mul(8)) / 1000;
    format!("{} kbps", kbps)
}

pub fn trigger_linux_notification(title: &str, body: &str) -> Result<(), String> {
    let desktop = current_desktop();
    let _ = append_log_line("info", "rust:notification", "trigger_linux_notification requested", Some(&format!("desktop={} title={}", desktop, title)));
    
    // We try multiple commands to ensure notification reaches the user regardless of DE specific configurations.
    let mut success = false;

    if desktop.contains("gnome") {
        if try_command("gdbus", &["call", "--session", "--dest", "org.freedesktop.Notifications", "--object-path", "/org/freedesktop/Notifications", "--method", "org.freedesktop.Notifications.Notify", "BinaryStars", "0", "dialog-information", title, body, "[]", "{}", "3000"]).is_ok() {
            success = true;
        }
    } else if desktop.contains("kde") || desktop.contains("plasma") {
        if try_command("qdbus", &["org.freedesktop.Notifications", "/org/freedesktop/Notifications", "org.freedesktop.Notifications.Notify", "BinaryStars", "0", "dialog-information", title, body, "[]", "{}", "3000"]).is_ok() {
            success = true;
        }
    }
    
    // Generic fallback
    if !success {
        if try_command("notify-send", &[title, body]).is_ok() {
            success = true;
        }
    }

    if success { Ok(()) } else { Err("Failed to send notification via any available command".to_string()) }
}

#[tauri::command]
pub fn show_notification(title: String, body: String) -> Result<(), String> {
    trigger_linux_notification(&title, &body)
}

#[tauri::command]
pub async fn get_elevation_status() -> Result<ElevationStatus, String> {
    Ok(ElevationStatus { is_root: Command::new("id").arg("-u").output().map(|o| String::from_utf8_lossy(&o.stdout).trim() == "0").unwrap_or(false) })
}

#[tauri::command]
pub async fn request_elevated_mode() -> Result<(), String> { Err("sudo mode relaunch is deprecated".to_string()) }

#[tauri::command]
pub async fn perform_local_action(action_type: String, payload_json: Option<String>) -> Result<String, String> {
    let started_at = Instant::now();
    let _ = append_log_line("info", "rust:actions", "perform_local_action requested", Some(&format!("action_type={}", action_type)));
    let action_result = match parse_local_action_type(action_type.as_str())? {
        LocalActionType::BlockScreen => { block_screen_linux()?; Ok("{}".to_string()) }
        LocalActionType::Shutdown => { shutdown_linux()?; Ok("{}".to_string()) }
        LocalActionType::Reboot => { reboot_linux()?; Ok("{}".to_string()) }
        LocalActionType::ListInstalledApps => list_installed_apps_json(),
        LocalActionType::ListRunningApps => list_running_apps_json(),
        LocalActionType::LaunchApp => { launch_app_linux(payload_json)?; Ok("{}".to_string()) }
        LocalActionType::CloseApp => close_app_linux(payload_json).map(|_| "{}".to_string()),
    };
    let _ = append_log_line("info", "rust:actions", "perform_local_action completed", Some(&format!("elapsed_ms={}", started_at.elapsed().as_millis())));
    action_result
}

#[tauri::command]
pub async fn get_device_info() -> Result<DeviceInfo, String> {
    tauri::async_runtime::spawn_blocking(move || {
        let hostname = hostname::get().map(|h| h.into_string().unwrap_or_default()).unwrap_or_else(|_| "unknown".to_string());
        let ifaces = if_addrs::get_if_addrs().map_err(|e| format!("if_addrs error: {}", e))?;
        
        let mut sys = System::new_all();
        sys.refresh_cpu();
        std::thread::sleep(Duration::from_millis(250));
        sys.refresh_cpu();
        sys.refresh_memory();

        let cpu_load = sys.global_cpu_info().cpu_usage() as u8;
        let mem_load = if sys.total_memory() == 0 { 0 } else { (sys.used_memory() as f64 / sys.total_memory() as f64 * 100.0) as u8 };
        
        let mut networks = Networks::new_with_refreshed_list();
        let mut initial: HashMap<String, (u64, u64)> = HashMap::new();
        for (name, net) in &networks { initial.insert(name.to_string(), (net.received(), net.transmitted())); }
        std::thread::sleep(Duration::from_secs(1));
        networks.refresh();

        let mut ipv4_address = "127.0.0.1".to_string();
        let mut ipv6_address = "::1".to_string();
        let mut interfaces = Vec::new();
        let mut total_rx = 0u64;
        let mut total_tx = 0u64;

        for iface in ifaces {
            let ip = iface.ip();
            match ip {
                IpAddr::V4(v4) if !v4.is_loopback() && ipv4_address == "127.0.0.1" => ipv4_address = v4.to_string(),
                IpAddr::V6(v6) if !v6.is_loopback() && ipv6_address == "::1" => ipv6_address = v6.to_string(),
                _ => {}
            }
            if let Some((r0, t0)) = initial.get(&iface.name) {
                if let Some(net) = networks.get(&iface.name) {
                    let rx = net.received().saturating_sub(*r0);
                    let tx = net.transmitted().saturating_sub(*t0);
                    total_rx += rx; total_tx += tx;
                    interfaces.push(InterfaceInfo { name: iface.name, ips: vec![ip.to_string()], rx_bytes_per_sec: rx, tx_bytes_per_sec: tx });
                }
            }
        }

        Ok(DeviceInfo {
            hostname, ip_address: ipv4_address, ipv6_address,
            battery_level: read_battery_percent(), cpu_load_percent: Some(cpu_load), memory_load_percent: Some(mem_load),
            wifi_upload_speed: format_kbps(total_tx), wifi_download_speed: format_kbps(total_rx),
            interfaces
        })
    }).await.map_err(|e| format!("spawn error: {}", e))?
}

pub fn list_installed_apps_json() -> Result<String, String> {
    let mut cache = installed_apps_cache().lock().unwrap();
    if let Some(val) = &*cache { return Ok(val.clone()); }
    let mut apps = Vec::new();
    let paths = ["/usr/share/applications", "/usr/local/share/applications"];
    for path in paths {
        if let Ok(entries) = fs::read_dir(path) {
            for entry in entries.flatten() {
                if let Ok(content) = fs::read_to_string(entry.path()) {
                    if let Some(desktop) = DesktopEntry::decode(&entry.path(), &content).ok() {
                        apps.push(InstalledAppItem {
                            name: desktop.name(None).map(|v| v.to_string()).unwrap_or_default(),
                            exec: desktop.exec().map(|v| v.to_string()).unwrap_or_default(),
                            icon: desktop.icon().map(|v| v.to_string()),
                            categories: desktop.categories().map(|v| v.to_string()),
                            no_display: desktop.no_display(),
                        });
                    }
                }
            }
        }
    }
    let json = serde_json::to_string(&apps).map_err(|e| e.to_string())?;
    *cache = Some(json.clone());
    Ok(json)
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
    if let Ok(output) = output {
        if output.status.success() {
            let stdout = String::from_utf8_lossy(&output.stdout);
            for line in stdout.lines() {
                let mut parts = line.split_whitespace();
                let _ = parts.next(); let _ = parts.next();
                if let Some(pid_text) = parts.next() {
                    if let Ok(pid) = pid_text.parse::<u32>() { if pid > 0 { result.insert(pid); } }
                }
            }
        }
    }
    result
}

fn resolve_group_leader_pid(pid: u32, parent_by_pid: &HashMap<u32, u32>, window_owner_pids: &HashSet<u32>) -> u32 {
    if window_owner_pids.contains(&pid) { return pid; }
    let mut current = pid;
    let mut last_known = pid;
    let mut guard = 0usize;
    while guard < 64 {
        guard += 1;
        let Some(parent) = parent_by_pid.get(&current).copied() else { break; };
        if parent == 0 || parent == current { break; }
        last_known = parent;
        if window_owner_pids.contains(&parent) { return parent; }
        current = parent;
    }
    last_known
}

fn is_generic_exec_token(token: &str) -> bool {
    matches!(token, "sh" | "bash" | "env" | "flatpak" | "python" | "python3" | "java" | "systemd" | "gdbus" | "dbus-daemon")
}

fn exec_primary_token(exec: &str) -> Option<String> {
    let cleaned = clean_exec(exec);
    let parts = shlex::split(&cleaned)?;
    let program = parts.first()?;
    let token = Path::new(program).file_name().and_then(|v| v.to_str())?.trim().to_lowercase();
    if token.is_empty() || token.len() < 3 || is_generic_exec_token(token.as_str()) { return None; }
    Some(token)
}

fn process_identity_tokens(process: &RunningProcessSnapshot) -> HashSet<String> {
    let mut tokens: HashSet<String> = HashSet::new();
    let name = process.name.trim().to_lowercase();
    if !name.is_empty() { tokens.insert(name); }
    let exe = Path::new(&process.exe).file_name().and_then(|v| v.to_str()).unwrap_or_default().trim().to_lowercase();
    if !exe.is_empty() { tokens.insert(exe); }
    let cmd_parts = shlex::split(&process.command_line).unwrap_or_else(|| process.command_line.split_whitespace().map(|s| s.to_string()).collect());
    for part in cmd_parts {
        let raw = part.trim().to_lowercase();
        if raw.is_empty() { continue; }
        let basename = Path::new(&raw).file_name().and_then(|v| v.to_str()).unwrap_or_default().trim().to_lowercase();
        if !basename.is_empty() { tokens.insert(basename); }
    }
    tokens
}

fn build_token_grouped_running_apps(processes: &[RunningProcessSnapshot]) -> Option<Vec<RunningAppItem>> {
    let payload = list_installed_apps_json().ok()?;
    let installed_apps = serde_json::from_str::<Vec<InstalledAppItem>>(&payload).ok()?;
    let mut token_to_name: HashMap<String, String> = HashMap::new();
    for app in installed_apps {
        if let Some(token) = exec_primary_token(&app.exec) { token_to_name.entry(token).or_insert(app.name); }
    }
    if token_to_name.is_empty() { return None; }
    let mut grouped: HashMap<String, Vec<RunningProcessSnapshot>> = HashMap::new();
    for process in processes {
        let identities = process_identity_tokens(process);
        for id in identities { if token_to_name.contains_key(&id) { grouped.entry(id).or_default().push(process.clone()); } }
    }
    let mut items: Vec<RunningAppItem> = grouped.into_iter().filter_map(|(token, mut members)| {
        if members.is_empty() { return None; }
        members.sort_by_key(|m| m.pid);
        let main = members.first()?.clone();
        let mut pids: Vec<i32> = members.iter().map(|m| m.pid as i32).collect();
        pids.sort_unstable(); pids.dedup();
        let cpu = members.iter().map(|m| m.cpu_usage).sum::<f32>();
        let mem = members.iter().map(|m| m.memory_mb).sum::<f64>();
        Some(RunningAppItem {
            main_pid: main.pid as i32, pid: main.pid as i32,
            name: token_to_name.get(&token).cloned().unwrap_or_else(|| main.name.clone()),
            exe: main.exe, cpu_usage: cpu, memory_mb: mem, command_line: main.command_line,
            process_count: pids.len(), pids, has_visible_window: false,
        })
    }).collect();
    if items.is_empty() { return None; }
    items.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    Some(items)
}

pub fn list_running_apps_json() -> Result<String, String> {
    let mut sys = System::new_all();
    sys.refresh_processes();
    let current_uid = read_current_uid();
    let processes: Vec<RunningProcessSnapshot> = sys.processes().values()
        .filter(|p| {
            let Some(uid) = current_uid else { return false; };
            read_process_uid(p.pid().as_u32()) == Some(uid)
        })
        .map(|p| RunningProcessSnapshot {
            pid: p.pid().as_u32(), ppid: read_process_ppid(p.pid().as_u32()).unwrap_or(0),
            name: p.name().to_string(),
            exe: p.exe().map(|path| path.to_string_lossy().into_owned()).unwrap_or_default(),
            cpu_usage: p.cpu_usage(), memory_mb: p.memory() as f64 / 1024.0 / 1024.0,
            command_line: p.cmd().iter().map(|s| s.to_string()).collect::<Vec<_>>().join(" "),
        })
        .collect();

    if let Some(items) = build_token_grouped_running_apps(&processes) { return serde_json::to_string(&items).map_err(|e| e.to_string()); }

    let window_owner_pids = detect_window_owner_pids();
    let parent_by_pid: HashMap<u32, u32> = processes.iter().map(|p| (p.pid, p.ppid)).collect();
    let process_by_pid: HashMap<u32, RunningProcessSnapshot> = processes.iter().map(|p| (p.pid, p.clone())).collect();
    let mut grouped_by_leader: HashMap<u32, Vec<RunningProcessSnapshot>> = HashMap::new();
    for process in processes {
        let leader = resolve_group_leader_pid(process.pid, &parent_by_pid, &window_owner_pids);
        grouped_by_leader.entry(leader).or_default().push(process);
    }
    let mut items: Vec<RunningAppItem> = grouped_by_leader.into_iter().map(|(leader_pid, mut members)| {
        members.sort_by_key(|m| m.pid);
        let leader = process_by_pid.get(&leader_pid).cloned().unwrap_or_else(|| members[0].clone());
        let mut pids: Vec<i32> = members.iter().map(|m| m.pid as i32).collect();
        pids.sort_unstable(); pids.dedup();
        RunningAppItem {
            main_pid: leader.pid as i32, pid: leader.pid as i32, name: leader.name, exe: leader.exe,
            cpu_usage: members.iter().map(|m| m.cpu_usage).sum(),
            memory_mb: members.iter().map(|m| m.memory_mb).sum(),
            command_line: leader.command_line, process_count: pids.len(), pids,
            has_visible_window: window_owner_pids.contains(&leader.pid),
        }
    }).collect();
    if items.iter().any(|i| i.has_visible_window) { items.retain(|i| i.has_visible_window); }
    items.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    serde_json::to_string(&items).map_err(|e| e.to_string())
}

pub fn clean_exec(exec: &str) -> String {
    let mut out = String::new();
    let mut chars = exec.chars().peekable();
    while let Some(ch) = chars.next() {
        if ch == '%' { let _ = chars.next(); continue; }
        out.push(ch);
    }
    out.trim().to_string()
}

pub fn launch_app_linux(payload_json: Option<String>) -> Result<(), String> {
    let payload: serde_json::Value = serde_json::from_str(&payload_json.unwrap_or_default()).map_err(|e| e.to_string())?;
    let exec = payload["exec"].as_str().ok_or("missing exec")?;
    let cleaned = clean_exec(exec);
    let parts = shlex::split(&cleaned).ok_or("invalid exec format")?;
    if parts.is_empty() { return Err("empty exec".to_string()); }
    Command::new(&parts[0]).args(&parts[1..]).spawn().map_err(|e| e.to_string())?;
    Ok(())
}

pub fn close_app_linux(payload_json: Option<String>) -> Result<(), String> {
    let payload: serde_json::Value = serde_json::from_str(&payload_json.unwrap_or_default()).map_err(|e| e.to_string())?;
    let pids = payload["pids"].as_array().ok_or("missing pids")?;
    for pid_val in pids {
        if let Some(pid) = pid_val.as_i64() {
            let _ = Command::new("kill").arg("-9").arg(pid.to_string()).status();
        }
    }
    Ok(())
}

#[derive(Debug, PartialEq)]
pub enum LocalActionType { BlockScreen, Shutdown, Reboot, ListInstalledApps, ListRunningApps, LaunchApp, CloseApp }

pub fn parse_local_action_type(action_type: &str) -> Result<LocalActionType, String> {
    match action_type {
        "block_screen" => Ok(LocalActionType::BlockScreen),
        "shutdown" => Ok(LocalActionType::Shutdown),
        "reboot" | "reset" => Ok(LocalActionType::Reboot),
        "list_installed_apps" => Ok(LocalActionType::ListInstalledApps),
        "list_running_apps" => Ok(LocalActionType::ListRunningApps),
        "launch_app" => Ok(LocalActionType::LaunchApp),
        "close_app" => Ok(LocalActionType::CloseApp),
        _ => Err("Unsupported local action".to_string()),
    }
}
