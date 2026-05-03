use serde::Deserialize;
use std::io::{BufRead, BufReader, Write};
use std::net::TcpListener;
use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use rand::{distributions::Alphanumeric, Rng};
use reqwest::blocking::Client;
use sha2::{Digest, Sha256};
use url::Url;

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

pub fn is_running_as_root() -> bool {
    std::process::Command::new("id").arg("-u").output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim() == "0").unwrap_or(false)
}

pub fn open_url_with_fallback(url: &str) -> Result<(), String> {
    if webbrowser::open(url).is_err() {
        if std::process::Command::new("xdg-open").arg(url).status().is_err() {
             return Err("Failed to open browser".to_string());
        }
    }
    Ok(())
}

#[tauri::command]
pub async fn oauth_get_provider_token(
    provider: String,
    client_id: String,
    redirect_uri: String,
    google_client_secret: Option<String>,
    microsoft_tenant_id: Option<String>,
    microsoft_scope: Option<String>,
) -> Result<String, String> {
    let provider = OAuthProvider::parse(&provider)?;
    if is_running_as_root() { return Err("OAuth disabled in root mode".to_string()); }

    tauri::async_runtime::spawn_blocking(move || {
        let redirect = Url::parse(&redirect_uri).map_err(|e| e.to_string())?;
        let host = redirect.host_str().unwrap_or("localhost");
        let port = redirect.port_or_known_default().unwrap_or(80);
        let listener = TcpListener::bind(format!("{}:{}", host, port)).map_err(|e| e.to_string())?;

        let state: String = rand::thread_rng().sample_iter(&Alphanumeric).take(32).map(char::from).collect();
        let code_verifier: String = rand::thread_rng().sample_iter(&Alphanumeric).take(96).map(char::from).collect();
        let code_challenge = URL_SAFE_NO_PAD.encode(Sha256::digest(code_verifier.as_bytes()));

        let (auth_url, token_url, scope) = match provider {
            OAuthProvider::Google => {
                let s = "openid email profile".to_string();
                (format!("https://accounts.google.com/o/oauth2/v2/auth?client_id={}&response_type=code&redirect_uri={}&scope={}&code_challenge={}&code_challenge_method=S256&state={}&access_type=offline&prompt=select_account", client_id, redirect_uri, s, code_challenge, state), "https://oauth2.googleapis.com/token".to_string(), s)
            }
            OAuthProvider::Microsoft => {
                let t = microsoft_tenant_id.unwrap_or_else(|| "common".to_string());
                let s = microsoft_scope.unwrap_or_else(|| format!("api://{}/access_as_user openid profile email offline_access", client_id));
                (format!("https://login.microsoftonline.com/{}/oauth2/v2.0/authorize?client_id={}&response_type=code&redirect_uri={}&response_mode=query&scope={}&code_challenge={}&code_challenge_method=S256&state={}", t, client_id, redirect_uri, s, code_challenge, state), format!("https://login.microsoftonline.com/{}/oauth2/v2.0/token", t), s)
            }
        };

        open_url_with_fallback(&auth_url)?;
        let (mut stream, _) = listener.accept().map_err(|e| e.to_string())?;
        let mut reader = BufReader::new(&mut stream);
        let mut request_line = String::new();
        reader.read_line(&mut request_line).map_err(|e| e.to_string())?;
        let requested_path = request_line.split_whitespace().nth(1).unwrap_or("/");
        let callback_url = format!("http://localhost{}", requested_path);
        let parsed = Url::parse(&callback_url).map_err(|e| e.to_string())?;

        let mut code = String::new();
        for (k, v) in parsed.query_pairs() {
            if k == "code" { code = v.to_string(); }
        }

        let http_client = Client::new();
        let mut form = vec![("grant_type", "authorization_code"), ("code", &code), ("redirect_uri", &redirect_uri), ("client_id", &client_id), ("code_verifier", &code_verifier)];
        if let Some(s) = google_client_secret.as_ref() { form.push(("client_secret", s)); }
        
        let token_res: OAuthTokenResponse = http_client.post(token_url).form(&form).send().map_err(|e| e.to_string())?.json().map_err(|e| e.to_string())?;
        
        let res = match provider {
            OAuthProvider::Google => token_res.id_token,
            OAuthProvider::Microsoft => token_res.access_token.or(token_res.id_token),
        }.ok_or("No token returned")?;

        let body = "Auth complete. You can close this window.";
        let _ = stream.write_all(format!("HTTP/1.1 200 OK\r\nContent-Length: {}\r\n\r\n{}", body.len(), body).as_bytes());
        Ok(res)
    }).await.map_err(|e| e.to_string())?
}

#[tauri::command]
pub async fn oauth_get_auth_url(_provider: String) -> Result<String, String> { Err("Not implemented".to_string()) }
#[tauri::command]
pub async fn oauth_get_refresh_token(_provider: String, _refresh_token: String) -> Result<String, String> { Err("Not implemented".to_string()) }
#[tauri::command]
pub async fn oauth_logout(_provider: String) -> Result<(), String> { Ok(()) }
