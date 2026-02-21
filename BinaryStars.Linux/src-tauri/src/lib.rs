use serde::Serialize;
use std::collections::{BTreeMap, HashMap};
use std::io::{BufRead, BufReader, Write};
use std::net::IpAddr;
use std::net::TcpListener;
use std::time::Duration;
use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use rand::{distributions::Alphanumeric, Rng};
use reqwest::blocking::Client;
use serde::Deserialize;
use sha2::{Digest, Sha256};
use url::Url;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![get_device_info, oauth_get_provider_token])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
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
    microsoft_tenant_id: Option<String>,
    microsoft_scope: Option<String>,
) -> Result<String, String> {
    let provider = OAuthProvider::parse(&provider)?;

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

        webbrowser::open(&auth_url)
            .map_err(|e| format!("Could not open browser for OAuth login: {}", e))?;

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

        let response_body = if oauth_error.is_some() {
            "Authentication failed. You can close this window."
        } else {
            "Authentication completed. You can close this window and return to BinaryStars."
        };

        let response = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            response_body.len(),
            response_body
        );
        stream
            .write_all(response.as_bytes())
            .map_err(|e| format!("Failed writing OAuth callback response: {}", e))?;

        if let Some(error) = oauth_error {
            return Err(format!("OAuth provider returned error: {}", error));
        }

        let callback_state = callback_state.ok_or_else(|| "OAuth callback missing state".to_string())?;
        if callback_state != state {
            return Err("OAuth state validation failed".to_string());
        }

        let code = auth_code.ok_or_else(|| "OAuth callback missing authorization code".to_string())?;

        let http_client = Client::builder()
            .timeout(Duration::from_secs(45))
            .build()
            .map_err(|e| format!("Failed creating HTTP client: {}", e))?;

        let token_response = http_client
            .post(token_url)
            .form(&[
                ("grant_type", "authorization_code"),
                ("code", code.as_str()),
                ("redirect_uri", redirect_uri.as_str()),
                ("client_id", client_id.as_str()),
                ("code_verifier", code_verifier.as_str()),
                ("scope", scope.as_str()),
            ])
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
    })
    .await
    .map_err(|e| format!("OAuth task failed: {}", e))?
}
